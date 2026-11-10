# Brother Matrizes Windows — canal oficial de novidades (PR #226)

**Status em 09/11/2026:** estrutura de leitura Windows implementada SOMENTE na branch QA. **Manifesto de release Windows estável ainda não publicado**, nenhum pacote comercial disponibilizado. Não equivale a Firebase Cloud Messaging nem instala atualizações.

## Separação de plataformas

| Origem | Aplicativo consumidor | Consequência |
|---|---|---|
| `main/updates/stable.json` | Android comercial e leitura de notícias Android na tela Comunidade Windows | Nunca anuncia instalação Windows, não baixa APK no Windows |
| `main/updates/windows/stable.json` | Consulta Windows implementada em `DesktopOfficialWindowsReleaseNotice` | HTTP 404 significa manifesto oficial ainda indisponível; nenhuma atualização oferecida |

A tela Comunidade Windows possui **dois cartões separados**: “Atualizações Brother Matrizes Windows” e “Atualizações oficiais Android”. O usuário só consulta o canal Windows mediante clique. O canal não faz downloads, não executa ou instala binários, não gera licenças, não verifica cotas e não altera o aplicativo Android.

O aplicativo verifica HTTPS, host e caminho imutáveis, ignora redirecionamentos, limita tamanho e tempo de leitura, exige JSON real (GitHub Raw pode usar MIME `text/plain`), `applicationId` original, `platform:windows`, `channel:stable`, `status:published`, `versionName` sem sufixo, `versionCode` positivo e `releaseTag:windows-v<versão>`. Links de download fornecidos no JSON **não são consumidos**. Notas visíveis são limitadas e URLs são removidas.

## Exemplo de contrato — apenas documento, NÃO colocar em `main/updates/windows/stable.json` ainda

```json
{
  "applicationId": "com.timachado.brothermatrizes",
  "platform": "windows",
  "channel": "stable",
  "status": "published",
  "versionName": "1.0.0",
  "versionCode": 100,
  "releaseTag": "windows-v1.0.0",
  "releaseNotes": "Compatibilidade validada com matrizes oficiais.;Editor e biblioteca Windows homologados.;Versão portátil assinada e testada."
}
```

**Os valores são fictícios para testes de contrato. Não representam uma versão lançada.**

## Portões antes de publicação

1. Concluir paridade funcional Android → Windows, formatos, edição e backups com testes E2E em Windows real.
2. Homologar Google/PKCE, status de cotas/entitlements V2 no backend e importação comercial, **em staging primeiro**. Não implantar alterações no Supabase de produção sem aprovação específica.
3. Gerar pacote portátil comercial, com ícone original, assinatura digital, teste antivírus/SmartScreen, verificação de hash e execução no PC do cliente.
4. Publicar um release oficial `windows-vN.N.N` no repositório somente após a aprovação de distribuição; publicar o manifesto `updates/windows/stable.json` **por último**, com revisão dupla. Não substituir o manifesto Android.
5. Testar atualização de notícias Windows sem download automático. Notificações automáticas específicas Windows exigem controle de consentimento separado dos avisos Android antes de serem habilitadas.

## Evidências de QA nesta PR

- `DesktopOfficialWindowsReleaseNoticeTest`: 404/falta de manifesto, metadados sintéticos válidos, rejeição de Android/QA/draft, número/versão/tag falsos, MIME HTML, redirecionamento HTTP, indisponibilidade e URLs de terceiros nas notas.
- Testes de paridade MIME do GitHub Raw em `DesktopOfficialReleaseNoticeTest` e `DesktopOfficialCommunityGroupResolverTest`.
- Workflow `.github/workflows/brother-windows-portable-qa.yml` compila e executa esses testes sem conectar à rede de produção. A confirmação do novo workflow deve ser registrada separadamente.

**Esta etapa não publica release, não altera WordPress, Supabase, Android comercial ou arquivos de usuários.**


## Consentimento de avisos automáticos para Windows (09/11/2026)

O pacote QA passa a ter duas preferências independentes, ambas inicialmente **desativadas**:

- **Novidades Android no Windows:** `windows-news-v1.txt`, leitura de `updates/stable.json`.
- **Releases Windows:** `windows-official-release-notices-v1.txt`, leitura EXCLUSIVA de `updates/windows/stable.json`.

A escolha sobre Android jamais habilita o recebimento automático de anúncios Windows e vice-versa. O registro local armazena somente a escolha e o maior `versionCode` já informado. O app só consulta o feed Windows **enquanto a janela normal está aberta**, no máximo a cada **6 horas**; modo captura/QA é silencioso. Nenhum serviço residente, inicialização com Windows, FCM, agendamento externo ou serviço em nuvem é criado.

Ao habilitar, a consulta inicial registra a versão já publicada como conhecida, sem notificar notícias antigas. Caso o manifesto retorne **404**, a preferência pode ser habilitada, mas **não há qualquer anúncio**; quando ocorrer a primeira publicação real, essa será a primeira mensagem. Em erro de rede ou manifesto inválido, uma nova ativação falha sem persistir consentimento. Ao desabilitar, a próxima consulta não acontece. Após reiniciar o aplicativo, o mesmo número de versão não produz outro aviso, inclusive quando o sistema não disponibilizou a notificação nativa.

**Não é atualização automática:** o leitor apenas oferece texto, não consome campos de download, não instala binários e não abre URLs do manifesto. Também não valida o direito comercial do usuário. Nenhum manifesto de versão Windows foi criado em `main` nesta etapa.

**QA unitária:** `DesktopWindowsOfficialReleaseAutoCheckTest` valida opt-in independente, canal não publicado, versão seguinte, reinício, erro de rede, desistência durante consulta e sistema sem bandeja de notificação. O workflow Windows portable deve compilar e executar esses testes com dados sintéticos.
