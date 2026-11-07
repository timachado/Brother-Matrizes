# Brother Matrizes Windows — reconstrução fiel ao Android 1.11.8

**Decisão oficial do proprietário:** 06/11/2026. A QA Windows do PR #225 foi
reprovada por diferença substancial de layout, identidade visual e funções.
Este esforço é **novo**, criado a partir de `main` e NÃO da branch rejeitada.
A versão Android comercial permanece intocável.

## 1. Fonte única da verdade

- Android **1.11.8 (versionCode 195)**, em `app/build.gradle.kts`.
- Revisão de lançamento histórica: `7540a056c564b75a94ca47b99217f4e5b5afa091`.
- Composição/navigation: `app/src/main/java/com/timachado/brothermatrizes/MainActivity.kt`.
- Home aprovada: `app/src/main/java/com/timachado/brothermatrizes/HomeScreen.kt`.
- Tema oficial: `app/src/main/java/com/timachado/brothermatrizes/ui/theme/BrotherMatrizesTheme.kt`.
- Ações, fluxos e estados devem ser derivados de cada `*Screen.kt` Android,
  não da lista de abas e dos controles da QA Windows descartada.
- Evidência visual de referência existente: teste Android
  `app/src/androidTest/java/com/timachado/brothermatrizes/PremiumHomeScreenshotTest.kt`,
  que captura a Home REAL e valida componentes em emulador. Executá-lo
  em branch isolada para obter captura da versão de referência.

**Regra:** arquivo executável compilado ≠ equivalência; só aceitar
resultados confirmados por comparação visual de telas + testes dos fluxos.

## 2. Contrato visual da Home — confirmado no código Android

Manter nesta ordem e hierarquia (sem inventar barra lateral Desktop):
1. Grid rolável Material 3 Expressive de **duas colunas**, com espaçamento
   original de 8dp horizontal / 6dp vertical e margem horizontal de 12dp.
2. Cabeçalho: símbolo BM aprovado com cantos arredondados, textos
   **Brother / Matrizes**, slogan original e sino de novidades/comunidade.
3. Card de atualizações `BrotherUpdateHomeCard` com as condições originais.
4. `PremiumHeroCard` com **fotografia aprovada**, gradiente azul-noite,
   título **O que vamos / bordar hoje?** e subtítulo original.
5. CTA primária dourada **Criar nome** em largura total, mantendo a fonte,
   tamanho, cor, opções de ponto e o destino original.
6. Grade **Ferramentas**: Biblioteca de fontes, Abrir matriz, Simulador,
   Editor, Converter, Enviar, com os mesmos ícones e lógica condicional
   quando nenhuma matriz está aberta.
7. Seção **Recente**, comportamento de limpar e CTA para criar o primeiro nome.
8. Card **Comunidade e novidades** e rodapé com versão.

### Navegação original, não a versão Desktop rejeitada

Quando estiver em rotas de primeiro nível, a barra inferior arredondada
contém **Início, Criar, Biblioteca, Fontes, Conta**, nesta ordem, com
`FioNavigationSurface` e indicador dourado. Ao adaptar o aplicativo
para monitor Windows, pode-se redistribuir **espaço** para leitura e
edição, mas não eliminar, renomear, substituir ou redirecionar
essas ações sem comprovar equivalência.

### Recursos visuais obrigatórios

A compilação Android gera os recursos reais a partir de arquivos da marca:
- `branding/brother_bm_premium_approved.webp.base64` → BM Premium aprovado.
- `branding/brother_home_hero.base64` → fotografia aprovada de bordado;
  a tarefa de `app/build.gradle.kts` gera o recurso que a Home usa.
- `branding/brother_bm_launcher_safe.webp.base64` → launcher seguro.
- Ícones vetoriais originais `app/src/main/res/drawable/ic_ms_*.xml` e
  `ic_premium_bell.xml`.

**Não trocar** esses arquivos por ícones genéricos, figuras geradas
ou um novo mockup. Verificar as licenças e a integridade dos recursos
no pipeline antes de empacotar o Windows.

### Tema

