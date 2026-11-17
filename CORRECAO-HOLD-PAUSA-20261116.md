# Vegas — correção de hold e pausa — 16/11/2026

## Estado inspecionado antes de editar
Esta cópia não é repositório Git; não há diff Git de referência. `.worktmp-rodada-pause` estava vazia e não foram identificadas alterações de uma tentativa posterior na árvore fonte: o arquivo mais recente de gestão era de 17:51, anterior ao relatório anterior das 17:54. O relatório anterior indicava que `networkChange` condicionava apenas `vegasPauseEvents.resume()` a `shouldBeRunning()`, mas o hold ainda era liberado independentemente; inspeção do código confirmou isso. Ausência de Git impede provar que nenhuma edição não registrada ocorreu anteriormente.

## Alterações
- `DeviceStateReceiver.java` e novo `PauseRunPolicy.java`: as três condições (rede, tela e usuário) e a ação de liberação agora compartilham um monitor. Timeout condicional não substitui retorno de rede/tela que já ocorreu. A condição de tela desligada é atualizada atomicamente. Continua o atraso de rede existente e a dependência da preferência `netchangereconnect`.
- `OpenVPNManagement.java`: `PausedStateCallback.runIfAllowed` permite executar a ação sob o mesmo monitor que protege as mudanças do estado. Implementações que não adaptam o método recusam a execução por padrão; mudança deliberada fail-closed, podendo impedir retomada em integrações externas não adaptadas.
- `OpenVpnManagementThread.java` e novo `HoldReleaseController.java`: qualquer `hold release` e `network-change` percorre essa autorização; callback ausente é recusado. Troca de callback é serializada com a autorização. Os caminhos de hold normal, retorno de rede, `resume`, `reconnect` e timer chamam o mesmo gate. O hold só é marcado liberado após sucesso da escrita; uma falha mantém a espera, sem garantia de retry automático. Duplicatas são serializadas. O antigo sleep de 3 s sob chamada concorrente foi substituído por retry agendado com intervalo mínimo de 5 s entre liberações, usando relógio monotônico; o timer revalida a permissão. O tempo mudou em relação à implementação anterior e é preciso validar em Android.
- `app/src/test/java/de/blinkt/openvpn/core/HoldReleaseControllerTest.java`: oito testes JVM direcionados dos controladores reais, com comandos/relógio simulados.

## Evidências
Gradle 8.10.2 offline, JDK 17, SDK 35 e `TMPDIR`/`java.io.tmpdir` em `.worktmp-rodada-pause`: `:app:assembleDebug :app:testDebugUnitTest` terminou **BUILD SUCCESSFUL in 32s**, 62 tarefas, 6 executadas e 56 up-to-date; log `build-correcao-pause-final-20261116.log`. Resultados XML: **55 testes, 0 falhas, 0 erros, 0 ignorados**; oito novos incluem pausas por usuário e tela, retorno de mesma/outra rede, reconexão sem hold, ausência de rede, timer após pausa, retomada autorizada, falha de escrita, callback incompatível, timeout atrasado e concorrência com latches entre mudança de permissão e escrita simulada. As suítes de regressão `tests/run_strict_profile_tests.py` (3 programas), `tests/run_java_tests.py` (6 programas), `tests/verify_project.py` e `tests/verify_isolation.py` passaram. Os scripts criaram e removeram exclusivamente diretórios temporários de teste próprios; nenhum dado de usuário foi removido. O shell da primeira invocação registrou erro posterior ao comando Gradle por usar o nome reservado `status` no zsh; o log confirma que o Gradle teve sucesso. A invocação final voltou código 0. JVM registrou aviso de shared memory, apesar do temp próprio. `/tmp` 100% e disco principal ~343 MiB livres no final.

APK de debug interno SHA-256 `4d27a82a520754c5b3833302f0a16e9bae59d7c05aabe18d86186ba08ffd843f`; não distribuir. `ENGINE_LIFECYCLE_VERIFIED=false` permanece e a fixture continua dependente dessa flag. Sem mudanças em TLS, Tor ou GitHub; Gate e fixture não foram habilitadas.

## Limites importantes
Testes executam auxiliares reais com comandos simulados, não DeviceStateReceiver/Management/Handler/socket/Intents ou OpenVPN Android. Nenhum teste Android foi realizado; nenhum tráfego, TUN, TLS, vazamento ou ciclo nativo é comprovado. A escrita do socket é feita sob o lock de permissão para evitar a corrida check-then-act: se bloquear, pode atrasar pausa e a thread chamadora; o lock também envolve os comandos adicionais de bytecount/state. Revisão de bloqueios e testes em dispositivo ainda necessários antes de qualquer desbloqueio. Outras transições e publicações de eventos de pausa/resume/estado não foram totalmente serializadas: um callback de pausa ou resume atrasado pode produzir estado visual obsoleto; `signalusr1`, controle de callbacks/timers, estado CONNECTED antigo, timers de reconexão e permissões permanecem sujeitos aos limites documentados antes. A perda de rede com `netchangereconnect=false` segue sem atualizar estado de rede como anteriormente. Mesmo após uma escrita bem-sucedida, não há confirmação de recebimento do motor. Revisão independente e teste Android controlado são indispensáveis.

SHA-256 das alterações:
```
6d7503e7c9de2e52cb23f547a6882a9b0648a1390ee3ae8591e0b9f95353d821  DeviceStateReceiver.java
585cd52d47d5f93a553bf00d9fb28d8e49d253546b15415f509ecb95d1a9be16  OpenVPNManagement.java
9c588aa36fbe10ca23cb82577681f5f0cf6f15a098681dacb179ec80d473314e  OpenVpnManagementThread.java
3fde1ca1ec117959493619a69627b8fa846f981f272ea3aa9a56f97b2cd5d3c7  PauseRunPolicy.java
86d5e33ea81d9a113ad1ef67ece6b66eb5a0419c7d78bae1c0bf0800938b02d7  HoldReleaseController.java
b9ad5c2cb9b61c33e858fe0ddf746c56befb7a1bb7bb0805e9044a373d1e813b  HoldReleaseControllerTest.java
```
