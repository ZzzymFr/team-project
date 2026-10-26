import random
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import torch

from model import bos_id, eos_id, pad_id, max_len, d_model, vital_mini, vocab_size, load_training_weights
from train import collate
from train_test import IGNORE, load_generator, make_candidates, prefix_labels, run_epoch

JUDGE_PREFIXES = ("test.", "process.", "judge_decoder.", "judge_norm.")


class PrefixJudgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_labels_belong_to_already_read_prefix_and_stay_wrong(self):
        reference = torch.tensor([[bos_id, 1, 2, 3, eos_id, pad_id]])
        correct = prefix_labels(reference, reference)
        self.assertEqual(correct.tolist(), [[IGNORE, 1, 1, 1, 1, IGNORE]])
        candidate = torch.tensor([[bos_id, 1, 9, 3, eos_id, pad_id]])
        self.assertEqual(prefix_labels(candidate, reference).tolist(),
                         [[IGNORE, 1, 0, 0, 0, IGNORE]])

    def test_early_eos_is_wrong_and_after_eos_is_ignored(self):
        reference = torch.tensor([[bos_id, 1, 2, eos_id, pad_id]])
        candidate = torch.tensor([[bos_id, 1, eos_id, 2, 3]])
        self.assertEqual(prefix_labels(candidate, reference).tolist(), [[IGNORE, 1, 0, IGNORE, IGNORE]])
        candidate = torch.tensor([[bos_id, 1, 2, 3, eos_id]])
        self.assertEqual(prefix_labels(candidate, reference).tolist(), [[IGNORE, 1, 1, 0, 0]])

    def test_candidates_are_reproducible_valid_and_have_real_negative_prefixes(self):
        gold = torch.tensor([[bos_id, 1, 2, eos_id], [bos_id, 3, eos_id, pad_id]])
        scores = torch.zeros(2, 4, vocab_size)
        scores[..., pad_id] = 100
        scores[..., bos_id] = 99
        for seed in range(15):
            result = make_candidates(gold, scores, random.Random(seed))
            again = make_candidates(gold, scores, random.Random(seed))
            for first, second in zip(result, again):
                self.assertTrue(torch.equal(first, second))
            tokens, labels, owners = result
            self.assertEqual(owners.tolist(), [0, 0, 0, 1, 1, 1])
            self.assertTrue(tokens[:, 0].eq(bos_id).all())
            self.assertFalse(tokens[:, 1:].eq(bos_id).any())
            self.assertTrue(labels[:, 0].eq(IGNORE).all())
            for row in (1, 2, 4, 5):
                self.assertTrue(labels[row].eq(0).any())
            for row in (0, 3):
                self.assertFalse(labels[row].eq(0).any())

    def test_insertion_never_exceeds_decoder_limit(self):
        gold = torch.tensor([[bos_id] + [1] * (max_len - 2) + [eos_id]])
        for seed in range(10):
            tokens, labels, _ = make_candidates(gold, torch.zeros(1, max_len, vocab_size), random.Random(seed))
            self.assertLessEqual(tokens.size(1), max_len)
            self.assertTrue(labels[1:].eq(0).any())

    def test_training_updates_only_judge_and_preserves_batchnorm_buffers(self):
        model = vital_mini()
        model.requires_grad_(False)
        for parameter in model.judge_parameters():
            parameter.requires_grad_(True)
        before = {name: tensor.clone() for name, tensor in model.state_dict().items()}
        batch = collate([(torch.rand(1, 128, 64), [1, 2]), (torch.rand(1, 128, 80), [3])])
        optimizer = torch.optim.AdamW(model.judge_parameters(), lr=1e-3)
        model.train()  # run_epoch 必须主动恢复本体的 eval 状态。
        metrics = run_epoch(model, [batch], "cpu", 7, optimizer)
        self.assertTrue(torch.isfinite(torch.tensor(metrics["loss"])))
        self.assertGreater(metrics["count_0"], 0)
        self.assertGreater(metrics["count_1"], 0)
        after = model.state_dict()
        for name in before:
            if not name.startswith(JUDGE_PREFIXES):
                self.assertTrue(torch.equal(before[name], after[name]), name)
        self.assertFalse(torch.equal(before["test.weight"], after["test.weight"]))
        self.assertFalse(torch.equal(before["process.weight"], after["process.weight"]))
        for key in ("judge_decoder.self_attention.q_proj.weight", "judge_decoder.cross_attention.v_proj.weight"):
            self.assertFalse(torch.equal(before[key], after[key]), key)
        self.assertTrue(all(p.grad is None for name, p in model.named_parameters()
                            if not name.startswith(JUDGE_PREFIXES)))
        self.assertFalse(model.cnn.training)
        self.assertEqual(run_epoch(model, [batch], "cpu", 3), run_epoch(model, [batch], "cpu", 3))

    def test_head_returns_logits_for_loss_and_exclusive_probabilities_for_inference(self):
        model = vital_mini().eval()
        images = torch.rand(2, 3, 128, 64)
        tokens = torch.tensor([[bos_id, 1, 2, 3, eos_id], [bos_id, 2, eos_id, pad_id, pad_id]])
        # 公共接口接收原始图片和 token，检测不能再借道生成器的词表分数。
        with patch.object(model, "decode", side_effect=AssertionError("检测不应调用生成解码器")), \
                patch.object(model, "encode_image", wraps=model.encode_image) as encode:
            logits = model.judge_prefix(images, tokens, return_logits=True)
            probabilities = model.judge_prefix(images, tokens)
            self.assertEqual(encode.call_count, 2)
        self.assertEqual(tuple(logits.shape), (2, 5, 2))
        torch.testing.assert_close(probabilities, logits.softmax(-1))
        torch.testing.assert_close(probabilities.sum(-1), torch.ones(2, 5))

    def test_judge_is_causal_and_reads_image_and_earlier_tokens(self):
        torch.manual_seed(9)
        model = vital_mini().eval()
        images = torch.randn(1, 3, 128, 64)
        tokens = torch.tensor([[bos_id, 1, 2, 3]])
        original = model.judge_prefix(images, tokens, return_logits=True)
        changed_future = tokens.clone()
        changed_future[0, 3] = 9
        changed = model.judge_prefix(images, changed_future, return_logits=True)
        torch.testing.assert_close(original[:, :3], changed[:, :3])
        short = model.judge_prefix(images, tokens[:, :3], return_logits=True)
        torch.testing.assert_close(original[:, :3], short)
        changed_past = tokens.clone()
        changed_past[0, 1] = 8
        past = model.judge_prefix(images, changed_past, return_logits=True)
        self.assertFalse(torch.allclose(original[:, -1], past[:, -1]))
        other_image = model.judge_prefix(torch.randn_like(images), tokens, return_logits=True)
        self.assertFalse(torch.allclose(original, other_image))

    def test_judge_ignores_masked_image_regions_and_prefix_tokens(self):
        model = vital_mini().eval()
        tokens = torch.tensor([[bos_id, pad_id, 1]])
        token_mask = tokens.eq(pad_id)
        memory = torch.randn(1, 5, d_model)
        images = torch.randn(1, 3, 128, 64)
        image_mask = torch.tensor([[False, False, False, True, True]])
        with patch.object(model, "encode_image", return_value=memory):
            baseline = model.judge_prefix(images, tokens, token_mask, image_mask, return_logits=True)
        changed = memory.clone()
        changed[:, 3:] = torch.randn_like(changed[:, 3:]) * 100
        tokens[0, 1] = 9
        with patch.object(model, "encode_image", return_value=changed):
            actual = model.judge_prefix(images, tokens, token_mask, image_mask, return_logits=True)
        torch.testing.assert_close(baseline[:, [0, 2]], actual[:, [0, 2]])

    def test_old_generator_checkpoint_loads_but_missing_backbone_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as folder:
            path = Path(folder) / "base.pt"
            model = vital_mini(num_tokens=154)
            state = {name: tensor for name, tensor in model.state_dict().items()
                     if not name.startswith(JUDGE_PREFIXES)}
            torch.save({"model": state}, path)
            loaded, _ = load_generator(path, "cpu")
            self.assertEqual(loaded.test.in_features, d_model)
            self.assertEqual({name for name, p in loaded.named_parameters() if p.requires_grad},
                             {name for name, _ in loaded.named_parameters() if name.startswith(JUDGE_PREFIXES)})
            torch.testing.assert_close(loaded.head.weight, state["head.weight"])
            del state["decoder1.ffn.gate.weight"]
            torch.save({"model": state}, path)
            with self.assertRaises(RuntimeError):
                load_generator(path, "cpu")

    def test_vocabulary_expansion_preserves_judge_and_accepts_pre_judge_weights(self):
        old = vital_mini(num_tokens=154)
        new = vital_mini()
        state = old.state_dict()
        self.assertTrue(load_training_weights(new, state))
        for key, tensor in state.items():
            if key.startswith(JUDGE_PREFIXES):
                torch.testing.assert_close(new.state_dict()[key], tensor)
        self.assertEqual(state["test.weight"].shape, (2, d_model))
        state = {key: tensor for key, tensor in new.state_dict().items()
                 if not key.startswith(JUDGE_PREFIXES)}
        restored = vital_mini()
        self.assertFalse(load_training_weights(restored, state))
        torch.testing.assert_close(restored.head.weight, new.head.weight)

    def test_legacy_judge_migration_preserves_backbone_and_rejects_partial_layers(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as folder:
            path = Path(folder) / "linear.pt"
            old = vital_mini()
            backbone = {key: value for key, value in old.state_dict().items() if not key.startswith(JUDGE_PREFIXES)}
            state = dict(backbone, **{"test.weight": torch.randn(2, vocab_size), "test.bias": torch.randn(2)})
            for nonlinear in (False, True):
                if nonlinear:
                    state.update({"process.weight": torch.randn(vocab_size, vocab_size),
                                  "process.bias": torch.randn(vocab_size)})
                torch.save({"model": state}, path)
                loaded, _ = load_generator(path, "cpu")
                for key, value in backbone.items():
                    torch.testing.assert_close(loaded.state_dict()[key], value)
                self.assertEqual(loaded.test.in_features, d_model)
                new = vital_mini()
                self.assertFalse(load_training_weights(new, state))
                for key, value in backbone.items():
                    torch.testing.assert_close(new.state_dict()[key], value)
            del state["process.bias"]
            torch.save({"model": state}, path)
            with self.assertRaisesRegex(ValueError, "process"):
                load_generator(path, "cpu")
            state = old.state_dict()
            del state["judge_decoder.self_attention.q_proj.weight"]
            torch.save({"model": state}, path)
            with self.assertRaisesRegex(ValueError, "新版检测头"):
                load_generator(path, "cpu")


if __name__ == "__main__":
    unittest.main()
