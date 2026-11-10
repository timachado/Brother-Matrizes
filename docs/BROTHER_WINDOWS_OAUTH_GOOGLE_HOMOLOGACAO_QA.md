# Brother Matrizes Windows — homologação Google OAuth / PKCE (QA)

**Escopo:** branch `feature/windows-fiel-android-1-11-8-20261106`, PR #226.
**Status:** checklist técnico; **não** é autorização para publicar EXE ou
alterar a configuração de autenticação da produção.

## Fluxo e responsabilidades

1. `DesktopPkceLoopbackListener` abre `127.0.0.1` em porta efêmera,
   sem escutar conexões externas.
2. `DesktopPkceHandshake` cria `bm_state` de uso único e PKCE S256.
   O callback da sessão é
   `http://127.0.0.1:<porta>/brother-matrizes/auth?bm_state=<valor>`.
3. `DesktopSupabaseGooglePkceClient` abre
   `https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/authorize` no
   navegador do sistema (não WebView), com `provider=google`,
   `redirect_to`, `code_challenge` e `code_challenge_method=s256`.
4. Após a volta com `code` e `bm_state` corretos, troca o código uma vez
   em `/auth/v1/token?grant_type=pkce`.
5. `DesktopSupabaseGoogleIdentityVerifier` consulta
   `GET /auth/v1/user`; exige `app_metadata.provider/providers`
   compatível com Google. Somente então `DesktopWindowsAuthQaController`
   pode proteger a sessão no DPAPI e gerenciá-la com FileLock do Windows.
6. `DesktopBrotherEntitlementReader` consulta **GET** somente leitura
   em `/functions/v1/brother-entitlement`; cruza a identidade do servidor.
   **O resultado é apenas informativo**: não é um entitlement usado para
   importar fontes, matrizes, consumir cota ou desbloquear Pro.

## Pontos que dependem de confirmação no Supabase Dashboard

A conexão Supabase disponível nesta QA **não expõe a lista atual de Redirect
URLs do Auth**. É proibido pressupor que o callback desktop esteja liberado.

- Em **Supabase > Authentication > URL Configuration > Redirect URLs**,
  verificar compatibilidade do retorno loopback com porta efêmera e
  `bm_state`. Uma expressão **candidata, ainda não homologada** para a
  allowlist é:
  `http://127.0.0.1:*/brother-matrizes/auth\?bm_state=*`
  **Não aplicar automaticamente**. O `?` literal está escapado
  (`\?`) porque a documentação do Supabase define `?` sem escape como
  curinga de um único caractere. O `*` da porta e do `bm_state` segue
  sendo um candidato de allowlist, não uma confirmação remota.
  Validar correspondência com o mecanismo de globs e com o formato
  **exato** do redirect gerado no teste real.
- Não alterar `Site URL` ou redirecionamentos Android aprovados. Não usar
  wildcard abrangente fora de `127.0.0.1` e do caminho da aplicação.
- O redirect registrado no **Google Cloud Console** é o callback HTTPS
  **do Supabase**, não o endpoint local de cada computador:
  `https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/callback`.
  Confirmar os dados no Google Provider do Supabase, sem divulgar secrets.
- Não habilitar OAuth real ou login comercial na UI antes de concluir QA
  de ponta a ponta. O callback de **erro via fragmento de URL** não chega
  ao servidor HTTP local; nesse caso a aplicação ainda usa timeout (5 min).
  Erros na **query** com state correto encerram imediatamente o fluxo.

Documentação oficial:
- https://supabase.com/docs/guides/auth/redirect-urls
- https://supabase.com/docs/guides/auth/social-sign-in/auth-google
- https://supabase.com/docs/guides/auth/sessions/pkce-flow

## Plano de testes com conta exclusiva de QA

1. **Sem alterar dados comerciais:** verificar previamente Redirect URLs,
   Google Provider e chave pública (nunca service role). Não reutilizar
   contas reais de clientes.
2. Login positivo: browser externo -> Google -> loopback -> PKCE -> getUser.
   Conferir que não há token na URL, nos logs ou em arquivo plaintext.
3. Login negativo: cancelamento, state incorreto, callback duplicado,
   porta/Host diferentes, callback expirado e tentativa de HTTP externo.
   Nenhum desses casos deve criar arquivo DPAPI ou habilitar Pro.
4. Reinício: recuperar o token DPAPI do usuário Windows correto; apenas uma
   instância pode possuir o FileLock; logout remove sessão.
5. Assinatura: confirmar `user.id` e `user.email` iguais em `getUser` e
   `brother-entitlement`; simular free, trial, Pro ativo e expirado.
   **Nunca gerar compras, licenças, trial real ou consumo de cota em QA.**
