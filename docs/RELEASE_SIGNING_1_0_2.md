# Assinatura comercial — Brother Matrizes 1.0.2

## Estado
- A versão 1.0.2 (`versionCode=168`) compilou em release sem assinatura e passou nos testes unitários.
- O APK comercial 1.0.1 está assinado com o certificado SHA-256 `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`.
- A chave original foi entregue em 10/10/2026 como `Brother-Matrizes-Production-Key-v1.jks`, com credenciais separadas em `Brother-Matrizes-Production-Key-v1-CREDENCIAIS-PRIVADAS.txt`.
- **Não criar novo keystore. Não fazer upload do `.jks` ou das credenciais em arquivos do repositório, comentários de issue, PR ou chat.**

## Preparação única de segredos
Abra GitHub no navegador do Android, preferencialmente em modo computador, depois `timachado/Brother-Matrizes → Settings → Secrets and variables → Actions`.
Crie os quatro segredos de repositório com os seguintes nomes (os valores são privados e não ficam no código):

| Nome | Valor |
|---|---|
| `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64` | Conteúdo binário do JKS original codificado em Base64, sem quebras de linha |
| `BROTHER_MATRIZES_RELEASE_STORE_PASSWORD` | Senha da loja do arquivo original |
| `BROTHER_MATRIZES_RELEASE_KEY_ALIAS` | Alias original |
| `BROTHER_MATRIZES_RELEASE_KEY_PASSWORD` | Senha privada da chave original |

Obs.: Base64 não protege a chave por criptografia; o segredo deve ser armazenado somente em GitHub Actions Secrets. Evite copiá-lo para editores online ou serviços externos.

## Execução manual
Um workflow de assinatura **somente manual** foi adicionado à branch `main`, no arquivo `.github/workflows/sign-brother-1.0.2-manual.yml`. Ele usa um SHA de fonte revisado e fixado para esta versão.
1. Abra `Actions → Brother Matrizes 1.0.2 - assinatura comercial manual → Run workflow`.
2. O GitHub compila o app em release não assinada e chama `scripts/sign-1.0.2-with-original-key.sh` com segredos de Actions.
3. O script recusa versões, pacotes ou certificados diferentes dos esperados.
4. Se aprovado, gera `Brother-Matrizes-v1.0.2-CANDIDATO-ASSINADO-TESTES` como artefato privado/da conta GitHub, **não publica automaticamente no site**.
5. Baixe o candidato e teste a instalação por cima do 1.0.1 em dispositivo de teste, verificando login Google, licenças, projetos PES/DST/JEF, fontes e matrices; valide o cartão de Atualizações.

## Advertências
- O APK de auditoria `app-release-unsigned.apk` não é instalável como atualização de produção.
- O certificado não pode ser recuperado do APK assinado: só o `.jks` com a chave privada original possibilita atualizar a instalação existente.
- O workflow manual não vai executar com sucesso enquanto os quatro segredos não estiverem configurados.
- A versão 1.0.1 do site e a release branch devem permanecer intactas até todos os testes.