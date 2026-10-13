# Brother Matrizes — login Google e atualização obrigatória

Data: 13/10/2026. PR #58, candidata 1.10.5 (versionCode 181).

## Estado real

- **Produção pública:** v1.10.4, code 180; `updates/stable.json` mantém `minSupportedVersionCode: 0`. Não bloquear 1.10.4 enquanto 1.10.5 não for publicada.
- **Login/cotas:** antes de escolher arquivo, `requestMatrixPicker` exige sessão Google; importações por `ACTION_VIEW/SEND` também exigem sessão e usam reserva/finalização no servidor. `BrotherGoogleProviderRules` valida `auth.app_metadata` (não `user_metadata`). Conta com apenas senha/e-mail não é suficiente.
- **Servidor:** Edge Function `brother-usage` versão 2 implantada no projeto existente; Google verificado com `auth.getUser(token)` e `user.app_metadata`. Nenhum dado ou configuração de pagamento foi migrado. Política de versões existe no código e utiliza `BROTHER_MINIMUM_CLIENT_VERSION_CODE`; não configuramos/ativamos esse mínimo durante a homologação.
- **APK 1.10.5:** `BrotherMandatoryUpdateGate` consulta o manifesto stable do GitHub antes de liberar navegação. Se a versão instalada estiver abaixo de `minSupportedVersionCode`, mantém tela não dispensável com download seguro e botão de verificação. Se o estado da versão não puder ser confirmado, exige conexão/reverificação e não dá acesso às telas.
- **Instalação:** atualização é iniciada pelo usuário usando a instalação Android; APK oficial deve corresponder a pacote, SHA-256, versionCode e certificado comercial original. Não há instalação silenciosa nem remoção automática do Play Protect.

## Limitação que não pode ser eliminada remotamente

Uma versão 1.0.0 ou similar que **não traz a rotina de bloqueio nem utiliza os serviços online** não pode receber um novo bloqueio de interface por meio do manifesto. O Android não permite alterar código de APK instalado sem instalação de outro APK. É possível negar operações em serviços que versões antigas realmente chamem, mas NÃO prometer bloqueio de funções locais sem rede. Também não inventar uma notificação in-app compatível com APK que não possua essa função.

## Ativação somente APÓS publicação comercial

1. Completar QA da candidata 1.10.5 (code 181), inclusive telas, sessão Google, erros offline, importação de matriz PES/ZIP, fonte ZIP, entitlement, licença vitalícia e atualização.
2. Testar atualização **por cima** da 1.10.4 usando APK release assinado com certificado comercial original, sem desinstalação e com os projetos preservados.
3. Publicar release estável 1.10.5 com APK verificado e SHA-256; confirmar URL direta funcionando, sem HTML/ZIP trocado.
4. Atualizar atomicamente `updates/stable.json`: `versionName=1.10.5`, `versionCode=181`, `minSupportedVersionCode=181`, `downloadApkUrl` direto e SHA-256 correto do APK. Não alterar arquivo estável enquanto o binário não existir.
5. Verificar a partir de aparelho com 1.10.4: se o APK antigo tiver verificação de atualizações suficiente, deve oferecer atualização; clientes sem bloqueio local ainda poderão acessar funções offline, limitação declarada.
6. **Somente após confirmar instalação e acesso da versão 181**, configurar no Supabase o segredo `BROTHER_MINIMUM_CLIENT_VERSION_CODE=181` para que `brother-usage` retorne HTTP 426 em reservas/finalizações de clientes com versão abaixo de 181 ou sem header. Nunca ativar mínimo maior que a última versão assinada disponível.
7. Validar duas contas reais autorizadas: Google + PRO (importações sem cota gratuita), Google + Gratuito (3 fontes e 5 matrizes por mês, vinculadas ao usuário) e recusa de sessão não Google. Também validar tentativa de uso de versão 180 e recovery após atualizar.
8. Verificar logs sem registrar tokens nem dados pessoais e não publicar avisos em massa antes da autorização comercial.

## Cenários de risco

- Internet fora / GitHub indisponível: novos APKs com verificação obrigatória exibem ação **Verificar novamente** e acesso à página oficial em vez de ignorar o bloqueio.
- WooCommerce/Efí indisponível: não conceder PRO por conta própria; manter as licenças existentes e tratar erro informativo.
- Manifesto com `minSupportedVersionCode` maior que `versionCode`: inválido; não publicar.
- Atualização cancelada no instalador: o bloqueio da nova versão permanece; instalação pode ser repetida. Nunca remover dados, arquivos nem certificados.
- 1.0.0 offline sem verificação remota: **não controlável remotamente**; qualquer alegação contrária é incorreta.
