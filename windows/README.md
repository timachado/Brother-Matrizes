# Brother Matrizes Desktop — homologação Windows (NÃO COMERCIAL)

Este projeto Windows está isolado do Android comercial. O PR não modifica
`app/`, `supabase/` nem pipelines oficiais de publicação.

## Funcionalidades disponíveis na QA

- Compose Desktop com instaladores EXE/MSI gerados por runner Windows.
- Abertura local de DST/PES/JEF usando os parsers oficiais Android.
- Prévia de pontadas, cores, dimensões, dados da matriz e sequência visual
  manual. Não é simulação real de velocidade, tensão ou costura.
- Transformações geométricas (rotação, escala, deslocamento, espelhamento)
  com restauração do estado carregado.
- Nomes em fonte integrada (ponto corrido/Satin), pelo `TextMatrixGenerator`.
- Monogramas Linear, Clássico e Empilhado pelo `MonogramGenerator`.
- Biblioteca local de uma pasta escolhida explicitamente, com DST/PES/JEF/FLP.
  A pasta pode ser lembrada e esquecida nas preferências Windows; sua listagem
  nunca é refeita automaticamente na inicialização.
- Conversão DST/PES/JEF pelo `MatrixConverter` com verificação de releitura
  antes da gravação; PE-DESIGN PC é um perfil experimental.
- **Projetos editáveis .FLP nativos do Android**: abrir um projeto existente
  e salvar o estado atual como um arquivo novo, usando exatamente o
  `ProjectCodec` do Android (formato v11).
- Seleção e **pré-visualização TTF/OTF** no computador, sem instalar fontes.
  Este recurso apenas verifica o arquivo e exibe uma amostra da fonte.
  **Ainda não gera pontadas a partir de fontes TTF/OTF externas.**

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

## Segurança local

- Nenhuma importação ou upload de matrizes, projetos ou fontes à nuvem.
- Arquivos DST/PES/JEF: limite 8 MiB; FLP: 32 MiB; TTF/OTF: 12 MiB.
- Pastas listadas sem recursão, sem seguir links simbólicos e com limite de
  500 entradas; interface exibe até 60 por vez.
- A biblioteca lembra **só o caminho da pasta**; não armazena conteúdo,
  matrizes, senhas ou credenciais nas preferências.
- Gravação sempre com `CREATE_NEW`, sem sobrescrever arquivos existentes.
- Projetos FLP passam por codificação/decodificação e conferência de pontos/
  cores, matrizes convertidas passam por releitura dos parsers oficiais.
- Fontes externas são validadas por extensão, assinatura e carregamento JVM;
  a prévia NÃO equivale a autorização de exportação de bordado de fonte.

## Bloqueadores comerciais

Ainda falta a paridade integral com o Android: editor por objetos e letras,
importação TTF/OTF com conversão segura de contornos em pontadas e ensaios
Satin, biblioteca avançada, backup completo, simulador de máquina, outros
recursos aprovados, autenticação/licenciamento/cotas/planos, atualizações
protegidas e integrações Windows equivalentes.

Os geradores Android de fonte externa usam `android.graphics` e
`AndroidSkiaOutlineExtractor`, **incompatíveis diretamente com Desktop JVM**.
Por isso a UI atual só pré-visualiza a fonte; não finge que gerou Satin.

Antes de publicar: regressão Android e Windows, prova com PE-DESIGN 11 e
máquina de bordado, instalação em computador Windows real, revisão de
segurança, assinatura do pacote e aprovação comercial separada.

## Compilação

No diretório `windows/`, usando JDK 17 e Gradle 8.14:

```shell
gradle test compileKotlin
gradle packageExe packageMsi
```

Workflow `.github/workflows/brother-windows-desktop-qa.yml`:
testes e empacotamento com retenção curta dos artefatos **QA**, sem publicar
release comercial.

**Não mesclar nem publicar este PR enquanto estiver em rascunho.**
