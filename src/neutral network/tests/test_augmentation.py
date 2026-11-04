import json
import random
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageDraw
from torch.utils.data import DataLoader

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from augmentation import _geometry, augment_formula_image
from model import load_vocab
from preprocess import load_formula_image
from train import MathWriting, collate


class AugmentationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.image_path = self.root / 'formula.png'
        picture = Image.new('RGB', (220, 70), 'white')
        draw = ImageDraw.Draw(picture)
        draw.line((20, 20, 60, 45, 95, 15), fill='blue', width=4)
        draw.line((120, 30, 195, 30), fill='red', width=3)
        draw.ellipse((200, 44, 204, 48), fill='black')
        picture.save(self.image_path)
        (self.root / 'sample.inkml').write_text(
            '<ink xmlns="http://www.w3.org/2003/InkML"><trace>0 0, 20 20, 30 0</trace></ink>')
        rows = [{'image': 'formula.png', 'latex': 'x+1'},
                {'path': 'sample.inkml', 'format': 'inkml', 'latex': 'y'}]
        (self.root / 'train.jsonl').write_text(''.join(json.dumps(row) + '\n' for row in rows))
        self.vocab = {token: i for i, token in enumerate(load_vocab())}

    def tearDown(self):
        self.temporary.cleanup()

    def dataset(self, train=True, **kwargs):
        return MathWriting(str(self.root), ['train'], self.vocab, train, **kwargs)

    def test_seeded_rgb_augmentation_preserves_shape_range_and_source(self):
        original_bytes = self.image_path.read_bytes()
        image = load_formula_image(self.image_path, invert=False)
        original = image.clone()
        for seed in range(20):
            actual = augment_formula_image(image, random.Random(seed))
            torch.testing.assert_close(actual, augment_formula_image(image, random.Random(seed)))
            self.assertEqual(actual.shape, image.shape)
            self.assertEqual(actual.dtype, torch.float32)
            self.assertTrue(torch.isfinite(actual).all())
            self.assertTrue(torch.all((actual >= 0) & (actual <= 1)))
            self.assertFalse(torch.equal(actual[0], actual[2]))
        self.assertTrue(torch.equal(image, original))
        self.assertEqual(original_bytes, self.image_path.read_bytes())

    def test_expanded_geometry_keeps_all_corner_marks(self):
        picture = Image.new('RGB', (300, 128), 'white')
        draw = ImageDraw.Draw(picture)
        for box, colour in [((10, 10, 20, 20), 'red'), ((280, 10, 290, 20), 'blue'),
                            ((10, 108, 20, 118), (0, 255, 0)), ((280, 108, 290, 118), 'yellow')]:
            draw.rectangle(box, fill=colour)
        for seed in range(30):
            pixels = np.asarray(_geometry(picture, random.Random(seed), (255, 255, 255)))
            r, g, b = pixels[..., 0], pixels[..., 1], pixels[..., 2]
            for mark in ((r > 200) & (g < 50) & (b < 50),
                         (b > 200) & (g < 50) & (r < 50),
                         (g > 200) & (r < 50) & (b < 50),
                         (r > 200) & (g > 200) & (b < 50)):
                self.assertGreater(np.count_nonzero(mark), 10, f'corner mark lost for seed {seed}')

    def test_validation_and_disabled_training_are_exact_and_do_not_consume_randomness(self):
        plain = self.dataset(train=False)
        for dataset in (self.dataset(train=False, augment_probability=1),
                        self.dataset(augment=False), self.dataset(augment_probability=0)):
            before = random.getstate()
            for i in range(2):
                actual, label = dataset[i]
                reference, expected_label = plain[i]
                self.assertTrue(torch.equal(actual, reference))
                self.assertEqual(label, expected_label)
            self.assertEqual(random.getstate(), before)

    def test_training_changes_pixels_without_changing_tokens_or_breaking_mixed_batch(self):
        dataset = self.dataset(augment_probability=1)
        plain = self.dataset(train=False)
        random.seed(11)
        samples = [dataset[i] for i in range(2)]
        self.assertFalse(torch.equal(samples[0][0], plain[0][0]))
        for i, (_, tokens) in enumerate(samples):
            self.assertEqual(tokens, plain[i][1])
        batch = collate(samples)
        self.assertEqual(batch[0].shape[:3], (2, 3, 128))
        self.assertTrue(torch.isfinite(batch[0]).all())

    def test_worker_seed_is_reproducible(self):
        dataset = self.dataset(augment_probability=1)
        def read_once():
            return list(DataLoader(dataset, batch_size=2, collate_fn=collate, num_workers=1,
                                   generator=torch.Generator().manual_seed(123)))[0]
        first, second = read_once(), read_once()
        for left, right in zip(first, second):
            self.assertTrue(torch.equal(left, right))

    def test_invalid_probability_is_rejected(self):
        for value in (-0.1, 1.1, float('nan')):
            with self.assertRaises(ValueError):
                self.dataset(augment_probability=value)


if __name__ == '__main__':
    unittest.main()
