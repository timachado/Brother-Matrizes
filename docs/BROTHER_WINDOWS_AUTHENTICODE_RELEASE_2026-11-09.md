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

## Preparação para emissão do certificado OV (10/11/2026)

**Estado:** levantamento de fornecedores e requisitos concluído. Compra,
validação da entidade, emissão, entrega, assinatura real e publicação
**NÃO realizadas**. Sem dados pessoais ou credenciais no repositório.

### Decisão técnica provisória

Para primeiro lançamento com o workflow existente, priorizar **Code Signing
OV com token criptográfico FIPS/CC fornecido pela CA**, desde que haja um
Windows dedicado e controlado pela empresa. O pipeline existente usa
`signtool.exe` e consulta o certificado no Windows Certificate Store;
o token deve ser compatível com esse fluxo. Não usar GitHub-hosted runners
para guardar token ou PIN, nem conectá-lo ao computador de cliente.

- **Cotação inicial:** oferta Code Signing OV no portal Sectigo Brasil,
  cerca de R$ 3.026/ano com dispositivo conforme FIPS, preço anunciado
  e sujeito a confirmação de impostos, frete, validação e disponibilidade:
  https://www.sectigo.com.br/produtos/certificado-codesigning.php
- **Alternativa remota:** DigiCert Code Signing + KeyLocker, HSM em nuvem;
  exige avaliar custo recorrente e adaptar o workflow para o serviço
  de assinatura da CA, sem colocar chave privada no GitHub:
  https://www.digicert.com/signing/compare-code-signing-certificates
  https://docs.digicert.com/en/certcentral/get-started/set-up-your-certcentral-subscription/add-a-code-signing-certificate-subscription.html
- **Outra cotação:** GlobalSign Code Signing OV, conferir opção com token,
  logística nacional e razão social do editor:
  https://www.globalsign.com/pt-br/code-signing-certificate
- **Azure Artifact Signing Public Trust não elegível para entidade brasileira**
  segundo a lista de países atual; a existência da região Brazil South
  não equivale à elegibilidade da emissão Public Trust:
  https://learn.microsoft.com/pt-br/azure/artifact-signing/quickstart

### Verificações obrigatórias ANTES da compra

1. Confirmar se há CNPJ ativo e qual é a **razão social legal exata**.
   Não presumir que "T.I. Machado Soluções em Tecnologia" seja o nome
   cadastrado. Perguntar à CA se aceita o tipo societário/MEI aplicável
   e como mostrar nome fantasia registrado, se permitido.
2. Ter documentos de constituição/MEI e comprovantes que a CA solicitar,
   endereço físico verificável e contato empresarial independente para
   confirmação por telefone/e-mail; representante autorizado. O formulário
   consultado da oferta Sectigo Brasil solicita **CNPJ e razão social
   coincidentes com a Receita Federal** e **e-mail não gratuito do
   contato organizacional** (Gmail/Hotmail/Yahoo não aceitos ali).
   Se ainda não houver domínio/e-mail corporativo, providenciar ou
   negociar uma modalidade aceita pela autoridade antes do pagamento.
   Fonte: https://www.sectigo.com.br/produtos/order.php
3. Confirmar que o produto é **Code Signing OV para Authenticode EXE Windows**,
   e não SSL/TLS OV, e-CNPJ comum, S/MIME ou assinatura de PDF.
4. Confirmar **token FIPS/CC compatível**, suporte a SignTool Windows,
   Windows Certificate Store, entrega no Brasil, driver, suporte, frete,
   prazo, impostos e política de reemissão. Solicitar **orçamento escrito**
   e prova de que o emissor/cadeia é confiável para Windows.
5. Registrar separadamente responsável pelo PIN e runner Windows
   `self-hosted`, com acesso restrito. Nunca copiar PIN, PFX exportável,
   segredo, service_role ou chave privada para GitHub, docs, logs ou chat.
6. Certificado tem que possuir **EKU Code Signing** OID
   `1.3.6.1.5.5.7.3.3`, editor correspondente à entidade jurídica
   validada e assinatura com carimbo RFC 3161. Validade máxima das novas
   emissões desde 01/03/2026 é **460 dias** (CA pode oferecer um ano).

### Fluxo após emissão

- Ativar runner Windows seguro com token, driver e Windows SDK/SignTool.
  Verificar o certificado por thumbprint, sujeito, validade, EKU e
  que a chave seja inacessível fora do módulo de hardware.
