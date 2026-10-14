# Brother Matrizes — arquitetura comercial gratuita, sem migrar o site
Data: 2026-10-10
Status: **PROPOSTA / PROTÓTIPO LOCAL, NÃO IMPLANTADO — NÃO LIBERAR VENDAS AUTOMÁTICAS**

## Decisão do projeto
- Manter o WordPress, WooCommerce, Efí Bank, App Commerce Core e site da T.I. Machado na InfinityFree por enquanto. Nenhuma mudança em produção ou no Bíblia EBD.
- Supabase permanece **exclusivamente** para login Google, nunca para pagamentos, assinaturas, planos ou licenças.
- Usar uma infraestrutura complementar Cloudflare Workers Free + D1 para **avaliar** uma API acessível ao Android.
- O WooCommerce/App Commerce Core continua fonte de verdade comercial; uma cópia de leitura do catálogo NÃO confere entitlement.

## Causa da falha atual
O WordPress hospedado na InfinityFree exige desafio de navegador para requisições recebidas. Chamadas da API Android ao endpoint `/wp-json/brother-matrizes/v1/usage/authorize` retornam HTTP 403/HTML, não JSON; a importação local da fonte TTF/OTF não é executada após falha da reserva de cota. Não remover verificação ou inventar quota/Pro.
Referência do provedor: https://forum.infinityfree.com/t/why-isnt-api-access-working-on-my-website/115198

## Topologia a homologar
1. **WordPress/WooCommerce/App Commerce Core**: planos e preços oficiais, checkout, pedidos, renovação, cancelamento, promoções e direito do cliente à licença.
2. **WordPress -> Cloudflare (tráfego DE SAÍDA, HTTPS/HMAC)**: publicar projeções **assinadas**, com sequência durável e idempotência; o provedor permite pedidos de saída. O WordPress não pode depender de callback de entrada da Cloudflare.
3. **Cloudflare Workers + D1**: oferecer API estável ao Android; validar token Google contra chaves públicas JWKS do Supabase quando for implementado acesso por usuário; usar dado de licença somente derivado de fatos confirmados no WordPress. D1 deve manter índice e controlar limites.
4. **Efí -> ponto de webhook validado**: recebimentos não significam concessão automática. Homologar TLS/mTLS, verificação de payload/transação, duplicatas e expiração. WordPress deve processar os fatos de forma segura por fluxo de saída, sem consultar rotas bloqueadas da InfinityFree. Quando isso não puder ser garantido, manter cobrança/ativação automática desabilitadas.
5. **Android**: até API completa e validada, apenas navegação e inspeção de ZIP, RAR, fontes e matrizes podem operar localmente; consumo de cota e acesso pago falham fechados se não há autorização. Nenhuma mudança de `WORDPRESS_URL` foi feita nesta etapa.

## Protótipo preparado — escopo estrito
Protótipo Cloudflare Worker + D1 testado localmente, entregue separadamente como `Brother-Matrizes-Cloudflare-Free-PoC.zip`, ainda não instalado/implantado.
- GET `/health` => status de protótipo, **ready_for_sales=false**.
- POST `/internal/catalog-sync` => HMAC SHA-256, janela curta de timestamp, limite do payload e revisão monotônica contra replay.
- GET `/wp-json/brother-matrizes/v1/plans` => retorna somente catálogo previamente sincronizado; falha com HTTP 503 caso não atualizado em 10 minutos.
- Demais rotas `/me`, `/usage/*`, `/checkout` etc. => **503**, sem autenticação/licenças fictícias.
- Testes Node locais: 7 de 7 aprovados em 2026-10-10; não constituem homologação do servidor, sincronização WordPress ou pagamento Efí.

## Bloqueadores antes de publicar vendas
- Conta Cloudflare Free própria, Worker e D1 provisionados com ID real; segredo HMAC guardado fora de Git/APK.
- Módulo de saída do App Commerce Core integrado apenas ao Brother Matrizes, com backup, fila durável, revisões transacionais e rastreabilidade de falhas.
- Demonstração de que a sincronização acontece sem depender apenas de visitas/WordPress wp-cron; cron externo não deve chamar WordPress da InfinityFree.
- Autenticação JWT verificada no Worker por JWT assinado do Supabase, não confiar em preço, plano ou ID enviados pelo celular.
- Projeto completo de licenças verificadas, cotas atômicas, estorno/revogação, expirados, idempotência e reconciliação de pedidos.
- Confirmar capacidade de receber webhook **Efí com mTLS quando exigido** no serviço gratuito escolhido, inclusive hostname/SSL/certificados (o protótipo não contém webhook).
- Homologação da compra real, renovação, estorno, promoção de estoque finito, teste grátis, rede intermitente, troca de celular e limites do D1/Workers Free.
- Aguardar API completa antes de atualizar o APK e antes de liberar cobrança automática.

## Alternativa
Caso a sincronização por saída não seja confiável, manter o site institucional na InfinityFree e hospedar apenas o WordPress/WooCommerce comercial em um ambiente separado e compatível (p. ex. VM Always Free sujeita a cadastro/capacidade), com migração segura e autorização expressa do usuário. **Não presumir que um proxy contorna restrição de entrada.**

## Fontes verificadas em 10/10/2026
- InfinityFree (APIs de entrada bloqueadas; chamadas de saída permitidas): https://forum.infinityfree.com/t/why-isnt-api-access-working-on-my-website/115198
- Cloudflare Workers Free: https://developers.cloudflare.com/workers/platform/limits/
- Cloudflare D1 Free: https://developers.cloudflare.com/d1/platform/pricing/
- Oracle Always Free (alternativa): https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Cloudflare mTLS (requer verificação de aplicabilidade ao webhook da Efí): https://developers.cloudflare.com/ssl/client-certificates/
