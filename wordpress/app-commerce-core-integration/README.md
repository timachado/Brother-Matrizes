# Brother Matrizes — ponte de catálogo App Commerce Core → Cloudflare

**Status: código de homologação, NÃO instalado no WordPress e NÃO habilitado.**

Módulo aditivo para integrar ao App Commerce Core **existente**, nunca como um segundo plugin. Sem tocar em outros aplicativos, cadastros, tabelas de licença, checkout WooCommerce ou integração Efí.

## Funcionamento

- Chama **internamente** o endpoint WordPress `/brother-matrizes/v1/plans` com `rest_do_request()`: não realiza requisições REST para a InfinityFree.
- Confere códigos permitidos, nomes, centavos, preço WooCommerce e status; aborta se houver preços ausentes em plano pago ativo.
- Envia somente o catálogo de planos de Brother Matrizes para o Worker, via HTTPS POST com assinatura HMAC-SHA256 sobre `<timestamp>.<JSON exato>`.
- Obtém segredo pela constante privada `BM_CATALOG_SYNC_SECRET` do `wp-config.php`; no Worker, segredo homônimo `CATALOG_SYNC_SECRET`. **NUNCA** usar as credenciais Efí, tokens Google ou o `service_role` Supabase.
- Mantém contador monotônico de revisão e bloqueio simples para evitar sincronizações paralelas; erros não revelam dados pessoais nem segredos.
- Agenda a sincronização a cada 3 min via WP-Cron **somente quando habilitado**; WordPress WP-Cron depende de tráfego, portanto não garante atualização ininterrupta.
- No Cloudflare, catálogo envelhecido após 10 minutos retorna **503**, não exibe ofertas stale; licenças e `usage/*` permanecem **503**.
- Nenhum pedido nem licenciamento é espelhado nesta etapa.

## Integração de homologação, não executar em produção agora

1. Obter **a versão exata atualmente instalada** do ZIP App Commerce Core antes de integrar, com backup e staging. Não substituir o Core por uma versão antiga.
2. Incluir `bm-cloudflare-catalog-relay.php` via `require_once` no módulo comercial **Brother Matrizes** já incluído no Core, preservando os arquivos de WooCommerce/Efí.
3. Conferir que a rota pública interna do Core retorna `plans` com `code, name, active, price_cents, currency`; não hardcodar preços nem permitir outros apps.
4. Criar segredo aleatório de pelo menos 32 bytes, compartilhado fora de Git entre a constante WordPress `BM_CATALOG_SYNC_SECRET` e o Worker `CATALOG_SYNC_SECRET`. Opcional: `BM_CATALOG_SYNC_ENABLED`=false até homologação.
5. Com código **aprovado em staging**, ativar e validar o endpoint Cloudflare com HMAC inválido (401), válido (200), replay (409), expiração (503) e mudanças de preços em WooCommerce.
6. Só então habilitar atualização recorrente. Monitorar o status via option `bm_cloudflare_catalog_sync_status` no WordPress, sem disponibilizar isso publicamente.
7. Não alterar `BuildConfig.WORDPRESS_URL` no APK até implementação segura das APIs autenticadas e licenças no Worker.

### Incompatibilidades e riscos a revisar

- **Disponibilidade:** WP-Cron usa visitas como gatilho. A cada 3 minutos é apenas a frequência solicitada, não uma garantia; a expiração de 10 minutos é obrigatória. Se esse requisito não for confiável, considerar outra hospedagem para backend comercial ou mecanismo legítimo de saída.
- **Checkout:** o Android atualmente exige que URL de checkout pertença ao host `WORDPRESS_URL`. Não redirecionar esse host para Cloudflare sem mudar o projeto e validar HTTPS, autenticação e origem do checkout.
- **Assinaturas:** sincronizar preços não equivale a conceder acesso Pro, registrar pagamentos Efí ou consumo de cotas. Não liberar compras automáticas ainda.
- **Instalação:** falta o ZIP fonte da versão atual 2.6.9-rc1 no repositório, portanto **não fiz integração/instalação do módulo no plugin real**.

Referências: https://developer.wordpress.org/reference/functions/wp_schedule_event/ e https://developers.cloudflare.com/workers/configuration/cron-triggers/
