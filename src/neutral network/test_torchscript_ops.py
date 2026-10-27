"""检查导出后逐步计算与原始模型的一致性；可生成 Java 对照数据。"""

import argparse
from pathlib import Path
import struct
import tempfile
import unittest

import torch

from export_torchscript_ops import ROOT, DECODER_PREFIXES, export, layer_methods, load_model
import torch.nn.functional as F


def encode(ops, model, image):
    # 池化与位置编码不属于此次导出范围，直接使用 Python 原操作。
    x = image
    for conv, norm, pool in [('conv', 'norm', True), ('conv2', 'norm2', True),
                             ('conv3', 'norm3', True), ('con4', 'norm4', False),
                             ('con5', 'norm5', False)]:
        x = getattr(ops, 'cnn_' + norm)(getattr(ops, 'cnn_' + conv)(x))
        if conv != 'con5':
            x = ops.silu(x)
        if pool:
            x = F.max_pool2d(x, 2, 2)
    return model.encode(model.cnn.position_embedding(x))


def decode(ops, model, tokens, memory, padding, memory_padding):
    x = model.token_embedding(tokens) * 16 + model.token_pos[:, :tokens.size(1)]
    for prefix in ['decoder1', 'decoder2', 'decoder3']:
        x = getattr(ops, prefix)(x, memory, padding, memory_padding)
    return ops.head(ops.final_norm(x))


def judge(ops, model, tokens, memory, memory_padding):
    x = model.token_embedding(tokens) * 16 + model.token_pos[:, :tokens.size(1)]
    x = ops.judge_decoder(x, memory, tokens.eq(67), memory_padding)
    return F.softmax(ops.test(ops.silu(ops.process(ops.judge_norm(x)))), dim=-1)


def cases():
    rng = torch.Generator().manual_seed(49)
    for batch, width, length in [(2, 32, 3), (1, 80, 5)]:
        image = torch.rand(batch, 3, 128, width, generator=rng)
        tokens = torch.randint(0, 67, (batch, length), generator=rng)
        tokens[:, 0] = 68
        tokens[:, -1] = 67
        yield image, tokens


class ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        cls.model = load_model(ROOT / 'checkpoints/model.pt')
        # 测试实际保存并重新加载后的文件，不仅测试内存中的 ScriptModule。
        cls.ops = export(ROOT / 'checkpoints/model.pt', Path(cls.directory.name) / 'ops.pt')

    def test_all_layers_match_checkpoint(self):
        with torch.inference_mode():
            for name, (_, layer) in layer_methods(self.model).items():
                with self.subTest(method=name):
                    if isinstance(layer, torch.nn.Conv2d):
                        x = torch.randn(2, layer.in_channels, 9, 13)
                    elif isinstance(layer, torch.nn.BatchNorm2d):
                        x = torch.randn(2, layer.num_features, 9, 13)
                    elif isinstance(layer, torch.nn.Linear):
                        x = torch.randn(2, 3, layer.in_features)
                    elif isinstance(layer, torch.nn.Embedding):
                        x = torch.tensor([[68, 4, 67]])
                    else:
                        x = torch.randn(2, 3, 256)
                    torch.testing.assert_close(getattr(self.ops, name)(x), layer(x))

    def test_complete_decoders_match_original(self):
        rng = torch.Generator().manual_seed(76)
        empty = torch.empty(0, dtype=torch.bool)
        with torch.inference_mode():
            for batch, tq, tk in [(2, 3, 7), (1, 5, 2), (1, 1, 3)]:
                x = torch.randn(batch, tq, 256, generator=rng)
                memory = torch.randn(batch, tk, 256, generator=rng)
                token_padding = torch.zeros(batch, tq, dtype=torch.bool)
                if tq > 1:
                    token_padding[:, -1] = True
                memory_padding = torch.zeros(batch, tk, dtype=torch.bool)
                memory_padding[:, -1] = True
                for name in DECODER_PREFIXES:
                    layer, method = getattr(self.model, name), getattr(self.ops, name)
                    with self.subTest(method=name, shape=(batch, tq, tk)):
                        torch.testing.assert_close(method(x, memory), layer(x, memory))
                        torch.testing.assert_close(method(x, memory, empty, empty), layer(x, memory))
                        for use_tokens, use_memory in [(True, False), (False, True), (True, True)]:
                            tm = token_padding if use_tokens else None
                            mm = memory_padding if use_memory else None
                            expected = layer(x, memory, tm, mm)
                            torch.testing.assert_close(method(x, memory, tm, mm), expected)
                            torch.testing.assert_close(method(x, memory, tm if tm is not None else empty,
                                                              mm if mm is not None else empty), expected)

    def test_causal_mask_is_inside_decoder(self):
        rng = torch.Generator().manual_seed(81)
        with torch.inference_mode():
            x = torch.randn(1, 5, 256, generator=rng)
            memory = torch.randn(1, 7, 256, generator=rng)
            changed = x.clone()
            changed[:, 3:] += 20 * torch.randn(1, 2, 256, generator=rng)
            for name in DECODER_PREFIXES:
                method = getattr(self.ops, name)
                torch.testing.assert_close(method(x, memory)[:, :3], method(changed, memory)[:, :3])

    def test_silu_matches_original(self):
        x = torch.linspace(-10, 10, 24).reshape(2, 3, 4)
        torch.testing.assert_close(self.ops.silu(x), F.silu(x))

    def test_full_stepwise_outputs_and_masks(self):
        with torch.inference_mode():
            for image, tokens in cases():
                memory = self.model.encode_image(image)
                actual_memory = encode(self.ops, self.model, image)
                torch.testing.assert_close(actual_memory, memory)
                padding = tokens.eq(67)
                memory_padding = torch.zeros(memory.shape[:2], dtype=torch.bool)
                memory_padding[:, -1] = True
                for masked in (False, True):
                    token_mask = padding if masked else None
                    image_mask = memory_padding if masked else None
                    expected = self.model.decode(tokens, memory, token_mask, image_mask)
                    actual = decode(self.ops, self.model, tokens, actual_memory, token_mask, image_mask)
                    torch.testing.assert_close(actual, expected)
                    expected_judge = self.model.judge_prefix(image, tokens, memory_key_padding_mask=image_mask)
                    torch.testing.assert_close(judge(self.ops, self.model, tokens, actual_memory, image_mask), expected_judge)

    def test_inference_state_and_exports(self):
        names = set(self.ops._c._method_names()) - {'forward'}
        expected = set(layer_methods(self.model)) | set(DECODER_PREFIXES) | {'silu'}
        self.assertEqual(names, expected)
        self.assertNotIn('Swiglu', names)
        self.assertNotIn('generate', names)
        # decoder 内部的所有 Linear / 归一化层仍可独立调用。
        self.assertIn('decoder1_self_attention_q_proj', names)
        self.assertIn('judge_decoder_ffn_norm', names)
        before = {name: value.clone() for name, value in self.ops.named_buffers()}
        with torch.inference_mode():
            for image, tokens in cases():
                memory = encode(self.ops, self.model, image)
                decode(self.ops, self.model, tokens, memory, None, None)
                judge(self.ops, self.model, tokens, memory, None)
        for name, value in self.ops.named_buffers():
            torch.testing.assert_close(value, before[name], rtol=0, atol=0)


