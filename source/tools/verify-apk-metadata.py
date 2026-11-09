#!/usr/bin/env python3
"""Verify the signed preview's version against the Gradle source of truth."""
import argparse
from pathlib import Path
import re


def expected_version(gradle):
    patterns = (
        r"def ragdollPreviewVersionCode = (\d+)",
        r"def openMwAndroidVersionName = '([^']+)'",
        r'versionName calculateVersion\(\) \+ "([^"]+)"',
    )
    values = []
    for pattern in patterns:
        match = re.search(pattern, gradle)
        if not match:
            raise ValueError('Cannot determine the release version from app/build.gradle')
        values.append(match[1])
    return values[0], values[1] + values[2]


def verify(gradle, info):
    code, name = expected_version(gradle)
    if f"versionCode='{code}'" not in info or f"versionName='{name}'" not in info:
        raise ValueError(f'Signed APK version must match Gradle: {name} ({code})')
    return code, name


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--gradle', required=True, type=Path)
    parser.add_argument('--info', required=True, type=Path)
    args = parser.parse_args()
    code, name = verify(args.gradle.read_text(), args.info.read_text())
    print(f'Verified signed APK version: {name} ({code})')


if __name__ == '__main__':
    main()
