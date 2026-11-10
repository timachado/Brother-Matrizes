# Brother Matrizes Windows — pré-homologação Google OAuth e cotas TTF/OTF

**Estado:** QA. **Não há autorização para comercialização nem para habilitar importações TTF/OTF.**

## Fatos verificados em 07/11/2026

- Supabase `dwpcddiramxlhavdmmyn`: projeto `ACTIVE_HEALTHY`.
- Edge Functions `brother-entitlement` v5 e `brother-usage` v7: `ACTIVE`.
- `DesktopSupabaseGooglePkceClient`: fluxo Google/PKCE via navegador externo, callback local IPv4 `127.0.0.1`, porta efêmera e estado aleatório; `DesktopWindowsAuthQaController`: sessão protegida com DPAPI e verificação real da identidade antes de persistir.
- `DesktopCommercialFontImportGate`: por padrão utiliza `WindowsFontSessionUnavailable` e **rejeita importação antes de obter token Google validado e reserva de cota no servidor**.
- **Não foi possível consultar as URLs de retorno permitidas no Supabase Auth com o conector disponível.** O status ativo do projeto ou do provedor Google não prova que os redirecionamentos Windows estejam homologados.
- A tela **Minha Conta** pode fazer, apenas com opt-in QA, uma consulta pública `GET /auth/v1/settings` para `external.google`. Isso não autentica usuário, não consome cota e **não verifica a allowlist**.

## Configuração correta, sem alterar o Android

1. Verificar **Supabase Dashboard → Authentication → URL Configuration**, especificamente **Redirect URLs**:
   <https://supabase.com/dashboard/project/dwpcddiramxlhavdmmyn/auth/url-configuration>
2. No fluxo Windows atual de QA, o `redirect_to` é uma URL do tipo `http://127.0.0.1:PORTA/brother-matrizes/auth?bm_state=ESTADO`. A porta é alocada dinamicamente; **não há um URI único e fixo**. Uma entrada wildcard limitada ao loopback para testes é **candidata**, não configuração verificada:
   `http://127.0.0.1:*/brother-matrizes/auth?bm_state=*`
   Validar cuidadosamente a sintaxe de wildcard do Supabase (inclusive o caráter `?` e o estado); **não adicionar `http://**`, `http://127.0.0.1/**` nem URLs genéricas em produção**. A versão comercial deve preferir um redirecionamento estável e específico, com análise de conflito de portas e threat model antes de sua liberação.
3. Confirmar que o Google Provider está ativo no Supabase. O endpoint público `/auth/v1/settings` indica somente a disponibilidade do provedor.
4. **No Google Cloud**, os Authorized redirect URIs do cliente web devem conter o callback HTTPS do próprio Supabase:
   `https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/callback`
   **Não configurar a URL `127.0.0.1` como callback Google**. O loopback é retorno *Supabase → aplicativo Windows*.
5. Não compartilhar client secret, service role, tokens OAuth, certificados, credenciais ou cookies no chat, GitHub, logs ou no ZIP. O aplicativo usa apenas chave `sb_publishable_...` fornecida em runtime QA autorizado.
6. Somente após confirmar de modo explícito a allowlist, realizar login Google numa instalação Windows de QA com **conta autorizada de teste**. Verificar estado PKCE, local loopback, resposta `getUser`, origem Google, renovação, logout, DPAPI e cancelamento, sem usar conta de cliente.

## Verificação local em Windows autorizado (sem alterar produção)

O executável QA anterior permanece fechado por padrão. Para disponibilizar a opção de diagnóstico no Windows, o responsável pela homologação deve usar **somente uma chave publicável** já existente e ativa do projeto. Não inserir secret keys.

Em uma sessão temporária do PowerShell, a partir da pasta portátil **completa**:

```powershell
# Usar SOMENTE a chave publicável do projeto OFICIAL Brother Matrizes
# e apenas depois de verificar a allowlist do OAuth e autorizar o teste.
$env:BROTHER_WINDOWS_QA_GOOGLE_AUTH = "enabled"
$env:BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY = "<CHAVE_PUBLICAVEL_DO_SUPABASE_BROTHER_MATRIZES>"
.\BrotherMatrizesQA.exe
# Ao finalizar os testes, limpar as variáveis desta sessão:
Remove-Item Env:BROTHER_WINDOWS_QA_GOOGLE_AUTH -ErrorAction SilentlyContinue
Remove-Item Env:BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY -ErrorAction SilentlyContinue
```

Na aba **Minha Conta**, utilizar **Verificar provedor Google (sem login)**. O retorno indica somente se `external.google` está ativo no endpoint público. Não abrir login de contas reais antes de a configuração de callback estar autorizada e testada. O aplicativo não grava essa chave em arquivos nem exibe a chave em logs.

## Autoteste do retorno local sem credenciais

Na tela **Minha Conta**, o botão **Testar retorno local OAuth (sem Google)** fica disponível mesmo sem chave Supabase. Ele só inicia após clique explícito e:

