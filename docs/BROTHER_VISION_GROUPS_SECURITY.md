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
- Limites: no máximo 10 grupos escolhidos, 25 miniaturas recentes por grupo, 8 resultados e mínimo heurístico de similaridade 80%.
- Exige `isMember`, rejeita secret chats e retornos de outro grupo; valida permalinks públicos; aceita cancelamento de corrotina.
- Testes JUnit cobrindo consentimento, privacidade, pertinência, não invenção de resultados e links.

## Integração Android acrescentada nesta etapa
- Fonte TDLib Android (Maven Central, versões pinadas 0.1.1) com login Telegram **separado**: telefone, código, eventual senha de duas etapas. Sessão permanece no espaço privado do app, sem ser transmitida à T.I. Machado.
- Lista dos grupos acessíveis na sessão, excluindo canais, chats secretos, grupos dos quais não é membro e mídias marcadas como protegidas.
- Busca TDLib de mensagens com filtro de **fotografias**, limitada às miniaturas mais recentes e tamanho controlado, sem scraping de servidores, sem ML e sem indexação permanente. Documentos com imagem ainda não são examinados.
- Tela Jetpack Compose com seleção de grupos, consentimento expresso por consulta e disparo automático após o usuário selecionar uma foto. Mostra apenas correspondências reais; links são obtidos da própria TDLib, nunca inventados. Importação PES/DST/JEF preservada.
- A compilação de homologação usa package `com.timachado.brothermatrizes.groupqa` e desativa OTA e notificações comerciais.

## Bloqueios antes de chamar este recurso de funcional
- Não há `api_id` e `api_hash` da aplicação Telegram da T.I. Machado neste repositório. Devem ser registrados em https://my.telegram.org e fornecidos **via variáveis de compilação seguras**, não por mensagens nem incluídos no GitHub.
- Nenhum login Telegram real, grupo, arquivo ou imagem privada foi usado nos testes. Autenticação adicional exigida pela plataforma (email, QR etc.) pode precisar de suporte.
- O hash de 64 bits identifica **imagens iguais/semelhantes**, não desenhos diferentes do mesmo objeto ao nível do Google Lens. Não há garantia de encontrar matriz correspondente nem o respectivo arquivo PES/DST/JEF.
- A API limita a amostra às fotos mais recentes: grupos com milhares de mensagens podem não conter a postagem procurada nessa janela.
- É necessária a avaliação dos Termos de API do Telegram antes de lançar esse recurso como ferramenta comercial. O algoritmo é **determinístico, não IA/ML**, usa consulta local pontual e não deve ser convertido em crawler ou modelo de aprendizado sobre grupos.
- Ainda são obrigatórios QA Android, testes com consentimento, cancelamento, limitações de dados e atualizações sobre assinatura comercial. **Não mesclar/publicar sem validação.**
- Não reutilizar o PR #197 (catálogo externo) ou #196 (palavras-chave/local).

**Segurança de release:** manter a versão comercial inalterada até testes em aparelho e avaliação dos termos. Nunca pedir que o usuário desinstale a versão oficial. Nenhum token, número de telefone ou código Telegram deve ser enviado a WordPress, Supabase ou T.I. Machado.
