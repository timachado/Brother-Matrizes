# Brother Matrizes para Windows — paridade obrigatória com o Android 1.11.8

**Fonte oficial:** `app/src/main/java/com/timachado/brothermatrizes/MainActivity.kt`,
`app/build.gradle.kts` (versionName 1.11.8, versionCode 195)
e APK comercial assinado fornecido pelo proprietário em 05/11/2026.
SHA-256 do APK recebido: `3c51947aaf5337df517b3328686103adb2ae5cf8c1f1f2b9bfd9a95ee8193a90`.

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
| `Home` / `HomeScreen` | Início, ações, recentes, atalhos e comunicação visual da versão oficial | **Pendente** — há somente painel técnico |
| `CreateName` / `CreateNameScreen` | Mesmas fontes, opções e resultado do criador de nomes mobile | **Parcial** — geração básica pelo motor compartilhado |
| `CreateMonogram` / `MonogramScreen` | Estilos, ajustes, prévia e fluxo completo de monogramas | **Parcial** — motor compartilhado, UI simplificada |
| `CreateDrawing` / `CreateDrawingScreen` | Criação/desenho e controles equivalentes adaptados a mouse | **Pendente** |
| `FontLibrary` / `FontLibraryScreen` | Fontes internas, importadas e gestão conforme regras do mobile | **Parcial** — TTF/OTF e gerador Satin em QA |
| `ProjectLibrary` / `ProjectLibraryScreen` | Projetos, recentes, favoritos, enviados, backups e biblioteca | **Parcial** — arquivos locais e FLP, sem gestão completa |
| `TelegramSearch` / `TelegramMatrixSearchScreen` | Fluxo de busca/importação e controles de conta do Android | **Pendente** |
| `ArchiveImport` / `ArchiveExtractorScreen` | Inventário e importação segura ZIP, RAR/RAR5 e 7Z de fontes/matrizes | **Parcial** — ZIP local seletivo para DST/PES/JEF em desenvolvimento |
| `Account` / `AccountHostScreen` | Login, conta, direitos/licenciamento e configurações seguros | **Pendente** — não reutilizar tokens Android |
| `Community` / `BrotherCommunityScreen` | Ajuda, comunidade, suporte e ações correspondentes | **Pendente** |
| `Transfer` / `MachineTransferScreen` | Transferência/compartilhamento para máquina compatível | **Pendente** — exportação de arquivo não é transferência |
| `Viewer` / `ViewerScreen` | Todas as visualizações, bastidores e modos de prévia | **Parcial** — prévia técnica e bastidores |
| `Simulator` / `SimulatorScreen` | Fluxos, modos e reprocessamento equivalentes ao Android | **Parcial** — sequência manual sem simulação completa |
| `Converter` / `ConverterScreen` | Formatos, exportação, compartilhamento e verificações equivalentes | **Parcial** — conversão local DST/PES/JEF |
| `NameEditor` / `NameEditorScreen` | Editor de nomes, objetos, controles e histórico | **Parcial** — letras separadas e controles limitados |
| `Editor` / `EditorScreen` | Editor completo: ferramentas, propriedades, camadas e acabamento | **Parcial** — transformações, cores e Desfazer/Refazer |

**Nenhuma rota está declarada 100% paritária.** Recursos de Android não
compatíveis diretamente com Windows (Intents, Storage Access Framework,
permissões, WorkManager, Firebase/FCM, serviços de login) precisam de
adaptadores específicos com equivalência funcional e testes, nunca cópia
irrefletida. As políticas de assinatura, conta e importação devem continuar
seguras e consistentes entre plataformas.

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