1. Abre temporariamente um servidor vinculado exclusivamente a `127.0.0.1` na porta local efêmera.
2. Envia um callback **fictício** com estado incorreto, exigindo HTTP 400 sem encerrar a sessão de escuta.
3. Envia um segundo callback fictício com o estado correto e verifica HTTP 200, proteção `no-store` e entrega única do código sintético.
4. Fecha a porta local e a thread temporária mesmo quando há erro.

O diagnóstico **não abre navegador**, **não acessa Google/Supabase**, não solicita dados pessoais, não grava tokens, não usa cotas e não importa fontes. Um resultado aprovado confirma **somente** o funcionamento do callback local neste PC — **não confirma que o Supabase aceite a Redirect URL**. Esse último ponto continua exigindo verificação administrativa no Dashboard e login de QA explicitamente autorizado.

Os testes automatizados `DesktopPkceLoopbackSelfTestTest` executam o mesmo fluxo em socket real no runner Windows, junto com `DesktopPkceLoopbackListenerTest`, e são exigidos antes da geração do EXE portátil.

## Importação TTF/OTF — portão comercial

A interface Fontes permanece **somente leitura**. O `brother-usage` valida JWT e reserva no servidor a operação `import_font`. A operação completa deverá respeitar:

- identidade Google confirmada pelo servidor e sessão válida;
- cliente Windows `DesktopBrotherFontUsageGateway` exige uma chave `sb_publishable_...` (nunca `sb_secret_...`, `service_role` ou JWT privado) e recusa espaços/caracteres de controle; a configuração não deve embutir segredos na distribuição;
- campos `allowed` na reserva e `ok` na finalização devem ser **booleanos JSON verdadeiros**. A string `"true"` não autoriza operações;
- `reserve(import_font)` antes de ler/importar a fonte externa;
- nome/assinatura TTF/OTF e tamanho verificados, sem links simbólicos;
- `finalize(success=true)` apenas quando um arquivo novo é efetivamente criado;
- `finalize(success=false)` para duplicatas/falhas, com rollback seguro quando o servidor não confirma;
- nenhuma liberação por plano local, flag QA, pré-visualização da assinatura ou `user_metadata`;
- comparação de pontadas com o motor Satin Android aprovado antes de liberar geração de matrizes comerciais.

A reserva/finalização via server-side **não pode ser ensaiada no projeto de produção com contas de cliente**. Usar mocks em CI e ambiente autorizado de teste para o teste integrado.

## Critérios objetivos

- CI `brother-windows-account-readonly-qa.yml` aprova `DesktopGoogleProviderReadinessTest`, Auth PKCE, leitura entitlement e a captura Minha Conta;
- captura não contém dados reais de cliente;
- configuração real da allowlist é confirmada pela administração autorizada, sem inferência;
- teste manual do redirect e da renovação sem vazamento;
- somente após portabilidade do motor de bordado e verificação das cotas considerar habilitar TTF/OTF e assinar o EXE.

**Fontes técnicas:** [Supabase Auth Redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls) e [Google Sign-In](https://supabase.com/docs/guides/auth/social-sign-in/auth-google).

## Decisão do proprietário: mesmo backend Android/Windows — 08/11/2026

A versão Windows é uma adaptação do **Brother Matrizes Android oficial**,
não um produto independente. **Não criar um segundo Supabase**.
O único backend autorizado para o aplicativo Windows é o projeto
`dwpcddiramxlhavdmmyn`, que já sustenta o Android.
Usuários, identidades Google, planos Pro, licenças e servidor de cotas
continuam sob as **mesmas regras de autorização server-side**.
Não há tabelas, contas ou políticas Pro paralelas no PC.

As classes `DesktopBrotherBackendTarget.official()` e
`DesktopWindowsAccountQaOptIn` usam o identificador oficial fixo,
sem variável de staging. Os clientes PKCE, token/refresh, GET /user,
GET /settings, GET brother-entitlement e o diagnóstico de cotas
usam o mesmo host. A sessão local Windows permanece protegida
separadamente por DPAPI em `qa-brother-account-<project-ref>`;
esta pasta **não significa outro projeto Supabase**.
Não reutilizar os arquivos locais de sessão do Android.

Por segurança, a QA distribuível mantém o login desligado por padrão.
O opt-in explicitamente configurado apenas prepara o fluxo; não abre
navegador nem acessa rede até o operador invocar uma ação.
Validar primeiro a allowlist PKCE em procedimento autorizado e usar
**conta de teste sem direitos pagos reais**. Nunca modificar o
Supabase, cadastrar usuário de cliente, consumir cotas ou alterar
assinaturas automaticamente. O pipeline utiliza
`HttpURLConnection` simulado, sem tocar a rede comercial.

**Prioridade retomada:** portar integralmente as 16 rotas Android para
interface expandida de PC, substituindo ações `informNotPorted` por
adaptações nativas (arquivos, compartilhamento, teclado, mouse,
biblioteca, editor etc.). Não se consegue portar um APK diretamente
como um EXE Windows sem reconstrução das integrações de plataforma.
Os projetos FLP/DST/PES/JEF armazenados no dispositivo Android não
aparecem automaticamente no PC; é necessário abrir/importar os
arquivos ou homologar uma sincronização autorizada.
