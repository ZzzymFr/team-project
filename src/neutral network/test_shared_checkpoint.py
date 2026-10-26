import contextlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import torch
from torch.utils.data import Dataset

import train
import train_test
from model import vital_mini


class TinyImages(Dataset):
    def __init__(self, root, splits, vocab, **kwargs):
        self.items = [("image", str(Path(root) / splits[0] / "image.png"), [1, 2, 3])]
        self.sources = ["MathWriting"]

    def __len__(self):
        return 1

    def __getitem__(self, index):
        return torch.full((1, 128, 64), 0.5), self.items[index][2]


class SharedCheckpointTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_default_paths_agree_independently_of_working_directory(self):
        with patch.object(sys, "argv", ["train.py"]), \
                patch("train.training_log", return_value=contextlib.nullcontext()), \
                patch("train.train") as run:
            train.main()
        generator_path = run.call_args.args[0].weights
        with patch.object(sys, "argv", ["train_test.py"]):
            judge_path = train_test.parse_args().weights
        self.assertTrue(generator_path.is_absolute())
        self.assertEqual(generator_path, judge_path)

    def test_alternating_training_updates_one_file_and_preserves_other_component(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as directory:
            root = Path(directory)
            weights = root / "existing.pt"
            initial = vital_mini().state_dict()
            torch.save({"model": initial, "image_invert": False, "custom_metadata": "keep"}, weights)

            def run_generator(val_loss):
                argv = ["train.py", "--weights", str(weights), "--out", str(root),
                        "--data", str(root), "--epochs", "1", "--batch-size", "1",
                        "--eval-n", "1", "--no-personal-train", "--workers", "0"]
                validation = {"loss": val_loss, "token_acc": 0.5, "sample_count": 1, "token_count": 4}
                exact = {"rate": 0.0, "correct": 0, "sample_count": 1}
                with patch.object(sys, "argv", argv), patch("train.MathWriting", TinyImages), \
                        patch("torch.cuda.is_available", return_value=False), \
                        patch("torch.backends.mps.is_available", return_value=False), \
                        patch("train.evaluate_sources", return_value=(validation, {"MathWriting": validation})), \
                        patch("train.exp_rate", return_value=(exact, {"MathWriting": exact})):
                    train.main()
                return torch.load(weights, map_location="cpu", weights_only=True)

            generated = run_generator(0.2)
            self.assertFalse(torch.equal(initial["head.weight"], generated["model"]["head.weight"]))
            self.assertTrue(torch.equal(initial["test.weight"], generated["model"]["test.weight"]))
            self.assertTrue(torch.equal(initial["process.weight"], generated["model"]["process.weight"]))
            argv = ["train_test.py", "--weights", str(weights), "--data", str(root),
                    "--epochs", "1", "--batch-size", "1", "--no-personal-train", "--device", "cpu"]
            with patch.object(sys, "argv", argv), patch("train_test.MathWriting", TinyImages):
                train_test.main()
            judged = torch.load(weights, map_location="cpu", weights_only=True)
            for name, tensor in generated["model"].items():
                if not name.startswith(("test.", "process.", "judge_decoder.", "judge_norm.")):
                    self.assertTrue(torch.equal(tensor, judged["model"][name]), name)
            self.assertFalse(torch.equal(generated["model"]["test.weight"], judged["model"]["test.weight"]))
            self.assertFalse(torch.equal(generated["model"]["process.weight"], judged["model"]["process.weight"]))
            self.assertEqual(judged["val_loss"], generated["val_loss"])
            self.assertEqual(judged["validation_signature"], generated["validation_signature"])
            self.assertEqual(judged["judge"]["task"], train_test.TASK)
            self.assertEqual(judged["judge"]["judge_input"], "prefix_tokens_and_images")
            key = "judge_decoder.cross_attention.v_proj.weight"
            self.assertFalse(torch.equal(generated["model"][key], judged["model"][key]))
            latest_judge = torch.load(root / "last.pt", map_location="cpu", weights_only=True)
            self.assertEqual(latest_judge["judge"]["validation"], judged["judge"]["validation"])
            torch.testing.assert_close(latest_judge["model"]["test.weight"], judged["model"]["test.weight"])

            generated_again = run_generator(0.1)
            self.assertTrue(torch.equal(judged["model"]["test.weight"], generated_again["model"]["test.weight"]))
            self.assertTrue(torch.equal(judged["model"]["process.weight"], generated_again["model"]["process.weight"]))
            self.assertTrue(torch.equal(judged["model"][key], generated_again["model"][key]))
            self.assertEqual(generated_again["judge"]["validation"], judged["judge"]["validation"])
            self.assertEqual(generated_again["custom_metadata"], "keep")
            # 验证表现变差时仍保存最新状态，主权重保留较好的那轮。
            unchanged_best = run_generator(0.4)
            latest = torch.load(root / "last.pt", map_location="cpu", weights_only=True)
            self.assertEqual(unchanged_best["val_loss"], 0.1)
            self.assertEqual(latest["val_loss"], 0.4)
            self.assertEqual(set(root.rglob("*.pt")), {weights, root / "last.pt"})


if __name__ == "__main__":
    unittest.main()
