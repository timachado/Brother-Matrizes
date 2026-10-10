# Brother Matrizes — WordPress + InfinityFree, sem Cloudflare

Decisão de arquitetura: 10/10/2026. Ramo `refactor/brother-matrizes-clean-0469`.

## Contrato de responsabilidades
- WordPress hospedado na InfinityFree, plugin TI Machado App Commerce Core e WooCommerce: cadastro do app, planos, produtos e direito comercial à licença.
- Efí Bank: processamento dos pagamentos e assinaturas via integração existente do WooCommerce; não migrar nem duplicar esse fluxo.
- Supabase: **somente autenticação Google**, nunca base de planos, pagamentos, licenças, cotas ou assinaturas.
- Cloudflare Worker/D1: **descontinuados** como caminho de execução, sem importação de dados, sincronização nem chamadas do APK.

## Estado existente que deve ser preservado
- App WordPress Brother Matrizes: ID 307, rascunho.
- Produto WooCommerce Vitalício Lançamento: ID 308, rascunho, preço R$ 249,90, estoque previsto de 50.
- Mensal, Anual e Vitalício: preços configurados, mas ofertas inativas; não publicar automaticamente.
- Funcionalidades de criação/edição/importação/simulação locais do Android: não remover nem desproteger recursos pagos inadvertidamente.

## Desligamento seguro
- No `wp-config.php`, garantir **uma única** definição PHP válida: `define('BM_CATALOG_SYNC_ENABLED', false);`.
- Não excluir segredo do Worker em produção por automação do GitHub nem editar banco de dados manualmente.
- O arquivo de integração Cloudflare pode permanecer instalado, inerte, no Core até revisão/testes de remoção. Não atualizar o plugin usando ZIP antigo.
- A automação GitHub de Cloudflare foi limitada a execução **manual** sem acesso à API externa. O protótipo fica arquivado no repositório, não é backend oficial.

## Restrição obrigatória da InfinityFree gratuita
A segurança de navegador no plano gratuito bloqueia chamadas REST diretas de apps Android, clients HTTP, webhooks e serviços externos, frequentemente com HTTP 403/HTML. O endpoint `/wp-json/brother-matrizes/v1/plans` pode abrir no Chrome sem ser consumível por `HttpURLConnection` do Android.

Fontes oficiais do provedor:
- https://forum.infinityfree.com/t/why-wont-my-mobile-app-connect-to-my-website/115197
- https://forum.infinityfree.com/t/why-isnt-api-access-working-on-my-website/115198

**Não** contornar a proteção com cookies falsos, scraping do desafio, CORS genérico, URLs alternativas nem afirmando que mudar `User-Agent` resolve.
**Não** declarar licenciamento Pro, cotas, webhook Efí ou checkout automático "funcionando" por simples retorno HTTP 200 no navegador.

## Caminho viável dentro do desejo de permanecer na InfinityFree
1. Preservar as operações que funcionam inteiramente no dispositivo sem exigir API.
2. Oferecer catálogo/compra/gerenciamento pelo WordPress aberto no navegador real (Custom Tabs/Chrome), mantendo pagamentos na Efí pelo WooCommerce.
3. Antes de oferecer concessão de Pro no APK, projetar **retorno assinado e vinculado à identidade/dispositivo** de um fluxo de autenticação no navegador (deep link + nonce + prova criptográfica). Validar expiração, duplicidade, revogação, fraude e reconciliação. Não tratar uma página web ou URL de sucesso de pagamento como prova de licença.
4. Se a exigência é de licença e cotas automáticas em tempo real no APK, informar claramente que a InfinityFree gratuita é insuficiente como backend de API; a alternativa seria hospedagem com suporte a REST/webhooks, sem obrigar a usar Cloudflare.
5. Validar o método de confirmação de pagamentos Efí que funcione no plano da InfinityFree; callbacks externos podem falhar. Não disponibilizar planos pagos até homologar liquidação, expiração, reembolso e ativação.

## Verificações na retomada
- Confirmar site/WordPress e rota pública sem erro 500.
- Confirmar flag Cloudflare false sem divulgar `BM_CATALOG_SYNC_SECRET`.
- Garantir que nenhuma alteração no app faça downgrade da política de direitos pagos: falhar fechado quando não houver licença verificável, e não debitar cotas em falhas de rede.
- Criar e testar separadamente páginas web nativas ao domínio WordPress e mecanismo assinado de volta ao Android.
