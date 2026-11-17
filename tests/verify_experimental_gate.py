#!/usr/bin/env python3
"""Static wiring regressions only; not a substitute for Android/TLS integration tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent.parent
read=lambda p:(r/p).read_text()
g=read('app/src/main/java/br/com/vegas/independente/GateVpn.kt')
a=read('app/src/main/java/br/com/vegas/independente/MainActivity.kt')
p=read('app/src/main/java/br/com/vegas/independente/ExperimentalGatePolicy.java')
m=read('openvpn-engine/src/main/java/de/blinkt/openvpn/core/OpenVpnManagementThread.java')
s=read('app/src/main/java/br/com/vegas/independente/GateSession.java')
manifest=read('app/src/debug/AndroidManifest.xml')
assert 'ENGINE_LIFECYCLE_VERIFIED' not in g
assert 'fun canLaunch() = BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 26' in g
assert 'check(canLaunch())' in g
for x in ('VpnService.prepare(ctx)', 'ExperimentalGatePolicy.consentMatches(hash, row.encodedProfile)', 'CatalogRepository.results()?.any { it === row }', 'VPNLaunchHelper.startOpenVpn(p, ctx.applicationContext, nonce)', 'p.mConnections[0].mServerName=v.ip', 'p.mConnections[0].mServerPort=v.port','p.mCheckRemoteCN=true', 'p.mRemoteCN=ExperimentalGatePolicy.NAME', 'p.mExpectTLSCert=true','p.mUseCustomConfig=false','p.mConnections[0].mUseCustomConfig=false'):
 assert x in g,x
assert 'if (!debug) return null;' in p and 'ROOT_SHA256.equals' in p and 'ca.checkValidity()' in p
assert 'GateVpn.approveCandidate()' in a and 'GateVpn.requestAndroidConsent(this)' in a and 'resultCode == RESULT_OK' in a
assert 'IP, DNS ou tráfego podem vazar' in a and 'podem registrar' in a
assert 'getDeclaredMethod("getInt$")' not in m and 'getDeclaredMethod("setInt$"' not in m
assert 'ParcelFileDescriptor.dup(received)' in m and 'duplicate.getFd()' in m and 'mOpenVPNService.protect(number)' in m
assert 'pfd.getFileDescriptor()' in m and 'failSocketProtection()' in m and 'PROTECT_FAILED' in m
assert '"CONNECTED".equals(currentstate)' in m and 'vegasPauseEvents.progress(SessionVpnStatus.Event.CONNECTED)' in m
assert 'terminalPending && event == Event.CONNECTED' in s
assert 'android:enabled="true"' in manifest and 'android.permission.BIND_VPN_SERVICE' in manifest
print('PASS static debug gate, consent/hash/endpoint/TLS, no hidden FD APIs, fail-closed, no synthetic CONNECTED, service')
