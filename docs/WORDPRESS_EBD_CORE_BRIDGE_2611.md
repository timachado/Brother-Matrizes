# Brother Matrizes x Bíblia EBD — reaproveitamento do App Commerce Core

A principal correção para o lançamento comercial do Brother Matrizes é a inclusão da oferta
promocional no **mesmo ledger WooCommerce** que já atende os outros aplicativos da T.I. Machado.
Não migrar nem modificar a Bíblia EBD, Efí ou o Supabase.

## Defeito identificado no Core 2.6.10-rc1 (staging)

O editor Brother salva o ID do produto promocional em `bm_license_products['pro_lifetime_launch']`,
mas podia retornar cedo quando o produto já estava vinculado. Em consequência, um produto
virtual criado manualmente — como o #308 — podia permanecer sem os metadados
`_ti_app_id=307` e `_ti_app_plan=lifetime`.

A rotina existente `tiac_process_order` do App Commerce Core atribui licenças pelo
`_ti_app_id` do produto/item, então uma venda no site não ficaria necessariamente
associada ao aplicativo certo.

## Correção homologada no pacote 2.6.11-rc1 de STAGING

- Ao salvar o cadastro #307 no editor WordPress, o módulo verifica o produto #308,
  inclusive se o vínculo já estava salvo.
- Preenche exclusivamente `_ti_app_id` e `_ti_app_plan` quando ausentes.
- Rejeita produtos já vinculados a outro app (como Bíblia EBD) ou plano incompatível.
- Não altera preço de R$249,90, estoque de 50, status rascunho, visibilidade,
  pagamento, recorrência, credenciais nem dados de clientes.
- Não publica produtos, gera pedidos ou concede licenças antecipadamente.
- O emissor ECDSA continua desligado por padrão.

## Testes executados
- `php -l`: todos os arquivos PHP válidos.
- Mock independente PHP: backfill idempotente de produto #308; preço e estoque
  preservados; não toma produto de outro app; ignora POST sem nonce.
- `unzip -t`: íntegro. Nenhum arquivo Cloudflare no pacote.

## Limites
Este ajuste resolve **venda paga WooCommerce → licença no portal WordPress**,
mas **não resolve** o vínculo do Google Supabase com o comprador e a entrega
assinada para uso offline no APK. A InfinityFree Free bloqueia a API REST direta
do Android e a tentativa de conexão WPVibe ao REST do site retornou HTML.
Não afirmar que uma venda Web já desbloqueia o APK.

Status: pacote de STAGING; instalar somente após backup, sem ativar vendas
antes de teste real de pagamento Efí, status, reembolso e assinatura APK.
