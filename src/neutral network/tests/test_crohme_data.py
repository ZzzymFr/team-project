import io
from contextlib import ExitStack
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import data


class Response(io.BytesIO):
    def __init__(self, payload, status, headers):
        super().__init__(payload)
        self.status, self.headers = status, headers


class RangeDownloadTests(unittest.TestCase):
    def test_resume_validate_ranges_merge_and_reuse_offline(self):
        payload = bytes(range(37))
        requests = []
        def urlopen(request, **kwargs):
            if request.get_method() == 'HEAD':
                return Response(b'', 200, {'Content-Length': str(len(payload)), 'ETag': 'version1'})
            value = request.get_header('Range')
            requests.append(value)
            start, end = map(int, value.removeprefix('bytes=').split('-'))
            return Response(payload[start:end + 1], 206,
                            {'Content-Range': f'bytes {start}-{end}/{len(payload)}'})
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'archive.zip'
            target.with_suffix('.zip.part').write_bytes(payload[:17])
            with patch('urllib.request.urlopen', side_effect=urlopen):
                data.download_ranges('https://example.test/archive', target, workers=2, part_size=8)
            self.assertEqual(target.read_bytes(), payload)
            self.assertNotIn('bytes=0-7', requests)
            self.assertNotIn('bytes=8-15', requests)
            self.assertFalse(target.with_suffix('.zip.ranges').exists())
            with patch('urllib.request.urlopen', side_effect=AssertionError('network called')):
                self.assertEqual(data.download_ranges('https://example.test/archive', target), target)

    def test_rejects_server_ignoring_range(self):
        def urlopen(request, **kwargs):
            return Response(b'bad', 200, {'Content-Length': '3'})
        with tempfile.TemporaryDirectory() as directory, patch('urllib.request.urlopen', side_effect=urlopen):
            target = Path(directory) / 'archive.zip'
            with self.assertRaisesRegex(ValueError, '范围不匹配'):
                data.download_ranges('https://example.test/archive', target, workers=1, part_size=8)
            self.assertFalse(target.exists())


class CrohmePreparationTests(unittest.TestCase):
    def test_default_mix_includes_crohme_and_sample_remains_mathwriting_only(self):
        for arguments, expected, output in [
            ([], {'mathwriting', 'unimer', 'hme100k-hf', 'crohme2023'}, 'mixed'),
            (['--sample'], {'mathwriting'}, 'mixed-sample'),
        ]:
            with self.subTest(arguments=arguments), tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
                stack.enter_context(patch.object(data, 'DATA_DIR', Path(directory)))
                stack.enter_context(patch.object(sys, 'argv', ['data.py'] + arguments))
                for name in ('download', 'extract', 'download_hf', 'prepare_mathwriting',
                             'prepare_unimer', 'prepare_hme100k', 'prepare_crohme2023'):
                    stack.enter_context(patch.object(data, name, return_value={}))
                mix = stack.enter_context(patch.object(data, 'mix_manifests', return_value={'counts': {}}))
                data.main()
                self.assertEqual(set(mix.call_args.args[0]), expected)
                self.assertEqual(mix.call_args.args[1], Path(directory).resolve() / output)

    def test_parallel_extraction_preserves_files_and_rejects_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'input.zip'
            content = {f'data/group{i % 3}/{i}.inkml': f'trace {i}' for i in range(30)}
            with zipfile.ZipFile(archive, 'w') as stream:
                for name, text in content.items():
                    stream.writestr(name, text)
            data.extract_zip(archive, root / 'output', workers=4)
            for name, text in content.items():
                self.assertEqual((root / 'output' / name).read_text(), text)
            with zipfile.ZipFile(archive, 'w') as stream:
                stream.writestr('../outside.txt', 'invalid')
            with self.assertRaises(ValueError):
                data.extract_zip(archive, root / 'unsafe', workers=4)
            self.assertFalse((root / 'outside.txt').exists())

    def test_official_splits_labels_and_unpaired_modalities(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(data, 'DATA_DIR', Path(directory)):
            root = Path(directory) / 'crohme2023/TC11_CROHME23'
            for split, label in [('train', '$cos x$'), ('val', 'y'), ('test', 'z')]:
                path = root / 'INKML' / split / 'source/sample.inkml'
                path.parent.mkdir(parents=True)
                path.write_text('<ink xmlns="http://www.w3.org/2003/InkML">'
                                f'<annotation type="truth">{label}</annotation>'
                                '<trace>0 0, 10 10</trace></ink>', encoding='utf-8')
            orphan = root / 'INKML/train/source/orphan.inkml'
            orphan.write_text('<ink xmlns="http://www.w3.org/2003/InkML"><trace>0 0</trace>'
                              '<traceGroup><annotation type="truth">x</annotation></traceGroup></ink>')
            archive = Path(directory) / 'CROHME23.zip'
            archive.write_bytes(b'fixture')
            with patch.object(data, 'download_crohme2023', return_value=archive), \
                    patch.object(data, 'extract_zip'):
                paths = data.prepare_crohme2023()
            for split, label in [('train', 'cos x'), ('valid', 'y'), ('test', 'z')]:
                rows = [json.loads(line) for line in paths[split].read_text().splitlines()]
                self.assertEqual(len(rows), 1)
                self.assertEqual(rows[0]['latex'], label)
                self.assertEqual(rows[0]['format'], 'inkml')
            report = json.loads((root.parent / 'conversion.json').read_text())
            self.assertEqual(report['skipped']['train/missing_label_or_traces'], 1)

    def test_standalone_cli_preserves_existing_mixed_directory(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(data, 'DATA_DIR', Path(directory)), \
                patch.object(sys, 'argv', ['data.py', '--sources', 'crohme2023']), \
                patch.object(data, 'prepare_crohme2023', return_value={}), \
                patch.object(data, 'mix_manifests', return_value={'counts': {}}) as mix:
            data.main()
            self.assertEqual(mix.call_args.args[1], Path(directory).resolve() / 'crohme2023-mixed')


if __name__ == '__main__':
    unittest.main()
