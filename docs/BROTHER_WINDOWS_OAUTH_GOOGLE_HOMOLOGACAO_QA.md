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
  `http://127.0.0.1:*/brother-matrizes/auth?bm_state=*`
  **Não aplicar automaticamente**. Validar correspondência com o mecanismo
  de globs e com o formato **exato** do redirect gerado no teste real.
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
