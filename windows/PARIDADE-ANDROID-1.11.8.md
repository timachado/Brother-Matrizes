# Brother Matrizes para Windows — paridade obrigatória com o Android 1.11.8

**Fonte oficial:** `app/src/main/java/com/timachado/brothermatrizes/MainActivity.kt`,
`app/build.gradle.kts` (versionName 1.11.8, versionCode 195)
e APK comercial assinado fornecido pelo proprietário em 05/11/2026.
SHA-256 do APK recebido: `3c51947aaf5337df517b3328686103adb2ae5cf8c1f1f2b9bfd9a95ee8193a90`.
A seção `META-INF/version-control-info.textproto` do APK identifica o commit
**`7540a056c564b75a94ca47b99217f4e5b5afa091`**, confirmado no GitHub
como `release(v1.11.8): PUBLICAR_V1_11_8_APROVADO_PELO_PROPRIETARIO`.
Os arquivos `MainActivity.kt` e `app/build.gradle.kts` dessa revisão foram
comparados por SHA com os mesmos arquivos na branch Windows e **coincidem**.
Essa revisão deve servir como baseline funcional de referência.


**Objetivo aprovado:** aplicativo Windows **completo** e adaptado para mouse,
teclado, monitor e arquivos locais, com todas as funções do mobile. **Não**
é apenas um visualizador, um emulador Android, um instalador ou a QA portátil.

**Estado real:** a implementação Windows Desktop 0.1.0 ainda NÃO atende a
essa paridade. Uma compilação aprovada pelo GitHub Actions significa somente
que os componentes explicitamente testados passaram, **não** que o produto
Windows esteja completo nem comercialmente homologado.

## Paridade das 16 rotas reais do Android

| Rota Android 1.11.8 | Requisito Windows completo | Situação Windows |
| --- | --- | --- |
| `Home` / `HomeScreen` | Início, ações, recentes, atalhos e comunicação visual da versão oficial | **Parcial** — entrada desktop com marca, slogan e atalhos da Home oficial; navegação por 7 áreas e recentes locais; ainda não é a tela mobile completa |
| `CreateName` / `CreateNameScreen` | Mesmas fontes, opções e resultado do criador de nomes mobile | **Parcial** — geração básica pelo motor compartilhado |
| `CreateMonogram` / `MonogramScreen` | Estilos, ajustes, prévia e fluxo completo de monogramas | **Parcial** — motor compartilhado, UI simplificada |
| `CreateDrawing` / `CreateDrawingScreen` | Criação/desenho e controles equivalentes adaptados a mouse | **Parcial** — desenho livre e SVG local, seleção/movimento por caminho, histórico e persistência opcional FLP + .bmvector (QA); paridade total pendente |
| `FontLibrary` / `FontLibraryScreen` | Fontes internas, importadas e gestão conforme regras do mobile | **Parcial** — TTF/OTF e gerador Satin em QA |
| `ProjectLibrary` / `ProjectLibraryScreen` | Projetos, recentes, favoritos, enviados, backups e biblioteca | **Parcial** — arquivos locais, FLP, recentes, favoritos e backup/recuperação local de projeto FLP (QA); enviados, backup de conta e biblioteca remota pendentes |
| `TelegramSearch` / `TelegramMatrixSearchScreen` | Fluxo de busca/importação e controles de conta do Android | **Pendente** |
| `ArchiveImport` / `ArchiveExtractorScreen` | Inventário e importação segura ZIP, RAR/RAR5 e 7Z de fontes/matrizes | **Parcial** — ZIP local seletivo para DST/PES/JEF em desenvolvimento |
| `Account` / `AccountHostScreen` | Login, conta, direitos/licenciamento e configurações seguros | **Pendente** — não reutilizar tokens Android |
| `Community` / `BrotherCommunityScreen` | Ajuda, comunidade, suporte e ações correspondentes | **Pendente** |
| `Transfer` / `MachineTransferScreen` | Transferência/compartilhamento para máquina compatível | **Pendente** — exportação de arquivo não é transferência |
| `Viewer` / `ViewerScreen` | Todas as visualizações, bastidores e modos de prévia | **Parcial** — prévia técnica e bastidores |
| `Simulator` / `SimulatorScreen` | Fluxos, modos e reprocessamento equivalentes ao Android | **Parcial** — sequência manual sem simulação completa |
| `Converter` / `ConverterScreen` | Formatos, exportação, compartilhamento e verificações equivalentes | **Parcial** — conversão local DST/PES/JEF |
| `NameEditor` / `NameEditorScreen` | Editor de nomes, objetos, controles e histórico | **Parcial** — letras separadas, seleção e arrasto individual pelo mouse em composição com camadas verificadas (QA) |
| `Editor` / `EditorScreen` | Editor completo: ferramentas, propriedades, camadas e acabamento | **Parcial** — transformações, cores, Desfazer/Refazer, atalhos de teclado e arrasto da matriz inteira em memória (QA) |

