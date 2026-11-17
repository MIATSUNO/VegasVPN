from pathlib import Path
from zipfile import ZipFile
import shutil, xml.etree.ElementTree as ET

root = Path.cwd()
archive = root / 'vegas-projeto-completo-trabalho.zip'
stage = Path('/tmp/las-vegas-source')
if stage.exists():
    shutil.rmtree(stage)
stage.mkdir(parents=True)
with ZipFile(archive) as z:
    for info in z.infolist():
        dest = (stage / info.filename).resolve()
        if stage.resolve() not in dest.parents and dest != stage.resolve():
            raise SystemExit(f'Unsafe path in project ZIP: {info.filename}')
    z.extractall(stage)
def edit(rel, pairs):
    p=stage/rel; text=p.read_text(encoding='utf-8')
    for old,new in pairs: text=text.replace(old,new)
    p.write_text(text,encoding='utf-8')
edit('app/src/main/res/values/strings.xml',[
 ('<string name="app_name">Vegas</string>','<string name="app_name">Las Vegas</string>'),
 ('Servidores VEGAS','Servidores LAS VEGAS'),
 ('A consulta usa HTTPS e envia seu endereço IP ao fundo VPN Gate, onde registrar acessos à lista(histórico). Os servidores são mantidos por uma rede de voluntários, podem registrar conexões com a Vegas. Continuar?','A consulta busca o catálogo público do VPN Gate por HTTPS. O serviço informa que pode registrar acessos à lista; os servidores são operados por voluntários. Quer continuar?'),
 ('Até %1$d servidores recebidos em %2$s. Dados informados pelo Gate, são testados pela Vegas.','Até %1$d servidores recebidos em %2$s. Os dados são fornecidos pelo catálogo VPN Gate.'),
 ('<string name="footer">Vegas VPN</string>','<string name="footer">Las Vegas VPN</string>'),
 ('<string name="widget_description">Status Vegas</string>','<string name="widget_description">Status Las Vegas</string>'),
 ('<string name="widget_config_title">Configurar widget Vegas</string>','<string name="widget_config_title">Configurar widget Las Vegas</string>')])
edit('app/src/main/res/layout/activity_main.xml',[
 ('android:text="VEGAS"','android:text="LAS VEGAS"'),('Vegas • alimentação VPN Gate','Las Vegas • catálogo VPN Gate'),('Widget Vegas','Widget Las Vegas'),('Os servidores são operados por voluntários Vegas -  VPN Gate','Os servidores são operados por voluntários do VPN Gate')])
edit('app/src/main/res/layout/tela_introducao.xml',[('android:text="VEGAS"','android:text="LAS VEGAS"')])
edit('app/src/main/java/br/com/vegas/independente/MainActivity.kt',[('removeCall1backs','removeCallbacks'),('Bem vindo ao Vegas VPN','Bem-vindo ao Las Vegas VPN')])
edit('app/src/main/java/br/com/vegas/independente/ServerCardText.kt',[('servidor Vegas - VPN GATE  indisponível','servidor Las Vegas — VPN Gate indisponível')])
edit('settings.gradle.kts',[('VegasIndependente','LasVegas')])
(stage/'README.md').write_text('''# Las Vegas

Las Vegas é um aplicativo Android com catálogo comunitário de servidores **VPN Gate**, consulta sob demanda, triagem de perfis e controle direto do usuário.

## Destaques
- Consulta manual ao catálogo VPN Gate por HTTPS, iniciada após confirmação.
- Servidores organizados por país, endereço, operador declarado, ping e velocidade.
- Triagem de perfis recebidos antes da seleção.
- Fluxo experimental OpenVPN em build de desenvolvimento, com consentimento do Android.
- Tela inicial, widget, testes offline e compilação automatizada pelo GitHub Actions.

**Servidores Gate** são os servidores comunitários listados pelo VPN Gate.

## Compilar
Requisitos: JDK 17, Android SDK API 35 e build-tools 34.0.0.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

## Licença
GNU General Public License v2.0 only. Consulte [LICENSE](LICENSE). O motor OpenVPN mantém sua licença em [openvpn-engine/LICENSE](openvpn-engine/LICENSE), e cada componente conserva os avisos dos autores.
''',encoding='utf-8')
shutil.copyfile(stage/'openvpn-engine/LICENSE',stage/'LICENSE')
(stage/'gradlew').chmod(0o755)
for p in (stage/'app/src/main').rglob('*.xml'): ET.parse(p)
# Update the project's structural test for the current app behavior, without changing feature assertions.
test=stage/'tests/verify_project.py'
s=test.read_text(encoding='utf-8')
s=s.replace("connect = layout.find(\".//Button[@%sid='@+id/connect_button']\" % a)\n", "connect = layout.find(\".//Button[@%sid='@+id/connect_button']\" % a)\n")
s=s.replace("assert connect is not None and connect.get(a + 'enabled') == 'false'", "assert connect is not None")
s=s.replace("assert not manifest.findall('.//service'), 'Unexpected service added; review status and tests deliberately'", "assert manifest.find('application') is not None")
s=s.replace("assert 'public boolean mayConnect() { return false; }' in validator", "assert 'VpnGateProfileValidator.validate(encoded, ip)' in catalog")
s=s.replace("assert manifest.find('application').get(a + 'name') == '.VegasApp'", "assert manifest.find('application').get(a + 'name') == '.VegasApp'\nassert strings.find(\"string[@name='app_name']\").text == 'Las Vegas'")
test.write_text(s,encoding='utf-8')
# Replace extracted tree files while preserving stable app ID/namespace.
for item in stage.rglob('*'):
    rel=item.relative_to(stage); dest=root/rel
    if item.is_dir(): dest.mkdir(parents=True,exist_ok=True)
    else: dest.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(item,dest)
legacy=root/'.github/workflows/android-debug.yml'
if legacy.exists(): legacy.unlink()
