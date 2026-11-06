# Brother Matrizes Desktop — homologação Windows (NÃO COMERCIAL)

> **Meta oficial de produto:** replicar o Android comercial 1.11.8
> integralmente, com interface expandida para Windows. A QA atual
> NÃO É O APP COMPLETO. Auditoria por 16 rotas e critérios de lançamento:
> [PARIDADE-ANDROID-1.11.8.md](PARIDADE-ANDROID-1.11.8.md).

Este projeto Windows está isolado do Android comercial. O PR não modifica
`app/`, `supabase/` nem pipelines oficiais de publicação.

## Identidade do instalador Windows

A partir desta revisão, o nome do aplicativo, do executável e dos atalhos
do Windows é **Brother Matrizes**, com o ícone BM dourado e azul-marinho
fornecido pelo proprietário da marca. O arquivo ICO é derivado da imagem
oficial, armazenado no repositório como Base64 e restaurado no diretório
`build/` com validação SHA-256 antes do empacotamento. O menu Iniciar
usa o grupo **T.I. Machado**; a Área de Trabalho recebe um atalho
**Brother Matrizes**. Isso não altera o ícone nem o pacote Android.

## Versão portátil (sem instalador) — QA restrita

O workflow agora gera também `Brother-Matrizes-Windows-Portable-0.1.0-QA.zip`
com a pasta completa `Brother Matrizes/` (`Brother Matrizes.exe`,
`app/` e `runtime/`), além de `LEIA-ME-PORTATIL.txt`. É uma
versão **portátil**, não um instalador: depois de extrair o ZIP inteiro,
basta abrir `Brother Matrizes/Brother Matrizes.exe`. O EXE sozinho
não funciona sem as subpastas de bibliotecas e runtime.

A QA extrai o ZIP em outra pasta e testa o launcher e a interface
gráfica a partir dali, sem instalar nem criar atalhos. O artefato
privado do GitHub Actions chama-se
`brother-windows-portable-QA-NAO-COMERCIAL`, com hash SHA-256
em `SHA256SUMS.txt`. **Esta versão não possui licenciamento
comercial, assinatura digital ou homologação física** e não deve
ser publicada publicamente até essas etapas serem concluídas.

Para hospedar esse arquivo no WordPress, a hospedagem precisa
aceitar seu tamanho e extensão. Sem conexão administrativa válida
com o WordPress da T.I. Machado, a criação do link público não
pode ser considerada executada.

## Instalação no PC — atalhos e inicialização