6. Importação: `DesktopCommercialFontImportGate` permanece bloqueado
   pelo `WindowsFontSessionUnavailable` até homologar autenticação real,
   operação reserva/finaliza, idempotência e equivalência comercial.
7. Só após homologação funcional/visual do aplicativo Windows inteiro
   preparar assinatura, empacotamento e distribuição do .exe.

## Evidência atual

Os testes locais/CI atuais usam Google/Supabase HTTP **simulados**.
A aprovação do pipeline **não** é comprovação de login real nem de liberação
do redirect na configuração remota. O projeto Android `main`, WordPress,
WooCommerce, Supabase comercial e o site T.I. Machado não foram modificados.

## Proteção adicional do loopback e cancelamento — 07/11/2026

Na branch Windows isolada foram adicionados controles sem modificar
Android, permissões comerciais, contas reais ou servidores:

- `DesktopPkceLoopbackListener.awaitCallback` recebe verificação opcional
  de cancelamento do chamador e usa polling HTTP de 300 ms,
  preservando o limite total de 5 minutos do fluxo.
- Leitura dos cabeçalhos HTTP fica sujeita a prazo absoluto de 3 segundos
  por conexão; cabeçalhos incompletos são rejeitados sem consumir
  `bm_state` ou código de autorização.
- `DesktopGooglePkceLoginCoordinator` verifica cancelamento antes
  de processar o callback, antes da troca de código e antes de retornar
  uma sessão transitória.
- Testes de regressão cobrem cancelamento sem troca de tokens e
  encerramento da porta, cabeçalho travado e callback legítimo posterior.

