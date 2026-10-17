## Correção do desvio para Minha Conta — 17/10/2026

O vídeo `1000378909.mp4` demonstrou que **a sessão de cliente WordPress foi criada
corretamente**, mas `tiac_auth=checkout` enviou o usuário à página de conta e o
botão público de Assinar Mensal apresentou outra confirmação de e-mail.
O login integrado funcionava; **o destino pós-login estava errado**.

A versão **0.1.1-rc2** do plugin corrige a navegação após o ticket para o carrinho
e checkout reais do WooCommerce (`wc_get_checkout_url()`), usando apenas
IDs oficiais confirmados publicamente no Store API:

- `pro_monthly` → WooCommerce **329** (Brother Matrizes Mensal)
- `pro_yearly` → **330** (Anual)
- `pro_lifetime` → **331** (Vitalício padrão)
- `pro_lifetime_launch` → **308** (Vitalício lançamento)

Cada produto é conferido no servidor quanto a nome, tipo, preço válido,
disponibilidade e estoque. A compra de outros aplicativos no mesmo site
fica preservada: carrinhos com produtos distintos são rejeitados sem limpeza
automática; nenhum preço, pedido, assinatura ou gateway é editado.

**Instalação:** atualizar o plugin **Brother Matrizes — Login Integrado de
Checkout** existente usando o ZIP 0.1.1-rc2 com substituição do plugin.
O valor privado `bm_checkout_handoff_key_v1` fica em `wp_options` e
é reutilizado; não apagar ou recriar a chave no Supabase.
O APK QA **já instalado** pode repetir o teste sem nova compilação, porque
emite exatamente os mesmos tickets e não escolhe a URL final.

**Testes obrigatórios:** (1) entrar com Google no QA, (2) escolher plano,
(3) confirmar que a página de destino é checkout com **o produto correto
e preço WooCommerce atual**, (4) verificar oferta mensal/anual no Efí
sem efetuar pagamento real, (5) confirmar que não houve acesso PRO
antes da transação. Se a compra recorrente não for disponibilizada
pelo Efí para produtos simples, não cobrar: investigar regras do
Payment Mode Guard já instalado em homologação.

---
# Brother Matrizes — checkout integrado Google → WooCommerce (homologação)

## Problema reproduzido

O Android já autentica via Google/Supabase Auth, mas o fluxo atual
`WordPressWebStore.planUrl` abre `minha-conta/?tiac_auth=confirm&...`
sem autenticar o navegador do WordPress. A página mostra novo formulário de
cadastro ou login (vídeo enviado pelo desenvolvedor em 16/10/2026).

## Fluxo proposto

1. Cliente autenticado com Google escolhe Mensal, Anual ou Vitalício.
2. Android confirma sessão Google e envia seu access token **apenas no
   header HTTPS Authorization** para a Edge Function
   `brother-checkout-handoff` do mesmo projeto Supabase Auth.
3. A função revalida o usuário no Supabase Auth, verifica provedor Google e
   e-mail confirmado e emite uma credencial de **45 segundos** cifrada
   por AES-256-GCM com chave compartilhada apenas entre servidores.
4. O navegador abre `https://timachado.ifree.page/minha-conta/?bm_checkout=<bilhete-opaco>`.
   Não contém token Google, senha ou e-mail em texto claro.
5. Plugin WordPress independente descriptografa localmente, verifica expiração,
   destinatário, plano e uso único (nonce gravado atomically em `wp_options`).
   Vínculo é pelo `sub` autenticado, com proteção a contas já vinculadas;
   só reutiliza contas de cliente/assinante com e-mail Google confirmado.
   Contas administrativas **nunca são vinculadas automaticamente**.
6. Cria sessão normal WordPress e direciona ao fluxo de checkout já existente,
   `tiac_auth=checkout`. Produto especial de lançamento mantém página #308.
7. WooCommerce/Efí seguem responsáveis por preços, pedidos e liquidação.
   Nenhum plano Pro é liberado apenas por login ou pelo retorno do navegador.

## Implantação — duas pontas devem estar prontas

**NÃO MEXER NO APK PÚBLICO 1.10.9 enquanto não terminar QA real.**
`ENABLE_CHECKOUT_HANDOFF` fica falso por padrão e só pode ser habilitado
no build de homologação com `-PbrotherCheckoutHandoff=true`.

- Instale uma única vez o plugin isolado `wordpress/brother-checkout-handoff` no WordPress.
  Quando ativado, o plugin gera uma chave aleatória de 32 bytes e guarda como opção privada sem autoload.
- No WordPress, entre em **Ferramentas → Brother — Checkout Google**. Confira que a chave foi criada e clique em **Mostrar ou ocultar** para copiá-la.
  Não envie essa chave no chat, prints ou repositório. Como alternativa técnica, o plugin também aceita uma constante privada `BM_CHECKOUT_HANDOFF_KEY` no `wp-config.php`.
- No Supabase do app (**projeto `dwpcddiramxlhavdmmyn`**), abra **Edge Function Secrets** e crie/atualize `BROTHER_CHECKOUT_HANDOFF_KEY` com o valor copiado do WordPress.
  Esta chave jamais deve ser colocada no APK, no GitHub ou em JavaScript.
- A Edge Function de homologação é `brother-checkout-handoff` e valida por conta própria o token do Google/Supabase via `auth.getUser`; o gateway está com `verify_jwt=false` para suportar chaves do tipo `sb_publishable_`, mas a função nega qualquer chamada sem JWT de usuário verificado. Ela não altera o checkout comercial em produção até que o APK seja explicitamente habilitado.
  Não substitua App Commerce Core, Efí, WooCommerce ou outros plugins.
- Implante a função `supabase/functions/brother-checkout-handoff/index.ts`
  com JWT Auth habilitado e valide `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
- Compile Android com a flag somente após o servidor responder corretamente.
- Teste cliente Google novo e existente, e-mail sem confirmação, contas
  administradoras, bilhete usado duas vezes, 45s expirados, navegador diferente,
  loja indisponível, cancelamento, pagamento Efí sandbox, renovação, estorno,
  vinculação à licença e retorno ao app. Confirmar também que compras de outros
  aplicativos do mesmo WordPress não mudaram.

## Limitações importantes

- A hospedagem InfinityFree gratuita bloqueia parte das requisições REST
  realizadas por clientes automáticos. O WordPress **não precisa consultar
  Supabase** para aceitar o bilhete: validação criptográfica é local.
  O app se comunica diretamente com a Edge Function Supabase.
- A URL externa temporária é um *bearer credential* opaco e de curta duração;
  mesmo cifrada, não deve ser compartilhada, registrada ou enviada a terceiros.
  O plugin define `no-store`, `no-referrer` e consome uma única vez.
- A associação inicial de clientes pelo e-mail **confirmado** deve passar
  por revisão de política comercial, com atenção a contas antigas e e-mails
  reciclados. Administradores nunca entram nesse fluxo.
- Se houver erro no serviço, o Android deve deixar o cliente usar o
  login tradicional do site. Nunca expor access_token Supabase numa URL.
- Este código é de **homologação**, não significa que o site, Edge Function
  ou APK comercial tenham sido publicados.

## Google Play Protect

Não existe API ou flag para impedir verificações. A empresa deve confirmar
a identidade no Play Console/Android Developer Console, registrar o pacote
`com.timachado.brothermatrizes` e a assinatura comercial original e manter
releases assinadas consistentes. Para reduzir atrito, distribuir pelo Google
Play, quando possível. Assinatura e verificação do desenvolvedor não garantem
eliminação de avisos do Play Protect; nunca recomendar desativá-lo.