**Nenhuma rota está declarada 100% paritária.** Recursos de Android não
compatíveis diretamente com Windows (Intents, Storage Access Framework,
permissões, WorkManager, Firebase/FCM, serviços de login) precisam de
adaptadores específicos com equivalência funcional e testes, nunca cópia
irrefletida. As políticas de assinatura, conta e importação devem continuar
seguras e consistentes entre plataformas.

## Identidade visual Android 1.11.8 no Windows (QA)

O Windows utiliza agora as cores exatas `Fio*` do Android
(`BrotherMatrizesTheme.kt`): fundos, superfícies, texto, dourado,
dourado suave e erros, nos **modos escuro e claro**. Os esquemas
Material 3, formas arredondadas e tipografia também partem da versão
Android, em um tema Desktop isolado. O cabeçalho exibe a marca
**Brother / Matrizes**, e o usuário pode alternar escuro/claro na
sessão. A escolha ainda não persiste após fechar o aplicativo.

Esta etapa implementa **identidade cromática**; não significa que as
16 telas, a distribuição de controles e os recursos específicos
do Android já tenham paridade visual ou funcional. O símbolo oficial
do aplicativo permanece no ICO do executável; a renderização do
mesmo recurso gráfico dentro de todas as telas ainda será auditada.

## Navegação Desktop — primeira etapa implementada

A interface de homologação passou a separar as funções já existentes
em oito áreas: **Início, Visualizar, Criar nome, Desenhar, Monogramas, Fontes,
Editor e Biblioteca**. A prévia técnica permanece visível à esquerda,
enquanto as ferramentas de cada área ocupam o painel lateral; as seções
não usadas deixam de ficar empilhadas em uma única rolagem.

Os módulos Android **Conta, Comunidade, Telegram, Transferir**
e outros fluxos ainda não portados não são oferecidos como botões
funcionais nem descritos como concluídos. Criar uma aba não comprova
paridade: continuam necessários portadores de serviços, persistência e
testes funcionais por recurso.

## Retomada de arquivos recentes (QA)

A área **Início** agora mostra até seis recentes, com armazenamento
limitado a 12 caminhos locais previamente escolhidos pela pessoa nos
fluxos de abrir DST/PES/JEF, abrir FLP ou selecionar arquivos da biblioteca.
A reabertura só acontece após clique; o app não lê o conteúdo desses
arquivos na inicialização, não procura arquivos em outras pastas e não
envia matrizes ou caminhos para a rede. Arquivos ausentes ou não
permitidos deixam de aparecer, e **Limpar arquivos recentes** apaga
a preferência local no Windows.

A lista é mantida somente no perfil do usuário da máquina, sem
sincronização com a biblioteca comercial do Android. Não representa ainda
a paridade de favoritos, backups, projetos enviados ou biblioteca remota.

## Favoritos locais da biblioteca (QA)

A tela **Biblioteca** permite marcar e desmarcar arquivos DST, PES, JEF
e FLP previamente selecionados. O filtro **FAVORITOS** combina com a
pesquisa por nome dentro da pasta já autorizada, sem indexar outras
pastas ou acessar a rede. Em **Início**, os favoritos persistidos podem
ser reabertos explicitamente com um clique. **Limpar favoritos** apaga
somente referências guardadas no perfil do Windows, jamais os arquivos.

