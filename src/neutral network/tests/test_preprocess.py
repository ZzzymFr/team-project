import sys
import tempfile
import unittest
from pathlib import Path

import torch
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from preprocess import EmptyFormulaImageError, check_formula_image, load_formula_image
from start import load_image
from train import convert


class RgbPreprocessingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def picture(self, colour, background='white', mode='RGB'):
        image = Image.new(mode, (160, 80), background)
        ImageDraw.Draw(image).rectangle((30, 20, 120, 60), fill=colour)
        path = self.root / 'formula.png'
        image.save(path)
        return path

    def test_rgb_colours_survive_including_bright_yellow(self):
        for colour in ((255, 0, 0), (0, 0, 255), (255, 255, 0)):
            with self.subTest(colour=colour):
                path = self.picture(colour)
                check_formula_image(path)
                image = load_formula_image(path, invert=False)
                self.assertEqual(image.shape[:2], (3, 128))
                self.assertEqual(image.shape[-1] % 16, 0)
                torch.testing.assert_close(image[:, 64, image.shape[-1] // 2],
                                           torch.tensor(colour, dtype=torch.float32) / 255)
                torch.testing.assert_close(image[:, 0, 0], torch.ones(3))

    def test_black_and_white_inputs_remain_equal_across_channels(self):
        for foreground, background in [('black', 'white'), ('white', 'black')]:
            image = load_formula_image(self.picture(foreground, background), invert=False)
            self.assertTrue(torch.equal(image[0], image[1]))
            self.assertTrue(torch.equal(image[1], image[2]))
            self.assertEqual(image[0, 0, 0].item(), float(background == 'white'))

    def test_transparency_is_composited_on_white(self):
        path = self.picture((255, 0, 0, 128), (0, 0, 0, 0), 'RGBA')
        image = load_formula_image(path, invert=False)
        torch.testing.assert_close(image[:, 64, image.shape[-1] // 2],
                                   torch.tensor([255, 127, 127], dtype=torch.float32) / 255)

    def test_explicit_inversion_and_inference_keep_rgb_shape(self):
        path = self.picture('red')
        plain = load_formula_image(path, invert=False)
        inverted = load_formula_image(path, invert=True)
        torch.testing.assert_close(inverted, 1 - plain)
        torch.testing.assert_close(load_image(path, invert=False), plain.unsqueeze(0))

    def test_blank_image_rejected_by_check_and_load(self):
        path = self.picture('white')
        for operation in (check_formula_image, load_formula_image):
            with self.assertRaises(EmptyFormulaImageError):
                operation(path)

    def test_mixed_inkml_and_rgb_batch_preserves_channels_and_padding(self):
        colour = load_formula_image(self.picture('red'), invert=False)
        mono = torch.zeros(1, 128, colour.shape[-1] + 16)
        mono[:, 40:80, 20:40] = 1
        batch, widths = convert([colour, mono])
        self.assertEqual(batch.shape, (2, 3, 128, mono.shape[-1]))
        self.assertEqual(widths.tolist(), [colour.shape[-1], mono.shape[-1]])
        torch.testing.assert_close(batch[0, :, :, :colour.shape[-1]], colour)
        self.assertTrue(torch.all(batch[0, :, :, colour.shape[-1]:] == 1))
        torch.testing.assert_close(batch[1], mono.expand(3, -1, -1))


if __name__ == '__main__':
    unittest.main()
