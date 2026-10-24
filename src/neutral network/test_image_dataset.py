r"""运行：.venv\Scripts\python.exe -m unittest test_image_dataset -v"""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import torch
from PIL import Image, ImageDraw
from torch.utils.data import DataLoader

from preprocess import EmptyFormulaImageError, load_formula_image
from train import BucketSampler, MathWriting, collate, convert, load_vocab, resolve_data_root


class ImageDatasetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="test-images-", dir=Path(__file__).parent)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.vocab = {token: i for i, token in enumerate(load_vocab())}
        for name, background, ink in (("light.png", "white", "black"),
                                      ("dark.png", "black", "white")):
            image = Image.new("RGB", (80, 40), background)
            ImageDraw.Draw(image).line((10, 10, 60, 30), fill=ink, width=3)
            image.save(self.root / name)
        Image.new("RGB", (80, 40), "white").save(self.root / "blank.png")
        Image.new("RGBA", (80, 40), (0, 0, 0, 0)).save(self.root / "transparent.png")
        (self.root / "broken.png").write_bytes(b"not an image")
        names = ["blank.png", "light.png", "broken.png", "dark.png", "transparent.png"]
        rows = "".join(json.dumps({"image": name, "latex": "x"}) + "\n" for name in names)
        for split in ("train", "valid"):
            (self.root / f"{split}.jsonl").write_text(rows, encoding="utf-8")

    def test_blank_inference_still_raises_with_path(self):
        for name in ("blank.png", "transparent.png"):
            with self.subTest(name=name), self.assertRaises(EmptyFormulaImageError) as error:
                load_formula_image(self.root / name)
            self.assertIn(str(self.root / name), str(error.exception))

    def test_preflight_and_multiworker_loader(self):
        for training, split in ((True, "train"), (False, "valid")):
            with self.subTest(split=split):
                dataset = MathWriting(str(self.root), [split], self.vocab, train=training)
                self.assertEqual(len(dataset), 2)
                self.assertEqual(len(dataset.invalid_images), 3)
                self.assertEqual([Path(item[1]).name for item in dataset.items],
                                 ["light.png", "dark.png"])
                loader = DataLoader(dataset, batch_sampler=BucketSampler(
                    [len(item[2]) for item in dataset.items], 2, shuffle=False),
                    collate_fn=collate, num_workers=2)
                batches = list(loader)
                self.assertEqual(len(batches), 1)
                self.assertEqual(tuple(batches[0][0].shape[:3]), (2, 3, 128))

    def test_unexpected_preprocessing_errors_are_not_hidden(self):
        with patch("train.load_formula_image", side_effect=ValueError("unexpected")):
            with self.assertRaisesRegex(ValueError, "unexpected"):
                MathWriting(str(self.root), ["valid"], self.vocab, train=False)

    def test_direct_labels_are_reread_after_updates(self):
        for split in ("train", "valid"):
            (self.root / f"{split}.jsonl").unlink()
        for split, image in (("train", "light.png"), ("val", "dark.png")):
            folder = self.root / split
            folder.mkdir()
            (folder / "labels.jsonl").write_text(
                json.dumps({"image": "../" + image, "latex": "x"}) + "\n", encoding="utf-8")

        def training():
            return MathWriting(str(self.root), ["train"], self.vocab, train=True,
                               personal_train=self.root / "train")

        self.assertEqual(len(training()), 1)  # 自动个人目录不能导致重复样本。
        validation = MathWriting(str(self.root), ["valid"], self.vocab, train=False)
        self.assertEqual(Path(validation.items[0][1]).name, "dark.png")
        with Image.open(self.root / "light.png") as image:
            image.save(self.root / "train" / "new.png")
        with (self.root / "train" / "labels.jsonl").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps({"image": "new.png", "latex": "y"}) + "\n")
        updated = training()
        self.assertEqual(len(updated), 2)
        self.assertEqual(updated.items[1][2], [self.vocab["y"]])

    def test_mixed_manifest_has_priority_over_val_alias(self):
        (self.root / "val").mkdir()
        (self.root / "val" / "labels.jsonl").write_text(
            json.dumps({"image": "../light.png", "latex": "y"}) + "\n", encoding="utf-8")
        validation = MathWriting(str(self.root), ["valid"], self.vocab, train=False)
        self.assertEqual(len(validation), 2)
        self.assertTrue(all(item[2] == [self.vocab["x"]] for item in validation.items))

    def test_training_and_validation_keep_original_polarity(self):
        from start import load_image
        for split, training in (("train", True), ("valid", False)):
            dataset = MathWriting(str(self.root), [split], self.vocab, train=training)
            for index, expected_background in enumerate((1.0, 0.0)):
                image, _ = dataset[index]
                self.assertEqual(image[0, 0, 0].item(), expected_background)
                inference = load_image(Path(dataset.items[index][1]), invert=False)
                self.assertTrue(torch.equal(inference[0, :1], image))

    def test_batch_padding_keeps_each_background(self):
        white = torch.ones(1, 128, 64)
        black = torch.zeros(1, 128, 80)
        wide = torch.ones(1, 128, 96)
        white[:, 32:64, 16:32] = 0
        black[:, 32:64, 16:32] = 1
        batch, widths = convert([white, black, wide])
        self.assertEqual(widths.tolist(), [64, 80, 96])
        self.assertTrue(torch.all(batch[0, :, :, 64:] == 1))
        self.assertTrue(torch.all(batch[1, :, :, 80:] == 0))
        self.assertTrue(torch.equal(batch[0, :1, :, :64], white))
        self.assertTrue(torch.equal(batch[1, :1, :, :80], black))

    def test_relative_data_path_fallback_and_precedence(self):
        work = self.root / "working"
        script = self.root / "script"
        work.mkdir()
        (script / "data" / "mixed").mkdir(parents=True)
        with patch("train.Path.cwd", return_value=work), patch("train.__file__", str(script / "train.py")):
            self.assertEqual(resolve_data_root("data/mixed"), script / "data" / "mixed")
            (work / "data" / "mixed").mkdir(parents=True)
            self.assertEqual(resolve_data_root("data/mixed"), work / "data" / "mixed")
            missing = work / "missing"
            self.assertEqual(resolve_data_root(str(missing)), missing)
            self.assertEqual(resolve_data_root("missing"), missing)


if __name__ == "__main__":
    unittest.main()
