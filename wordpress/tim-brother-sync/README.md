## Novo na versão 1.1.0 — App Commerce Core

Integração somente de leitura (filtro `get_post_metadata`) dos campos `_ti_app_version`, `_ti_app_release_notes`, `_ti_app_apk_attachment_id` e `_ti_app_download`, exclusivamente para o cadastro `ti_app` **307**. Campos confirmados em cópia do App Commerce Core **2.6.6**. Acesso `_ti_app_download_access` é consultado, **nunca alterado**.

- Versão e notas aparecem dinamicamente a partir do manifesto `main/updates/stable.json`.
- Se o Core está em modo `free`, o arquivo GitHub validado torna-se a origem de download e o attachment antigo permanece armazenado, sem exclusão.
- Se está em `licensed`, as URLs/attachments do Core não são alteradas e o fluxo comercial é mantido.
- Nenhum campo financeiro, produto, pedido, plano ou licença é modificado.
- Se o manifesto não estiver disponível, o Core recebe os metadados originais.
- Download de APK disponibilizado **publicamente** no GitHub pode ser obtido fora da loja. Recursos Pro continuam dependendo da licença validada no aplicativo.
- A versão de produção atual do Core pode ser mais recente que 2.6.6: testar primeiro a UI, o link e a tela de compra antes de tratar como concluído.

**Instalação única:** no WordPress, atualize **Brother Matrizes — Atualizações Automáticas** instalando o ZIP v1.1.0 e aceitando substituir a versão antiga. **Não substitua o App Commerce Core**. Em Ferramentas → Brother Matrizes, veja o status da integração. Desativar apenas esse plugin restaura os dados originais do Core sem apagar registros.

---

# Brother Matrizes — sincronização automática no WordPress

Este plugin faz a **sincronização de informações de atualizações** entre o GitHub e o WordPress. Foi criado especificamente para `timachado.ifree.page`; **não modifica o WooCommerce, produtos, pedidos, preços, login, Supabase, Efí ou licenças**.

## Instalação única (WordPress → Plugins)
1. Envie o arquivo `tim-brother-sync.zip` pelo painel **Plugins → Adicionar novo → Enviar plugin → Instalar agora → Ativar**.
2. Confirme em **Ferramentas → Brother Matrizes** que aparece a versão atual do manifesto.
3. Abra o endpoint `https://timachado.ifree.page/wp-json/tim-brother-updates/v1/latest?channel=stable` e confira a versão e o hash.
4. Para mostrar o cartão de novidades também na página pública do aplicativo, adicione apenas **uma vez** o shortcode `[brother_matrizes_release]` ao conteúdo do WordPress. Ele mantém o botão atual do site. Opcionalmente, `[brother_matrizes_release direct="yes"]` mostra um botão de download direto; use-o apenas se a distribuição do APK público estiver alinhada às licenças comerciais.
5. Nas próximas versões **não é necessário editar o WordPress**: ao atualizar `main/updates/stable.json` no GitHub, o plugin consulta os novos dados automaticamente.

## Como funciona
- Intercepta **somente GET** para `/tim-brother-updates/v1/latest?channel=stable`; outras rotas do site continuam exatamente iguais.
- Busca no GitHub Raw a versão, hash SHA-256, URL do APK e release notes; valida appId, channel, versão, hash e domínio.
- Usa cache de 3 minutos (transient) para evitar solicitações em excesso.
- Se o WordPress não conseguir consultar o GitHub, **preserva o endpoint anterior**, em vez de substituir com dados inválidos.
- O shortcode usa consulta HTTPS do navegador ao manifesto GitHub. Isso ajuda em hospedagens que bloqueiam conexões PHP externas; sua saída não contém credenciais nem modifica páginas automaticamente.
- Endpoint de teste: `/wp-json/tim-brother-sync/v1/latest`.

## Publicação comercial automatizada no GitHub

Workflow: `.github/workflows/brother-commercial-release.yml`, exibido como **Brother Matrizes - Lancamento Comercial Automatizado** na aba Actions.

A publicação comercial só pode ser executada a partir de uma branch revisada `release/brother-matrizes-X.Y.Z`; a versão/código devem coincidir com `app/build.gradle.kts` e ser maiores que a versão pública.

1. Rodar **Actions → Brother Matrizes - Lançamento Comercial Automatizado → Run workflow** (na branch main), informar **source_ref**, **version_name**, **version_code** e **release_notes**.
2. O workflow executa testes e build Android, assina com o certificado comercial original, confere pacote, código, assinatura Android v2/v3 e SHA-256.
3. Publica o APK em GitHub Releases. Depois baixa o arquivo do endereço público **sem token** e compara SHA-256.
4. Só depois atualiza o `updates/stable.json` da branch main, mantendo a página comercial do WordPress como `downloadUrl` e fornecendo a URL do binário em `downloadApkUrl`.
5. Com o plugin instalado, WordPress sincroniza versão, novidades e link; quem já está no novo aplicativo baixa o APK pelo botão interno.
6. O alerta FCM opcional (`announce`) roda somente depois da conferência do manifesto; configure o serviço Firebase antes de habilitá-lo. Um FCM aceito não garante entrega a todos os dispositivos.

