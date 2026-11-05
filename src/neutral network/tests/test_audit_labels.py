import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_labels import inspect_label
from model import load_vocab


class AuditLabelsTests(unittest.TestCase):
    def test_structure_and_escaped_symbols(self):
        vocab = set(load_vocab())
        for label in (r'\begin{pmatrix}a&b\\c&d\end{pmatrix}', r'\{x\}', r'50\%', r'1+1=3'):
            self.assertEqual(inspect_label(label, vocab)[1], {})
        # OCR 转录不能因数学等式为假而被判错。
        self.assertIn('unbalanced_braces', inspect_label(r'x_{1}}', vocab)[1])
        self.assertIn('unbalanced_environments', inspect_label(r'\begin{matrix}x\end{cases}', vocab)[1])
        self.assertIn('unpaired_dollar_review', inspect_label('$x', vocab)[1])

    def test_coverage_and_length_are_not_structural_errors(self):
        self.assertEqual(inspect_label(r'\mathrm{d}', set(load_vocab()))[1], {'oov': [r'\mathrm']})
        self.assertEqual(inspect_label('x' * 299, set(load_vocab()))[1], {'too_long': 301})


if __name__ == '__main__':
    unittest.main()
