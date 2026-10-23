# Decisão técnica — busca visual em grupos Telegram

Data: 2026-10-23
Estado: **bloqueado pelos mecanismos oficiais de acesso, ainda não implementado**

## Critério de aceite do produto
Quando o usuário escolher ou fotografar um bordado, o Brother Matrizes deve comparar automaticamente essa imagem com as publicações dos grupos Telegram dos quais ele participa, encontrar a matriz correspondente (se publicada e acessível) e apresentar o arquivo/mensagem de origem, sem solicitar palavras-chave.

Também foram fixados os requisitos de manter a pesquisa por texto com as lupas e os fluxos PES/DST/JEF, não exigir uma segunda autenticação Telegram e preservar assinatura/instalação da versão comercial.

## Limitação técnica comprovada
1. O Telegram instalado é outro aplicativo Android, com sessão e dados isolados. Detectá-lo ou abrir um link `tg:` não fornece acesso ao seu banco de mensagens, lista de grupos, fotos ou documentos.
2. A pesquisa oficial do Telegram disponibiliza **consultas por texto** e **filtros de mídia** para clientes autorizados, e não um endpoint de consulta por imagem.
3. Google ML Kit OCR reconhece **texto na foto**; a classificação genérica identifica possíveis assuntos. Esses recursos não conseguem por si só correlacionar um bordado com arquivos PES/DST/JEF em grupos que o Brother Matrizes não pode acessar.
4. O PR #199 implementa **busca por texto assistida por foto**, não uma busca reversa nos grupos. Seu APK de QA não valida a funcionalidade solicitada. **Não mesclar como Brother Vision Search / Google Lens.**
5. O PR #198 tentou acesso direto via TDLib com **autenticação própria da conta Telegram**, mas esse requisito de segundo login foi explicitamente rejeitado. Não reintroduzi-lo de forma silenciosa.

Fontes oficiais:
- https://core.telegram.org/api/search
- https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1search_chat_messages.html
- https://core.telegram.org/api/links
- https://core.telegram.org/bots/faq

## Caminhos tecnicamente realizáveis, mas com concessões
- **Sessão própria autorizada (TDLib):** depois da autenticação do Telegram dentro do Brother Matrizes, uma busca visual pontual poderia examinar as mensagens e fotos dos grupos acessíveis à sessão, respeitando conteúdo protegido e as regras Telegram. Não há compartilhamento automático da sessão instalada; alguns métodos de login podem pedir telefone, código, e-mail ou 2FA.
- **Bot com participação dos grupos:** administradores precisariam autorizar/incluir um bot nos grupos, com escopo de mensagens explicitamente permitido. Ele não ganha retroativamente todo o histórico dos grupos, não representa todos os grupos que cada usuário participa, e a indexação de imagens exige consentimento/licenças apropriados.
- **Mídia fornecida voluntariamente pelo usuário/titular:** imagens ou arquivos exportados/compartilhados explicitamente para o aplicativo poderiam compor um índice local autorizado. Não abrange automaticamente grupos Telegram.

Nenhuma dessas alternativas satisfaz simultaneamente **busca visual automática em todos os grupos privados + nenhuma autenticação/consentimento adicional + somente Telegram instalado**.

## Segurança e release
- Manter lupas e pesquisa por texto comercial intactas.
- Não afirmar que uma publicação relacionada ao texto é a matriz encontrada.
- Não induzir o usuário a instalar APK como se ele já efetuasse a busca reversa.
- Não usar Accessibility, scraping de arquivos privados do Telegram, códigos/credenciais de terceiros ou contorno de mídias protegidas para simular acesso aos grupos.
- A evolução do motor visual só deve ser integrada à versão comercial após uma fonte de mídias **autorizada**, testes de correspondência com matrizes reais, proteção dos usuários, validação em Android real e preservação da assinatura atual.

## Estado de GitHub em 2026-10-23
- PR #196: protótipo por palavras e biblioteca pessoal, encerrado.
- PR #197: catálogo corporativo autorizado, encerrado.
- PR #198: cliente TDLib com autenticação própria, encerrado porque segundo login não foi aceito.
- PR #199: busca por descrição com OCR e Telegram instalado, **compila**, mas não cumpre busca reversa; mantido como experimento separado, sem publicação comercial.
