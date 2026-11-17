#!/usr/bin/env python3
"""Offline structural checks; not a substitute for Android build or device tests."""
from pathlib import Path
import json
import struct
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
main = root / 'app/src/main'
a = '{http://schemas.android.com/apk/res/android}'
xmls = list(main.rglob('*.xml'))
for path in xmls:
    ET.parse(path)
print(f'OK: {len(xmls)} XML files parsed')
manifest = ET.parse(main / 'AndroidManifest.xml').getroot()
permissions = [p.get(a + 'name') for p in manifest.findall('uses-permission')]
assert permissions == ['android.permission.INTERNET']
assert manifest.find('application').get(a + 'usesCleartextTraffic') == 'false'
assert not manifest.findall('.//service'), 'Unexpected service added; review status and tests deliberately'
receiver = manifest.find(".//receiver[@%sname='.VegasWidget']" % a)
assert receiver is not None and receiver.get(a + 'exported') == 'true'
assert receiver.find("intent-filter/action[@%sname='android.appwidget.action.APPWIDGET_UPDATE']" % a) is not None
assert receiver.find("meta-data[@%sname='android.appwidget.provider']" % a) is not None
layout = ET.parse(main / 'res/layout/activity_main.xml').getroot()
connect = layout.find(".//Button[@%sid='@+id/connect_button']" % a)
tor = layout.find(".//Switch[@%sid='@+id/tor_option']" % a)
assert connect is not None and connect.get(a + 'enabled') == 'false'
assert tor is None, 'Tor UI must not be included'
servers = json.loads((main / 'assets/servers.json').read_text(encoding='utf8'))
assert isinstance(servers, list)
assert all(isinstance(s, dict) and isinstance(s.get('name'), str) and s['name'].strip() for s in servers)
print(f'OK: server configuration has {len(servers)} valid entries')
widget = (main / 'java/br/com/vegas/independente/VegasWidget.kt').read_text(encoding='utf8')
assert 'setOnClickPendingIntent(R.id.widget_root, pending)' in widget
assert 'Intent(context, MainActivity::class.java)' in widget
assert 'FLAG_IMMUTABLE' in widget
strings = ET.parse(main / 'res/values/strings.xml').getroot()
assert strings.find("string[@name='widget_status']").text == 'VPN: consulte o aplicativo'
assert 'separado do VPN Gate' in strings.find("string[@name='tor_heading']").text
assert 'não bloqueia o tráfego normal' in strings.find("string[@name='tor_summary']").text
assert manifest.find(".//activity[@%sname='.WidgetConfigActivity']" % a) is not None
info = ET.parse(main / 'res/xml/vegas_widget_info.xml').getroot()
assert info.get(a + 'configure') == 'br.com.vegas.independente.WidgetConfigActivity'
assert info.get(a + 'updatePeriodMillis') == '0'
for status in ('idle', 'loading', 'success', 'failed', 'interrupted'):
    assert strings.find("string[@name='widget_" + status + "']") is not None
assert 'CatalogRepository.state(context)' in widget
assert 'CatalogRepository.refresh' not in widget
activity = (main / 'java/br/com/vegas/independente/MainActivity.kt').read_text(encoding='utf8')
assert 'setPositiveButton(R.string.catalog_consent_yes) { _, _ -> CatalogRepository.refresh(applicationContext) }' in activity
assert 'CatalogRepository.addListener(listener)' in activity and 'CatalogRepository.removeListener(listener)' in activity
assert 'server.inspection' in activity and 'profile_has_check' in activity
assert 'server.screeningReasons' in activity
catalog = (main / 'java/br/com/vegas/independente/VpnGateCatalog.java').read_text(encoding='utf8')
validator = (main / 'java/br/com/vegas/independente/VpnGateProfileValidator.java').read_text(encoding='utf8')
assert 'VpnGateProfileValidator.validate(encoded, ip)' in catalog
assert 'public boolean mayConnect() { return false; }' in validator
assert '.chars()' not in validator.replace('String.chars()', '')
assert strings.find("string[@name='profile_screening_incompatible']") is not None
assert strings.find("string[@name='profile_screening_no_finding']") is not None
assert manifest.find('application').get(a + 'allowBackup') == 'false'
assert manifest.find('application').get(a + 'name') == '.VegasApp'
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    data = (main / f'res/drawable-{density}/vegas_launcher.png').read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n' and struct.unpack('>II', data[16:24]) == (size, size)
print('OK: disabled connection, no Tor UI, widget declaration and click wiring, icons')
print('Note: static checks do not compile Kotlin, verify UI behavior, or run Android.')
