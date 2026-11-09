# Brother Matrizes Windows — plano de assinatura digital Authenticode

**Criado em 09/11/2026.** Pipeline preparado apenas na branch draft do PR #226.
**Nenhum certificado, assinatura, publicação ou deploy foi feito.**

## Para o Brasil

Azure Artifact Signing opera na região Brazil South, mas a assinatura
**Public Trust** não está atualmente disponível para organizações brasileiras.
A alternativa apropriada para a distribuição de um EXE portátil comercial é
certificado **OV Code Signing** emitido por CA confiável no Windows (exemplos:
DigiCert, GlobalSign, Sectigo) com **razão social legal validada**.

A chave privada OV precisa ser protegida por token criptográfico ou HSM.
Não armazenar `.pfx`, PIN, chave exportável, certificado com chave privada
ou tokens de assinatura no GitHub, neste chat, no APK ou em ZIP de clientes.

## Workflow commercial preparado

`.github/workflows/brother-windows-authenticode-release.yml`.

- **Somente `workflow_dispatch` em main**; não assina PR/push/QA.
- Compila e roda os testes Windows em runner GitHub sem certificado.
- Bloqueia versão QA `0.0.1`, título `QA interna` e versão
  discordante. Esses são bloqueios intencionais até a aprovação comercial.
- Entrega binário não assinado como artifact temporário (1 dia).
- Segundo job usa `environment: brother-windows-code-signing` e
  runner dedicado `[self-hosted, Windows, X64, brother-signing]`.
  O runner deve ser de propriedade da organização, restrito e com
  certificado OV não exportável vinculado ao token/HSM. Não usa checkout.
- O environment deve exigir **required reviewers** e permitir **somente main
  protegida**. Variáveis de ambiente para provisionamento (não são segredos):
  `BM_SIGNING_ENABLED=approved` (somente após revisão de lançamento),
  `BM_CERT_THUMBPRINT` (40 caracteres hex),
  `BM_SIGNER_SUBJECT` (Subject exato de razão social na CA),
  `BM_RFC3161_TIMESTAMP` (URL oficial do servidor de timestamp).
- SignTool assina o launcher com `/fd SHA256 /tr <timestamp> /td SHA256`
  e verifica a assinatura com `verify /pa /all /tw`, mais
  `Get-AuthenticodeSignature` com cadeia válida, editor e timestamp.
- Só produz **artefato privado** se tudo passar. Não publica no site
  nem remove SmartScreen, Defender ou altera cotas/dados comerciais.

## Antes de ativar

1. Definir razão social válida para emissão de certificado OV.
2. Adquirir certificado reconhecido, em token/HSM, e configurar
   acesso seguro pelo SignTool/Windows Certificate Store de signer dedicado.
   Se a CA oferecer somente assinatura remota, adaptar o passo de assinatura
   à API homologada antes de ativar; não pressupor acesso ao store.
3. Configurar proteção da main, required reviewers do environment e
   runner Windows dedicado; instalar Windows SDK/SignTool.
4. Terminar o app Windows comercial, retirar indicadores QA legitimamente,
   validar Free/Pro e a versão definitiva; nunca assinar um QA como release.
5. Acionar manualmente. Revisar SHA256, certificado e timestamp,
   executar testes em Windows limpo e publicar em HTTPS somente após aprovação.

A assinatura Authenticode do EXE não garante a confiança SmartScreen imediata
nem elimina detecções específicas do Microsoft Defender em arquivos PES.
Avaliá-las separadamente.

Referências:
https://learn.microsoft.com/pt-br/windows/apps/package-and-deploy/code-signing-options
https://learn.microsoft.com/pt-br/azure/artifact-signing/quickstart
https://learn.microsoft.com/en-us/windows/win32/seccrypto/time-stamping-authenticode-signatures
https://learn.microsoft.com/en-us/windows/win32/seccrypto/using-signtool-to-verify-a-file-signature

## Lançamento oficial por GitHub Releases (após assinatura)

Terceiro job `publish` no mesmo workflow, condicionado a `sign=success`
e a `github.ref=refs/heads/main`. O ambiente
`brother-windows-release-publication` exige revisão/allowlist de branch
configuradas pelo administrador da organização. A variável protegida
`BM_PUBLISHING_ENABLED=approved` precisa ser ativada conscientemente; OFF
por padrão. Sem certificado OV e HSM, não existe job de assinatura concluído
e não há lançamento.

Em runner Windows independente de quem assina, o publicador verifica
outra vez `Get-AuthenticodeSignature` + SignTool `/pa /all /tw`,
o relatório SHA256 do EXE, ausência de chaves privadas, diretórios app/runtime
e integridade do ZIP resultante. Produz:
- `Brother-Matrizes-Windows-X.Y.Z-OFICIAL-ASSINADO.zip` com runtime
- `SHA256SUMS.txt` para verificação de integridade
- Publicação em GitHub Releases com tag `windows-vX.Y.Z`, sem sobrescrever
  releases anteriores e sem mudar a indicação "latest" Android.

A publicação no WordPress da empresa é outro passo: a conexão WPVibe
não estava autorizada e a URL oficial configurada no aplicativo não forneceu
REST JSON válido na tentativa de ligação de 09/11/2026. **Não alterar o site
nem inventar um endereço de download**. O pipeline GitHub publica apenas
quando os pré-requisitos comerciais forem realmente completados.

O teste automatizado de política de assinatura valida o novo job em modo
offline; ele não dispõe de certificado e não executa assinatura real.
