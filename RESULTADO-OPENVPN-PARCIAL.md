# Resultado OpenVPN — 16/11/2026

## Estado real: NÃO CONECTA

Esta é uma cópia experimental independente; **não é uma VPN funcional nem uma integração concluída**. Não usar ou distribuir como VPN. Foi incorporado e compilado um módulo contendo código Java Android e binários nativos reais de OpenVPN, mas não foi ligada a interface Vegas ao motor, não foi implementado fluxo de consentimento Android, nem conexão, desconexão ou estados de túnel. O botão continua desativado. O serviço do módulo e suas três atividades foram desativados no manifesto; as atividades também não são exportadas. O widget informa somente o catálogo. Não há proteção, kill switch ou garantia contra vazamentos.

## O que foi feito

- Copiada a base `output/vegas-vpngate-tor-experimental-20261116` para `output/vegas-openvpn-integrado-20261116`.
- Baixado o projeto `https://github.com/nizwar/openvpn_library`, commit `ccd61bc39bcb5b03281615ce669d2109557f6bd1`, e copiado `vpnLib` para `openvpn-engine`. O módulo é baseado em ics-openvpn e contém o serviço `de.blinkt.openvpn.core.OpenVPNService`, gerenciamento e binários OpenVPN/ovpn3 para arm64-v8a, armeabi-v7a, x86 e x86_64. O build usa compileSdk 35 e AndroidX; foi alterado o manifesto para desabilitar serviço e atividades. Os binários são pré-compilados recebidos do upstream, não construídos neste ambiente.
- `settings.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`: inclusão do módulo e AndroidX. UI: removida a seção Tor, preservada identidade Vegas; texto informa bloqueio. Nada foi ligado à classe `OpenVpnApi` do módulo. O catálogo e a triagem anteriores continuam: a lista HTTPS exige confirmação; perfis não são persistidos intencionalmente e não são executados. `mayConnect()` sempre retorna false.
- Projeto traz arquivos e pesquisas históricas da base, inclusive estudos de Tor fora do aplicativo; Tor não foi incorporado ao APK. Os relatórios anteriores descrevem etapas antigas, não o estado desta cópia; este documento prevalece.

## Descobertas, bloqueios e segurança

Uma consulta HTTPS ao catálogo real revelou um perfil SoftEther com `proto tcp`, `cipher AES-128-CBC`, `data-ciphers AES-128-CBC`, `auth SHA1`, `resolv-retry infinite`, `<ca>`, `<cert>` e `<key>`, mas sem `remote-cert-tls` ou verificação específica explícita do nome de servidor. O atual validador não é um parser completo desse formato e irá rejeitá-lo; não foi modificado para autorizar. A presença da CA no mesmo perfil do diretório não estabelece identidade específica do relay por canal independente. Sem política de identidade verificável e parser que reconstrua somente diretivas permitidas, habilitar o motor poderia passar configuração não confiável ou indicar segurança não comprovada. Não foram ignorados erros de certificado nem executadas diretivas dos perfis.

Não foi desenvolvido e testado fluxo completo de consentimento, ciclo de vida, API 35 foreground service, estados de handshake, tratamento de revogação, rotas IPv4/IPv6, DNS e falhas. Não se pode assumir funcionamento ou proteção porque o wrapper contém essas classes. O motor não foi testado em dispositivo, com servidor controlado ou relays VPN Gate. Não foi feita auditoria de segurança, identidade, atualidade das bibliotecas nativas, comportamento do serviço, autenticação ou vazamentos.

## Licenças e proveniência

O wrapper declara GPL versão 2 (`openvpn-engine/LICENSE`) e inclui avisos em `openvpn-engine/src/main/assets/full_licenses.html` sobre ics-openvpn e exceção OpenSSL. O projeto upstream consultado não disponibiliza no próprio módulo os fontes e scripts correspondentes a todos os binários nativos pré-compilados incluídos; não foi possível determinar commits exatos ou verificar licenças e código-fonte correspondente de todos os componentes. O repositório é privado, mas isso **não garante** dispensa de obrigações ao distribuir o APK; não distribuir antes de identificar fontes correspondentes, versões, licenças transitivas e cumprir as obrigações aplicáveis. Nenhuma publicação foi feita. Outra alternativa investigada, o AAR `io.github.tim06:openvpn:1.1.3`, continha `libovpn3.so`, mas o repositório de código indicado no POM retornou indisponível; não foi incorporada.

## Build e testes

JDK 17, Gradle 8.10.2, AGP 8.7.3, SDK 35, build tools 34; `:app:assembleDebug` concluiu `BUILD SUCCESSFUL` (último build 40s, 10 tarefas executadas e 46 up-to-date). O APK é debug, não produção: `app/build/outputs/apk/debug/app-debug.apk`, 97.044.400 bytes, SHA-256 `53767b000e6ee39668c2dd0c655bc9824e4a2ececfc1d099a7606e5afb77e95a`; verificação de assinatura v1 e v2 passou. O APK possui bibliotecas nativas nas quatro ABIs mencionadas. O manifesto mesclado foi inspecionado com `aapt`: serviço desativado e protegido por `BIND_VPN_SERVICE`; atividades do motor desativadas e não exportadas. O APK é uma evidência experimental do build, não uma entrega funcional nem apropriada para distribuição.

`python3 tests/verify_project.py` passou; `python3 tests/run_java_tests.py` passou seis programas herdados, entre eles testes de CSV, estados do catálogo, análise superficial de perfis, diretivas perigosas e 2.000 entradas pseudoaleatórias; testes SOCKS/Tor são estudos herdados isolados, não executam Tor nem integram a VPN. `adb devices` não apresentou dispositivos. Sem testes Android/instrumentação, conexão, TLS, rotas, DNS, privacidade ou teste em aparelho. Avisos de bibliotecas nativas não stripped e memória compartilhada foram observados. O SDK manager do Gradle instalou automaticamente Android SDK Platform 33 fora da cópia durante a primeira tentativa; esse diretório recém-instalado foi removido para restaurar o ambiente.

## Trabalho restante

1. Obter fonte e scripts correspondentes aos binários e auditar licenças/versões; preferir build reproduzível do motor e revisão das dependências.
2. Definir autenticação independente apropriada para os relays e compatibilidade do VPN Gate sem enfraquecer TLS, inclusive validade, identificação e algoritmos. Estabelecer qual confiança o catálogo HTTPS pode ou não oferecer, e como lidar com relays voluntários.
3. Criar parser limitado e sanitização com testes de escape, blocos, duplicatas, caminhos, scripts, plugins, proxy e diretivas de rede; associar o perfil correto ao IP selecionado sem aceitar configuração arbitrária.
4. Ligar consentimento `VpnService.prepare`, seleção, estados e serviço foreground às telas; revisar API 35, permissões, proteção de socket, rotas, DNS, reconexão, revogação e desligamento. Só indicar conectado após confirmação autêntica do motor.
5. Testar servidor controlado e aparelhos/emuladores, CA/nome errado, MITM, rede, IPv4/IPv6/DNS, retomada de processo, revogação e vazamentos; auditoria independente antes de oferecer como VPN.
