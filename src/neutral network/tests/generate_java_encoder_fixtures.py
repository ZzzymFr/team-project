"""生成 Java 数值对照数据；以原生 Python 模型为基准，包含有训练更新的视觉 encoder。

python tests/generate_java_encoder_fixtures.py --checkpoint checkpoints/last.pt
生成的 .pt 仅用于测试，不覆盖应用模型。Java 入口：model.VitalMiniParityCheck / DecoderParityCheck。
"""
import argparse
import json
import struct
import sys
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from export_torchscript_ops import load_model, export, DECODER_PREFIXES, ENCODER_PREFIXES
from model import pad_id, bos_id

ROOT = Path(__file__).resolve().parents[3]


def write(output, tensor):
    array = tensor.detach().cpu().numpy()
    output.write(struct.pack('>i', array.ndim))
    for size in array.shape:
        output.write(struct.pack('>i', size))
    dtype = '?' if tensor.dtype == torch.bool else '>i8' if tensor.dtype == torch.int64 else '>f4'
    output.write(array.astype(dtype).tobytes())


@torch.no_grad()
def generate(checkpoint, folder):
    torch.set_num_threads(2)
    torch.manual_seed(20261103)
    folder.mkdir(parents=True, exist_ok=True)
    model = load_model(checkpoint)
    exported = export(checkpoint, folder / 'model_ops.pt')
    maximum_visual_change = 0.0
    with (folder / 'vital-reference.bin').open('wb') as output:
        cases = [(1, 1, 16), (2, 7, 32), (1, 200, 64)]
        output.write(struct.pack('>i', len(cases)))
        for batch, length, width in cases:
            image = torch.rand(batch, 3, 128, width)
            tokens = torch.randint(0, model.token_embedding.num_embeddings, (batch, length))
            tokens[:, 0] = bos_id
            if length > 1:
                tokens[:, 1] = model.token_embedding.num_embeddings - 1
                tokens[:, -1] = pad_id
            flat = model.encode(model.cnn(image))
            tm = tokens.eq(pad_id)
            mm = torch.zeros(batch, flat.size(1), dtype=torch.bool)
            mm[:, -2:] = True
            memory, masked_memory = model.encode_image(image), model.encode_image(image, mm)
            maximum_visual_change = max(maximum_visual_change, float((memory - flat).abs().max()))
            values = [image, tokens, mm, flat,
                      model(image, tokens), model(image, tokens, tm, mm),
                      model.judge_prefix(image, tokens, memory_key_padding_mask=mm),
                      model.judge_prefix(image, tokens),
                      model.judge_prefix(image, tokens, memory_key_padding_mask=mm, return_logits=True),
                      model(image, tokens, tm), model(image, tokens, memory_key_padding_mask=mm),
                      model.judge_prefix(image, tokens, torch.zeros_like(tm))]
            for name in ENCODER_PREFIXES:
                for mask in (None, mm):
                    expected = getattr(model, name)(flat, mask)
                    torch.testing.assert_close(getattr(exported, name)(flat, mask), expected)
                    values.append(expected)
            values.extend([memory, masked_memory, model.decode(tokens, memory),
                           model.decode(tokens, masked_memory, tm, mm)])
            for value in values:
                write(output, value)
    with (folder / 'decoder-reference.bin').open('wb') as output:
        cases = [(1, 1, 3), (2, 7, 11), (1, 200, 17)]
        output.write(struct.pack('>i', len(cases)))
        for batch, length, memory_length in cases:
            x, memory = torch.randn(batch, length, 256), torch.randn(batch, memory_length, 256)
            tm, mm = torch.zeros(batch, length, dtype=torch.bool), torch.zeros(batch, memory_length, dtype=torch.bool)
            if length > 1:
                tm[:, -2:] = True
            mm[:, -1] = True
            for value in (x, memory, tm, mm):
                write(output, value)
            for name in DECODER_PREFIXES:
                for mode in range(4):
                    write(output, getattr(model, name)(x, memory, tm if mode & 1 else None, mm if mode & 2 else None))
    report = {'checkpoint': str(checkpoint), 'vital_comparisons': 51, 'decoder_comparisons': 72,
              'maximum_visual_change': maximum_visual_change,
              'encoders_are_nonidentity': maximum_visual_change > 1e-4}
    (folder / 'fixture-report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', type=Path, default=ROOT / 'checkpoints/last.pt')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'target/visual-encoder')
    args = parser.parse_args()
    generate(args.checkpoint, args.output_dir)
