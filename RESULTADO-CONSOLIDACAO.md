# Resultado da consolidação experimental

## O que foi reconciliado

Base do widget preservada: repositório, recuperação após morte do processo, estados, configuração por instância e consulta somente após confirmação. A Activity apresenta o resumo de inspeção acrescentado ao parser, sem substituir o repositório pelo ciclo de vida da variante de inspeção. Pesquisa Arti/SOCKS permanece exclusivamente em `tests/`, sem código ou dependência incluída no app. Conectar e Tor continuam desabilitados. A consulta HTTPS manual ao catálogo é a exceção de rede explicitamente sujeita a consentimento; não há conexão VPN/Tor.

## Verificações locais (15/11/2026)

- `python3 tests/verify_project.py`: passou; 11 XMLs, permissões e controles, declaração e acionamento do widget, ícones e verificações estáticas do repositório e consentimento.
- `python3 tests/verify_isolation.py`: passou; pesquisa excluída de `app/src`, nenhuma dependência Arti detectada, nenhum serviço no manifesto-fonte. São verificações estáticas, não do manifesto mesclado.
- `python3 tests/run_java_tests.py`: passou nas quatro classes: catálogo (CSV, limites e inspeção acoplada), 25 pares de estados, inspeção de perfil e SOCKS falso em loopback. Não faz consulta online ou conexão Tor. O CI foi configurado para executar os três comandos; o workflow não foi rodado.
- Compilação direta local: AAPT2 34.0.0 compilou e vinculou recursos contra Android 35; JDK 17 compilou Java com R gerado e Kotlin 2.0.21 compilou todos os cinco arquivos Kotlin contra Android 35 e classes Java. Para o link foi usada cópia temporária do manifesto com `package`; artefatos foram removidos. Isto não é build Gradle completo, não gera APK validado nem verifica manifesto mesclado ou execução no Android.
- Gradle e testes de aparelho não foram executados: o armazenamento raiz tinha aproximadamente 57 MiB disponíveis e `/tmp` não tinha espaço livre. As ferramentas Java emitiram avisos de espaço, mas todos os comandos indicados terminaram com sucesso.

Nenhuma consulta ao catálogo foi realizada durante esta consolidação, preservando consentimento e privacidade. A inspeção de perfis é indicativa e não prova segurança, autenticidade ou possibilidade de conexão. A política antiabuso do VPN Gate informa registros de acessos à lista, conexões por três meses ou mais e cabeçalhos em servidores voluntários por pelo menos duas semanas. Leia `README.md` e `ARTI-RESULTADO-E-PROXIMOS-PASSOS.md` para detalhes e limitações.

Não houve publicação. Não há VPN, Tor, bloqueio de tráfego ou proteção implementados. A CI editada não foi executada no GitHub. É necessário testar Gradle, manifesto mesclado, aparelhos, launcher, consentimento, acessibilidade e segurança antes de qualquer distribuição; direitos do ícone estão pendentes.
