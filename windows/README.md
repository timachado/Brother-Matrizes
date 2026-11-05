# Brother Matrizes Desktop — homologação Windows (NÃO COMERCIAL)

Este projeto Windows está isolado do Android comercial. O PR não modifica
`app/`, `supabase/` nem pipelines oficiais de publicação.

## Amostras sintéticas para homologação física

O workflow Windows QA gera, via launcher empacotado, a subpasta
`qa/fixtures/` do artefato de instaladores. Contém DST, PES padrão Brother,
PES com perfil PC **experimental**, JEF, FLP oficial Android e um par
FLP + `.bmlayers` de letras independentes. Usa apenas nomes sintéticos
`QA` e `AB`, sem dados de clientes. O arquivo `SHA256SUMS.txt` da subpasta
é conferido pelo CI, mas **não é assinatura digital** e não autentica a origem.

Essas amostras tornam os testes físicos reproduzíveis; a aprovação do CI não
garante aceitação no PE-DESIGN 11 nem costura em bordadeira. Somente avaliadores
autorizados devem utilizar esse kit, de acordo com
`qa/ROTEIRO-HOMOLOGACAO-FISICA.md`.

## Funcionalidades disponíveis na QA

- Compose Desktop com instaladores EXE/MSI gerados por runner Windows.
- Abertura local de DST/PES/JEF usando os parsers oficiais Android.
- Prévia de pontadas, cores, dimensões, dados da matriz e sequência visual
  manual. Não é simulação real de velocidade, tensão ou costura.
- Navegação da prévia no PC: ajustar à janela, zoom de 1x a 8x,
  ampliar/reduzir pela roda do mouse mantendo o ponto sob o cursor
  (ao retornar a 1x, centraliza a imagem) e arrastar quando ampliada.
  Trata-se apenas de visualização: não altera pontadas, dimensões,
  histórico de edição ou arquivos.
- Transformações geométricas (rotação, escala, deslocamento, espelhamento),
  inclusive **expansão horizontal e vertical independentes (0,50–2,50)**,
  com restauração do estado carregado e **Desfazer/Refazer**. O histórico é
  apenas da sessão, inclui a restauração do original, invalida o refazer
  ao aplicar uma nova edição e guarda as **últimas oito operações**, sem
  duplicar matrizes enormes em RAM. Projetos FLP salvam somente o estado atual.
- **Composição de até 10 letras novas como objetos independentes (QA)**:
  permite selecionar uma letra, mover em X/Y, escalar e girar individualmente
  com histórico de parâmetros Desfazer/Refazer. Os glifos são gerados pelo
  `TextMatrixGenerator` do Android e transformados pelo `MatrixEditor`
  oficial; cada objeto é conectado com JUMP e há um único END. O resultado
  passa por `EmbroideryIntegrity`. O modo individual é **somente de sessão**,
  aceita letras latinas/acentos e espaços, não separa glifos importados de
  DST/PES/JEF. O FLP **oficial Android** continua salvando apenas as
  pontadas finais; opcionalmente é possível salvar, ao lado de um FLP **novo**,
  um sidecar Windows `.bmlayers` que guarda parâmetros de cada letra,
  vinculado ao FLP por SHA-256. Ao reabrir o par, a QA recalcula a matriz
  e exige correspondência integral das pontadas para habilitar os objetos.
  Se faltar o sidecar, o FLP continua abrindo normalmente, mas sem edição
  individual. Ajustes globais encerram o modo individual da composição.
  A compatibilidade de costura ainda exige homologação física.
- **Editor de cores RGB por índice de linha (até 64)**, com prévia
  atualizada, valores hexadecimais, preservação exata dos pontos e suporte
  a Desfazer/Refazer. Edita só metadados em memória: não modifica densidade,
  trocas de cor ou arquivo original. DST não conserva paleta completa; requer
  homologação das cores no formato exportado e na máquina.
- **Pré-checagem geométrica de bastidores oficiais** na matriz atual:
  considera tamanhos e margens internas do motor Android, distingue encaixe
  direto, possível orientação alternativa e excesso. É apenas orientação visual;
  não modifica os pontos nem garante suporte mecânico da bordadeira.
