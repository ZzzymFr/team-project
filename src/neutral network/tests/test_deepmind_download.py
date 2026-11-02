import hashlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import data


class DeepMindDownloadTests(unittest.TestCase):
    def test_verified_download_and_offline_reuse(self):
        payload = b'fixture archive'
        with tempfile.TemporaryDirectory() as directory, patch.object(data, 'DATA_DIR', Path(directory)), \
                patch.object(data, 'DEEPMIND_MD5', hashlib.md5(payload).hexdigest()):
            def fake_download(url, target):
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(payload)
                return target
            with patch.object(data, 'download', side_effect=fake_download) as download:
                report = data.prepare_deepmind()
                download.assert_called_once()
            self.assertEqual(report['sha256'], hashlib.sha256(payload).hexdigest())
            self.assertFalse(report['ocr_compatible'])
            self.assertFalse(report['extracted'])
            with patch.object(data, 'download', side_effect=AssertionError('network called')):
                self.assertEqual(data.prepare_deepmind(), report)

    def test_corrupt_archive_does_not_get_success_marker(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(data, 'DATA_DIR', Path(directory)):
            archive = data.DATA_DIR / 'downloads' / data.DEEPMIND_FILENAME
            archive.parent.mkdir()
            archive.write_bytes(b'corrupt')
            with patch.object(data, 'download', return_value=archive):
                with self.assertRaisesRegex(ValueError, '校验失败'):
                    data.prepare_deepmind()
            self.assertFalse((data.DATA_DIR / 'deepmind/download.json').exists())

    def test_standalone_cli_never_mixes_text_with_images(self):
        with patch.object(sys, 'argv', ['data.py', '--sources', 'deepmind']), \
                patch.object(data, 'prepare_deepmind') as download, \
                patch.object(data, 'mix_manifests', side_effect=AssertionError('text mixed into OCR')):
            data.main()
            download.assert_called_once_with()


if __name__ == '__main__':
    unittest.main()
