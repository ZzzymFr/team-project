import json
import sys
import tempfile
import unittest
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from labels import tokenize_label
from model import ids_to_latex, load_training_weights, load_vocab, vital_mini, vocab_size


class Vocab234Tests(unittest.TestCase):
    def test_matrix_and_new_commands_round_trip(self):
        table = load_vocab()
        vocab = {token: index for index, token in enumerate(table)}
        self.assertEqual(vocab_size, 234)
        self.assertEqual(vocab['_'], 72)
        self.assertEqual(vocab['&'], 231)
        for label in (r'\begin{pmatrix}a&b\\c&d\end{pmatrix}',
                      r'\begin{cases}x&x\geq0\\-x&x<0\end{cases}',
                      r'\nabla f+\iint x+\ddot{x}', r'A\subseteq B\cup\emptyset',
                      r'\limsup_{n\rightarrow\infty}a_n', r'50\%'):
            tokens = tokenize_label(label)
            self.assertEqual(tokenize_label(ids_to_latex([vocab[t] for t in tokens], table)), tokens)

    def test_reject_duplicate_keys_and_invalid_ids(self):
        document = json.loads(Path(__file__).resolve().parents[1].joinpath('tokeniser.json').read_text())
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'tokeniser.json'
            raw = json.dumps(document)
            path.write_text(raw.replace('"_": 72', '"_": 72, "_": 234'))
            with self.assertRaises(ValueError):
                load_vocab(path)
            document['base']['vocab']['_'] = 234
            path.write_text(json.dumps(document))
            with self.assertRaises(ValueError):
                load_vocab(path)

    def test_append_migration_preserves_weights_and_new_rows_learn(self):
        torch.set_num_threads(2)
        old = vital_mini(num_tokens=200)
        model = vital_mini()
        self.assertTrue(load_training_weights(model, old.state_dict()))
        for key, value in old.state_dict().items():
            actual = model.state_dict()[key]
            if key in ('token_embedding.weight', 'head.weight'):
                actual = actual[:200]
            self.assertTrue(torch.equal(actual, value), key)
        self.assertIs(model.head.weight, model.token_embedding.weight)
        model.eval()
        tokens = torch.tensor([[68, 209, 231, 233]])
        logits = model(torch.rand(1, 3, 128, 32), tokens)
        self.assertEqual(tuple(logits.shape), (1, 4, 234))
        loss = torch.nn.functional.cross_entropy(logits.flatten(0, 1), torch.tensor([209, 231, 233, 69]))
        loss.backward()
        gradient = model.token_embedding.weight.grad
        self.assertTrue(torch.isfinite(gradient).all())
        self.assertGreater(float(gradient[200:].abs().sum()), 0)


if __name__ == '__main__':
    unittest.main()