A QA anterior (#184) gerava EXE/MSI sem a opção explícita para criar
atalhos do aplicativo. Isso pode deixar o instalador visível sem um
atalho para **executar** o Brother Matrizes. A correção define
`windows.menu = true`, `menuGroup = "T.I. Machado"` e
`shortcut = true` no `build.gradle.kts`.

O CI desta revisão deve verificar **após instalar o MSI**:
- atalho no menu Iniciar e na Área de Trabalho, apontando para o
  `Brother Matrizes.exe` realmente instalado;
- abertura real da interface Compose a partir desse executável;
- limpeza pela desinstalação MSI.

Essa validação automatizada **não comprova o comportamento do instalador
EXE em todos os PCs**. Em um equipamento autorizado de homologação, se a
versão antiga QA 0.1.0 já estiver instalada, desinstalá-la pelas
Configurações do Windows antes de instalar a revisão corrigida. Não
executar EXE e MSI de instalação simultaneamente e não desabilitar
SmartScreen, antivírus ou controles de segurança para prosseguir.

Se a janela não abrir mesmo pelo atalho, registrar a edição do Windows,
versão/arquitetura, caminho da instalação, mensagem exibida e se o processo
fica ativo no Gerenciador de Tarefas. A ausência do atalho e uma falha de
inicialização são diagnósticos diferentes.

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
- **Importação seletiva de ZIP local (QA):** lista entradas DST/PES/JEF,
  exige seleção explícita, não extrai caminhos nem escreve arquivos do ZIP
  no disco e usa o parser oficial. Limites de 128 MB/300 entradas/256 MB
  descompactados; cada matriz aceita fica sujeita ao limite de 8 MiB.
  Importação RAR/RAR5/7Z, fontes e paridade do importador mobile ainda pendentes.
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

## Backup e recuperação local de projetos FLP (QA)

Na área **Biblioteca**, a pessoa pode escolher uma pasta e criar um novo
`Brother-Matrizes-Backup-...flp` a partir da matriz aberta, usando o
`ProjectCodec` oficial do Android. Os arquivos já existentes nunca são
sobrescritos. Se houver camadas de letras Windows ainda compatíveis com o
documento, o backup preserva também o par `.bmlayers`. O restaurador
lista apenas backups da pasta explicitamente escolhida, verifica conteúdo
e associação das camadas (quando presentes) e recupera o projeto apenas
em memória. Arquivos fonte, projetos do Android e dados da conta não
são modificados. Não é backup em nuvem, não preserva histórico de edição
da sessão e não substitui a homologação Windows.

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
executa o `Brother Matrizes.exe` desse pacote, sem abrir a interface,
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
verifica renderização básica com matriz, e exige pelo menos 80 pixels
com a cor dourada esperada na região de desenho da amostra sintética.
Esse limiar foi conferido contra capturas reais da QA #171, em que a janela
vazia não exibe esses pixels e a prévia sintética possui centenas deles.
É um smoke direcionado ao desenho, sem validar a qualidade de costura.

Uma terceira etapa usa `--ui-wheel-smoke-test` e envia uma rolagem
de mouse pelo `java.awt.Robot` para a prévia da matriz sintética.
O CI exige que a largura e a altura visíveis do bordado cresçam pelo
menos 10%, registra o antes/depois em PNG e produz o marcador
`BROTHER_WINDOWS_UI_WHEEL_ZOOM_OK`. Esse é um teste automatizado
de um gesto real no desktop virtual, **não substitui a verificação física
de mouse, DPI, desempenho ou acessibilidade**, nem comprova precisão
de costura ou compatibilidade com PE-DESIGN 11.

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

## Atalhos do canvas no Editor Windows (QA)

Clique na prévia dentro de **Editor** para ativar `Ctrl+Z` (desfazer),
`Ctrl+Y` ou `Ctrl+Shift+Z` (refazer), `Ctrl+0` (ajustar),
`Ctrl++` (ampliar) e `Ctrl+-` (reduzir). O foco fica restrito à
prévia, evitando capturar atalhos de edição de campos de texto.
Desfazer/Refazer usam o mesmo histórico reversível dos botões e não
gravam dados. O canvas ainda exige homologação interativa em PC físico.

## Desenho livre no Windows (QA)

Nova área **Desenhar** com canvas para mouse, desfazer o último traço e
limpar. Geração de uma matriz nova via **VectorMatrixGenerator oficial do
Android**, compartilhado em tempo de compilação com o módulo Windows.
A largura, o estilo (ponto corrido/Satin) e o bastidor são selecionáveis.
Os limites de pontos, geometria e bastidor são verificados antes de
aceitar a matriz. Nenhum arquivo do usuário é sobrescrito. Esta primeira
etapa ainda não inclui importação SVG nem paridade completa com a tela
Android. Testes físicos de costura permanecem obrigatórios.

## SVG no módulo Desenhar (QA)

Importação seletiva e local de arquivos **.svg** até 2 MB, usando o
parser `SimpleSvgParser` do Android compartilhado no build Windows.
Bloqueia arquivos ativos ou com referências externas (script, imagem,
DTD/entidade, evento e href), valida quantidades e dimensões, apresenta
prévia em canvas nativo Compose, sem executar SVG, e permite
gerar matriz **nova** com o gerador de pontos oficial. Limita-se ao
subconjunto de formas suportadas pelo parser Android; não abre
imagens raster dentro de SVG, não faz download de recursos e não
sobrescreve o arquivo original.

## SVG: editar caminhos no canvas (QA)

Clique sobre uma linha SVG para selecionar um caminho individual,
arraste para movimentá-lo, use Desfazer/Refazer SVG (oito revisões)
e Restaurar SVG importado. A geometria modificada é utilizada apenas
ao gerar uma nova matriz pelo motor vetorial Android. O arquivo
SVG original não é sobrescrito. A sessão vetorial não é persistida
como projeto editável ainda e requer homologação no Windows.

## Projeto SVG reeditável: FLP + .bmvector (QA)

A área Desenhar → SVG permite salvar novos pares `.flp` (codec v11 do
Android, pontadas) e `.bmvector` (caminhos vetoriais, largura, estilo,
bastidor, cor e SHA-256 do FLP). Nunca sobrescreve destino existente.
Ao reabrir, valida ambos os arquivos, recalcula bordado com o motor
oficial e compara todos os pontos antes de liberar objetos. O FLP
também abre isoladamente no Android, porém somente com as pontadas.
O arquivo SVG original não é modificado e o histórico de sessão
não fica persistido. QA não comercial, ainda sem validação física.

## Persistência de traços desenhados à mão — QA

**Desenhar → Desenho livre** oferece **Salvar FLP + traços** e
**Abrir FLP + traços**. São criados dois arquivos sem sobrescrita:
FLP v11 oficial e sidecar `.bmdraw` contendo traços normalizados
com SHA-256 vinculando ambos os arquivos. Ao abrir, o programa
regenera a matriz com o motor Android e compara todas as pontadas
antes de disponibilizar a edição no canvas. Um FLP isolado continua
legível no Android sem traços reeditáveis. Este recurso não
equivale à homologação física nem à paridade total do mobile.

## Cores e histórico de desenhos (QA)

A paleta de dez cores foi sincronizada com a tela Android 1.11.8.
O preview usa ARGB opaco e as cores são passadas ao motor oficial,
além de restauradas ao abrir pares FLP + arquivos auxiliares.
Desenho livre suporta oito revisões de Desfazer e Refazer, inclusive
limpeza da tela; nenhum arquivo existente é alterado.

## Smoke gráfico da área Desenhar no Windows (QA)

A automação de QA abre a tela Desenhar em duas execuções isoladas:
1. Desenho livre: o `Robot` do Windows realiza um traço de mouse,
   capturado antes/depois para comprovar mudança real no canvas.
2. SVG: uma forma vetorial sintética é interpretada pelo parser
   compartilhado com o Android e precisa produzir linhas visíveis.
As PNGs e os relatórios são armazenados somente no artefato
`brother-windows-ui-smoke-QA-NAO-COMERCIAL`; não entram na versão
comercial Android. Não representa homologação da bordadeira.

## Importar ZIP / 7Z / RAR / RAR5 (Windows QA)

A Biblioteca oferece inventário e abertura seletiva de matriz
DST/PES/JEF de pacote local, sem extrair caminhos ao disco. O motor
Android valida cada matriz após a leitura; os arquivos originais não
são modificados. Bibliotecas e limites de memória equivalentes
à rotina Android foram aplicados. Sem senha, fontes, importação
múltipla e homologação de todos os pacotes RAR5 nesta fase.

## Importar fontes de pacotes (QA)

Em **Fontes → Importar fonte TTF/OTF de ZIP / 7Z / RAR**, selecione
o pacote, escolha uma entrada, confira sua validade e indique onde
**salvar somente essa fonte como arquivo novo**. Nada é instalado
no Windows e não há sobrescrita de arquivos. A fonte passa pela
validação Java2D já usada no preview local e, após ser salva,
pode ser usada para gerar nome Satin em QA. Não há importação
em lote, senhas ou integração com licenciamento Android.
