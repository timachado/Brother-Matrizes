# Brother Matrizes — auditoria de planos e vitrine (13/10/2026)

## Escopo

Somente apresentação comercial do **Brother Matrizes**, app Android e componente isolado para o site WordPress. Produção atual **1.10.4 / código 180**. Próximo APK candidato **1.10.5 / código 181**. Nenhuma migração de dados, pagamento, usuário, assinatura ou produto.

## Dados verificados

| Plano | Produto Woo | Preço Store API em 13/10/2026 | Estado |
| --- | ---: | ---: | --- |
| Gratuito | nenhum | Grátis | Conta gratuita |
| PRO Mensal | 329 | R$ 19,90 | Em estoque |
| PRO Anual | 330 | R$ 159,90 | Em estoque |
| PRO Vitalício | 331 | R$ 399,90 | Em estoque |
| Vitalício de Lançamento | 308 | R$ 249,90 | 49 em estoque no instante da consulta |

Fonte pública: `https://timachado.ifree.page/wp-json/wc/store/v1/products?per_page=100`. O estoque e os preços devem ser consultados **novamente no checkout**, não tratados como eternos. O produto promocional foi criado para uma edição de até 50 licenças, mas nunca usar contador fictício.

## Direitos confirmados no código

- Servidor `supabase/functions/brother-entitlement/index.ts` trata `pro_monthly`, `pro_yearly`, `pro_lifetime` e `pro_lifetime_launch` como direitos PRO mediante evento de pagamento válido. Os dois vitalícios possuem classificação de acesso igual (`ranks = 3`) e não têm `current_period_end`.
- Trial de 7 dias: ativação voluntária e única com tempo calculado no servidor, sem exigência de cartão, não gera cobrança automática.
- `supabase/functions/brother-usage/index.ts` só isenta do limite mensal quando a confirmação PRO/trial é válida. A conta gratuita utiliza 3 importações de fontes e 5 de matrizes por mês por usuário.
- `AccountModels.kt` impõe expiração a mensal/anual/trial e preserva vitalícios. `BrotherEntitlementClient.kt` usa confirmação segura vinculada à identidade, não aos botões locais.
- Biblioteca local de projetos, criação e edição estão visíveis na navegação do aplicativo; não vendê-las indevidamente como exclusivas.
- A função `LocalBetaImportQuota.kt` aplica cotas locais **só em BETA separado**, não foi usada para inventar cotas comerciais.
- Não há regra comercial implementada de acesso Beta antecipado exclusivo do vitalício. A mensagem deve ser planejamento **não contratual**.
- Conversor EMB/PES **não oferecer como entregue**: implementação específica EMB/PES e suporte por formato ainda precisam de homologação.

## Ressalvas importantes

1. O servidor `brother-entitlement` devolve uma lista de planos com preços `null` e `active=false` para modalidades pagas. Isto não prova indisponibilidade no WooCommerce; mostra que o catálogo comercial detalhado ainda não está sincronizado na resposta autenticada. Até existir um catálogo backend validado, a UI Android exibe valores **apenas como referência**, e o checkout WordPress continua autoridade final.
2. Não usar o estado `plan.active=false` para revogar acessos; licença é independente de marketing. Não liberar acesso por retorno do navegador.
3. Produtos 329, 330 e 331 usam os links de checkout já praticados pela T.I. Machado; o produto 308 aponta ao próprio produto promocional. O plugin não toca nos pedidos nem configura Efí.
4. O produto 308 é do tipo `simple` no endpoint público; não afirmar detalhes de recorrência contratual com base apenas no rótulo 'Mensal/Anual'. Os termos do checkout devem ser revisados no admin.
5. O site está hospedado em InfinityFree e WPVibe não conseguiu autenticar pela API REST (resposta não-JSON). A proposta WordPress é um **plugin instalável manualmente**, sem publicação nem edição do tema. Inserir apenas o shortcode `[brother_matrizes_planos]` na área do Brother Matrizes. Para substituição visual, ocultar/remover somente os cards antigos específicos do Brother, nunca os cards globais de outros apps.

## Como testar

- [ ] Android: Gratuito, trial elegível/ativo/expirado, PRO Mensal, PRO Anual, vitalício regular e lançamento; conferir o banner de conta ativa e recuperar licença após login.
- [ ] Android: vitalício ativo não pode receber CTA de comprar outra licença equivalente.
- [ ] Android: comparar valores de vitrine com WooCommerce e confirmar checkout para cada produto (329,330,331,308) sem alterar pedidos.
- [ ] Android: validar responsividade em 360dp e fontes maiores; scrolling, comparativo, FAQ e contraste.
- [ ] WordPress: backup da página; instalar plugin em cópia/homologação, inserir shortcode na página Brother Matrizes **somente**, conferir mobile, estoque zerado, produto indisponível e link de checkout.
- [ ] Site: verificar menu, cases, projetos, outras páginas de aplicativos, WooCommerce e Efí inalterados.
- [ ] Segurança: nenhum acesso PRO concedido por front-end; confirmar assinatura WooCommerce/Efí com evento seguro no servidor.
- [ ] Oferta vitalícia: validar em contrato/página do produto o escopo de atualizações e futuros serviços.
- [ ] Beta: elaborar regras escritas e implementar autorização no servidor **antes** de anunciar prioridade como benefício pago.

## Resultado gerado

- Android: `BrotherPlanBenefits.kt`, vitrine reformulada em `AccountScreen.kt` e testes dedicados.
- WordPress: `wordpress/brother-planos-vitrine/brother-planos-vitrine.php` com `[brother_matrizes_planos]`; preços e estoque via `wc_get_product` em tempo de renderização, sem alterar pagamentos, produtos ou licenças.
- Pipelines independentes de QA Android e PHP/ZIP sem publicação.