- Concluir paridade comercial, autorizações Free/Pro, teste Google real
  e aprovação do proprietário; retirar o marcador QA por alteração
  revisada, nunca apenas renomeando um ZIP.
- Proteger branch `main` e ambientes
  `brother-windows-code-signing` e
  `brother-windows-release-publication` com revisão e autorização.
- Só então configurar variáveis sem segredos, executar manualmente
  `.github/workflows/brother-windows-authenticode-release.yml`
  na `main` com versão aprovada, verificar
  `SignTool verify /pa /all /tw`, SHA256 e assinatura. Publicação
  exige autorização separada e não pode sobrescrever o Android.
- Mesmo com assinatura válida, não prometer ausência imediata de alertas
  Microsoft SmartScreen: a reputação é construída com o histórico.

Fontes normativas:
https://cabforum.org/working-groups/code-signing/requirements/
https://learn.microsoft.com/pt-br/windows/apps/package-and-deploy/code-signing-options

## Alternativa para desenvolvedor autônomo sem CNPJ (10/11/2026)

**Perfil declarado:** proprietário atua como pessoa física com CPF, sem CNPJ.
**Não gravar CPF, RG, endereço pessoal ou documento no repositório.**

### Constatação que altera a estratégia anterior

- A documentação global da **Sectigo**, atualizada em 02/06/2026,
  afirma explicitamente que Code Signing OV (não EV) **pode ser emitido
  para indivíduos**, com verificação de identidade por documento com
  foto/vídeo, endereço e contato verificado. Portanto, a exigência de
  CNPJ não é universal para todos os emissores Code Signing:
  https://www.sectigo.com/faqs/detail/OV-Code-Signing-Validation-for-Organizations-and-Individuals
- **O revendedor Sectigo Brasil consultado não atende CPF**: o formulário
  do distribuidor exige CNPJ e declara não emitir Code Signing para PF.
  Não solicitar ali ou pagar sem aceite prévio por escrito:
  https://www.sectigo.com.br/produtos/order.php
- DigiCert e GlobalSign foram consultados em suas ofertas padrão,
  voltadas a entidades empresariais; não presumir elegibilidade para
  pessoa física no Brasil sem confirmação específica.
- Microsoft Store **assina pacotes MSIX/AppX submetidos à Store**, mas
  **não assina EXE/MSI avulsos nem o ZIP portátil distribuído pelo site**.
  A Microsoft recomenda conta de Empresa para freelancers distribuindo
  aplicativos em razão da própria profissão; conta Individual é destinada
  sobretudo a projetos não comerciais/não relacionados à profissão.
  Assim, não tratar Store como garantia de liberação comercial por CPF:
  https://learn.microsoft.com/en-us/windows/apps/publish/faq/get-started-with-the-microsoft-store
  https://learn.microsoft.com/pt-br/windows/apps/publish/faq/open-developer-account
- Azure Artifact Signing Public Trust para pessoas físicas no Brasil
  **não está disponível** na lista oficial de elegibilidade atual.

### Próxima ação sem custo ou alteração de produção

1. Contatar diretamente a **Sectigo internacional** ou um revendedor
   autorizado de Code Signing e solicitar confirmação **por escrito** de
   emissão **Code Signing não EV em nome de pessoa física residente no
   Brasil (CPF, sem CNPJ)** e preço final, emissão, driver, token/HSM.
2. Confirmar que o certificado será aceito pelo programa de raízes de
   confiança da Microsoft para **EXE Authenticode** e que o `Subject`
   apresentará o **nome civil validado** (a marca Brother Matrizes /
   T.I. Machado permanece no aplicativo, não pode ser improvisada como
   nome legal do editor no certificado).
3. Só pedir documentos civis diretamente no portal seguro da CA; nunca
   por chat, issue, commit, arquivo de build ou repositório.
4. Se o emissor **não aceitar CPF brasileiro**, não comprar certificado
   incompatível: comparar formalização opcional como MEI (apenas se a
   atividade for permitida, com orientação contábil) versus publicar um
   MSIX pela Store mediante conta elegível. Certificado ICP-Brasil e-CPF
   de assinatura geral não substitui automaticamente Code Signing.
5. O workflow `brother-windows-authenticode-release.yml` já compara
   `BM_SIGNER_SUBJECT` com o `Subject` exato do certificado; configurar
   apenas depois da emissão. Não afrouxar validação nem forjar subject.

**Estado:** consulta de elegibilidade em aberto; sem compra, emissão,
certificado, assinatura ou publicação.
