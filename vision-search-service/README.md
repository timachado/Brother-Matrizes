# Brother Lens — busca visual automática em matrizes autorizadas

**Objetivo aprovado:** selecionar/compartilhar uma imagem de bordado e receber automaticamente, sem palavras-chave nem cadastro manual de fotos pelo usuário, publicações de matrizes semelhantes **que estejam no acervo autorizado da T.I. Machado**. O usuário abre o link da publicação original do Telegram. **Não existe busca reversa global oficial no Telegram.**

## Implementação
- Android: `VisualTelegramSearchScreen` abre a galeria, inicia automaticamente a busca quando a foto é selecionada e mostra prévias e links de Telegram que vieram do servidor. O seletor original e a importação PES/DST/JEF não foram modificados. Ao compartilhar `image/*` com o app, a busca visual abre automaticamente.
- API: FastAPI / `POST /v1/search/image` (aceita imagem, valida sessão Google/Supabase, usa o mesmo modelo visual das referências e retorna até 8 resultados). Sem busca por texto, sem coleções locais pessoais. `POST /v1/catalog/items` recebe *diretamente* uma imagem fornecida pelo titular com autorização expressa, sem coletar canais do Telegram. `DELETE /v1/catalog/items/{id}` elimina a referência quando a autorização é revogada.
- Busca semântica real de imagens: OpenCLIP ViT-B-32 (vetores visuais de 512 dimensões), comparados por produto escalar de vetores normalizados. A base V1 SQLite suporta 5.000 referências autorizadas, com limite ajustável depois. Os valores são heurísticos, **não** garantias de identidade nem a certeza de um arquivo de matriz.
- Não são armazenadas as fotos enviadas pelos usuários na busca. A imagem fica apenas na memória para inferência e é descartada ao concluir. A referência de um titular é armazenada como vetor. Foto de prévia permanece no local indicado pelo titular autorizado.
- Sem scraping, sem leitura de conversas privadas, sem Bot de coleta, sem indexação ou processamento de conteúdo extraído do Telegram por sistemas de IA. O Telegram é apenas o destino da publicação informada diretamente pelo titular, com autorização de divulgação.

## Implantação — ainda NÃO efetuada
1. Obter dos criadores/distribuidores **imagens originais fornecidas diretamente** e consentimento explícito documentado para pesquisa visual e divulgação dos links das suas publicações.
2. Preparar servidor HTTPS com CPU/RAM suficientes, armazenamento persistente para `/data`, e um arquivo de pesos OpenCLIP ViT-B-32 autorizado e conferido em `/models/` (não incluso no repositório). Verificar as licenças dos pesos.
3. Configurar no servidor:
   - `BM_VISION_MODEL_CHECKPOINT=/models/model.pt`
   - `BM_VISION_DB=/data/brother-vision.sqlite3`
   - `BM_VISION_SUPABASE_URL` (URL pública do Supabase do Brother Matrizes)
   - `BM_VISION_SUPABASE_PUBLISHABLE_KEY` (chave publicável, **nunca** service_role)
   - `BM_VISION_ADMIN_TOKEN` com >=32 caracteres gerados secretamente e armazenados em secrets do servidor
4. Servir a API por HTTPS com TLS, tamanho de requisição <=4 MiB e rate limiting por IP + usuário no reverse proxy (necessário para produção). Testar `GET /health`.
5. Incluir itens pelo endpoint administrativo apenas após confirmação de titular, autorização, licença da prévia e da URL do post. Cada referência deve poder ser excluída imediatamente se o consentimento terminar.
6. Compilar um Android QA **separado**, usando `-PbrotherLensQaParallel=true -PbrotherVisionSearchUrl=https://DOMINIO_DA_API`. Esse domínio precisa existir e estar operativo. O APK de QA usa id `com.timachado.brothermatrizes.lensqa` e não deve substituir a versão comercial.
7. Para login Google na QA, a configuração OAuth precisa admitir o redirect do package QA. Até isso estar configurado, testes completos de conta nessa variante não são possíveis; **não contornar a autenticação**. Na versão comercial, o fluxo já existente é reaproveitado.
8. Só depois de testes com matrizes autorizadas reais, revisão de segurança, estimativas de custo, qualidade de correspondência, assinatura original e ensaio de upgrade, considerar mesclar na branch oficial.

## Exemplo de cadastro do titular
```bash
curl -fsS -X POST https://SUA-API/v1/catalog/items \
  -H "X-Admin-Token: ${BM_VISION_ADMIN_TOKEN}" \
  -F "image=@foto-fornecida-pelo-titular.jpg;type=image/jpeg" \
  -F "title=Girassol bordado" \
  -F "telegram_url=https://t.me/CANAL_AUTORIZADO/123" \
  -F "preview_url=https://ARQUIVO-DO-TITULAR/preview.jpg" \
  -F "matrix_format=DST" \
  -F "rights_holder=Nome do titular" \
  -F "authorization_reference=identificador-de-consentimento" \
  -F "authorized_for_visual_index=true"
```
A URL de exemplo acima é ilustrativa; não representa uma publicação existente.

## Estado e proibições
- Nenhum canal foi automaticamente varrido; nenhum catálogo legítimo foi entregue para indexação neste momento.
- Sem URL de API configurada, a interface informa claramente indisponibilidade. Não deve simular resultados.
- O usuário não precisa cadastrar referências ou fornecer palavras-chave; a curadoria de um acervo licenciado é feita separadamente por distribuidores e titulares que aderirem.
- O protótipo PR #196 de rótulos/local dHash foi **rejeitado e encerrado**, e não deve ser reutilizado.

Fontes de limitações: https://core.telegram.org/api/terms ; https://telegram.org/tos/content-licensing ; https://telegram.org/tos/bot-developers