São armazenados até 16 caminhos válidos (sem conteúdo de matriz,
credenciais ou chaves); arquivos removidos ou que excedam os limites
de importação deixam de aparecer. Essa solução NÃO sincroniza
favoritos da conta Android nem substitui o fluxo completo de projetos,
backups e compartilhamentos do aplicativo mobile.

## Critérios para a primeira versão comercial Windows

1. Todas as 16 rotas acima com fluxos equivalentes ao Android oficial,
   incluindo estados vazios, falhas, importação e persistência.
2. Usar/reaproveitar o mesmo motor de bordado da versão Android, com
   testes de regressão cruzada DST/PES/JEF/FLP, cores, geometrias e arquivos.
3. Layout Windows real: navegação, área de bordado ampla, painéis ajustáveis,
   rolagem/zoom, mouse, teclado e uso em resoluções de PC.
4. Conta, armazenamento, limites comerciais e atualizações implementados
   com autorização no backend; sem credenciais embutidas no programa.
5. Homologação física com PC real e, quando disponível, PE-DESIGN 11
   licenciado e bordadeira de teste; testes automáticos não substituem isso.
6. Instalador e portátil com ícone/nome oficiais, distribuição segura,
   assinatura de código e plano de manutenção/atualizações.
7. Nenhuma mudança de produção em Android ou clientes existente sem
   regressões e aprovação explícita do proprietário.

## Progresso técnico comprovado e limitações

O Windows já reutiliza parsers, geradores, transformações e o codec FLP
do Android. Possui criação de nomes/monogramas, edição geométrica parcial,
biblioteca local, prévia e ZIP portátil em QA. Há cobertura automatizada
de inicialização, instalação e exportação. **Esses testes não certificam
paridade de funcionalidades.** O APK Android é referência verificável do
produto oficial, não um EXE que possa ser convertido diretamente.

### Regra de comunicação e liberação

Não anunciar `Windows completo`, `lançamento comercial`, `100% Android`
ou `PE-DESIGN 11 validado` enquanto as respectivas verificações faltarem.
O PR #225 permanece draft, sem merge ou distribuição pública, até o
cumprimento dos critérios. O ZIP QA não substitui o aplicativo pretendido.

## Backup FLP local no Windows (QA, sem nuvem)

A Biblioteca oferece **Criar backup do projeto atual (FLP)** e **Recuperar backup da pasta escolhida**. A pessoa escolhe explicitamente a pasta; o app não indexa o disco ou acessa serviços remotos. Cada backup é gravado com nome novo `Brother-Matrizes-Backup-<UTC>-<id>.flp`, pelo **ProjectCodec v11 oficial do Android** e com proteção contra sobrescrita. Ao restaurar, abre somente em memória e mantém os arquivos existentes intactos.

Quando o projeto corresponde às letras reeditáveis criadas no Windows, grava também o `.bmlayers` vinculado por SHA-256 e o exige íntegro para recuperar os objetos. Sem sidecar, o FLP comum continua legível; a cópia não conserva o histórico Desfazer/Refazer. Esta função NÃO equivale ao backup de conta, biblioteca em nuvem ou sincronização mobile. Requer QA CI e homologação física antes da distribuição.

## Home Desktop alinhada à Home Android (QA)

A área central do Início exibe a marca **Brother Matrizes**, slogan
oficial, cartão destacado **Criar nome**, botão **Abrir matriz**, e
atalhos para fontes, monogramas, editor e biblioteca. O fluxo de
abertura DST/PES/JEF é o mesmo do botão superior, usando os parsers
oficiais, sem duplicação de lógica ou sobrescrita de arquivos. A
barra lateral mantém recentes e favoritos, em vez de duplicar os
botões principais. Não são apresentados como utilizáveis os serviços
de Conta, Comunidade, Transferência e Desenho ainda pendentes.
A adaptação não afirma paridade integral da Home ou das 16 rotas.

## Atalhos de teclado do editor (QA)

