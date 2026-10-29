import unittest

import torch

from model import BeamState, bos_id, ids_to_latex, load_training_weights, load_vocab, parse_tokens, vital_mini, vocab_size
from train import GREEK_ALIASES, tokenize_label


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

    def test_multiline_formula_round_trip(self):
        label = r"x+y=2\\x-y=0"
        self.assertEqual(load_vocab()[186:], ["<boms>", r"\\"])
        self.assertEqual(tokenize_label(label).count(r"\\"), 1)
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
        for old_size in (147, 148, 149, 154, 186, 187):
            with self.subTest(old_size=old_size):
                old = vital_mini(num_tokens=old_size)
                new = vital_mini()
                self.assertTrue(load_training_weights(new, old.state_dict()))
                self.assertTrue(torch.equal(new.token_embedding.weight[:old_size], old.token_embedding.weight))
                self.assertEqual(new.head.weight.data_ptr(), new.token_embedding.weight.data_ptr())
                self.assertEqual(new.token_embedding.weight.shape[0], vocab_size)
                for key, tensor in old.state_dict().items():
                    if key not in {"head.weight", "token_embedding.weight"}:
                        self.assertTrue(torch.equal(tensor, new.state_dict()[key]), key)
                self.assertFalse(load_training_weights(vital_mini(), new.state_dict()))

    def test_square_token_and_aliases(self):
        self.assertEqual(load_vocab()[147:149], [" ", r"\square"])
        for label in (r"\square", r"\Box", "□"):
            self.assertEqual(self.round_trip(label), r"\square")
        self.assertEqual(self.round_trip(r"x=x\square"), r"x=x\square")

    def test_inconsistent_legacy_heads_are_rejected(self):
        state = vital_mini(num_tokens=148).state_dict()
        state["head.weight"] = state["head.weight"].clone() + 1
        with self.assertRaisesRegex(ValueError, "不一致"):
            load_training_weights(vital_mini(), state)

    def test_inner_product_labels(self):
        for label in (r"\langle u,v\rangle=\sum_{i=1}^{n}u_i\overline{v_i}",
                      r"\|v\|=\sqrt{\langle v,v\rangle}",
                      r"u\perp v", r"V^{\perp}", r"\bar{z}",
                      r"\langle\cdot,\cdot\rangle:V\times V\rightarrow\mathbb{C}"):
            with self.subTest(label=label):
                self.assertEqual(self.round_trip(label), label)
        self.assertEqual(self.round_trip(r"\left\langle u,v\right\rangle"), r"\langle u,v\rangle")
        self.assertEqual(self.round_trip("⟨u,v⟩=0"), r"\langle u,v\rangle=0")
        self.assertEqual(self.round_trip("u⊥v"), r"u\perp v")
        for label in (r"\lVert v\rVert", r"\Vert v\Vert", "‖v‖"):
            self.assertEqual(self.round_trip(label), r"\|v\|")

    def test_beam_search_uses_checkpoint_vocab_size(self):
        for size in (147, 148, 149, 154, vocab_size):
            with self.subTest(size=size):
                state = BeamState(tokens=torch.tensor([[bos_id, 11], [bos_id, 12]]),
                                  scores=torch.zeros(2))
                logits = torch.full((2, size), -100.0)
                logits[0, 20:22] = 0.0
                logits[1, size - 1] = 10.0
                result = parse_tokens(logits, state, beam_width=1, max_steps=10)
                self.assertEqual(result.tokens.tolist(), [[bos_id, 12, size - 1]])

    def test_unsupported_vocab_shape_is_rejected(self):
        with self.assertRaises(RuntimeError):
            load_training_weights(vital_mini(), vital_mini(num_tokens=146).state_dict())

    def test_greek_letters_and_variants(self):
        for symbol, command in GREEK_ALIASES.items():
            with self.subTest(symbol=symbol):
                self.assertEqual(self.round_trip(symbol), command)
                self.assertEqual(self.round_trip(command + "_1"), command + "_1")
        self.assertEqual(self.round_trip(r"\epsilon>0,\xi\in\mathbb{R},\phi(x)=0"),
                         r"\epsilon>0,\xi\in\mathbb{R},\phi(x)=0")
        self.assertEqual(self.round_trip(r"\{f_n\}"), r"\{f_n\}")


if __name__ == "__main__":
    unittest.main()
