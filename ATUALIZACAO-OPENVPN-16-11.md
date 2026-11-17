# Atualização OpenVPN experimental — 16/11/2026

Este documento atualiza o relatório RESULTADO-OPENVPN-PARCIAL.md, que foi escrito antes da segunda rodada e está desatualizado em relação à interface, consentimento e serviço.

## Estado
Motor OpenVPN de terceiro foi incorporado e ligado, somente na variante debug, a uma fixture local fixa de teste; implementação inclui chamada VpnService.prepare, estados baseados em callbacks do motor e desconexão. A fixture requer provisionamento manual de certificados e servidor controlado. Nenhum aparelho foi detectado e não houve handshake nem teste do túnel. Não afirmar que funciona ou protege o tráfego. A seleção e a conexão dos perfis VPN Gate continuam bloqueadas: o validador mayConnect() sempre retorna false. Tor não está integrado. Não distribuir o APK.

## Verificação
Em revisão independente desta versão, assembleDebug terminou BUILD SUCCESSFUL em 37s; testDebugUnitTest foi NO-SOURCE. Verificadores estáticos e seis programas Java offline passaram; FixturePolicyTest com certificados de teste recém-gerados passou. Manifesto mesclado debug inclui OpenVPNService protegido por BIND_VPN_SERVICE. Não houve testes Android, TLS, rotas, DNS, IPv6, reconexão ou vazamentos. O APK debug recompilado tem SHA-256 3981b9060a97962a373fea77a868b5e2d4aa80f6103857deb794b2bd45392a55. Integração da fixture não comprova uma conexão operacional.

## Limitações relevantes
- Não foram confirmadas fontes, versões e obrigações de licença de todos os binários nativos pré-compilados; revisar antes de distribuir.
- A configuração pode conter chave inline escrita em cache/android.conf. Há tentativa de remoção, mas limpeza não é garantida em interrupção ou erro; revisar antes de usar credenciais reais.
- Os testes estáticos herdados não demonstram que os controles permaneçam desativados em tempo de execução e não substituem inspeção do manifesto mesclado.
- Verificação TLS do relay precisa de política clara: material distribuído via HTTPS pode estabelecer confiança no catálogo, mas uma CA ampla não identifica um relay específico. Não ignorar erros TLS. Falta parser seguro compatível com os perfis reais e testes em dispositivo.
