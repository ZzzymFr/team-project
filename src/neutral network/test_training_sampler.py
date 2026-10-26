import random
import unittest
from collections import Counter
from contextlib import nullcontext
from pathlib import Path
from unittest.mock import patch

import torch
from torch.utils.data import DataLoader, Dataset

import train
from train import SourceMixedBatchSampler, collate


class TrainingSamplerTests(unittest.TestCase):
    def test_default_entrypoint_uses_mixed_data_and_personal_training(self):
        with patch("sys.argv", ["train.py"]), patch("train.training_log", return_value=nullcontext()), \
                patch("train.train") as run:
            train.main()
        args = run.call_args.args[0]
        root = Path(train.__file__).resolve().parent
        self.assertEqual(Path(args.data), root / "data" / "mixed")
        self.assertEqual(args.personal_train, root / "train")
        self.assertFalse(args.no_personal_train)

    def test_sources_with_different_lengths_stay_mixed(self):
        sources = ["MathWriting"] * 600 + ["UniMER"] * 300 + ["HME100K"] * 100
        lengths = [5] * 600 + [150] * 300 + [290] * 100
        sampler = SourceMixedBatchSampler(lengths, sources, 32, seed=7)
        batches = list(sampler)
        flattened = [i for batch in batches for i in batch]
        self.assertEqual(sorted(flattened), list(range(1000)))
        self.assertEqual(len(batches), len(sampler))
        self.assertTrue(all(len(batch) == 32 for batch in batches[:-1]))
        self.assertEqual(len(batches[-1]), 8)
        for batch in batches[:-1]:
            self.assertEqual(set(sources[i] for i in batch), set(sources))
        # 各阶段保留原始占比，避免小来源在开头用尽而形成长尾。
        for stop in (320, 640, 960):
            counts = Counter(sources[i] for i in flattened[:stop])
            for source, proportion in (("MathWriting", .6), ("UniMER", .3), ("HME100K", .1)):
                self.assertLessEqual(abs(counts[source] - stop * proportion), 2)

    def test_seed_epoch_and_global_random_state(self):
        sources = ["a"] * 90 + ["b"] * 60
        sampler = SourceMixedBatchSampler([12] * 150, sources, 16, seed=13)
        state = random.getstate()
        first = list(sampler)
        self.assertEqual(random.getstate(), state)
        self.assertEqual(first, list(sampler))
        sampler.set_epoch(1)
        second = list(sampler)
        self.assertNotEqual(first, second)
        self.assertEqual(sorted(i for batch in second for i in batch), list(range(150)))
        replica = SourceMixedBatchSampler([12] * 150, sources, 16, seed=13)
        replica.set_epoch(1)
        self.assertEqual(second, list(replica))

    def test_extreme_imbalance_no_oversampling_or_dropped_tail(self):
        sources = ["a"] * 997 + ["b"] * 2 + ["personal"]
        sampler = SourceMixedBatchSampler([8] * len(sources), sources, 31)
        flattened = [i for batch in sampler for i in batch]
        self.assertEqual(sorted(flattened), list(range(1000)))
        self.assertEqual(Counter(sources[i] for i in flattened), Counter(sources))

    def test_single_source_small_batch_empty_and_invalid_inputs(self):
        for size in (0, 1, 3, 40):
            for batch_size in (1, 8):
                sampler = SourceMixedBatchSampler([4] * size, ["a"] * size, batch_size)
                batches = list(sampler)
                self.assertEqual(len(batches), len(sampler))
                self.assertEqual(sorted(i for batch in batches for i in batch), list(range(size)))
        with self.assertRaises(ValueError):
            SourceMixedBatchSampler([1], [], 4)
        with self.assertRaises(ValueError):
            SourceMixedBatchSampler([], [], 0)

    def test_dataloader_preserves_mixed_batches_and_collation(self):
        class Images(Dataset):
            def __len__(self):
                return 12

            def __getitem__(self, i):
                return torch.full((1, 128, 64), float(i)), [1] * (i % 3 + 1)

        sources = ["a"] * 4 + ["b"] * 4 + ["c"] * 4
        sampler = SourceMixedBatchSampler([i % 3 + 1 for i in range(12)], sources, 6)
        loader = DataLoader(Images(), batch_sampler=sampler, collate_fn=collate)
        batches = list(loader)
        for batch, expected in zip(batches, sampler):
            ids = batch[0][:, 0, 0, 0].int().tolist()
            self.assertEqual(ids, expected)
            self.assertEqual(Counter(sources[i] for i in ids), {"a": 2, "b": 2, "c": 2})
            self.assertEqual(batch[2].shape, batch[4].shape)


if __name__ == "__main__":
    unittest.main()