Apenas no módulo **Editor**, um clique na área de prévia permite usar
`Ctrl+Z` para Desfazer, `Ctrl+Y` ou `Ctrl+Shift+Z` para Refazer,
`Ctrl+0` para ajustar a ampliação, `Ctrl++` para ampliar e
`Ctrl+-` para reduzir. Os atalhos utilizam o mesmo
`DesktopEditTimeline` dos botões e nunca gravam arquivos em disco.
A verificação de teclas fica isolada no `DesktopEditorKeymap`, com
testes unitários. Quando o foco permanece em caixas de texto, as
teclas não são capturadas pelo canvas, preservando o fluxo de digitação.
A edição direta de objetos importados e a paridade total do editor
continuam pendentes. A interface requer homologação física.

## Mover matriz inteira com mouse (QA)

Na aba Editor, uma opção explícita ativa o arrasto da **matriz completa**.
O movimento ocorre visualmente no canvas durante o gesto. Ao soltar,
a distância em pixels é convertida para décimos de milímetro conforme
a escala e zoom atuais, validada em até 200 mm por eixo e aplicada pelo
`DesktopMatrixService.edit` usando o mesmo `MatrixEditor` do Android.
O histórico recebe uma única transformação reversível. Em caso de
erro/gesto cancelado, a posição visual retorna, sem gravar arquivo.
Não permite arrasto global em sessão com letras editáveis para evitar
dessincronizar as camadas `.bmlayers`: ali usam-se os controles
individuais de Criar nome. O arrasto **não representa seleção nem
movimentação de objetos importados individualmente**. Requer CI e
homologação interativa no Windows antes da distribuição comercial.

## Seleção individual de letras com mouse (QA)

Projetos gerados como **composição individual de letras** ou reabertos
com arquivo auxiliar `.bmlayers` válido podem selecionar uma letra
clicando sobre seus limites reais na prévia das áreas Criar nome/Editor.
Cada caixa de seleção é obtida da mesma geração de glifos com
`MatrixEditor` oficial e só fica ativa após comprovação de que as
pontadas regeneradas são exatamente iguais ao documento carregado.
No Editor, o botão **Editar propriedades da letra** leva aos controles
existentes de movimento, escala e rotação por objeto em Criar nome.
Clique apenas seleciona: não grava em disco nem altera a matriz. A
seleção destaca o objeto e respeita pan e zoom.

Não afirma extração de objetos ou letras de arquivos DST/PES/JEF
achatados. Seleção por mouse ainda exige teste interativo físico; o
sistema não substitui a versão comercial Android.

## Arrasto independente de letras (QA)

Em uma composição local de letras com camadas `.bmlayers` verificadas,
a prévia permite **clicar e arrastar somente uma letra**, nos módulos
Criar nome e Editor. A moldura de seleção acompanha o ponteiro; ao
soltar o mouse, a posição em pixels é convertida em deslocamento de
0,1 mm pelo `DesktopPreviewNavigation.viewport`. O
`DesktopLetterComposer.update` valida o limite de até ±100 mm por
letra; a renderização completa é refeita pelo motor compartilhado com
Android e uma única revisão é registrada no `DesktopLetterSession`
(Desfazer letra/Refazer letra). O arquivo original não é regravado.

Em fundo vazio, o arrasto continua sendo navegação visual quando
o zoom é maior que 1x. O zoom pela roda permanece independente.
Não permite manipulação individual de objetos extraídos de DST/PES/JEF
achatados. Não é homologação de compatibilidade comercial.

## Desenhar livre — primeira etapa Windows QA

A navegação agora inclui **Desenhar**, com canvas para traços usando mouse,
**Desfazer traço** e **Limpar**. Largura, estilo de ponto (corrido/Satin) e
bastidor podem ser escolhidos antes de **Gerar nova matriz de bordado**.
O serviço valida de 1 a 40 traços, até 300 coordenadas por traço e até
6.000 coordenadas em total, limites dimensionais e geometria finita.
Somente após os controles a versão Windows chama o
`VectorMatrixGenerator` real do Android, compilado dos mesmos fontes,
com `MachineFinishing`, `HoopValidator` e
`EmbroideryIntegrity`. O resultado é uma **nova** matriz em memória,
com integridade e enquadramento verificados; salvar/exportar continua
sendo ação explícita nas áreas existentes, sem modificar arquivos
anteriores.

