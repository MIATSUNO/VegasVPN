#!/usr/bin/env python3
"""Generate a disposable test PKI in a private temp dir, test, and remove it."""
import os, subprocess, tempfile
from pathlib import Path
root = Path(__file__).resolve().parent.parent
src = root / 'app/src/main/java/br/com/vegas/independente'
with tempfile.TemporaryDirectory(prefix='vegas-strict-') as tmp:
    os.chmod(tmp, 0o700)
    pki = Path(tmp)/'pki'; classes=Path(tmp)/'classes'
    pki.mkdir(); classes.mkdir()
    subprocess.run(['sh', 'tests/generate-fixture.sh', str(pki)], cwd=root, check=True)
    subprocess.run(['openssl', 'rsa', '-in', str(pki/'client.key'), '-traditional', '-out', str(pki/'client-rsa-traditional.key')], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    sources = [src/n for n in ('FixturePolicy.java','ProfileInspection.java','VpnGateProfileValidator.java','ExperimentalGatePolicy.java')]
    sources += [root/'tests/StrictProfileTest.java',root/'tests/FixturePolicyTest.java',root/'tests/ExperimentalGatePolicyTest.java']
    subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),*map(str,sources)], cwd=root, check=True)
    for name in ('FixturePolicyTest','StrictProfileTest'):
        subprocess.run(['java','-cp',str(classes),name,str(pki)],cwd=root,check=True)
    subprocess.run(['java','-cp',str(classes),'ExperimentalGatePolicyTest',str(pki),str(root/'tests/fixtures/isrg-root-x1.pem')],cwd=root,check=True)