- Nomes em fonte integrada (ponto corrido/Satin), pelo `TextMatrixGenerator`.
- Monogramas Linear, Clássico e Empilhado pelo `MonogramGenerator`.
- Biblioteca local de uma pasta escolhida explicitamente, com DST/PES/JEF/FLP.
  A pasta pode ser lembrada e esquecida nas preferências Windows; sua listagem
  nunca é refeita automaticamente na inicialização. A interface permite
  **pesquisar pelo nome, filtrar formatos e navegar em páginas de 40 itens**,
  alcançando até 500 arquivos já listados, sem buscar subpastas ou acessar rede.
- Conversão DST/PES/JEF pelo `MatrixConverter` com verificação de releitura
  antes da gravação; PE-DESIGN PC é um perfil experimental.
- **Projetos editáveis .FLP nativos do Android**: abrir um projeto existente
  e salvar o estado atual como um arquivo novo, usando exatamente o
  `ProjectCodec` do Android (formato v11).
- Seleção e **pré-visualização TTF/OTF** no computador, sem instalar fontes.
- **Geração Satin TTF/OTF experimental:** contornos obtidos por um adaptador
  Windows/Java2D, conectados ao **mesmo `CleanRoomSatinEngine.kt` oficial
  do Android**, compilado diretamente do código-fonte Android. A guarda de
  cobertura visual também possui adaptação Java2D. Há limites de complexidade
  e a geração falha fechada quando a silhueta não passa na verificação.
  **Não representa compatibilidade industrial comprovada; só QA.**
- A prévia da fonte externa acompanha o nome digitado; densidade Satin
  ajustável entre 0,3 e 1,2 mm e espaçamento entre letras entre 0 e 3 mm.
  A alteração atua somente sobre uma **nova matriz**. Fontes cursivas podem
  depender de ligaduras e, se a verificação de silhueta falhar, a geração
  é bloqueada.

## Compatibilidade do projeto .FLP

O arquivo `app/.../core/project/ProjectStore.kt` reúne `ProjectCodec` (Kotlin
puro) com `ProjectStore` (dependente de `android.content.Context`).
No build Windows, uma tarefa Gradle extrai **somente o trecho-fonte oficial**
do codec para `windows/build/generated/official-project/kotlin/`, usando os
imports JVM compatíveis. O trecho não é reimplementado manualmente; se o
formato do arquivo mudar, a extração falha explicitamente.

Assim, a QA usa a mesma codificação .FLP v11 do Android, inclusive os
metadados de pontos, cores, bastidor e nome editável previstos no codec.

**Limitação:** a gravação FLP captura o estado atual; não fornece histórico
de desfazer nem recria bytes de um arquivo fonte externo que não tenham sido
persistidos pelo codec Android. Abrir um FLP estabelece um novo estado-base
para a ação "Restaurar original" dentro da sessão Desktop.

## Verificação do launcher Windows empacotado

O CI da QA também cria um **app-image** com o runtime Java embarcado e
executa o `BrotherMatrizesDesktop.exe` desse pacote, sem abrir a interface,
com o argumento restrito `--smoke-test` e um caminho temporário de relatório.
Esse teste exercita o aplicativo **depois do empacotamento**, não só as classes
JVM isoladas:

- geração local de nome pela implementação oficial;
- conversão e releitura DST, PES e JEF, além da releitura sintética do
  perfil experimental PES para PE-DESIGN PC (sem validar o PE-DESIGN 11 real);
- gravação e reabertura FLP, conferindo a preservação dos pontos;
- tentativa de sobrescrever um FLP já criado, que obrigatoriamente deve falhar;
- desfazer/refazer de uma transformação, com pontos reconstituídos pelo
  motor Android e sem alterar o original;
- escrita do marcador `BROTHER_WINDOWS_QA_SMOKE_OK` somente após todos
  os controles passarem, com arquivos de teste apagados ao final.

O CI executa duas validações sequenciais no runner virtual Windows:

1. Executa o launcher da imagem distribuível com `--smoke-test`.
2. Instala o **MSI desta própria execução**, em modo silencioso e sem reiniciar,
   localiza o executável realmente instalado, executa o mesmo diagnóstico,
   confirma o marcador `BROTHER_WINDOWS_MSI_INSTALLED_SMOKE_OK` e
   **desinstala** o produto usando o ProductCode do MSI, conferindo a
   remoção do registro de instalação (`BROTHER_WINDOWS_MSI_UNINSTALL_OK`).
   A rotina recusa tocar em uma instalação preexistente e roda somente em
   runner efêmero. Não executa EXE/instalador gráfico sobre outros PCs.

