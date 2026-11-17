# Trabalho isolado OpenVPN — 16/11/2026

## Resultado e limites

**Não há conexão VPN implementada.** O aplicativo não contém motor OpenVPN, serviço Android `VpnService`, túnel, autenticação TLS de túnel, kill switch ou proteção de tráfego. O botão Conectar permanece desativado. O APK debug é apenas um catálogo; não o distribua como VPN. Não houve publicação, teste em aparelho, conexão VPN ou consulta ao diretório VPN Gate nesta tarefa. A cópia vem de `vegas-vpngate-revisao-isolada-20261116`, que acrescentou correções de blocos ao `ProfileInspection` e um validador auxiliar sobre `vegas-consolidada`.

## Progresso verificável

- `VpnGateCatalog.java`: aplica em memória o validador restritivo antes descartado aos até 100 perfis das linhas apresentadas, comparando o endereço do perfil ao IP da própria linha; conserva apenas enumerações imutáveis de achados, não o perfil. Não habilita execução nem retém uma associação de perfil para futura conexão. O catálogo, necessariamente, contém IPs públicos dos servidores e dados do diretório em memória; nada disso garante apagamento de memória.
- `MainActivity.kt` e `strings.xml`: exibem triagem com achados ou sem achados, sempre com bloqueio; ausência de achados **não** comprova segurança, autenticidade ou compatibilidade com OpenVPN. Estado do catálogo não é estado de VPN.
- `VpnGateProfileValidator.java`: evita `String.chars()`/streams, pois minSdk é 23; faltam testes reais nessa versão do Android. O bloqueio `mayConnect() == false` continua incondicional. Continua sendo um analisador restrito, não parser OpenVPN, não verifica hostname de certificado, confiança, expiração ou criptografia de túnel. Ele deliberadamente rejeita várias opções potencialmente legítimas; a triagem é indicativa e não mede compatibilidade real do VPN Gate.
- `tests/CatalogTest.java`, `tests/VpnGateProfileValidatorTest.java`, `tests/verify_project.py`: testes do novo encadeamento, da imutabilidade, mismatch do IP, diretiva perigosa, CA e bloqueio mesmo com sintaxe aceita. `tests/fixtures/triage-test-ca.pem` é certificado autoassinado de teste, público e **não confiável**; sua chave privada temporária foi descartada. Não é CA confiável nem informação de usuário.

## Por que ainda não está pronta

OpenVPN 3 Core é uma biblioteca C++ cliente; a aplicação precisa compilar e integrar uma ponte Android/JNI, implementações dos callbacks de TUN e proteção de socket, integração de eventos, segurança e empacotamento por ABI. O ambiente encontrado não tem NDK Android, CMake ou SWIG instalados e não há motor OpenVPN pronto nesta base. Incluir um wrapper não testado ou um serviço que se anuncia sem túnel não seria uma integração real. Essas são barreiras de engenharia remediáveis, não prova de impossibilidade do produto.

Além disso, a CA enviada no mesmo perfil não demonstra, por si, que o endpoint é o relay esperado: falta uma âncora e associação de identidade de cada servidor obtidas por canal independente ou uma política de confiança documentada e validada. `remote-cert-tls server` confere tipo/capacidade do certificado, não a identidade específica de um relay. TLS do diretório não estabelece sozinho isso. Pesquisa de 2021 sobre **SSL-VPN**, não especificamente OpenVPN, relata riscos de certificados compartilhados e falta de validação em uma configuração então estudada; não generalizar como prova do estado atual de VPN Gate/OpenVPN. Mesmo com identidade criptográfica correta, voluntários podem registrar e observar tráfego no relay; HTTPS fim a fim continua necessário. Não substituir validação por aceite cego ou ignorar erros para obter conexão.

## Motor pesquisado e licenças — ainda não incluídos

