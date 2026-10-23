# Brother Vision Search — integração incremental (Android)

Status: proposta de código em branch isolada; **não é publicação comercial**.

## Entregue nesta branch
- Entrada “Pesquisar por imagem” na tela existente “Buscar Matrizes”.
- Seleção de imagem via seletor Android e recepção de imagem via Compartilhar/Enviar.
- Classificação **local** com modelo do Google ML Kit, sem upload de foto para a nuvem.
- Até três termos de pesquisa, priorizados por confiança, traduzidos quando disponíveis e **editáveis**.
- Busca final aberta no Telegram oficial com o mecanismo já existente; nenhum acesso novo aos chats.
- Importação PES/DST/JEF e respectivas cotas continuam inalteradas.
- Sem novas permissões Android, credenciais ou coleta de conversas.

## Não implementado — limitações reais
- Não é integração direta com o aplicativo Google Lens nem a API de busca reversa do Google.
- Não compara pixels com todas as imagens ou mensagens do Telegram.
- Não acessa nem percorre canais/grupos privados, não baixa arquivos automaticamente.
- Não garante que uma correspondência por palavra-chave seja a matriz exata.

O Telegram restringe extração e indexação de conteúdo para treinamento/desenvolvimento/operação de sistemas de IA:
https://core.telegram.org/api/terms
https://telegram.org/tos/content-licensing

Uma pesquisa visual real com retornos de arquivo PES/DST/JEF só deve usar catálogo **próprio/autorizado**, criado por titulares dos desenhos ou contribuições voluntárias com consentimento explícito e mecanismo de exclusão. Links para Telegram poderão ser exibidos como referências, respeitadas as permissões e termos.

## Verificação antes de publicar
1. Executar testes unitários e compilação debug, sem instalar sobre a versão oficial.
2. No Android, testar seleção de JPEG, PNG, foto grande, foto inválida e cancelamento.
3. Compartilhar uma imagem pelo Telegram/galeria para Brother Matrizes: deve abrir “Buscar Matrizes” e oferecer termos, **não cobrar cota de importação**.
4. Verificar que importar PES, DST, JEF e arquivos ZIP ainda segue o fluxo anterior.
5. Confirmar que o termo é editável e que abrir Telegram requer os mesmos controles de conta/cota anteriores.
6. Validar a interface em tela pequena e com fonte acessível.
7. Checar aprovação e testes Android antes de qualquer merge e release.

Dependência nova: `com.google.mlkit:image-labeling:17.0.9` (modelo embarcado).