**QA automatizada aprovada em 07/11/2026:** commits `b2f957d`,
`c581a20`, `8a92553` e `8c966cf`, workflow OAuth/DPAPI
[#42374911265](https://github.com/timachado/Brother-Matrizes/actions/runs/42374911265)
`completed/success` no commit `8c966cf`. O job Windows compilou e executou
os testes OAuth, rede falsa, refresh, DPAPI e regressão de callback local.
Isso comprova apenas QA automatizada, não autenticação com Google real.

Permanecem bloqueados: login Google real, validação de Redirect URLs
no dashboard, autorização comercial, publicação do EXE e alterações
à versão Android oficial.

## Preflight offline de redirect — 07/11/2026

- Consulta de leitura `GET /auth/v1/settings` com chave **publicável**:
  o endpoint real informou `external.google: true`.
- `get_project` retornou `ACTIVE_HEALTHY`. Não se alterou
  OAuth, allowlist, domínio, senha, banco, usuário ou projeto.
- O conector Supabase não fornece leitura de Auth Redirect URLs;
  o estado da allowlist **continua desconhecido**.
- `DesktopPkceRedirectPreflight` exige URI HTTP estritamente em
  `127.0.0.1`, porta efêmera, caminho fixo e `bm_state` de 43
  caracteres Base64URL. O autoteste local invoca esse contrato.
- `DesktopPkceRedirectPreflightTest` tem casos negativos de host,
  protocolo, porta, caminho, parâmetros adicionais e fragmento.
- **QA Windows aprovada:** workflow OAuth/DPAPI
  [#42375796805](https://github.com/timachado/Brother-Matrizes/actions/runs/42375796805)
  `completed/success` no commit `64ede18`, incluindo o teste
  `DesktopPkceRedirectPreflightTest`. Isso não prova a allowlist remota.

Próximo gate manual e restrito: [Auth URL Configuration](https://supabase.com/dashboard/project/dwpcddiramxlhavdmmyn/auth/url-configuration),
sem alterar Site URL nem callbacks Android. Padrão proposto precisa
ser validado com a allowlist e teste com conta dedicada de QA antes
do Google real e da distribuição comercial.

## Revisão manual assistida da allowlist — 07/11/2026

Como as APIs autorizadas não expõem as **Auth Redirect URLs**, o acesso
ao dashboard requer uma sessão autenticada do proprietário. O
conector de navegador disponível não estava conectado e nenhuma
identidade Supabase foi usada para contornar esse limite.

Foi implementado um validador local somente para a tela **Minha Conta
QA**, visível quando há opt-in explícito do Windows:

- O responsável pode copiar do painel Supabase **apenas as URLs**
  existentes e colá-las temporariamente no campo de diagnóstico.
- `DesktopRedirectAllowlistReview` confere igualdade exata do padrão
  candidato com `\?` escapado; padrões mais amplos não passam.
- O texto não é salvo, transmitido, enviado ao servidor ou registrado
  em logs; limite de entrada de 8192 caracteres.
- A interface declara que uma coincidência na lista colada **não**
  confirma que o padrão esteja salvo, ativo, aceito pelo Supabase
  ou homologado por login Google real.
- `DesktopRedirectAllowlistReviewTest` cobre caso positivo,
  endereços semelhantes, wildcard amplo, conteúdo vazio e excesso
  de tamanho. Os workflows OAuth/DPAPI e Minha Conta receberam
  esses testes e a compilação da UI.

**Resultado de QA em 07/11/2026:**

- [OAuth/DPAPI #42376543324](https://github.com/timachado/Brother-Matrizes/actions/runs/42376543324):
  `completed/success` no commit `feaf8f6`. Compilação e testes
  offline de autenticação, DPAPI, PKCE e revisão exata de allowlist passaram.
- [Minha Conta #42376546723](https://github.com/timachado/Brother-Matrizes/actions/runs/42376546723):
  `completed/success` no commit `338a773`. Os testes unitários
  e a captura Compose Windows de Minha Conta passaram.
  Artefato QA `brother-windows-account-readonly-qa-screenshot`
  (ID `13576150878`, 27.836 bytes) disponibilizado.

Essas provas cobrem ambiente automatizado e não comprovam sessão
Supabase real, aprovação da allowlist nem assinatura comercial.
Ainda é necessária conferência autenticada do dashboard, teste
com conta dedicada de QA e aceite da paridade funcional completa
antes de distribuir o Windows comercial.


## Bloqueio de login sem revisão do redirect — 09/11/2026

**Causa identificada:** na revisão anterior, os dois sinalizadores de Google QA e chave pública tornavam visível o botão *Entrar com Google — QA*, mesmo sem comprovar a configuração da allowlist em **Authentication → URL Configuration**. A leitura `/auth/v1/settings` comprova somente a existência do provedor Google, não a lista de Redirect URLs. A documentação oficial Supabase recomenda que o `redirectTo` corresponda a uma URL explicitamente permitida ([Redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls)).

**Proteção aplicada:** o controle de QA agora exige `BROTHER_WINDOWS_QA_REDIRECT_REVIEWED=enabled`, além de `BROTHER_WINDOWS_QA_GOOGLE_AUTH=enabled` e `BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY` válido, antes de abrir navegador Google ou recuperar sessão em `Minha Conta`. Sem a revisão explícita, `DesktopWindowsGoogleQaEnvironment.signIn()` e `restoreAndVerify()` falham **antes** de usar HTTP ou navegador, independentemente do estado da interface. Todos os flags ficam desligados no executável portátil por padrão. O operador deve configurar o flag **somente após conferir manualmente** o padrão candidato da URL de retorno e escolher uma conta de testes dedicada.

A existência da flag **não** é prova de que a allowlist ativa corresponda ao padrão, nem comprova Google OAuth real ou funcionamento comercial. Não executar login de cliente, cobrança ou reserva de cotas antes da homologação. Testes `DesktopAccountQaScreenTest` e `DesktopWindowsFullGoogleQaFlowTest` verificam gate fechado sem aprovação, e o fluxo sintético aprovado apenas quando a opção de QA está explicitamente ativada. Não foram modificados GoTrue, Redirect URLs, Site URL, fornecedores Google, dados do Supabase ou Android comercial.


## Bloqueio completo da sessão DPAPI sem revisão do retorno OAuth — 09/11/2026

Auditoria de segurança da integração Windows: o controle `BROTHER_WINDOWS_QA_REDIRECT_REVIEWED=enabled` já impedia `signIn()` e `restoreAndVerify()`, mas **não** impedia o acesso a `verifiedAccessTokenForQa()` ou `accountPreview()` por uma sessão Google que tinha sido salva anteriormente no DPAPI. Os consumidores DaFont/Telegram/importadores podem usar esse método para obter o token. A simples presença de um DPAPI antigo não deve contornar a revisão do OAuth.

**Correção:** `DesktopWindowsGoogleQaEnvironment` verifica agora a mesma revisão antes de **todas as quatro** operações autenticadas: login, recuperação, obtenção de token e consulta do plano. O endpoint público `/auth/v1/settings` permanece somente diagnóstico e pode ser inspecionado sem autenticação. `logout()` permanece disponível para revogação local, mesmo quando a revisão está ausente.

**Teste obrigatório:** `DesktopWindowsFullGoogleQaFlowTest` exige zero chamadas de rede e zero abertura de navegador quando todas as operações autenticadas são invocadas sem a revisão; além disso, cria uma sessão DPAPI sintética previamente válida, reabre sem revisão, verifica que token/conta/recovery são bloqueados e que a sessão não é destruída pela inspeção. Quando o operador volta a marcar a revisão, a recuperação com verificação no servidor funciona no mesmo controller simulado.

Nenhuma chave real, conta de cliente, reserva de cota ou alteração na lista de Redirect URLs do Supabase foi usada. Esta é **defesa em profundidade**, não comprovação de que os redirects estão configurados no serviço oficial ou de que o login real Google foi homologado.
