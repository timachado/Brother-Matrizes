# Brother Matrizes Windows — MSIX para Microsoft Store (10/11/2026)

**Objetivo:** preparar o mesmo aplicativo Windows validado pelo proprietário em QA
como pacote MSIX para análise futura da Microsoft Store, sem comprar certificado,
sem modificar Android e sem publicar produto ou dados de clientes.

## O que foi preparado no GitHub

- Workflow isolado `.github/workflows/brother-windows-msix-store-qa.yml`.
- Testes unitários do empacotador `windows-fiel/qa/test_preparar_msix_store.py`.
- Staging `windows-fiel/qa/preparar_msix_store.py` que copia o
  `BrotherMatrizes.exe` da app-image **completa** (pastas `app` e `runtime`).
- Ícones 44x44, 150x150 e 50x50 gerados do WebP oficial aprovado no Android.
- `AppxManifest.xml` com `Windows.Desktop`, arquitetura x64,
  `packagedClassicApp`, `mediumIL` e `runFullTrust`; sem pedir
  capacidades não utilizadas.
- SDK Windows `MakeAppx.exe` cria o MSIX e o desempacota para validar a
  presença da aplicação, do runtime e do manifesto. Gera SHA256.
- O artefato publicado pelo **GitHub Actions interno** chama-se
  `Brother-Matrizes-Windows-MSIX-QA-UNSIGNED-NAO-PUBLICAVEL`;
  tem identidade fictícia `com.timachado.brothermatrizes.msixqa`,
  editor `CN=BrotherMatrizesQA-Untrusted` e versão fictícia `1.0.0.0`.
  **Não é instalável com confiança pública, não é candidato de submissão,
  não está assinado e não pode ser publicado**.
- A PR Windows #226 permanece em rascunho, sem merge à main.
  O APK Android e a assinatura JKS não são tocados.

## Verificação de elegibilidade do titular CPF sem CNPJ

A Microsoft disponibiliza inscrição gratuita de desenvolvedor através de
https://storedeveloper.microsoft.com/, inclusive para organizações.
Entretanto, a política Microsoft Store **10.14** estabelece que publicação
relacionada ao próprio negócio ou profissão, ou apresentada ao consumidor
como empresa, exige **Company developer account**, não Individual.

O cadastro Company exige verificação documental de identidade empresarial,
por D-U-N-S ou documentos empresariais oficiais aceitos. Ter somente CPF
não demonstra automaticamente elegibilidade. Sem CNPJ, é preciso pedir
por escrito ao Partner Center se uma atividade autônoma sem CNPJ no Brasil
pode ser validada por algum documento alternativo aceito. **Não realizar
cadastro Individual alegando que Brother Matrizes é um hobby**.

Canais oficiais:
- Política de tipo de conta:
  https://learn.microsoft.com/windows/apps/publish/store-policies#1014-account-type
- Elegibilidade e requisitos empresa:
  https://learn.microsoft.com/pt-br/windows/apps/publish/whats-new-company-developer
- Cadastro gratuito e formas de verificação:
  https://learn.microsoft.com/pt-br/windows/apps/publish/faq/open-developer-account
- Inscrição: https://storedeveloper.microsoft.com/

## O que depende do proprietário e da Microsoft

1. Confirmar com o suporte Microsoft Partner Center a elegibilidade da
   T.I. Machado como autônomo brasileiro sem CNPJ (tipo Company).
   Não enviar CPF, selfie, documentos ou credenciais ao GitHub nem ao chat.
2. Após conta aprovada, **reservar o nome Brother Matrizes** e obter os
   campos reais do Partner Center: `Identity Name`, `Publisher` e
   `PublisherDisplayName`.
3. Preparar o manifesto **final** com identidade real e versão aprovada,
   mantendo o código funcional aprovado em QA; o nome comercial no app
   pode continuar T.I. Machado, mas o editor exibido é definido
   pela identidade validada.
4. Rodar o Windows App Certification Kit e testar a versão instalada
   via MSIX, pois isolamento e redirecionamentos de arquivos de MSIX
   podem divergir do executável portable aprovado.