O upload dos instaladores só ocorre **depois** das verificações no
**runner virtual Windows**. Isso **não significa** teste de instalação em um
PC físico, que a UI funciona em todos os computadores, que as fontes passaram
em bordadeira real, ou que o produto foi homologado comercialmente.

## Beta fechada — piloto técnico interno

O workflow de QA gera um **kit para teste interno controlado**, não uma
release pública. Dentro do mesmo ZIP dos instaladores estão
`qa/BETA-FECHADA-01.md` e `qa/verify-beta-package.ps1` com instruções e
validação **offline** de SHA-256, tamanho dos EXE/MSI, ID do workflow e SHA
de origem do PR. Este verificador **não instala nem executa** binários.

A QA é atualmente offline, sem login/licenciamento Windows e sem mecanismo
de revogação; portanto **não deve ser anunciada como beta pública ou enviada
para clientes**. A primeira etapa é um piloto de laboratório, em PCs de teste
controlados e com dados sintéticos. Acesso externo depende de controles reais
de autenticação/licenças, disponibilidade de suporte e aprovação específica.

Nos workflows executados a partir de um pull request, o manifesto inclui
`github.event.pull_request.head.sha` (commit real do PR), e não
`github.sha` (commit de merge temporário criado pelo GitHub Actions).
Assim o testador consegue comparar `sourceCommit` com a revisão autorizada.

Consultar [`qa/BETA-FECHADA-01.md`](qa/BETA-FECHADA-01.md) para os
critérios de aceitação e registro de incidentes. SHA-256 não substitui
assinatura digital nem confirmação da origem do ZIP.

## Diagnóstico gráfico no runner Windows

A QA também pode iniciar o **launcher real do app-image sem argumentos de
negócio**, usando `--ui-smoke-test <relatorio.txt>`. Nesse modo a interface
Compose Desktop é aberta, verifica-se o título/dimensões da janela,
visibilidade e diversidade mínima de pixels renderizados; somente então
grava-se um relatório `BROTHER_WINDOWS_UI_SMOKE_OK` e uma imagem PNG da
**tela inicial vazia**, sem abrir arquivos ou autenticar contas.

No GitHub Actions, a etapa `Iniciar e capturar interface Windows real (QA)`
é obrigatória antes da instalação MSI e do upload. Se o desktop virtual do
runner não suportar captura gráfica, o teste falha explicitamente; não se
deve interpretar um screenshot ausente como aprovação visual. Quando passar,
um segundo artefato `brother-windows-ui-smoke-QA-NAO-COMERCIAL` disponibiliza
screenshot e relatório por sete dias. Uma segunda etapa obrigatória inicia
o mesmo launcher com `--ui-synthetic-smoke-test`, injeta exclusivamente uma
matriz `QA` gerada pelo motor oficial e exige uma captura PNG diferente da
tela vazia. As duas capturas e relatórios são arquivados juntos. O teste
verifica renderização básica com matriz, **sem comprovar zoom por mouse,
precisão visual da costura, PE-DESIGN 11 ou interação humana**.

Para homologação física, usar o roteiro versionado:
[`qa/ROTEIRO-HOMOLOGACAO-FISICA.md`](qa/ROTEIRO-HOMOLOGACAO-FISICA.md),
incluindo PE-DESIGN 11, bordadeira, assinatura e licenciamento.

## Integridade de pacotes de homologação (SHA-256)

A etapa final do workflow verifica a integridade dos instaladores gerados
**depois** de instalá-los, executar o diagnóstico e desinstalá-los na VM.
O pacote ZIP de QA passa a incluir:

- `exe/`: instalador EXE de homologação;
- `msi/`: instalador MSI de homologação;
- `qa/installer-manifest.json`: nome relativo, tamanho e SHA-256 de cada
  instalador, SHA do commit e ID da execução GitHub;
- `qa/SHA256SUMS.txt`: hashes em formato legível para comparação manual.

