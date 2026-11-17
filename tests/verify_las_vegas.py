from pathlib import Path
import xml.etree.ElementTree as ET
root=Path('.')
for p in (root/'app/src/main').rglob('*.xml'):
    ET.parse(p)
strings=ET.parse(root/'app/src/main/res/values/strings.xml').getroot()
assert strings.find("string[@name='app_name']").text == 'Las Vegas'
assert (root/'LICENSE').is_file()
assert (root/'README.md').is_file()
assert (root/'vegas-projeto-completo-trabalho.zip').exists() is False
assert (root/'app/build.gradle.kts').read_text().find('applicationId = "br.com.vegas.independente"') >= 0
print('OK: Las Vegas identity, open-source files, Android XML and stable application ID')
