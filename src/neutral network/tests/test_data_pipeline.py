import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import torch
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import data
from model import load_vocab, vital_mini
from train import MathWriting, collate, run_batch


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.patcher = patch.object(data, 'DATA_DIR', self.root)
        self.patcher.start()
        self.vocab = {token: i for i, token in enumerate(load_vocab())}

    def tearDown(self):
        self.patcher.stop()
        self.temp.cleanup()

    def image(self, name, y=15):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        image = Image.new('RGB', (90, 40), 'white')
        ImageDraw.Draw(image).line((15, y, 70, 30), fill='black', width=3)
        image.save(path)
        return path

    def manifest(self, path, rows):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(''.join(json.dumps(row) + '\n' for row in rows))

    def test_completed_download_does_not_access_network(self):
        path = self.root / 'file.zip'
        path.write_bytes(b'existing file')
        data.write_json(path.with_suffix('.zip.download.json'),
                        {'url': 'https://example.test/file', 'file': data.file_stamp(path)})
        with patch('urllib.request.urlopen', side_effect=AssertionError('network called')):
            self.assertEqual(data.download('https://example.test/file', path), path)

    def test_hf_cache_does_not_download_again(self):
        folder = self.root / 'downloads' / 'unimer'
        folder.mkdir(parents=True)
        archive = folder / 'UniMER-1M.zip'
        archive.write_bytes(b'downloaded')
        data.write_json(folder / '.download-complete.json', {
            'revision': data.HF_SOURCES['unimer'][1],
            'files': {archive.name: data.file_stamp(archive)}})
        with patch('huggingface_hub.snapshot_download', side_effect=AssertionError('download called')):
            self.assertEqual(data.download_hf('unimer'), folder)

    def test_zip_rejects_traversal(self):
        archive = self.root / 'unsafe.zip'
        with zipfile.ZipFile(archive, 'w') as stream:
            stream.writestr('../outside.txt', 'unsafe')
        with self.assertRaises(ValueError):
            data.extract_zip(archive, self.root / 'extracted')
        self.assertFalse((self.root / 'outside.txt').exists())

    def test_manifest_cache_skips_conversion(self):
        image = self.image('x.png')
        row = data.image_record(image, 'x', 'fixture', 'train')
        paths = data.prepare_manifests('fixture', {'version': 1}, lambda: iter([('train', row)]))
        def should_not_run():
            raise AssertionError('conversion repeated')
        self.assertEqual(data.prepare_manifests('fixture', {'version': 1}, should_not_run), paths)

    def test_mixing_excludes_duplicates_and_test_formulas(self):
        image = self.image('a.png')
        other = self.image('b.png', y=8)
        third = self.image('c.png', y=4)
        rows = [('test', data.image_record(image, 'x', 'fixture', 'test')),
                ('train', data.image_record(image, 'x', 'fixture', 'train')),
                ('train', data.image_record(other, 'x', 'fixture', 'train')),
                ('train', data.image_record(third, 'y', 'fixture', 'train'))]
        paths = data.prepare_manifests('fixture', {}, lambda: iter(rows))
        output = self.root / 'mixed'
        result = data.mix_manifests({'fixture': paths}, output)
        self.assertEqual(result['counts']['train/fixture'], 1)
        self.assertEqual(sum(result['skipped'].values()), 2)
        stamp = data.file_stamp(output / 'train.jsonl')
        data.mix_manifests({'fixture': paths}, output)
        self.assertEqual(stamp, data.file_stamp(output / 'train.jsonl'))

    def test_mixing_rehashes_legacy_labels_and_invalidates_old_cache(self):
        heldout = data.image_record(self.image('heldout.png'), r'\cos(x)', 'fixture', 'test')
        training = data.image_record(self.image('training.png', y=6), 'c o s ( x )', 'fixture', 'train')
        heldout['label_hash'], training['label_hash'] = 'old-a', 'old-b'
        paths = data.prepare_manifests('fixture', {}, lambda: iter([('test', heldout), ('train', training)]))
        output = self.root / 'mixed'
        report = data.mix_manifests({'fixture': paths}, output)
        self.assertEqual(report['skipped']['train/fixture/heldout_formula'], 1)
        self.assertEqual((output / 'train.jsonl').read_text(), '')
        report.pop('normalization_version')
        data.write_json(output / 'summary.json', report)
        refreshed = data.mix_manifests({'fixture': paths}, output)
        self.assertIn('normalization_version', refreshed)

    def test_unimer_uses_image_index_not_sorted_position(self):
        downloads = self.root / 'downloads'
        downloads.mkdir()
        image = self.image('sample.png').read_bytes()
        with zipfile.ZipFile(downloads / 'UniMER-1M.zip', 'w') as z:
            z.writestr('UniMER-1M/train.txt', 'a\nb\nc\n')
            z.writestr('UniMER-1M/images/0000002.png', image)
        with zipfile.ZipFile(downloads / 'UniMER-Test.zip', 'w') as z:
            for name in ('spe', 'cpe', 'sce', 'hwe'):
                z.writestr(f'UniMER-Test/{name}.txt', 'x\ny\n')
                z.writestr(f'UniMER-Test/{name}/0000001.png', image)
        paths = data.prepare_unimer(downloads, 0.05)
        rows = [json.loads(line) for p in paths.values() for line in p.read_text().splitlines()]
        self.assertEqual([r['latex'] for r in rows if r['original_split'] == 'train'], ['c'])
        self.assertEqual([r['latex'] for r in rows if r['original_split'] == 'test'], ['y'] * 4)

    def test_personal_train_and_mixed_batch_backward(self):
        root = self.root / 'mixed'
        picture = self.image('picture.png')
        ink = self.root / 'sample.inkml'
        ink.write_text('<ink xmlns="http://www.w3.org/2003/InkML"><trace>0 0, 20 20</trace></ink>')
        self.manifest(root / 'train.jsonl', [{'path': str(ink), 'format': 'inkml', 'latex': 'x'}])
        self.manifest(root / 'valid.jsonl', [{'image': str(picture), 'latex': 'y'}])
        personal = self.root / 'train'
        self.manifest(personal / 'labels.jsonl', [{'image': str(picture), 'latex': 'z'}])
        training = MathWriting(str(root), ['train'], self.vocab, True, personal_train=personal)
        validation = MathWriting(str(root), ['valid'], self.vocab, False, personal_train=personal)
        self.assertEqual(len(training), 2)
        self.assertEqual(len(validation), 1)
        batch = collate([training[0], training[1]])
        self.assertEqual(batch[0].shape[:3], (2, 3, 128))
        model = vital_mini()
        loss, _, _ = run_batch(model, batch, 'cpu', False)
        self.assertTrue(torch.isfinite(loss))
        loss.backward()
        self.assertIsNotNone(model.cnn.conv.weight.grad)

    def test_personal_images_need_labels(self):
        root = self.root / 'mixed'
        self.manifest(root / 'train.jsonl', [])
        self.image('train/unlabelled.png')
        with self.assertRaises(FileNotFoundError):
            MathWriting(str(root), ['train'], self.vocab, True, personal_train=self.root / 'train')

    def test_hme_parquet_conversion_and_reuse(self):
        import pyarrow as pa
        import pyarrow.parquet as pq
        image = self.image('sample.png').read_bytes()
        downloads = self.root / 'downloaded'
        (downloads / 'data').mkdir(parents=True)
        for split in ('train', 'test'):
            table = pa.Table.from_pylist([{'image': {'bytes': image, 'path': None},
                                         'label': 'x + 1', 'image_path': r'.\train\sample.png'}])
            pq.write_table(table, downloads / 'data' / f'{split}-00000-of-00001.parquet')
        paths = data.prepare_hme100k(downloads, 0.05)
        rows = [json.loads(line) for p in paths.values() for line in p.read_text().splitlines()]
        self.assertEqual(len(rows), 2)
        for row in rows:
            with Image.open(paths['train'].parent / row['path']) as decoded:
                self.assertEqual(decoded.size, (90, 40))
        with patch.object(data, 'image_hash', side_effect=AssertionError('conversion repeated')):
            data.prepare_hme100k(downloads, 0.05)

    def test_legacy_inkml_still_loads(self):
        folder = self.root / 'legacy' / 'train'
        folder.mkdir(parents=True)
        (folder / 'sample.inkml').write_text(
            '<ink xmlns="http://www.w3.org/2003/InkML">'
            '<annotation type="normalizedLabel">x+1</annotation>'
            '<trace>0 0, 20 20</trace></ink>')
        dataset = MathWriting(str(folder.parent), ['train'], self.vocab, False)
        self.assertEqual(len(dataset), 1)
        self.assertEqual(dataset[0][0].shape[:2], (1, 128))


if __name__ == '__main__':
    unittest.main()