## Configuração segura — uma única vez

No GitHub Settings → Environments, configure `brother-release-signing`, de preferência com proteção de aprovação manual. Armazene neste ambiente ou em GitHub Actions Secrets:

- `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64` — keystore comercial original em Base64
- `BROTHER_MATRIZES_RELEASE_STORE_PASSWORD` — senha do keystore comercial
- `BROTHER_MATRIZES_RELEASE_KEY_ALIAS` — alias da chave comercial original
- `BROTHER_MATRIZES_RELEASE_KEY_PASSWORD` — senha da chave comercial original
- `BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON` — credencial do projeto Firebase, somente se optar por notificação automática

**Nunca** envie o `.jks`, suas senhas ou credenciais Firebase ao repositório, WordPress, Issues, PRs ou arquivo público. Não recrie uma nova chave: o certificado X.509 esperado pelo workflow é `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`. Se os segredos estiverem ausentes ou a assinatura divergir, o workflow bloqueia a publicação.

## Limitações

- O plugin precisa ser instalado e ativado **uma única vez** no WordPress. Como `timachado.ifree.page` devolve HTML de bloqueio para clientes REST externos, a instalação não pôde ser executada automaticamente por esta integração.
- O plugin não muda textos de versões antigas que tenham sido escritos manualmente na página: nesse caso, remova a indicação antiga uma vez e use o shortcode dinâmico.
- PHP do servidor precisa alcançar `raw.githubusercontent.com` para que o REST seja sincronizado. Caso o hosting bloqueie conexões PHP, o endpoint antigo permanece; o shortcode e o app atualizado continuam consultando o GitHub diretamente.
- O plugin não modifica ou contorna as análises de Play Protect; o registro do desenvolvedor Google é um processo independente.
- O workflow não garante estabilidade de serviços externos e não substitui o QA em dispositivos físicos.

## Suporte e rollback

Se houver problemas, desative o plugin no painel WordPress: o endpoint preexistente volta a atender normalmente, sem alteração de banco de dados ou produtos. Nenhum dado é excluído.


---

## Comunidade e feedback (plugin 1.2.0)

Nova central opcional que não modifica o fluxo de compra/assinatura ou de download.

1. Após instalar a atualização do plugin, acesse **Ferramentas → Brother — Comunidade**.
2. Crie o canal no WhatsApp, copie o **link oficial do canal** e salve no campo mostrado. Se estiver vazio, os botões de seguir não são exibidos.
3. Edite **somente** a página pública Brother Matrizes no WordPress e coloque uma vez o shortcode \`[brother_matrizes_community]\`.
4. A central mostra a versão estável existente, direciona ao canal público e traz formulário de sugestões. Feedbacks são salvos **privadamente** em **Ferramentas → Brother — Feedbacks**; nunca publicados automaticamente.
5. O aplicativo consulta o endpoint de leitura pública \`/wp-json/tim-brother-sync/v1/community\`. Atualizar o link no WordPress **não exige gerar outro APK**. Em caso de falha de rede, o app mantém acesso à página oficial; o canal fica oculto até que a consulta funcione.

**Privacidade:** o formulário não solicita senhas ou números de licença. E-mail é opcional, armazenado apenas com autorização explícita. O formulário usa nonce, campo anti-bot e limite por origem; não há promessa de proteção absoluta contra spam. Revise e exclua feedbacks antigos de acordo com sua política de privacidade e LGPD. Em sites que usam cache agressivo de página, teste a validade do nonce do formulário. A T.I. Machado deve informar o tratamento de dados na política de privacidade.

**Rollback:** remova o shortcode ou desative somente o plugin Brother Matrizes — Atualizações Automáticas. Não remova o App Commerce Core. Os feedbacks já gravados no WordPress não são apagados automaticamente por desativação; sua exclusão deve ser uma decisão administrativa.

**Fallback de hospedagem:** se o link não estiver definido em Ferramentas → Brother — Comunidade, a central pública consulta `https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/community.json` pelo navegador. Esse JSON também é a fonte prioritária do novo Android, evitando a página HTML de bloqueio que às vezes substitui respostas da API WordPress. Para manter tudo sincronizado, cadastre o link no JSON do GitHub e evite um valor diferente na opção local do WordPress. A URL é validada como `https://whatsapp.com/channel/...`; não inclua tokens ou credenciais.
