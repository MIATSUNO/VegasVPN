#!/usr/bin/env python3
"""Verify the research transport is excluded and the research transport stays outside the app."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
main = root / 'app/src/main'
a = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(main / 'AndroidManifest.xml').getroot()
assert [p.get(a+'name') for p in manifest.findall('uses-permission')] == ['android.permission.INTERNET']
assert not manifest.findall('.//service')
for p in (root / 'app/src').rglob('*'):
    if p.is_file() and p.suffix in ('.kt', '.java', '.xml'):
        s = p.read_text(encoding='utf8')
        assert 'ExperimentalTorSocks' not in s, p
        assert 'TorSafetyModel' not in s, p
        assert 'ArtiProxy' not in s, p
settings = (root/'settings.gradle.kts').read_text()
build = (root/'app/build.gradle.kts').read_text()
assert 'gpmaven' not in settings and 'arti-mobile' not in build
assert (root/'tests/ExperimentalTorSocks.java').exists()
assert (root/'tests/TorSafetyModel.java').exists()
print('OK: research transport excluded from app sources; no Arti dependency; this test does not inspect merged motor manifest')
print('Static checks only; not a merged manifest or runtime test.')
