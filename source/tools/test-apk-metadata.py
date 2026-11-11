#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('apk_metadata', Path(__file__).with_name('verify-apk-metadata.py'))
metadata = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metadata)


class ApkMetadataTests(unittest.TestCase):
    def fixture(self, code, preview):
        return f"""def ragdollPreviewVersionCode = {code}
def openMwAndroidVersionName = '0.51.0-21-performance'
versionName calculateVersion() + "-ragdoll-preview.{preview}"
"""

    def test_current_and_future_previews_follow_gradle(self):
        for code, preview in ((5130, 11), (5131, 12)):
            name = f'0.51.0-21-performance-ragdoll-preview.{preview}'
            self.assertEqual(metadata.verify(self.fixture(code, preview), f"versionCode='{code}' versionName='{name}'"), (str(code), name))

    def test_old_version_code_is_rejected(self):
        with self.assertRaises(ValueError):
            metadata.verify(self.fixture(5130, 11), "versionCode='5129' versionName='0.51.0-21-performance-ragdoll-preview.11'")

    def test_wrong_version_name_is_rejected(self):
        with self.assertRaises(ValueError):
            metadata.verify(self.fixture(5130, 11), "versionCode='5130' versionName='0.51.0-21-performance-ragdoll-preview.10'")

    def test_missing_definition_is_rejected(self):
        with self.assertRaises(ValueError):
            metadata.expected_version('')

    def test_checkout_version_is_readable(self):
        gradle = Path(__file__).resolve().parents[1] / 'app/build.gradle'
        code, name = metadata.expected_version(gradle.read_text())
        self.assertGreater(int(code), 5121)
        self.assertTrue(name.startswith('0.53.0-1-performance-ragdoll-preview.'))


if __name__ == '__main__':
    unittest.main()
