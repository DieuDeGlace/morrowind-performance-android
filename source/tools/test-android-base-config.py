"""Exercise the production config composer used by Android packaging."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import unittest

BASH = 'bash'
COMPOSER = Path(__file__).resolve().parents[1] / 'buildscripts/include/compose-android-config.sh'

class AndroidBaseConfigTest(unittest.TestCase):
    def compose(self, generated, android):
        with tempfile.TemporaryDirectory(prefix='openmw-config-') as temporary:
            directory = Path(temporary)
            engine = directory / 'engine.cfg'
            mobile = directory / 'android.cfg'
            output = directory / 'output.cfg'
            engine.write_text(generated, encoding='utf-8', newline='\n')
            mobile.write_text(android, encoding='utf-8', newline='\n')
            result = subprocess.run([BASH, COMPOSER.as_posix(), engine.as_posix(),
                                     mobile.as_posix(), output.as_posix()],
                                    capture_output=True, text=True)
            return result, output.read_text(encoding='utf-8')

    def test_android_owns_user_data_and_engine_vfs_data_is_preserved(self):
        result, output = self.compose(
            'user-data="desktop-path"\ndata-local="desktop-data"\n'
            'data=./resources/vfs-mw\ndata="engine-extra"\n'
            'fallback=First,1\nfallback=Second,2\n',
            'no-sound=0\nencoding=win1252\nuser-data="?userdata?"\n')
        self.assertEqual(result.returncode, 0, result.stderr)
        entries = output.splitlines()
        self.assertEqual([line for line in entries if line.startswith('user-data=')],
                         ['user-data="?userdata?"'])
        self.assertNotIn('data-local=', output)
        self.assertIn('data=./resources/vfs-mw', entries)
        self.assertIn('data="engine-extra"', entries)
        self.assertIn('fallback=First,1', entries)
        self.assertIn('fallback=Second,2', entries)

    def test_whitespace_in_generated_scalar_keys_is_filtered(self):
        result, output = self.compose('  user-data = "desktop"\n\tdata-local = "old"\n',
                                      'user-data="?userdata?"\n')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output.strip(), 'user-data="?userdata?"')

    def test_missing_android_user_data_is_rejected(self):
        result, _ = self.compose('user-data="desktop"\n', 'encoding=win1252\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('exactly one user-data entry; found 0', result.stderr)

    def test_duplicate_android_user_data_is_rejected(self):
        result, _ = self.compose('', 'user-data="first"\nuser-data="second"\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('exactly one user-data entry; found 2', result.stderr)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--bash', default='bash')
    BASH = parser.parse_args().bash
    unittest.main(argv=[__file__], verbosity=2)