O workflow exige exatamente um EXE e um MSI, gera o manifesto,
recalcula os hashes e rejeita divergências. Um teste adicional usa pacotes
**fictícios** e comprova que a alteração de um byte, sem mudança de tamanho,
é detectada antes de qualquer upload QA.

**Como verificar após baixar o ZIP pelo GitHub:** extraia o arquivo e,
no PowerShell Windows, execute na pasta extraída:

```powershell
Get-ChildItem .\exe -Recurse -File -Filter *.exe | Get-FileHash -Algorithm SHA256
Get-ChildItem .\msi -Recurse -File -Filter *.msi | Get-FileHash -Algorithm SHA256
Get-Content .\qa\SHA256SUMS.txt
```

Compare cada hash retornado com o correspondente no manifesto. A estrutura
de pastas e os nomes de instaladores devem coincidir. Os checksums servem
para detectar alterações acidentais ou adulteração dos binários **se o
manifesto e a origem GitHub forem confiáveis**. Como os checksums seguem
no mesmo ZIP, eles **não autenticam por si só a origem**. Os instaladores
são deliberadamente **não assinados** nesta QA; assinatura Authenticode,
cadeia de confiança e distribuição comercial seguem pendentes.

## Segurança local

- Nenhuma importação ou upload de matrizes, projetos ou fontes à nuvem.
- Arquivos DST/PES/JEF: limite 8 MiB; FLP: 32 MiB; TTF/OTF: 12 MiB.
- A importação DST/PES/JEF valida o arquivo como regular sem seguir links
  simbólicos e lê no máximo 8 MiB + 1 byte para detectar alterações de
  tamanho entre a checagem inicial e a leitura, recusando excedentes.
- Pastas listadas sem recursão, sem seguir links simbólicos e com limite de
  500 entradas; interface exibe até 60 por vez.
- A biblioteca lembra **só o caminho da pasta**; não armazena conteúdo,
  matrizes, senhas ou credenciais nas preferências.
- Gravação sempre com `CREATE_NEW`, sem sobrescrever arquivos existentes.
- Projetos FLP passam por codificação/decodificação e conferência de pontos/
  cores, matrizes convertidas passam por releitura dos parsers oficiais.
- A leitura de FLP e de arquivos auxiliares `.bmlayers` usa limite efetivo
  de 32 MiB e 4096 bytes, respectivamente, e impede seguir links simbólicos
  também durante a abertura. Não altera o codec FLP Android.
- Fontes externas são validadas por extensão, assinatura e carregamento JVM.
  A criação Satin usa a fonte selecionada por ação explícita e só cria a matriz
  após o motor e o teste de silhueta aprovarem. Não usar em produção,
  bordados para clientes ou máquina real sem homologação física.

## Bloqueadores comerciais

Ainda falta a paridade integral com o Android: editor por objetos e letras,
conversão TTF/OTF com ensaios físicos de Satin e equivalência geométrica
Android/Windows, biblioteca avançada, backup completo, simulador de máquina, outros
recursos aprovados, autenticação/licenciamento/cotas/planos, atualizações
protegidas e integrações Windows equivalentes.

O gerador Android usa `android.graphics`; a portabilidade não é direta.
A versão Desktop mantém o motor Satin Kotlin original, mas adapta os
contornos e a verificação de silhueta para Java2D, com testes de geração
controlados. Há regressões automatizadas para palavras completas, possíveis
ligaduras, contraformas (letra "O") e múltiplas fontes locais do Windows.
Diferenças entre shaping, ligaduras, curva e rasterização Windows/Android
**ainda exigem comparação visual especializada e costura real**.
Nenhuma fonte importada é instalada automaticamente.

Antes de publicar: regressão Android e Windows, prova com PE-DESIGN 11 e
máquina de bordado, instalação em computador Windows real, revisão de
segurança, assinatura do pacote e aprovação comercial separada.

## Compilação

No diretório `windows/`, usando JDK 17 e Gradle 8.14:

```shell
gradle test compileKotlin
gradle createDistributable packageExe packageMsi
```

Workflow `.github/workflows/brother-windows-desktop-qa.yml`:
testes e empacotamento com retenção curta dos artefatos **QA**, sem publicar
release comercial.

**Não mesclar nem publicar este PR enquanto estiver em rascunho.**