5. Obter aprovação de submissão do proprietário separadamente,
   submeter o MSIX no Partner Center, aguardar a certificação e somente
   então usar o pacote assinado pela Microsoft Store. Não habilitar upload
   automático neste estágio.
6. O MSIX distribuído fora da Store precisa de assinatura confiável
   própria. A assinatura Microsoft da Store **não transforma o EXE**
   portátil do site em um executável Authenticode assinado.

## Estado em 10/11/2026

- Aceite funcional QA: **informado e registrado pelo proprietário**.
- MSIX: workflow de **QA criado**, aguardar o resultado dos testes no runner.
- Conta Company, identidade da Store, aceitação de autônomo CPF,
  testes em MSIX instalado, submissão e publicação: **não confirmados**.
- Microsoft Store não conectada; não realizar ações em conta ou Store
  usando credenciais não fornecidas.


## Evidência do pacote gerado — 10/11/2026

**GitHub Actions:** https://github.com/timachado/Brother-Matrizes/actions/runs/42762465675

- Commit: `fec5a5ed00cc818ed1f02ad2cd29c31360aa3133`.
- Resultado: **completed/success** em Windows 2025.
- Testes do gerador/ícone, testes Windows/Gradle, criação e reabertura
  com MakeAppx e upload do artefato: **aprovados**.
- Artefato: `Brother-Matrizes-Windows-MSIX-QA-UNSIGNED-NAO-PUBLICAVEL`,
  ID `13738562833`, 94.648.859 bytes (arquivo ZIP de artefato do Actions),
  expiração informada pelo GitHub em 15/11/2026.
- Artefato **sem assinatura**, com identidade fictícia; não instalar como
  aplicativo confiável nem enviar para a Microsoft Store.
- A PR #226 continua draft; nenhum release foi publicado, nenhuma
  alteração na branch main, APK Android, Supabase ou certificado JKS.
- Próximo bloqueio externo: Partner Center deve confirmar elegibilidade
  de titular profissional brasileiro com CPF, sem CNPJ, para publicar
  como empresa. Após aprovação, reservar app e obter Identity/Publisher.
- **Aviso técnico:** sucesso em MakeAppx não demonstra instalação
  MSIX, execução real sob isolamento de pacote, WACK ou aprovação Store.
  Essas verificações devem usar uma identidade válida de publicador.


## Identidade real informada pelo proprietário — 10/11/2026

O proprietário concluiu o cadastro de desenvolvedor Microsoft Store e criou
o rascunho **Brother Matrizes**, tipo **Aplicativo MSIX ou PWA**. Forneceu
os dois valores abaixo da página "Exibir a identidade do produto":

- `Package/Identity/Name`: `T.I.MachadoSoluesemTecnol.BrotherMatrizes`
- `Package/Identity/Publisher`: `CN=353309CB-0CCB-4E2D-8F88-AF631DAA395F`

Esses campos foram aplicados **literalmente** ao gerador
`preparar_msix_store.py` e às validações do workflow e teste. Não corrigir
ortografia dos identificadores, pois são valores técnicos reservados na Store.

**Ainda é necessário conferir o terceiro campo oficial:**
`Package/Properties/PublisherDisplayName`. O manifesto usa provisoriamente
`T.I. Machado Soluções em Tecnologia`, como marca declarada pelo responsável,
e não o identifica como valor confirmado pelo Partner Center.
O proprietário pode localizá-lo no mesmo painel de identidade.

**Importante:** a versão `1.0.0.0`, os títulos "QA MSIX" no launcher
e no manifesto, o fluxo de build e o artefato são de teste, sem assinatura.
O pacote usa identidade real, mas continua não pronto para submissão até
a confirmação do terceiro campo, preparação de versão comercial, testes
MSIX de instalação e aprovação explícita de envio.

A conta foi criada/verificada pelo portal, mas o tipo
`Individual` versus `Company` **não foi mostrado** ao assistente.
Não inferir que um cadastro concluído significa aceitação de
atividade comercial no tipo correto.

Fonte Microsoft — os três valores do manifesto:
https://learn.microsoft.com/pt-br/windows/uwp/publish/view-app-identity-details

Fonte Microsoft — pacote MSIX assinado pela Store ao publicar:
https://learn.microsoft.com/en-us/windows/apps/publish/get-started
