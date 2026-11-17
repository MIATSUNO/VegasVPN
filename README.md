# Las Vegas

**Las Vegas VPN** é um aplicativo Android voltado à consulta e seleção de servidores comunitários do **VPN Gate**.

## Recursos
- Consulta do catálogo VPN Gate sob demanda.
- Lista de servidores com país, endereço, ping e velocidade informados pelo catálogo.
- Seleção de servidor e interface em português.
- Tela inicial, configurações e widget Las Vegas.

Os **servidores Gate** são operados por voluntários.

## Compilação
Requisitos: JDK 17, Android SDK API 35 e build-tools 34.0.0.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

## Licença
Distribuído sob **GNU General Public License v2.0 only**. Consulte [LICENSE](LICENSE). O motor OpenVPN mantém sua licença em [openvpn-engine/LICENSE](openvpn-engine/LICENSE), e cada componente conserva os avisos de seus autores.
