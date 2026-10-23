# Brother Vision — busca por imagem em grupos da própria conta Telegram

## Requisito confirmado pelo responsável do produto
O usuário **não precisa possuir um canal ou cadastrar um catálogo**. Depois de conectar sua própria sessão Telegram e autorizar a pesquisa, o Brother Matrizes poderá buscar **somente nos grupos aos quais essa conta tem acesso**, respeitando conteúdos e restrições de grupo, sem pedir palavras-chave. Deve exibir apenas correspondências encontradas, com prévias/origem e opções de abrir a postagem acessível. Não inferir que existir uma foto implica existir um arquivo PES/DST/JEF.

## Limitação oficial e de privacidade
- Ter o Telegram instalado ou entrar no Brother Matrizes pelo Google **não** dá acesso à lista de grupos de outra aplicação. A TDLib exige um `api_id`/`api_hash` pertencente à T.I. Machado e uma **autorização Telegram separada feita pelo usuário**.
- A TDLib fornece busca de mensagens por texto e filtros por tipo de mídia, mas **não** fornece uma API global de busca reversa de imagem.
- Os Termos da API e de licenciamento de conteúdo restringem o uso de material do Telegram por sistemas de IA/ML, indexação e agregação sem consentimentos específicos dos envolvidos. **Pertencer ao grupo não confere consentimento coletivo para uma IA indexá-lo.**
- Uma primeira opção de baixo impacto é **comparação matemática efêmera** de miniaturas de mensagens que o cliente legitimamente pode visualizar (dHash 64-bit; não é sistema de IA). Sem varrer automaticamente grupos sem consentimento, sem scraping em servidor, sem persistir conteúdo de terceiros, sem contornar proteção de conteúdo, limites ou exclusão de mensagens.
- DHash encontra fotos iguais ou próximas, mas **não** encontra qualquer desenho em fotos com outra perspectiva como o Google Lens. O percentual é um sinal matemático de distância, não certeza de matriz.
- Em chats secretos, mídias autodestrutivas/protegidas ou onde o conteúdo não estiver acessível, **não pesquisar**. Conteúdo de grupo privado não tem permalink público inventado.
- O nome da aplicação não pode aparentar ser um aplicativo oficial do Telegram.

Fontes oficiais: https://core.telegram.org/api/terms ; https://telegram.org/tos/content-licensing ; https://core.telegram.org/tdlib/getting-started ; https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1search_chat_messages.html

## Implementado na branch (somente motor isolado)
- `TelegramGroupVisualSearch` aceita uma fonte de mídia vinculada a sessão Telegram, grupos selecionados com confirmação explícita e foto de entrada; compara miniaturas em memória com dHash; não coleta grupos sem permissão.
- Limites: no máximo 20 grupos escolhidos, 60 miniaturas recentes por grupo, 8 resultados e mínimo heurístico de similaridade 80%.
- Exige `isMember`, rejeita secret chats e retornos de outro grupo; valida permalinks públicos; aceita cancelamento de corrotina.
- Testes JUnit cobrindo consentimento, privacidade, pertinência, não invenção de resultados e links.

## NÃO implementado — bloqueio real de entrega
- Ainda falta adicionar cliente Android TDLib com credenciais da aplicação Telegram da T.I. Machado e fluxo de autenticação do usuário dentro do próprio Android (telefone, código e eventual verificação em duas etapas).
- Ainda falta adaptar mensagens TDLib (foto, documento com imagem, fonte) ao `TelegramGroupMediaSource`, observando downloads limitados, sem persistência de thumbnails e respeitando proteção de conteúdo.
- Ainda falta a tela visual com seleção de grupos e consentimento, a progressão da busca e a navegação para o resultado.
- Sem isso, **não existe APK funcional de busca nos grupos**. O motor isolado não deve ser apresentado como a funcionalidade pronta ou publicado comercialmente.
- Não reutilizar backend do PR #197, que procura catálogo de terceiros e foi encerrado. Não reutilizar o PR #196 de etiquetas de imagem.

**Segurança de release:** manter versão comercial inalterada até TDLib/login/QA em aparelho e avaliação dos termos. Nunca pedir que o usuário desinstale a versão oficial. Nenhum token, número de telefone ou código Telegram deve ser enviado a WordPress, Supabase ou T.I. Machado.