- Candidato: [OpenVPN 3 Core](https://github.com/OpenVPN/openvpn3), biblioteca C++ cliente; commit de referência consultado `0c1ce172fe918b05463d71e682a0c2f2d8fe7899`. [Licença desse commit](https://github.com/OpenVPN/openvpn3/blob/0c1ce172fe918b05463d71e682a0c2f2d8fe7899/LICENSE.md): MPL-2.0 ou AGPL-3.0, com exceção OpenSSL indicada. Auditar licenças e versões das dependências nativas, avisos e obrigações do caminho escolhido antes da distribuição; analisar os arquivos efetivamente incorporados.
- Alternativa [ics-openvpn / OpenVPN for Android](https://github.com/schwabe/ics-openvpn): segundo seu [LICENSE](https://github.com/schwabe/ics-openvpn/blob/master/doc/LICENSE.txt), GPL versão 2 ou posterior, com exceções específicas; código de exemplo de controle remoto tem Apache 2.0. O upstream diz não ser uma biblioteca destinada a inclusão, e utilização da hierarquia é descrita como obra derivada; revisar consequências antes de escolher. Nada dele foi copiado ou integrado.
- Este documento atribui a pesquisa; não afirma que motores estejam distribuídos neste APK. Direitos do ícone herdado permanecem desconhecidos.

## Privacidade e logs

A consulta ao catálogo exige confirmação, HTTPS e não persiste perfil intencionalmente. Persistem preferências de estado do catálogo e widget; não há log deliberado de perfis, certificados, IP do usuário, destinos ou dados do túnel; não há garantias de zero logs de Android, rede ou terceiros. [Política antiabuso do VPN Gate](https://www.vpngate.net/en/about_abuse.aspx) diz registrar acessos à lista e reter logs de conexão por três meses ou mais, inclusive IP de origem e metadados de destinos; documentos da variante anterior indicam cabeçalhos por pelo menos duas semanas em servidores voluntários. Confirme política na implantação. Não prometer anonimato nem no-logs. Fontes: [Android VPN](https://developer.android.com/develop/connectivity/vpn), [OpenVPN verificação do servidor](https://openvpn.net/community-docs/important-note-on-possible--man-in-the-middle--attack-if-clients-do-not-verify-the-certificate-of-the-server-they-are-connecting-to/), [artigo de 2021](https://doi.org/10.1155/2021/9091675).

## Build e testes observados

Offline, JDK 17, Gradle 8.10.2, Android SDK 35 e build-tools 34.0.0; cache das dependências copiado de ambiente existente para diretório isolado. `:app:assembleDebug` com um worker e compilação Kotlin in-process terminou `BUILD SUCCESSFUL in 2m 29s`, 34 tarefas executadas. `app/build/outputs/apk/debug/app-debug.apk`: SHA-256 `b76859cbb2011914d689085c083ae58b34fd7820e52c734049554985df8ca4ee`, 934 KiB, assinatura debug verificada v1 e v2. `aapt`: applicationId `br.com.vegas.independente`, minSdk 23, target 35, apenas INTERNET, sem serviço no manifesto mesclado. Debug assinado por chave de desenvolvimento do ambiente, não de produção; builds em outros ambientes podem gerar bytes/assinatura diferentes. O applicationId colide com variantes assinadas compativelmente.

`python3 tests/verify_project.py`, `tests/verify_isolation.py`, `tests/run_java_tests.py` passaram: 11 XMLs, controles desativados, cinco programas Java offline, 2.000 entradas pseudoaleatórias e teste de proxy falso local herdado. Não houve dispositivo em `adb devices`, nem teste Android, túnel, TLS, vazamentos, lifecycle ou conexão. Avisos JVM sobre memória compartilhada apareceram sem impedir build/testes. Tentativa inicial com cache isolado grande foi interrompida para evitar esgotar o disco; a segunda concluiu. Cache temporário isolado pode ser apagado após build para recuperar espaço.

Para reproduzir (dependências de plugin disponíveis online ou no cache; nenhum wrapper incluído):

```sh
export JAVA_HOME=/caminho/jdk-17
export ANDROID_HOME=/caminho/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
python3 tests/verify_project.py
python3 tests/verify_isolation.py
python3 tests/run_java_tests.py
gradle --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx512m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :app:assembleDebug
```

Versões Gradle e SDK como acima; reprodutibilidade funcional depende de obter dependências e ferramentas, não é bit-a-bit garantida. Não adicione `--offline` a menos que todos os artefatos estejam no cache.

## Próximos passos concretos

1. Definir política de confiança aplicável ao catálogo e identidade de cada relay, verificar por fontes independentes o que o VPN Gate efetivamente disponibiliza atualmente e estabelecer comportamento fail-closed; se essa identidade não for verificável, permanecer bloqueado. Planejar ameaça de relay malicioso, não só MITM.
2. Fixar commit/NDK/CMake/SWIG, revisar segurança e licenças transitivas, produzir bibliotecas por ABI e build verificável; criar bridge JNI e callbacks OpenVPN3, sem truncar parâmetros de TLS nem ativar legado inseguro para compatibilidade.
3. Projetar seleção e ciclo do perfil sem persistir segredos; converter por parser do motor e política segura, sem aceitar scripts, plugins, caminhos arbitrários ou chaves fornecidas como confiança automática.
4. Implementar `VpnService.prepare`, consentimento explícito, serviço protegido por `BIND_VPN_SERVICE`, foreground e notificações exigidos por API, `protect()` do socket, roteamento/DNS IPv4 e IPv6, desligamento, revogação e morte de processo; somente indicar conectado após eventos reais, interface e handshake autenticado, com tratamento de erros/reconexão e bloqueio de vazamentos.
5. Testar em emuladores e aparelhos suportados: CA e nome errados, MITM, endpoint divergente, revogação, sem rede, troca de rede, Always-on/lockdown, IPv4/IPv6, DNS, morte/retomada do processo, desconexão e vazamentos, além de servidor OpenVPN controlado e relays que satisfaçam a política. Fazer auditoria independente antes de disponibilizar.