**Ainda faltam:** importar SVG, cores da paleta completa, prévia dinâmica
das pontadas na tela Desenhar, persistência de traços como objetos,
histórico de desenho independente e equivalência visual integral.
A opção Satin requer validação de bordadeira real. Não distribuir
comercialmente antes de CI e homologação.

## Importação SVG vetorial local (QA)

A área Desenhar agora possui modos independentes **Desenho livre** e
**SVG**, importador local com limite de 2 MB (sem links simbólicos)
e guarda contra DTD/entidades, scripts, imagens externas, eventos e
referências `href`. O arquivo é aceito apenas após passagem pelo
`SimpleSvgParser` oficial do Android e verificação de geometria:
até 96 caminhos, 800 pontos por caminho e 6.000 no total, finitos,
dentro dos limites dimensionais definidos. A prévia desenha somente
linhas convertidas em dados vetoriais; não executa conteúdo XML,
scripts, imagens ou recursos externos.

A operação **Gerar nova matriz de bordado** usa o mesmo
`VectorMatrixGenerator`, `HoopValidator` e verificador de
integridade do Android. O SVG original jamais é gravado ou modificado.
Os dois modos preservam sua entrada ao alternar; falha de importação
descarta apenas o SVG inválido. Ainda faltam edição por objeto vetorial,
salvar desenho-fonte editável, opções completas do Android e testes de
bordado físico. Testar CI e PC real antes de distribuir.

## Seleção e arrasto de caminhos SVG (QA)

No modo **Desenhar → SVG**, após importação segura e validação dos
caminhos pelo parser Android, cada objeto vetorial pode ser selecionado
clicando próximo à sua linha e deslocado com o mouse na prévia. O teste
de seleção usa distância até segmentos, não apenas uma caixa que poderia
selecionar objetos distantes. A transformação é feita sobre uma
cópia de `VectorArtwork`, com validação de coordenadas e limites;
o arquivo SVG original permanece intocado. O histórico temporário
`DesktopSvgEditSession` mantém até oito movimentos reversíveis,
incluindo desfazer/refazer e opção de restaurar o vetor importado.
A exportação gera **nova** matriz a partir da geometria editada,
empregando o `VectorMatrixGenerator` oficial do Android.

Esta etapa não salva objetos vetoriais em FLP, não edita pontos de
bordado de arquivos importados nem entrega paridade visual integral
do Android. Os testes automatizados verificam seleção, transformação,
proteção do original, histórico e geração de pontadas; faltam
testes manuais no Windows e na bordadeira.

## Persistência de desenhos vetoriais FLP + .bmvector (QA)

No modo Desenhar → SVG, **Salvar FLP + vetores** grava **dois arquivos novos**
sem substituir arquivos existentes: `.flp` v11 oficial Android (pontadas,
cores e bastidor pelo `ProjectCodec`) e `.bmvector` Windows com caminhos,
identidade de origem, escala, estilo, cor e bastidor. O sidecar binário
versionado (v1, no máximo 96 KiB) contém o SHA-256 dos bytes exatos do FLP.
A gravação prepara e valida tudo antes de escrever, usa CREATE_NEW e
tenta reverter apenas os novos arquivos em caso de erro.

**Abrir FLP + vetores** exige par íntegro, verifica SHA-256, formato,
limites de caminhos/pontos e parâmetros, decodifica o FLP pelo codec
oficial e **regenera todas as pontadas** pelo motor `VectorMatrixGenerator`
oficial do Android. Só habilita reedição após comparação integral
de geometria, cores e comandos. A abertura reinicia o histórico
Desfazer/Refazer; não reabre o XML SVG original nem modifica arquivos.
Um FLP isolado continua abrindo normalmente no Android e Windows, mas
sem controle de caminhos vetoriais.

A associação SHA-256 detecta divergências, **não autentica a origem**,
não substitui assinatura de código e não equivale a um formato vetorial
nativo lido pelo Android. O modo Desenho livre ainda não salva seus
traços como camadas editáveis. CI e homologação no PC físico pendentes.
