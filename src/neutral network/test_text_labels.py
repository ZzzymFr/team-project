import unittest

import torch

from model import ids_to_latex, load_training_weights, load_vocab, vital_mini
from train import tokenize_label


class TextLabelTests(unittest.TestCase):
    def round_trip(self, label):
        table = load_vocab()
        vocab = {token: i for i, token in enumerate(table)}
        tokens = tokenize_label(label)
        self.assertTrue(all(token in vocab for token in tokens))
        return ids_to_latex([vocab[token] for token in tokens], table)

    def test_complete_sentence(self):
        label = r"\text{let }v\in V\text{ be arbitrary, }v=(x_1,...x_n)"
        self.assertEqual(self.round_trip(label), label)

    def test_text_is_not_math_and_preserves_word_spaces(self):
        label = r"\text{sin and cost {stay} literal}"
        self.assertEqual(self.round_trip(label), label)
        self.assertNotIn(r"\sin", tokenize_label(label))
        self.assertNotIn(r"\cos", tokenize_label(label))

    def test_math_normalization_and_command_separator(self):
        self.assertEqual(self.round_trip(r"sin(x) + \pi x"), r"\sin(x)+\pi x")
        self.assertEqual(self.round_trip(r"a_{i+1}=a_i+2"), r"a_{i+1}=a_i+2")

    def test_text_whitespace_and_unbalanced_braces(self):
        self.assertEqual(self.round_trip("\\text{a\t b\n c}"), r"\text{a b c}")
        with self.assertRaisesRegex(ValueError, "右花括号"):
            tokenize_label(r"\text{unfinished")

    def test_legacy_weights_keep_existing_rows_and_ties(self):
        old = vital_mini(num_tokens=147)
        new = vital_mini()
        self.assertTrue(load_training_weights(new, old.state_dict()))
        self.assertTrue(torch.equal(new.token_embedding.weight[:147], old.token_embedding.weight))
        self.assertEqual(new.head.weight.data_ptr(), new.token_embedding.weight.data_ptr())
        self.assertEqual(new.token_embedding.weight.shape[0], 148)
        for key, tensor in old.state_dict().items():
            if key not in {"head.weight", "token_embedding.weight"}:
                self.assertTrue(torch.equal(tensor, new.state_dict()[key]), key)
        self.assertFalse(load_training_weights(vital_mini(), new.state_dict()))


if __name__ == "__main__":
    unittest.main()
