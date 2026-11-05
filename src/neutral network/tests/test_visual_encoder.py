import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model import bos_id, d_model, encoder, vital_mini, vocab_size


class VisualEncoderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.previous_threads = torch.get_num_threads()
        torch.set_num_threads(2)

    @classmethod
    def tearDownClass(cls):
        torch.set_num_threads(cls.previous_threads)

    def setUp(self):
        torch.manual_seed(42)

    def test_first_visual_position_can_read_later_positions(self):
        block = encoder().eval()
        features = torch.randn(1, 4, d_model, requires_grad=True)
        block(features)[0, 0].square().sum().backward()
        self.assertGreater(features.grad[0, -1].abs().sum().item(), 0)

    def test_visual_padding_cannot_influence_valid_positions(self):
        block = encoder().eval()
        features = torch.randn(2, 4, d_model)
        mask = torch.tensor([[False, False, True, True], [False, False, False, True]])
        changed = features.clone()
        changed[mask] = torch.randn_like(changed[mask]) * 10
        with torch.no_grad():
            expected = block(features, mask)
            actual = block(changed, mask)
        torch.testing.assert_close(actual[~mask], expected[~mask])

    def test_forward_backpropagates_through_both_visual_encoders(self):
        model = vital_mini()
        image = torch.rand(2, 3, 128, 64)
        tokens = torch.tensor([[bos_id, 1, 2], [bos_id, 3, 4]])
        logits = model(image, tokens)
        self.assertEqual(logits.shape, (2, 3, vocab_size))
        targets = torch.tensor([[1, 2, 69], [3, 4, 69]])
        torch.nn.functional.cross_entropy(logits.flatten(0, 1), targets.flatten()).backward()
        for block in (model.encoder1, model.encoder2):
            for parameter in (block.self_attention.q_proj.weight, block.ffn.down.weight):
                self.assertIsNotNone(parameter.grad)
                self.assertTrue(torch.isfinite(parameter.grad).all())
                self.assertGreater(parameter.grad.abs().sum().item(), 0)
        self.assertGreater(model.cnn.conv.weight.grad.abs().sum().item(), 0)

    def test_forward_and_judge_mask_padding_before_visual_attention(self):
        model = vital_mini().eval()
        image = torch.rand(1, 3, 128, 64)
        tokens = torch.tensor([[bos_id, 1, 2]])
        # 直接扰动 CNN 输出的补边，隔离卷积边界效应，检查两个视觉层是否都屏蔽补边。
        features = torch.randn(1, d_model, 8, 4)
        changed = features.clone()
        changed[:, :, :, -1] = torch.randn_like(changed[:, :, :, -1]) * 10
        mask = torch.zeros(1, 8, 4, dtype=torch.bool)
        mask[:, :, -1] = True
        mask = mask.flatten(1)
        with torch.no_grad():
            for predict in (model, model.judge_prefix):
                with patch.object(model.cnn, 'forward', return_value=features):
                    expected = predict(image, tokens, memory_key_padding_mask=mask)
                with patch.object(model.cnn, 'forward', return_value=changed):
                    actual = predict(image, tokens, memory_key_padding_mask=mask)
                torch.testing.assert_close(actual, expected)

    def test_decoder_still_cannot_read_future_tokens(self):
        model = vital_mini().eval()
        image = torch.rand(1, 3, 128, 64)
        with torch.no_grad():
            expected = model(image, torch.tensor([[bos_id, 1, 2]]))
            changed = model(image, torch.tensor([[bos_id, 3, 4]]))
        torch.testing.assert_close(changed[:, 0], expected[:, 0])

    def test_generate_reuses_visual_encoding_across_decoding_steps(self):
        model = vital_mini().eval()
        image = torch.rand(1, 3, 128, 64)
        # 确保至少执行多个生成步骤，以检查视觉 encoder 不在每一步重复运行。
        with torch.no_grad():
            model.head.weight.zero_()
        with patch.object(model.encoder1, 'forward', wraps=model.encoder1.forward) as first, \
                patch.object(model.encoder2, 'forward', wraps=model.encoder2.forward) as second:
            sequences = model.generate(image, beam_width=1, max_steps=4)
        self.assertEqual(len(sequences[0]), 3)
        self.assertEqual(first.call_count, 1)
        self.assertEqual(second.call_count, 1)


if __name__ == '__main__':
    unittest.main()