def write_java_fixtures(path, include_vital=False):
    """使用 Python 原模型生成参考值，供 Java 独立实现逐项比较。"""
    import numpy as np

    model = load_model(ROOT / 'checkpoints/model.pt')
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('wb') as stream, torch.inference_mode():
        examples = list(cases())
        stream.write(struct.pack('>i', len(examples)))

        def write_tensor(tensor, dtype):
            stream.write(struct.pack('>i', tensor.ndim))
            stream.write(struct.pack('>' + 'i' * tensor.ndim, *tensor.shape))
            stream.write(tensor.cpu().numpy().astype(np.dtype(dtype)).tobytes())

        for image, tokens in examples:
            memory = model.encode_image(image)
            padding = tokens.eq(67)
            memory_padding = torch.zeros(memory.shape[:2], dtype=torch.bool)
            memory_padding[:, -1] = True
            write_tensor(image, '>f4')
            write_tensor(tokens, '>i8')
            write_tensor(memory_padding, '?')
            write_tensor(memory, '>f4')
            write_tensor(model.decode(tokens, memory), '>f4')
            write_tensor(model.decode(tokens, memory, padding, memory_padding), '>f4')
            write_tensor(model.judge_prefix(image, tokens, memory_key_padding_mask=memory_padding), '>f4')
            if include_vital:
                write_tensor(model.judge_prefix(image, tokens), '>f4')
                write_tensor(model.judge_prefix(image, tokens, memory_key_padding_mask=memory_padding,
                                                return_logits=True), '>f4')
                write_tensor(model.forward(image, tokens, padding, None), '>f4')
                write_tensor(model.forward(image, tokens, None, memory_padding), '>f4')
                write_tensor(model.judge_prefix(image, tokens, torch.zeros_like(padding), None), '>f4')
    print(f'Java reference data: {path}')


def write_decoder_fixtures(path):
    """为 model.decoder 的 Java 对照测试生成四个原 decoder 的输出。"""
    import numpy as np

    model = load_model(ROOT / 'checkpoints/model.pt')
    rng = torch.Generator().manual_seed(112)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('wb') as stream, torch.inference_mode():
        shapes = [(2, 3, 7), (1, 5, 2)]
        stream.write(struct.pack('>i', len(shapes)))

        def write_tensor(tensor, dtype):
            stream.write(struct.pack('>i', tensor.ndim))
            stream.write(struct.pack('>' + 'i' * tensor.ndim, *tensor.shape))
            stream.write(tensor.numpy().astype(np.dtype(dtype)).tobytes())

        for batch, length, image_length in shapes:
            x = torch.randn(batch, length, 256, generator=rng)
            memory = torch.randn(batch, image_length, 256, generator=rng)
            token_mask = torch.zeros(batch, length, dtype=torch.bool)
            memory_mask = torch.zeros(batch, image_length, dtype=torch.bool)
            token_mask[:, -1] = True
            memory_mask[:, -1] = True
            write_tensor(x, '>f4')
            write_tensor(memory, '>f4')
            write_tensor(token_mask, '?')
            write_tensor(memory_mask, '?')
            for name in DECODER_PREFIXES:
                for tm, mm in [(None, None), (token_mask, None), (None, memory_mask), (token_mask, memory_mask)]:
                    write_tensor(getattr(model, name)(x, memory, tm, mm), '>f4')
    print(f'Decoder Java reference data: {path}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    fixtures = parser.add_mutually_exclusive_group()
    fixtures.add_argument('--java-fixtures', type=Path)
    fixtures.add_argument('--decoder-fixtures', type=Path)
    fixtures.add_argument('--vital-fixtures', type=Path)
    args, remaining = parser.parse_known_args()
    if args.java_fixtures:
        write_java_fixtures(args.java_fixtures)
    elif args.vital_fixtures:
        write_java_fixtures(args.vital_fixtures, include_vital=True)
    elif args.decoder_fixtures:
        write_decoder_fixtures(args.decoder_fixtures)
    else:
        unittest.main(argv=['test_torchscript_ops.py', *remaining])