O tema não é somente dourado/azul: reutilizar a semântica de
`BrotherMatrizesTheme`, incluindo modo claro/escuro, tipografia,
arredondamentos, estados de botão, superfícies, contrastes e densidade.
Tokens principais escuros confirmados: fundo `#08131D`, superfície
`#101D28`, navegação `#102A40`, dourado `#E6BE70`,
texto `#F7F4EE`, texto secundário `#A9B3BC`.
Copiar paleta sem hierarquia, componentes e comportamentos **não**
atende a este contrato.

## 3. Inventário de paridade funcional: 16 rotas da versão oficial

| Rota Android | Arquivo fonte / área | Aceitação Windows |
| --- | --- | --- |
| Home | `HomeScreen.kt` | Mesmo cabeçalho, banner, hero, grade, recentes, CTAs, navegação e estados |
| CreateName | `CreateNameScreen.kt` | Fontes, estilos, cores, dimensões, pontos e saída equivalentes |
| CreateMonogram | `MonogramScreen.kt` | Mesmos controles e geração de monograma |
| CreateDrawing | `CreateDrawingScreen.kt` | Desenho e opções originais adaptadas ao mouse |
| FontLibrary | `FontLibraryScreen.kt` | Gestão, importação e acesso às fontes |
| ProjectLibrary | `ProjectLibraryScreen.kt` | Projetos, filtros, enviados, backup e estados de conta |
| TelegramSearch | `TelegramMatrixSearchScreen.kt` | Busca, permissões, importação e fluxos completos |
| ArchiveImport | `ArchiveExtractorScreen.kt` | ZIP/RAR/7Z, avisos, segurança, seleção e importação |
| Account | `AccountHostScreen.kt` | Autenticação, direitos e estados comerciais de verdade |
| Community | `BrotherCommunityScreen.kt` | Conteúdos, ajuda e ações oficiais |
| Transfer | `MachineTransferScreen.kt` | Meios de transferência equivalentes e validados |
| Viewer | `ViewerScreen.kt` | Modos de visualização, dados e bastidores |
| Simulator | `SimulatorScreen.kt` | Sequência e controles equivalentes |
| Converter | `ConverterScreen.kt` | Mesmos formatos, limites, erros e exportações |
| NameEditor | `NameEditorScreen.kt` | Edição, parâmetros e aplicação de nomes |
| Editor | `EditorScreen.kt` | Ferramentas, estados, histórico e acabamento |

**Critério para cada rota:** checklist visual, navegação, eventos reais,
estados sem dados, dados sintéticos, entradas inválidas, salvamento,
reabertura e comparação de pontadas quando aplicável. Nenhum item
pode ser marcado pronto com botão inerte, formulário simulado ou
apenas um preview.

## 4. Ordem de implementação e bloqueios

**Portão A — baseline:** compilar/capturar a Home oficial no Android
em ambiente de QA, preservar recursos gerados e inventariar telas.
**Portão B — UI fiel:** adaptar componentes e contratos do Android
a Compose Desktop com imagens e ícones originais; produzir screenshot
real Windows com mesma janela de referência, conferida lado a lado.
**Portão C — funções:** fazer cada ação apontar para o mesmo motor ou
adaptador Windows com testes dos estados originais.
**Portão D — distribuição:** conferir as 16 rotas, conta/licenciamento,
PE-DESIGN 11, bordadeira física, assinatura e instalação/atualização
segura antes de divulgar.

Adaptações inevitáveis de Android para Windows (URI, câmera, arquivos,
permissões, login, notificações) devem ser implementadas por **adaptadores
de plataforma**, sem reproduzir UI falsa ou reduzir escopo silenciosamente.

Não gerar/compartilhar um novo EXE/ZIP de avaliação enquanto
**Portão B** não tiver evidências visuais aceitas pelo proprietário.

## 5. Situação deste documento

Este arquivo é um **contrato de reconstrução**, não um binário nem uma
declaração de que o Windows fiel já existe. O primeiro resultado é o
isolamento da branch e a rastreabilidade das fontes; captura Android,
porting visual, testes funcionais e homologação permanecem pendentes.
