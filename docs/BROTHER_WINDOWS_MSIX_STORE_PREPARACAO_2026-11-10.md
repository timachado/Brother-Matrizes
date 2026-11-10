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
