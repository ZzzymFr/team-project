import contextlib
import io
import json
import math
import random
import tempfile
import unittest
from collections import Counter
from pathlib import Path

import torch
from PIL import Image, ImageDraw
from torch.utils.data import DataLoader, Dataset, Subset

from model import max_len, vocab_size
from train import (BucketSampler, MathWriting, VALIDATION_SOURCES, collate, evaluate_sources,
                   exp_rate, fixed_exp_indices, load_vocab, save_exp_sample, source_groups)


class ExampleDataset(Dataset):
    def __init__(self, sizes):
        self.items, self.sources = [], []
        for source, size in sizes.items():
            for i in range(size):
                self.items.append(("image", f"/{source}/{i}.png", [1]))
                self.sources.append(source)

    def __len__(self):
        return len(self.items)

    def __getitem__(self, index):
        return torch.ones(1, 128, 64), self.items[index][2]


class ValidationTests(unittest.TestCase):
    def setUp(self):
        workspace = Path(__file__).resolve().parent
        temporary = tempfile.TemporaryDirectory(prefix="test-validation-", dir=workspace)
        self.root = Path(temporary.name).resolve()
        self.assertTrue(self.root.is_relative_to(workspace))
        self.addCleanup(temporary.cleanup)

    def test_balanced_stable_sampling_and_manifest(self):
        dataset = ExampleDataset(dict.fromkeys(VALIDATION_SOURCES, 1200))
        state = random.getstate()
        selected = fixed_exp_indices(dataset, 1000, 0)
        self.assertEqual(random.getstate(), state)
        self.assertEqual(len(set(selected)), 1000)
        self.assertEqual(Counter(dataset.sources[i] for i in selected),
                         {"MathWriting": 334, "UniMER": 333, "HME100K": 333})
        self.assertNotEqual(selected, fixed_exp_indices(dataset, 1000, 1))
        with contextlib.redirect_stdout(io.StringIO()):
            protocol = save_exp_sample(dataset, selected, self.root, 0, 1000)
            paths = [dataset.items[i][1] for i in selected]
            dataset.items.reverse()
            dataset.sources.reverse()
            reordered = fixed_exp_indices(dataset, 1000, 0)
            self.assertEqual(paths, [dataset.items[i][1] for i in reordered])
            again = save_exp_sample(dataset, reordered, self.root, 0, 1000)
        self.assertEqual(protocol, again)
        saved = json.loads(Path(protocol["sample_file"]).read_text(encoding="utf-8"))
        self.assertEqual(saved["source_counts"], protocol["source_counts"])
        self.assertEqual(len(saved["samples"]), 1000)

    def test_small_source_redistribution_and_actual_denominator(self):
        dataset = ExampleDataset({"MathWriting": 2, "UniMER": 20, "HME100K": 20})
        selected = fixed_exp_indices(dataset, 12, 0)
        self.assertEqual(Counter(dataset.sources[i] for i in selected),
                         {"MathWriting": 2, "UniMER": 5, "HME100K": 5})
        self.assertEqual(set(fixed_exp_indices(dataset, 1000, 0)), set(range(42)))
        with self.assertRaises(ValueError):
            fixed_exp_indices(dataset, 2, 0)
        with self.assertRaises(ValueError):
            fixed_exp_indices(ExampleDataset({}), 1000, 0)

    def test_source_metadata_survives_shuffle_and_filter(self):
        vocab = {token: i for i, token in enumerate(load_vocab())}
        rows = []
        raw_sources = ["mathwriting", "unimer", "hme100k-hf"]
        for index, source in enumerate(raw_sources):
            name = f"{index}.png"
            image = Image.new("RGB", (64, 128), "white")
            ImageDraw.Draw(image).line((20, 10, 40, 110), fill="black", width=4)
            image.save(self.root / name)
            rows.append({"image": name, "latex": "x", "source": source})
        Image.new("RGB", (64, 128), "white").save(self.root / "blank.png")
        rows.append({"image": "blank.png", "latex": "x", "source": "unimer"})
        rows.append(rows[0].copy())
        manifest = self.root / "valid.jsonl"
        manifest.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        dataset = MathWriting(str(self.root), ["valid"], vocab, train=False, max_samples=10)
        self.assertEqual(len(dataset), 3)
        self.assertEqual(len(dataset.invalid_images), 1)
        for source, item in zip(dataset.sources, dataset.items):
            self.assertEqual(source, VALIDATION_SOURCES[int(Path(item[1]).stem)])
        rows.append({**rows[0], "source": "unimer"})
        manifest.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "来源"):
            MathWriting(str(self.root), ["valid"], vocab, train=False)

    def test_per_source_metrics_weight_tokens_and_ignore_padding(self):
        dataset = ExampleDataset({"MathWriting": 2, "UniMER": 1, "HME100K": 1})
        labels = [[1], [2, 2, 2], [1, 1], [2]]
        dataset.items = [(kind, path, ids) for (kind, path, _), ids in zip(dataset.items, labels)]

        class ConstantModel(torch.nn.Module):
            def forward(self, images, tokens, **kwargs):
                logits = torch.zeros(*tokens.shape, vocab_size)
                logits[..., 1] = 2.0
                return logits

        groups = source_groups(dataset)
        results = []
        for batch_size in (1, 3):
            loaders = {source: DataLoader(Subset(dataset, indices), batch_sampler=BucketSampler(
                [len(dataset.items[i][2]) for i in indices], batch_size, False), collate_fn=collate)
                for source, indices in groups.items()}
            overall, by_source = evaluate_sources(ConstantModel(), loaders, "cpu", False)
            # 总共 11 个有效 token，其中 3 个为预测字符 1；其余包含 EOS。
            log_z = math.log(math.exp(2.0) + vocab_size - 1)
            expected_loss = log_z - 0.1 * 2.0 / vocab_size - (3 / 11) * 0.9 * 2.0
            self.assertAlmostEqual(overall["loss"], expected_loss, places=5)
            self.assertEqual(overall["token_count"], 11)
            self.assertEqual(overall["sample_count"], 4)
            self.assertAlmostEqual(overall["token_acc"], 3 / 11)
            self.assertAlmostEqual(by_source["MathWriting"]["token_acc"], 1 / 6)
            self.assertAlmostEqual(by_source["UniMER"]["token_acc"], 2 / 3)
            self.assertEqual(by_source["HME100K"]["token_acc"], 0)
            results.append(overall)
        self.assertAlmostEqual(results[0]["loss"], results[1]["loss"], places=5)

    def test_exp_rate_uses_fixed_indices_and_full_length(self):
        dataset = ExampleDataset(dict.fromkeys(VALIDATION_SOURCES, 1))
        dataset.items[0] = ("image", "/long.png", [1] * 150)
        indices = [2, 0, 1]

        class Predictions(torch.nn.Module):
            def __init__(self):
                super().__init__()
                self.calls = 0

            def generate(inner, image, beam_width, max_steps):
                self.assertEqual(beam_width, 1)
                self.assertEqual(max_steps, max_len)
                self.assertFalse(inner.training)
                expected = dataset.items[indices[inner.calls]][2]
                inner.calls += 1
                return [expected if inner.calls < 3 else [9]]

        overall, sources = exp_rate(Predictions(), dataset, "cpu", indices)
        self.assertEqual(overall["sample_count"], 3)
        self.assertAlmostEqual(overall["rate"], 2 / 3)
        self.assertEqual(sources["MathWriting"]["rate"], 1)
        self.assertEqual(sources["UniMER"]["rate"], 0)
        with self.assertRaises(ValueError):
            exp_rate(Predictions(), dataset, "cpu", [])


if __name__ == "__main__":
    unittest.main()
