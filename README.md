# Las Vegas

**Las Vegas** é um aplicativo Android com catálogo comunitário de servidores **VPN Gate**, consulta sob demanda, triagem dos perfis e controle direto do usuário.

## Destaques
- Consulta manual do catálogo VPN Gate por HTTPS, iniciada após confirmação.
- Servidores organizados por país, endereço, operador declarado, ping e velocidade.
- Triagem dos perfis recebidos antes da seleção.
- Fluxo experimental OpenVPN em build de desenvolvimento, com consentimento do Android.
- Tela inicial e widget para acompanhar a consulta.
- Interface em português, testes offline e compilação automatizada pelo GitHub Actions.

**Servidores Gate** refere-se ao catálogo público do VPN Gate, cujos servidores são operados por voluntários.

## Compilar
Requisitos: JDK 17, Android SDK API 35 e build-tools 34.0.0.
```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

## Licença
Distribuído sob **GNU General Public License v2.0 only**. Consulte [LICENSE](LICENSE). O motor OpenVPN mantém sua licença em [openvpn-engine/LICENSE](openvpn-engine/LICENSE), e cada componente conserva os avisos de seus autores.
