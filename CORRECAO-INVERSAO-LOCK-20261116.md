# Vegas — inversão específica de locks, 16/11/2026

## Escopo e situação
Alterações na MESMA cópia isolada `output/vegas-openvpn-integrado-20261116`. Não houve acesso/publicação GitHub, alterações Tor ou de outros projetos. `GateVpn.ENGINE_LIFECYCLE_VERIFIED=false` permanece, e a verificação dentro de `start()` também permanece. Fixture continua DESABILITADA; seu stop legado não envia ID e é rejeitado pelo serviço. Não reativá-la antes de migrar e validar o ciclo por sessão. VPN Gate não está liberada ou validada; não distribuir o APK debug.

## Inversão tratada
`VpnStatus` chama listeners sob o monitor global da classe. A notificação construída no callback adquiria `mProcessLock` ao ler UUID/nonce, enquanto `requestSessionStopLocked()` podia chamar `management.stopVPN()` sob `mProcessLock` e alcançar `VpnStatus`: havia ordem inversa. Esta alteração remove a aquisição específica de `mProcessLock` em `addVpnActionsToNotification`, sem modificar o mecanismo global de callbacks, a serialização do serviço ou antecipar cleanup.

`NotificationSession` guarda uma única referência `volatile` a um par imutável (UUID da sessão, nonce). O serviço publica o par sob `mProcessLock` após criação do nonce, limpa-o no início de `endVpnService` sob o mesmo lock, e a notificação lê um snapshot sem adquirir aquele lock. Ambos os campos pertencem à mesma publicação; ainda pode haver snapshot desatualizado, inclusive após cleanup. A ação carrega UUID e nonce do snapshot; o handler continua conferindo UUID e, quando presente, nonce sob `mProcessLock`. A ação nova inclui nonce na URI do PendingIntent. Não há ação caso não exista snapshot. Um snapshot antigo não é garantia de que a notificação visual já desapareceu.

**Limite importante de compatibilidade:** o handler aceita UUID correto sem nonce para `GateVpn`. Uma ação legada sem nonce com UUID que coincida com o atual pode parar a sessão (inclusive em caso de reutilização); portanto a solução de identidade não é universal. UUID e nonce não substituem revisão de autorização/intents/serviço exportado. Não houve teste de PendingIntent ou de reprodução de UUID no Android.

## Verificação exata
- JDK 17 e Gradle 8.10.2 offline com SDK 35 e `TMPDIR`/`java.io.tmpdir` na `.worktmp` da cópia: `:app:assembleDebug :app:testDebugUnitTest` — **BUILD SUCCESSFUL in 37s; 62 tarefas, 9 executadas, 53 up-to-date**. Log `build-lock-review-20261116.log`.
- Resultados XML JVM: **22 testes, 0 falhas, 0 erros, 0 ignorados**. GatePolicy 2, GateSession 6, NotificationSession 3, ProcessExitWaiter 4, ProcessStartGate 6, SessionVpnStatus 1.
- Três novos testes JVM exercitam o código real `NotificationSession`: publicação/limpeza e snapshot antigo imutável; concorrência entre par publicado e leitor, sem observar par misturado; modelo controlado com dois monitores/latches no qual um stop segura o lock de processo e aguarda o lock de status, enquanto callback segura status e lê snapshot sem esperar o lock de processo. **Esse último é um modelo de concorrência, não execução de `OpenVPNService`, `VpnStatus`, management, notificação ou Android reais.** O teste de publicação concorrente é amostral, não prova exaustiva de todas as intercalações.
- `tests/run_strict_profile_tests.py`: três programas passaram (allowlist e credenciais da fixture de teste, parser de perfil/casos negativos e política de root/nome/release/consentimento). Chaves de PKI temporárias de teste, não compartilhar. `tests/verify_project.py` e `tests/verify_isolation.py` passaram suas verificações estáticas, sem provar comportamento Android ou manifest mesclado.
- `adb devices -l`: lista vazia. Sem aparelho/emulador, teste real de serviço/notificação, OpenVPN nativo, handshake, tráfego, DNS, IPv6 ou vazamentos.
- APK debug interno `app/build/outputs/apk/debug/app-debug.apk`: 43.935.226 bytes, SHA-256 `003c09dd3326b8511fb1a0a327e53e5f63bcc562824504d2d41158f39649eed9`; compilação não prova funcionalidade nem segurança.
- Disco principal 348 MiB disponíveis e `/tmp` 100%; JVM ainda registrou aviso de memória compartilhada, apesar do temp próprio. Nenhuma limpeza de projetos de usuário foi feita.

## Riscos e próximos passos
Isto remove apenas o arco identificado `VpnStatus -> notificação -> mProcessLock`; não prova ausência de outros ciclos, inclusive callbacks de terceiros, listeners de status/log/bytecount, locks do receiver e chamadas para o Android. `management.stopVPN` e `stopProcess`/`destroy` continuam chamados enquanto se segura `mProcessLock` e podem bloquear por tempo indeterminado; não se comprovou responsividade, nem tratamento completo de exceções/cleanup ou sincronização de todos os campos visuais. Testar realisticamente em Android concorrência, notificação, ações atrasadas, UUID reutilizado, recriação, morte de processo, stop/revoke e bloqueios; refatorar captura/stop/cleanup de modo que sessão antiga não prejudique sessão nova. Depois, validar TUN, sockets protegidos, tráfego, DNS/IPv6, vazamentos e TLS com fixture controlada. Não ligar Gate nem fixture antes de validação.

## SHA-256 da alteração
```
8d58b214a94c2e593fe93e07da152ada56393088bd0b9c8a13769b54edc43fc2  openvpn-engine/src/main/java/de/blinkt/openvpn/core/OpenVPNService.java
e1a1e0018d95f16ec62b6c784b6c18028c9fa3d8567980e9c924a2e6a6fad67d  openvpn-engine/src/main/java/de/blinkt/openvpn/core/NotificationSession.java
3f83933b74901f0cd3777bebe367bc370793a28f660a5098b1d7c37798f222f6  app/src/test/java/de/blinkt/openvpn/core/NotificationSessionTest.java
```
