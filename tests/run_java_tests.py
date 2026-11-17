#!/usr/bin/env python3
"""Run offline Java tests, including isolated loopback SOCKS tests; fail CI on failure."""
import os
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
src = root / 'app/src/main/java/br/com/vegas/independente'
tests = root / 'tests'
# TMPDIR can be overridden in constrained environments.
with tempfile.TemporaryDirectory(prefix='vegas-java-tests-') as tmp:
    files = [src / n for n in ('VpnGateCatalog.java', 'ProfileInspection.java', 'CatalogState.java', 'VpnGateProfileValidator.java')]
    files += [tests / n for n in ('CatalogTest.java', 'CatalogStateTest.java', 'ProfileInspectionTest.java', 'VpnGateProfileValidatorTest.java', 'ExperimentalTorSocks.java', 'ExperimentalTorSocksTest.java', 'TorSafetyModel.java', 'TorSafetyModelTest.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', tmp, *map(str, files)], check=True, cwd=root)
    for name in ('CatalogTest', 'CatalogStateTest', 'ProfileInspectionTest', 'VpnGateProfileValidatorTest', 'br.com.vegas.independente.ExperimentalTorSocksTest', 'br.com.vegas.independente.TorSafetyModelTest'):
        print('RUN:', name, flush=True)
        subprocess.run(['java', '-cp', tmp, name], check=True, cwd=root)
print('PASS: all six offline Java test programs')
