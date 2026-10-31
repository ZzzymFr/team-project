import contextlib
import argparse
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import train
import train_decoder
from model import vital_mini


class DecoderTrainingTests(unittest.TestCase):
    def test_missing_pretrained_cnn_is_rejected_before_loading_dataset(self):
        with tempfile.TemporaryDirectory() as directory:
            args = argparse.Namespace(data='unused', batch_size=1, workers=0, freeze_cnn=True,
                                      weights=Path(directory) / 'output.pt',
                                      init_weights=Path(directory) / 'missing.pt')
            with self.assertRaisesRegex(FileNotFoundError, '预训练权重'):
                train.train(args)

    def test_entrypoint_uses_shared_training_and_checkpoint_output(self):
        with patch.object(train, 'train') as run, \
                patch.object(train, 'training_log', side_effect=lambda args: contextlib.nullcontext()):
            train_decoder.main(['--epochs', '2', '--batch-size', '3'])
            args = run.call_args.args[0]
            self.assertTrue(args.freeze_cnn)
            self.assertEqual(args.epochs, 2)
            self.assertEqual(args.batch_size, 3)
            self.assertEqual(args.weights.parent, Path(train.__file__).resolve().parents[2] / 'checkpoints')
            self.assertEqual(args.init_weights, args.weights)

    def test_regular_entrypoint_still_trains_full_generator(self):
        with patch.object(train, 'train') as run, \
                patch.object(train, 'training_log', side_effect=lambda args: contextlib.nullcontext()):
            train.main([])
            self.assertFalse(run.call_args.args[0].freeze_cnn)
            self.assertEqual(run.call_args.args[0].weights.parent.name, 'checkpoints')

    def test_frozen_cnn_parameters_and_buffers_survive_train_eval_cycles(self):
        torch.set_num_threads(2)
        torch.manual_seed(42)
        model = vital_mini()
        model.cnn.requires_grad_(False)
        for p in model.judge_parameters():
            p.requires_grad_(False)
        before = {k: v.clone() for k, v in model.cnn.state_dict().items()}
        decoder_before = model.decoder1.cross_attention.q_proj.weight.detach().clone()
        embedding_before = model.token_embedding.weight.detach().clone()
        optimizer = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=1e-4)
        for _ in range(2):
            model.eval()
            train.set_training_mode(model, True)
            self.assertFalse(model.cnn.training)
            self.assertTrue(model.decoder1.training)
            image = torch.rand(2, 3, 128, 64)
            tokens = torch.tensor([[68, 188, 12], [68, 199, 13]])
            targets = torch.tensor([[188, 12, 69], [199, 13, 69]])
            loss = torch.nn.functional.cross_entropy(model(image, tokens).flatten(0, 1), targets.flatten())
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            self.assertTrue(all(p.grad is None for p in model.cnn.parameters()))
            optimizer.step()
        self.assertTrue(all(torch.equal(before[k], v) for k, v in model.cnn.state_dict().items()))
        self.assertFalse(torch.equal(decoder_before, model.decoder1.cross_attention.q_proj.weight))
        self.assertFalse(torch.equal(embedding_before, model.token_embedding.weight))
        self.assertIs(model.head.weight, model.token_embedding.weight)


if __name__ == '__main__':
    unittest.main()
