# Brother Matrizes Windows — retomada segura de recentes FLP (QA)

**Escopo:** PR #226, branch `feature/windows-fiel-android-1-11-8-20261106`.
**Referência de produto:** Android comercial 1.11.8, HomeScreen original.
**Liberação:** somente QA. Nenhum merge na `main`, EXE comercial ou
alteração na versão oficial Android foi autorizado.

## Problema funcional

O Windows persistia os IDs dos projetos recentemente abertos na Biblioteca
(`DesktopLibraryActivityStore`), mas ao reiniciar a Home criava
`activeDesign = null` e perdia o cartão **Recente** da sessão anterior.

## Correção implementada

- `DesktopRecentProjectRecovery.mostRecentValid` recupera a última matriz
  FLP válida **somente entre os IDs presentes no catálogo da pasta previamente
  escolhida**. Um caminho arbitrário presente no arquivo de preferências não
  autoriza abrir arquivos fora desse catálogo.
- Verifica de novo que o FLP é arquivo regular, não link simbólico,
  com caminho filho da pasta selecionada, e reutiliza
  `DesktopEmbroideryFiles.open` e o codec oficial do Android.
- Limita a busca a 30 recentes, ignora arquivos desaparecidos/corrompidos
  e tenta o próximo projeto válido. Leitura no `Dispatchers.IO`.
- Ao abrir normalmente o aplicativo, o projeto recuperado preenche apenas
  o estado **em memória** do componente Android `HomeScreen`. A ação
  **Recente** continua abrindo o Visualizador original.
- Se o usuário já tiver iniciado outra ação/criado uma matriz, a recuperação
  em andamento **não substitui** o trabalho atual.
- Todos os modos de captura de QA, abertura explícita pelo Explorer
  e outros cenários de teste desabilitam a retomada automática.
- Não há gravação em FLP original, importação de fontes, consumo de cota,
  autenticação Google ou transferência para máquinas.

## Cobertura automatizada

`DesktopRecentProjectRecoveryTest` valida:

1. Fechar/reabrir logicamente as preferências e recuperar o último projeto
   FLP sintético pelo mesmo codec, preservando os bytes originais.
2. Recusar caminho externo ao catálogo, arquivo desaparecido e arquivo
   modificado/corrompido entre varredura e recuperação; tentar o próximo
   válido e preservar o conteúdo de arquivos externos.
3. Compilação da UI no workflow Windows Home, e regressão da imagem
   executável QA, sem distribuição comercial.

O workflow `brother-windows-flp-backup-qa.yml` incorpora o teste
`DesktopRecentProjectRecoveryTest` e a regressão das preferências.

**Importante:** QA automatizada é distinta de teste manual em PC de cliente.
O suporte a recentes em pastas arbitrárias, arquivo criado e salvo fora da
Biblioteca selecionada, e todos os demais recursos Android ainda dependem
de porting/homologação separada.

## Execuções de verificação

- [Biblioteca e FLP](https://github.com/timachado/Brother-Matrizes/actions/runs/42377148148)
- [Compilação da Home](https://github.com/timachado/Brother-Matrizes/actions/runs/42377129078)
- [Portátil interno QA](https://github.com/timachado/Brother-Matrizes/actions/runs/42377129130)

As execuções devem ser consultadas antes de marcar a alteração como
aprovada; a existência de um link não significa resultado positivo.

## Primeiro resultado verificado de CI — 07/11/2026

- [Windows FLP/Biblioteca #42377148148](https://github.com/timachado/Brother-Matrizes/actions/runs/42377148148)
  terminou `completed/success` no commit `44b6777`, incluindo
  `DesktopRecentProjectRecoveryTest` e
  `DesktopLibraryActivityStoreTest`.
- A alteração posterior `5496b38` desliga explicitamente a recuperação
  automática em todos os modos de screenshot de QA. Sua revisão completa
  depende das execuções específicas da mesma revisão:
  [FLP #42377281275](https://github.com/timachado/Brother-Matrizes/actions/runs/42377281275),
  [Home #42377281288](https://github.com/timachado/Brother-Matrizes/actions/runs/42377281288)
  e [portátil interno #42377281318](https://github.com/timachado/Brother-Matrizes/actions/runs/42377281318).
- **Não interpretar builds antigos como homologação da última revisão.**

## Integração Abrir / Salvar / Biblioteca — 07/11/2026

- `DesktopLibraryRecentTracker` atualiza o catálogo e coloca o FLP
  **validado** em primeiro lugar no histórico, apenas quando o caminho
  estiver na **pasta FLP já selecionada pelo usuário**.
- A abertura por **JFileChooser** e pelo **Explorador do Windows**, bem como
  a confirmação de **Salvar projeto FLP**, chamam o mesmo rastreador.
  A abertura de DST/PES/JEF não implica indexação falsa em catálogo FLP.
- Não há troca automática de pasta, acesso a projetos de outras pastas,
  inclusão de arquivo corrompido ou escrita em matrizes existentes.
- A codificação e a gravação FLP, assim como a leitura pelo seletor,
  agora executam em `Dispatchers.IO`, para reduzir bloqueios da janela.
- `DesktopLibraryRecentTrackerTest` cobre inclusão de novo arquivo,
  deduplicação/capacidade do histórico, arquivo corrompido, ausente,
  externo à pasta e preservação dos bytes originais.
- O workflow Windows de Biblioteca/FLP foi atualizado para executar
  a nova suíte. Ainda é necessário validar o resultado final do
  pipeline e a experiência manual em PC Windows.

A lista de recentes fora da pasta selecionada e a sincronização
de projetos com armazenamento de nuvem não foram implementadas aqui.
Essa mudança não altera assinaturas, cotas ou autenticação comercial.

## Validação da integração Abrir/Salvar/Biblioteca — 07/11/2026

A revisão `9be3845`, que inclui `DesktopLibraryRecentTracker`,
a integração da UI e `DesktopLibraryRecentTrackerTest`, passou pelo
[GitHub Actions FLP/Biblioteca #42378267773](https://github.com/timachado/Brother-Matrizes/actions/runs/42378267773)
com resultado `completed/success`. O job executou os testes de
biblioteca, recentes, exportação/restauração FLP, integridade dos dados
e verificações para não criar um instalador comercial.

A compilação visual da Home e a criação do novo pacote portátil QA
são execuções separadas; exigem resultados próprios antes de qualquer
alegação de aprovação do pacote completo.

## Pacote portátil de QA da integração Abrir/Salvar — 07/11/2026

- [GitHub Actions #42378251643](https://github.com/timachado/Brother-Matrizes/actions/runs/42378251643):
  `completed/success` após os testes prioritários, geração de
  `createDistributable`, inicialização e capturas em Windows runner.
- Artefato **temporário, privado e NÃO COMERCIAL**
  [#13576493156](https://github.com/timachado/Brother-Matrizes/actions/runs/42378251643/artifacts/13576493156),
  nome `Brother-Matrizes-Windows-PORTABLE-QA-NÃO-COMERCIAL`.
  Contém launcher `BrotherMatrizesQA.exe` e as pastas requeridas
  de runtime e aplicação. Não é executável independente isolado,
  nem instalador, nem versão homologada para clientes.
- O pipeline de Home [#42378251655](https://github.com/timachado/Brother-Matrizes/actions/runs/42378251655)
  concluiu compilação, validação de fonte Android e testes unitários.
  A etapa de captura visual permanece sujeita ao resultado final do job.

A validade do artefato é limitada pelas políticas de retenção do
GitHub Actions. Nenhum release público ou upload no site foi feito.
