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
