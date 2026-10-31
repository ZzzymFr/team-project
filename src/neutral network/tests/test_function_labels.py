import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from labels import FUNCTIONS, TOKEN_RE, tokenize_label
from model import ids_to_latex, load_vocab
from data import label_key


class FunctionLabelsTests(unittest.TestCase):
    def test_all_function_spellings_and_longest_match(self):
        for name in FUNCTIONS:
            for raw in (name + '(x)', ' '.join(name) + ' ( x )', '\\' + name + '(x)',
                        r'\operatorname{' + name + '}(x)', r'\mathrm{' + name + '}(x)'):
                with self.subTest(raw=raw):
                    self.assertEqual(tokenize_label(raw), ['\\' + name, '(', 'x', ')'])

    def test_observed_attached_variables(self):
        cases = {
            r'ntcos\theta': ['n', 't', r'\cos', r'\theta'],
            r'ecos\theta': ['e', r'\cos', r'\theta'],
            r'vtan\alpha': ['v', r'\tan', r'\alpha'],
            'klogv': ['k', r'\log', 'v'],
            'loglogN': [r'\log', r'\log', 'N'],
            'sinxcosxdx': [r'\sin', 'x', r'\cos', 'x', 'd', 'x'],
            'sin h': [r'\sin', 'h'],
            'cos t': [r'\cos', 't'],
        }
        for raw, expected in cases.items():
            with self.subTest(raw=raw):
                self.assertEqual(tokenize_label(raw), expected)

    def test_preserve_ambiguous_words_and_commands(self):
        for raw in ('since', 'cost', 'distance', 'logistic', 'arcsinh', 'limsup', 'colim',
                    's i n c e', r'\sinx', r'\operatorname{colim}', r'\mathrm{cost}',
                    r'\begin{matrix}x\end{matrix}'):
            with self.subTest(raw=raw):
                self.assertEqual(tokenize_label(raw), TOKEN_RE.findall(raw))

    def test_preserve_text_and_aliases(self):
        self.assertEqual(tokenize_label(r'cos x+\text{cos  x {sin}}'),
                         [r'\cos', 'x', '+', r'\text', '{', 'c', 'o', 's', ' ', 'x', ' ',
                          '{', 's', 'i', 'n', '}', '}'])
        self.assertEqual(tokenize_label(r'\left(α\le x\right)'), ['(', r'\alpha', r'\leq', 'x', ')'])
        with self.assertRaises(ValueError):
            tokenize_label(r'\text{unclosed')

    def test_round_trip_and_dedup_use_training_tokens(self):
        table = load_vocab()
        lookup = {t: i for i, t in enumerate(table)}
        for raw in ('sinxcosxdx', 's i n h (x)', 'ntcos x', r'\text{cos x}+arccos y'):
            tokens = tokenize_label(raw)
            self.assertEqual(tokenize_label(ids_to_latex([lookup[t] for t in tokens], table)), tokens)
        self.assertEqual(label_key('cos x'), label_key(r'\cos x'))
        self.assertEqual(label_key('c o s ( x )'), label_key(r'\cos(x)'))
        self.assertNotEqual(label_key(r'\sin x'), label_key(r'\sinx'))
        self.assertNotEqual(label_key(r'\text{a b}'), label_key(r'\text{ab}'))

    def test_python_java_vocab_and_new_ids(self):
        root = Path(__file__).resolve().parents[2]
        java = json.loads((root / 'main/java/model/checkpoints/tokeniser.json').read_text())['base']['vocab']
        table = load_vocab()
        self.assertEqual({t: i for i, t in enumerate(table)}, java)
        self.assertEqual(table[188:], ['\\' + n for n in
            'arcsin arccos arctan sinh cosh tanh exp max min cot sec csc'.split()])


if __name__ == '__main__':
    unittest.main()
