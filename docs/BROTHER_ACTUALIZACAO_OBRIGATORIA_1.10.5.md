# Brother Matrizes — login obrigatório na importação e atualização mínima (candidata 1.10.5)

> ESTADO: proposta na PR #58, sem ativação em produção. Produção vigente: 1.10.4.
> O APK 1.0.0 não pode receber código retroativamente. Uma versão antiga que
> trabalhe inteiramente offline e nunca chame servidor não pode ser bloqueada
> remotamente por uma alteração feita hoje.

## Importação de matrizes

1. Ao tocar **Abrir matriz** na Home, Viewer ou pesquisa de matrizes, o app usa
   `BrotherMatrizesAccountService.ensureGoogleSession()` antes de abrir o
   seletor. Sem sessão, navega para **Minha Conta** e explica o motivo.
2. Arquivos de matriz escolhidos (inclusive por compartilhamento ou Abrir com
   Android) passam pela função `authorizeImport(context, isFont=false)`
   ANTES de carregar o arquivo; `finalizeImport(... success=true/false)` é
   chamado após processar e registrar o resultado no Supabase. Cota gratuita
   é de até 5 matrizes importadas com sucesso por mês na conta autenticada.
3. Ao abrir ZIP/RAR, o extrator já usa a reserva por item `canImport` e
   `onImportFinished`; não reservar duas vezes.
4. Abrir novamente um projeto local previamente importado não é nova
   importação e NÃO deve consumir outra cota.
5. No serviço `brother-usage`, identidade Google é confirmada usando o
   `auth.getUser(token)` e `user.app_metadata.provider/providers`, jamais
   `user_metadata`. Cotas e licenças PRO são decididas no servidor.
6. Durante `BETA_LOCAL_IMPORTS=true` no debug, a reserva pode ser local
   para QA; o APK release comercial mantém `false` e sempre exige servidor.

## Atualização obrigatória

- `BrotherMandatoryUpdateGate` verifica o manifesto oficial GitHub
  `updates/stable.json` ao abrir/voltar ao app. Se
  `installedVersionCode < minSupportedVersionCode <= publishedVersionCode`,
  bloqueia todas as demais telas com um botão **Atualizar agora** e
  **Verificar novamente**, sem `Depois`, sem `X` e sem ação Voltar.
- O APK é baixado apenas do canal oficial por `BrotherApkUpdater`,
  com hash SHA-256, packageName, versionCode e certificado original conferidos.
  A instalação depende de confirmação do Android e não pode ser silenciosa.
- Para clientes que já receberam a exigência online, metadados confirmados
  são mantidos em SharedPreferences; a ausência temporária de conexão NÃO
  libera a tela obrigatória. Se o dispositivo nunca verificou e está offline,
  não se pode afirmar versão incompatível, então a checagem falha aberta.
- **O manifesto público está com `minSupportedVersionCode: 0`. Não subir
  antes da publicação estável e testada do APK 1.10.5 / versionCode 181.**
- A função server-side `brother-usage` possui um interruptor opcional,
  `BROTHER_MINIMUM_CLIENT_VERSION_CODE`, inicialmente desativado (unset/0).
  Se ativado após publicação, importações autenticadas por clientes com
  versionCode menor ou sem `X-Brother-App-Version-Code` retornam HTTP 426
  e instrução de atualização. Isso só alcança versões que usam a API,
  e o header reportado pelo cliente não é atestado criptograficamente.

## Ativação — ordem mandatória

1. Homologar APK v1.10.5 (181) instalado *por cima*, restaurar Google,
   validar arquivo PES/DST, ZIP, Gratuito/PRO, quota/limite e offline.
2. Confirmar que o APK 181 está publicamente disponível, assinado com o
   certificado comercial original, instalável e com SHA correto.
3. Publicar `updates/stable.json` da **mesma** versão 181, conferindo
   `downloadApkUrl`, `sha256`, `versionCode` e `applicationId`.
4. Apenas depois de homologar e testar download atualizar
   `minSupportedVersionCode: 181` no manifesto, com aprovação explícita.
5. Implante a versão revista da função `brother-usage` após testes de
   segurança e configure `BROTHER_MINIMUM_CLIENT_VERSION_CODE=181`
   **somente depois da publicação verificada**, sabendo que clientes
   sem API ainda podem trabalhar offline.
6. Comunicados aos usuários devem incluir link real do APK, explicação de
   login Google obrigatório, motivo da atualização e orientação para
   **instalar por cima sem desinstalar**. Monitorar erros e suporte.

## Não alegar

- Não há forma de alterar remotamente a interface do APK 1.0.0 para exibir
  modal novo se esse código não foi incluído na versão antiga.
- Não há como impedir integralmente uso offline de APKs antigos sem controles
  previamente existentes; o corte do backend só impede ações feitas via API.
- Não prometer eliminação de avisos do Play Protect nem instalação sem ação
  do usuário.
