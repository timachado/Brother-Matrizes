# DaFont e NetFontes — seletores estáveis no Windows

A interface Windows usa diretamente os endereços e as regras oficiais
\`DaFontNavigationRules\` / \`NetFontesNavigationRules\` do Android, com
pesquisa DaFont vinculada a uma reserva/finalização de cota no servidor
e NetFontes sem descontar a cota específica de busca DaFont.

Esta revisão conclui a padronização **local** dos seguintes seletores:
- Analisar TTF/OTF baixada de uma das fontes (somente leitura).
- Escolher TTF/OTF para solicitar importação autorizada pelo servidor.
- Inspecionar ZIP de fontes DaFont/NetFontes (somente leitura).
- Confirmar individualmente uma fonte do ZIP antes de qualquer reserva.

Os diálogos Swing são executados na EDT e aguardados via corrotina IO,
sem bloquear a thread Compose; sinais de operação ocupada protegem
o seletor, e não apenas a análise posterior. Cancelamento fecha o fluxo
sem importar nem consumir cotas. Nenhuma rotina de scraping, API privada
de terceiros, download automático ou autorização Pro local foi adicionada.

Testes Kotlin \`DesktopNativeFontPickerTest\` verificam os filtros TTF/OTF
e ZIP sem abrir janelas ou tocar em arquivos do cliente.

**Não libera a importação comercial padrão**: login Google e contas/cotas
Free/Pro continuam dependendo da homologação V2 em staging. Os motores
de satin/contorno permanecem aqueles já integrados ao Windows, com QA.
A titularidade/licença de cada fonte continua sendo do autor; o catálogo
não garante permissão para uso comercial de bordados.
