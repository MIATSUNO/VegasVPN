# Vegas + Arti: pesquisa isolada, ainda sem integração

## Resultado e segurança

Nesta cópia consolidada, NÃO há motor Arti, VPN ou conexão Tor. O app tem INTERNET exclusivamente para consulta manual ao catálogo após consentimento; não possui VpnService. Conectar e Tor permanecem desativados, e o widget informa “VPN indisponível”. A internet normal não é bloqueada; não é kill switch. O histórico de pesquisa abaixo refere-se à variante isolada original.

Em `tests/ExperimentalTorSocks.java` há código de pesquisa isolado fora do source set do app: estabelece socket apenas para 127.0.0.1, solicita a um servidor SOCKS5 a conexão TCP para o domínio fixo `check.torproject.org:443` usando ATYP domínio (sem consulta DNS local ao destino) e propaga falhas sem conectar diretamente ao destino. Não há HTTP, TLS, checagem do certificado, nem código do app que o execute. `tests/ExperimentalTorSocksTest.java` verifica o protocolo com um servidor falso local, inclusive falhas. Não é um teste de Tor nem prova ausência de vazamentos em Android.

**Não ativar nem conectar esse protótipo a uma porta real:** uma porta SOCKS local não identifica seu dono; outra aplicação pode ocupá-la antes do Arti ou se passar por ele. A API oficial fornece portas fixas/configuráveis e `start()` não comprova bootstrap nem sucesso de bind. Sem mecanismo de autenticação/identificação e coordenação comprovados, tráfego para o SOCKS não deve ser rotulado Tor. Sem TLS validado nem resposta confiável, a tentativa TCP não demonstra saída Tor. Isolamento de testes, não incorporação, foi a alternativa conservadora solicitada.

## Pesquisa de upstream e licenças

- README oficial do Guardian Project descreve Arti Mobile como biblioteca oficial, apoiada pelo Tor Project e experimental: https://gitlab.com/guardianproject/tormobile/arti-mobile . Dependência Android anunciada `org.torproject:arti-mobile:1.7.0.1`; não adicionada aqui.
- Sources.jar do artefato publicado, obtido do commit fixo https://github.com/guardianproject/gpmaven/tree/f3758020a7a879bf82bc13923ac1941505b33a48/org/torproject/arti-mobile/1.7.0.1 , SHA-256 `891a279a6ca23de3b56deda41cefaabc61788b89b5e85f3e7a93649c03a0ca90`. Java publicado confirma `ArtiProxy.Builder(context).build()`, `start()`, `stop()`, `getSocksPort()`, portas padrão 9150/9151 e não ser thread-safe. `start()` ignora retorno do JNI. Builder tem `setSocksPort`, `setDnsPort`, `setCacheDir`, `setStateDir` e `setWrapWebView`. `setWrapWebView(true)` usa `addDirect()`; não usar em um modo que pretenda bloquear falhas. Nenhum código de app Tor foi copiado.
- POM dessa versão não declara licenças; enumera Kotlin stdlib 2.1.10, AndroidX appcompat 1.6.0 e webkit 1.6.0: https://raw.githubusercontent.com/guardianproject/gpmaven/f3758020a7a879bf82bc13923ac1941505b33a48/org/torproject/arti-mobile/1.7.0.1/arti-mobile-1.7.0.1.pom . Arquivo `android/arti/build.gradle` da árvore atual possui SPDX MIT, mas isso não prova licença integral de wrapper, binário ou transitivas; precisa esclarecer com upstream, identificar commit-fonte correspondente ao AAR, gerar SBOM com avisos e revisar cada componente antes de distribuir. https://gitlab.com/guardianproject/tormobile/arti-mobile/-/blob/main/android/arti/build.gradle . A pesquisa original complementar não integra este pacote; seus detalhes devem ser verificados novamente antes de integração.
- Hash de metadados do mesmo repositório não autentica origem independentemente. O AAR não foi incluído nesta cópia. Antes de confiar, autenticar proveniência, conferir checksum, ABIs, compatibilidade 16 KB e dependências transitivas; compilar e executar no Android.

## Próximas etapas

1. Resolver identidade e exclusividade do endpoint SOCKS e erros/concorrência de start/stop da biblioteca; verificar API e artefato precisamente correspondentes. Integrar Arti real apenas em modo experimental explicitamente restrito ao app, com timeout, sem fallback nem resolução DNS local, TLS com verificação de hostname/certificado e checagem independente de saída Tor. Nenhum estado deve anunciar conectado por retorno de `start()` ou porta aberta. Testar colisões, substituição maliciosa de listener, bootstrap e falhas sob captura de tráfego em dispositivos reais.
2. VPN do aparelho é um projeto separado: requer `VpnService`, consentimento, interface TUN, rotas IPv4/IPv6, mecanismo TUN→TCP→SOCKS e DNS, tratamento/bloqueio explícito de UDP e QUIC, e prevenção de recirculação com proteção dos sockets do motor ou arquitetura rigorosamente verificada. A API pública examinada de ArtiProxy não expõe callback `VpnService.protect()`; não foi demonstrada uma alternativa segura. Não ativar VPN até resolver e testar tudo. Android: https://developer.android.com/develop/connectivity/vpn ; Tor não transporta IP/UDP arbitrário: https://support.torproject.org/about-tor/alt-design/transport-all-ip-packets/ .
3. Testar morte do processo, revogação, perda/troca de rede, always-on/lockdown, falha da ponte, DNS, IPv4/IPv6, UDP e outros apps, em dispositivos e com observadores independentes. Proteção não é garantida quando o sistema revoga VPN. Revisar licenças do ícone escolhido, cuja titularidade original não foi confirmada.

## Testes reproduzíveis, fora do Android

Use JDK 17 e diretório temporário com espaço. Na raiz desta cópia:

```sh
python3 tests/verify_project.py
python3 tests/verify_isolation.py
mkdir -p /caminho/temporario/classes
javac -d /caminho/temporario/classes tests/ExperimentalTorSocks.java tests/ExperimentalTorSocksTest.java
java -cp /caminho/temporario/classes br.com.vegas.independente.ExperimentalTorSocksTest
```

Os testes locais verificaram SOCKS falso: greeting, domínio remoto, recusas, resposta truncada, timeout, proxy ausente e porta inválida. Não são auditoria de segurança e não analisam o manifesto mesclado. Sem dispositivo ADB conectado; armazenamento raiz com cerca de 58 MiB livres e `/tmp` cheio; outro Gradle estava em execução e não foi interrompido. Não foi feito build Gradle/APK ou tráfego Tor real.
