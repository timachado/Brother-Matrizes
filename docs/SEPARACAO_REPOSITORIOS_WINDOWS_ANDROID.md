# Brother Matrizes — divisão oficial de código Android e Windows

## Objetivo

Manter **dois repositórios GitHub autônomos** para reduzir riscos de publicação cruzada, preservar o aplicativo Android comercial e preparar a versão Windows sem textos/avisos da plataforma Android.

| Plataforma | Repositório | Estado em 10/11/2026 |
|---|---|---|
| Android | `timachado/Brother-Matrizes` | Existente; branch `main` permanece o produto Android |
| Windows | `timachado/Brother-Matrizes-Windows` | **NÃO CRIADO**; recomendado privado até homologação comercial |

**Esta branch é um preparo de migração; NÃO declara que o segundo repositório foi criado.**
Não remover `windows-fiel/`, branches de QA ou PRs do repo Android antes de obter clone funcional no repo Windows e builds separados aprovados.

## Diagnóstico técnico

Atualmente `windows-fiel/build.gradle.kts` busca arquivos em `../app/src/main/java/com/timachado/brothermatrizes`, além de outros caminhos em `../app`. Isso ocorre em tarefas como `copyAndroidHome`, `copyAndroidCreateName`, visualização, envio e desenho. Simplesmente mover `windows-fiel/` para outro GitHub sem esses fontes **quebra a compilação**.

## Estratégia de migração sem regressões

1. Provisionar repositório **privado** `timachado/Brother-Matrizes-Windows` e autorizar o conector GitHub da T.I. Machado a acessá-lo. Não transportar credenciais, certificados de assinatura, chaves de serviço nem secrets dos workflows.
2. Criar branch `migration/windows-standalone` no novo repositório e importar snapshot da **branch Windows aprovada**, incluindo `windows-fiel/`, Gradle wrapper e os módulos/fontes obrigatórios identificados pelo build atual. Não copiar históricos de usuários nem arquivos de produção.
3. Internalizar dependências `../app` em código Windows (e em um `core/` de domínio portável quando seguro). Reapontar `windows-fiel/build.gradle.kts` para arquivos **locais ao novo repositório**. Preservar funcionalidade; não fazer reimplementação decorativa, mudança de motor ou liberação fictícia de recursos Pro.
4. Ativar CI Windows independente: compilação Kotlin/JVM Compose, testes, app-image/EXE portátil, ícone, atalhos e capturas de QA. Separar versão, release, changelog e manifesto Windows dos fluxos APK/FCM Android.
5. Validar E2E em PC Windows com conta de teste autorizada: Google OAuth/PKCE `127.0.0.1`, e-mail visível em Minha Conta, licença via backend, proteção DPAPI, fontes, bibliotecas, SVG em orientação correta, rotação do mouse, Picture-in-Picture, importação, simulador, backup e restauração.
6. Apenas após **compilação, QA funcional e comparação de paridade aprovadas**: tornar o novo repositório a fonte oficial do Windows; arquivar/remover componentes e workflows Windows do antigo repo por um PR separado e auditado. O Android continua em seu repositório sem alterações de comportamento.

## Identidade de interface — Windows

- Nome público: **Brother Matrizes Windows**.
- Em `Minha Conta`: `Planos e benefícios — Brother Matrizes Windows`.
- No catálogo, Telegram e backups: textos próprios do Windows; manter compatibilidade com formatos legados.
- No painel Comunidade: mostrar apenas canal de atualizações **Windows**, grupo e feedback. **Não** apresentar releases APK Android como notícias, nem consultar automaticamente notificações de Android.
- O nome de uma classe interna `DesktopAndroid*` **não é texto público**; renomear apenas durante migração com testes de referência para evitar quebrar importação de backups e lógica de paridade.

## Proteções e critérios para declarar a migração concluída

- Nenhuma alteração em `main` Android nem em Supabase de produção durante a preparação.
- Nenhum `../app` obrigatório em Gradle do novo repositório.
- Nenhuma execução Windows depende do manifesto Android `updates/stable.json`.
- APK e EXE publicados por pipelines distintos; assinaturas, segredos, permissões e artefatos separados.
- O Supabase Auth e a verificação da assinatura continuam oficiais e com autorização de servidor, sem usar flag local para Pro.
- CI Windows e teste físico com login Google **obrigatórios** antes de disponibilizar EXE comercial.

## Estado deste preparo

- Branch de diagnóstico OAuth: `fix/windows-google-account-visibility-20261110`, PR #230.
- Branch de textos/plataforma: `refactor/windows-ui-platform-separation-20261110`.
- Repositório Windows ainda ausente; a migração de código e seu CI só podem ser marcados como completos após existir o destino autorizado.
