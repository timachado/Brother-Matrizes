# Comparação de licenciamento — Bíblia EBD APK v164 × Brother Matrizes

Data: 10/10/2026. Origem: inspeção somente de leitura do APK Bíblia EBD
`1.19.2-v164-BIBLIOTECA-BUSCA-IMPORTAR.apk` fornecido pelo proprietário e da
implementação da função de entitlement no seu próprio projeto, sem publicar
token, segredos ou dados dos clientes.

## Fluxo comprovado no código da Bíblia EBD

1. O aplicativo é uma WebView com recursos HTML/JS locais.
2. O login Google usa Supabase Auth, com sessão armazenada pelo aplicativo.
3. Ao entrar e ao voltar ao primeiro plano, o frontend solicita o
   entitlement a uma **Edge Function separada** (não diretamente à
   InfinityFree).
4. A função valida a sessão Supabase no servidor e consulta as tabelas
   específicas de assinaturas e eventos comerciais da Bíblia EBD.
5. Um serviço comercial dedicado reconcilia eventos recebidos do site antes
   de atualizar a assinatura Premium.
6. Os preços e URLs de compra são oferecidos pelo site WordPress.
7. O frontend renderiza o indicador Premium segundo o entitlement obtido
   no servidor; não libera Pro só por abrir a página de checkout.

**Conclusão**: o modelo da Bíblia **não é apenas** 'site WordPress + login
Supabase' — existe uma camada de servidor e estado comercial dedicado no
Supabase para aquele app. Não duplicar essa camada no Brother sem mudar a
política de arquitetura aprovada.

## Restrições preservadas no Brother Matrizes

- Fonte de verdade do comércio: WordPress + WooCommerce + App Commerce Core;
  pagamentos Efí. Supabase exclusivamente autenticação.
- Cloudflare descontinuada.
- InfinityFree gratuita não oferece API REST Android confiável.
- O caminho automático do APK não deve utilizar os dados/billing-events da
  Bíblia EBD, nem herdar um entitlement Premium de outro app.
- Falso sucesso de pagamento e resposta do navegador nunca concedem Pro.
- O aplicativo #307 e o produto #308 continuam preservados em rascunho
  enquanto não há homologação.

## Adaptação segura efetuada em 10/10

- Tela Brother 'Minhas compras e licenças no site' abre
  `https://timachado.ifree.page/minha-conta/meus-aplicativos/` pelo
  navegador externo. Visitantes não autenticados recebem o login WordPress.
- Conta Google e loja permanecem sessões independentes.
- 'Licença Pro confirmada' só é exibido se o entitlement realmente declara
  `hasProAccess`; um retorno do servidor indicando plano gratuito não
  confirma o Pro.
- Protocolo `WordPressSignedLicense.verify` já permite testar criptografia,
  mas não está conectado à concessão de Pro até existir emissor WordPress,
  chave pública fixa e prova de associação comprador + Google + aparelho.

## Necessário para habilitar venda + Pro automático

1. Registrar pedidos WooCommerce e eventos Efí liquidados no Core.
2. Conferir status, reembolso, prazo, plano e titular do pedido em cada
   emissão de licença.
3. Fornecer fluxo **autenticado pelo comprador no navegador** que vincule,
   com prova confiável, a identidade Google e o aparelho ao direito comercial;
   não confiar em email ou sub informado livremente.
4. Instalar chave de assinatura privada fora do site público e incorporar
   somente chave pública confiável no APK de produção.
5. Emitir licença curta assinada para uso offline, com rotação e renovação
   projetadas para as limitações da hospedagem. A revogação não será
   imediata durante o lease.
6. Validar Efí real, PHP/WordPress no site, autenticação, ativação, plano
   mensal/anual/vitalício, reembolso, reinstalação, expiração e atualização
   de APK com mesma chave de distribuição.
7. Se a intenção mudar para replicar o mesmo servidor de entitlement da
   Bíblia EBD, isso exige autorização explícita para ampliar o papel do
   Supabase no Brother — **não fazer implicitamente**.

## Go / No-go lançamento

**Go:** demonstração beta gratuita após build + testes de aparelho +
assinatura de distribuição aprovadas.

**No-go:** afirmar que compra no WooCommerce libera Pro dentro do APK ou
publicar planos ativos sem teste ponta-a-ponta. Uma build compilada não
comprova fluxo de pagamento.
