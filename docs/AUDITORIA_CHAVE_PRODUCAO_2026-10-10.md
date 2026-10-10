# Auditoria da identidade de assinatura — Brother Matrizes
Data: 10/10/2026. Revisão sem leitura ou criação de chaves privadas.

## Evidências examinadas
- Repositório GitHub: `timachado/Brother-Matrizes`; histórico completo das 1.876 execuções GitHub Actions consultadas em páginas de 100.
- Todas as execuções encontradas do workflow `Build Signed Brother Matrizes Production APK` terminaram com `failure` (nenhuma `success` identificada). A execução recente `38073302039` falhou no passo `Validate production signing secrets`, por ausência de `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64`.
- A única GitHub Release listada (`v0.46.9`) contém `FioLab-0.46.9-debug.apk` e ZIP de debug, **não** um APK de produção.
- O CI Android 0.50.7-rc4 de QA `38071725174` concluiu com sucesso, mas seu certificado está registrado nos logs como `CN=Android Debug`, SHA-256 `b1e2d306386071aec40fc3ede232da2a2f6585bf9a0d0737110e4e193d46b198`.
- O workflow de produção contém o SHA-256 esperado `663d4338f085730a87efbf7a2bd3b0ccb91d7dc3696e625afa9c35a0e47c0403`, inserido no código na migração de identidade de 29/09/2026 (commit `14338557d48c002ba4d643a7b40256a581d4b3a5`). **O histórico GitHub disponível NÃO comprova que exista uma keystore de produção correspondente**. Não tratar esse hash como identidade comprovada de distribuição sem uma fonte original verificável.

## Conclusão
Não existe prova, **dentro do histórico GitHub examinado**, de que uma chave definitiva do Brother Matrizes tenha sido usada para uma distribuição de produção. Isso **não exclui** a possibilidade de APK assinado por fora do GitHub, instalado ou entregue anteriormente.

## Roteiro seguro para a primeira assinatura de produção
1. Confirmar se houve qualquer APK **release** anteriormente distribuído fora do GitHub com `applicationId=com.timachado.brothermatrizes`. Se houve, recuperar a assinatura desse APK e preservar a identidade.
2. Se nunca houve assinatura de produção anterior, criar **uma única keystore definitiva** em computador pessoal confiável, fora do repositório, usando `scripts/create_production_keystore.ps1` (Windows) ou `.sh` (Linux/macOS). Não colocar credenciais no chat.
3. Validar a chave localmente via `keytool -list -v` e registrar o SHA-256 público do certificado. O hash estático histórico **precisa ser deliberadamente reconciliado com o novo SHA**, sob aprovação do titular. Nunca eliminar silenciosamente a verificação.
4. Criar duas cópias protegidas da keystore em locais separados e guardar senhas num gerenciador seguro.
5. Cadastrar os quatro GitHub Actions secrets especificados em `docs/PRODUCTION_SIGNING.md`, diretamente no GitHub, sem publicar a chave ou os arquivos Base64.
6. Executar manualmente o workflow apenas na branch de preparação. Conferir `apksigner verify`, SHA-256 do certificado e SHA-256 do APK e não publicar até realizar os testes em aparelhos.
7. APK debug pré-existente e APK de release com assinatura nova não podem ser atualizados diretamente um sobre o outro. **Exportar projetos/fontes/matrizes antes de substituir o debug; não desinstalar sem backup verificado.**

## Restrições preservadas
- Não recriar projeto e não mudar `applicationId`, identidade Brother, funcionalidades, licenças, Bíblia EBD, Supabase Auth/Trial, WooCommerce ou Efí.
- Não executar geração de chaves efêmeras dentro do GitHub Actions sem processo controlado de custódia/backup.
- Não declarar release 1.0.0 estável antes de assinatura, atualização segura, testes de bordado e pagamentos.

## Adendo — criação da primeira keystore candidata

Após a auditoria, em 10/10/2026, o usuário autorizou expressamente gerar e executar uma assinatura de produção sem Termux. A nova keystore de produção foi gerada fora do repositório, com certificado público SHA-256 `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a` e credenciais em arquivo privado que deverão ser entregues ao titular para custódia. O hash antigo `663d4338f085730a87efbf7a2bd3b0ccb91d7dc3696e625afa9c35a0e47c0403` permanece apenas como referência histórica **não comprovada** e foi substituído como certificado esperado no workflow da branch de preparação.

APK assinado de verificação: `Brother-Matrizes-1.0.0-rc1-ASSINADO-PRODUCAO-QA.apk`, hash SHA-256 `97bec26cb16d5d9e2510e088ba27212fb6ff9b8a17472851de11597dfeaeeb8f`. Resultado da ferramenta oficial: v2=true, v3=true, certificado RSA 4096 verificado. **Não é ainda a versão 1.0.0 estável nem foi publicada em GitHub Release.**
