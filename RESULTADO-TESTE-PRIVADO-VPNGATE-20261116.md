# VPN Gate — APK experimental para teste privado (16/11/2026)

## Estado
Nesta retomada encontrei as alterações de desbloqueio já presentes na cópia, sem metadados Git para determinar um diff completo. A build debug permite tentar VPN Gate em Android 8+ após escolha de perfil compatível, aviso explícito e consentimento do Android. Release não habilita a política experimental. O serviço experimental está habilitado em debug. Não foi necessário enfraquecer TLS nem simular conexão. A fixture separada permanece desabilitada. O APK é de teste privado, não produto ou versão de distribuição.

O perfil deve passar pela política restritiva, que confere a CA ISRG Root X1, nome esperado `opengw.net`, papel de servidor, endpoint e demais campos. A configuração exige `verify-x509-name` e `remote-cert-tls server`. Essa CA pública e nome compartilhado NÃO provam identidade de um voluntário específico nem associação independente do nome com VPN Gate. AES-128-CBC/SHA1 são parâmetros legados. Perfis incompatíveis continuam bloqueados: sua aceitação não deve ocorrer reduzindo a verificação TLS.

Status `Conectado (estado do motor)` só reflete evento real do motor e não comprova tráfego, DNS ou ausência de vazamentos. Não houve dispositivo/emulador no `adb devices -l` e não foi feita tentativa real de conexão nesta retomada. Não estão comprovados operação do serviço Android, consentimento, handshake TLS, tráfego, DNS, IPv6, kill switch, reconexão, encerramento e resiliência do ciclo de vida. VPN Gate e operadores voluntários podem registrar metadados. Não usar para dados sensíveis ou depender de anonimato/proteção. Logs, arquivos temporários de chaves, binários, proveniência e licenças ainda requerem auditoria.

## Artefato e verificação
- `app/build/outputs/apk/debug/app-debug.apk`: 43.557.487 bytes, SHA-256 `776e76e97e4a269abe596aea460faaa2b10f705ca2677b1db701033f2ed0683d`.
- Pacote `br.com.vegas.independente.experimental`, versão `0.1.0-experimental`; instalação separada; arquiteturas arm64-v8a, armeabi-v7a, x86, x86_64. Manifesto minSdk 23, mas o teste VPN Gate exige Android 8/API 26+.
- Assinatura debug verificada v1 e v2. Apksigner também exibiu avisos sobre entradas META-INF não protegidas por JAR/v1; não confundir isso com uma auditoria de assinatura ou de segurança.
- Gradle 8.10.2 offline, JDK 17, SDK 35: `:app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL (62 tarefas up-to-date). O APK e as alterações já existiam quando a retomada começou. Reexecução explícita de `:app:testDebugUnitTest --rerun`: BUILD SUCCESSFUL, 87 testes JVM em 18 suítes, 0 falhas, 0 erros e 0 ignorados. Testes JVM não testam serviço nem rede Android reais.
- `tests/run_strict_profile_tests.py`: 3 programas passaram; PKI efêmera de teste. `tests/run_java_tests.py`: 6 programas passaram; testes Tor são modelos/proxy de loopback falso, não Tor real. `verify_experimental_gate.py`, `verify_project.py`, `verify_isolation.py`: passaram verificações estáticas, não testes de dispositivo/TLS. O texto legado de verify_project dizendo "disabled connection" é impreciso para o novo fluxo debug; aquela verificação isolada não prova o estado do botão.
- Sem aparelho ou emulador. `/tmp` segue cheio; usado diretório temporário próprio dentro da cópia. JVM emitiu avisos de memória compartilhada; a build/testes terminaram. Disco principal com cerca de 308 MiB livres.

Não foram tocados outros projetos ou GitHub nesta retomada. Como a cópia não é repositório Git, não há diff Git disponível para comprovar a autoria ou histórico completo das alterações anteriores à retomada.
