# Interface Vegas — VPN Gate experimental

## Alterações
Tela principal redesenhada em areia, branco, azul-petróleo e coral, com marca de conectividade, anel visual, status real em destaque, avisos preservados, consulta ao catálogo mediante consentimento e cartões por servidor. A referência escolhida na conversa é `generated/web_25055302_1.png`: o resultado inspira-se na composição e nas cores, mas não reproduz botões, navegação, históricos, dados ou funcionalidades inexistentes do mockup. O anel é decorativo, não um botão nem indicador de proteção.

Arquivos alterados: `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/styles.xml`, `app/src/main/java/br/com/vegas/independente/MainActivity.kt` (apresentação de cartões e botões, sem mudanças nas condições de elegibilidade ou ações). Novos: `app/src/main/res/drawable/connectivity_mark.xml`, `status_ring.xml`, `button_primary.xml`, `button_secondary.xml` e `app/src/main/res/color/button_text.xml`.

As chamadas ao catálogo, confirmação de consulta, triagem, consentimento VPN Android, fluxo experimental e estados existentes foram mantidos. O motor, autenticação, widget e integração Tor não foram modificados. Não há configurações fictícias. O widget continua apresentando estado da consulta ao catálogo, não uso de dados ou estado da VPN.

## Compilação e testes
Execução offline do Gradle com `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<projeto>/.worktmp-ui-20261116`, `TMPDIR` no mesmo local e compilador Kotlin in-process: `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks`. Resultado: BUILD SUCCESSFUL, 62 tarefas executadas; 18 suítes, 87 testes, 0 falhas, 0 erros, 0 ignorados. Relatório: `app/build/reports/tests/testDebugUnitTest/index.html`. Log: `build-ui-final-20261116.log`.

APK: `app/build/outputs/apk/debug/app-debug.apk`, 43.519.892 bytes; pacote `br.com.vegas.independente.experimental`, versão `0.1.0-experimental`, Android mínimo 23; SHA-256 `ed8d0a24c7692d0f1f71fc3553519c96cbb618ca56df54d6c1434f32cb48908d`. Assinatura debug verificada em v1 e v2. O verificador apresentou avisos para entradas META-INF sob v1; não é assinatura de publicação.

Não há aparelho/emulador conectado: layout, acessibilidade e conexão real não foram validados em Android. A VPN continua experimental: a conexão, identidade dos voluntários, ausência de vazamentos e proteção não estão comprovadas. A imagem de referência contém controles e informações ausentes no projeto, portanto não são replicados como se existissem.

Disco antes do build: raiz 99%, 323 MB livres; /tmp 100%. Primeira execução terminou com sucesso após falha do daemon Kotlin ao usar /tmp e fallback; foi repetida integralmente com compilador in-process e tmp local. Mesmo assim, a JVM alertou falta de espaço para arquivo de memória compartilhada. Aviso do Gradle: bibliotecas nativas não foram reduzidas (strip) e foram empacotadas como estão. Disco ao final: raiz 321 MB livres; /tmp continuava cheio. Nenhum outro projeto foi alterado ou publicado.
