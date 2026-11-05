import sys
import tempfile
import unittest
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from export_torchscript_ops import build_scripted, ENCODER_PREFIXES, DECODER_PREFIXES
from model import vital_mini


class ExportEncoderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)
        torch.manual_seed(20261103)
        cls.model = vital_mini().eval()
        scripted = build_scripted(cls.model)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'ops.pt'
            torch.jit.save(scripted, str(path))
            cls.exported = torch.jit.load(str(path)).eval()

    @torch.no_grad()
    def test_exported_encoders_match_native_with_dynamic_shapes_and_masks(self):
        for batch, length in ((1, 1), (2, 11), (1, 32)):
            x = torch.randn(batch, length, 256)
            mask = torch.zeros(batch, length, dtype=torch.bool)
            if length > 1:
                mask[:, -2:] = True
            for name in ENCODER_PREFIXES:
                for padding in (None, mask):
                    with self.subTest(name=name, shape=x.shape, masked=padding is not None):
                        expected = getattr(self.model, name)(x, padding)
                        actual = getattr(self.exported, name)(x, padding)
                        torch.testing.assert_close(actual, expected, rtol=1e-5, atol=1e-5)
                torch.testing.assert_close(getattr(self.exported, name)(x, torch.empty(0, dtype=torch.bool)),
                                           getattr(self.model, name)(x))

    @torch.no_grad()
    def test_visual_attention_is_noncausal_and_respects_padding(self):
        x = torch.randn(1, 8, 256)
        changed = x.clone()
        changed[:, -1] += torch.randn(256) * 10
        mask = torch.zeros(1, 8, dtype=torch.bool)
        mask[:, -1] = True
        for name in ENCODER_PREFIXES:
            block = getattr(self.exported, name)
            self.assertFalse(torch.allclose(block(x)[:, 0], block(changed)[:, 0]))
            torch.testing.assert_close(block(x, mask)[:, :-1], block(changed, mask)[:, :-1])

    @torch.no_grad()
    def test_existing_decoder_exports_remain_unchanged(self):
        x, memory = torch.randn(2, 5, 256), torch.randn(2, 9, 256)
        tm, mm = torch.zeros(2, 5, dtype=torch.bool), torch.zeros(2, 9, dtype=torch.bool)
        tm[:, -1], mm[:, -2:] = True, True
        for name in DECODER_PREFIXES:
            torch.testing.assert_close(getattr(self.exported, name)(x, memory, tm, mm),
                                       getattr(self.model, name)(x, memory, tm, mm), rtol=1e-5, atol=1e-5)


if __name__ == '__main__':
    unittest.main()
