# Brother Matrizes 1.10.1 — Hotfix do atualizador e visual Premium

Aplicativo Android `com.timachado.brothermatrizes`, versão **1.10.1**, código **177**.

## 1. Texto final para notificação Android / Firebase

**Título:** Brother Matrizes 1.10.1 chegou!

**Mensagem:** Sua nova versão está pronta! Confira o visual renovado, melhorias nas atualizações e mais estabilidade. Toque para conhecer as novidades.

Evitar mensagens extensas, emojis repetidos, hashes, referências a GitHub, WordPress, Firebase, APK ou erros de servidor.

## 2. Card Premium no Início

- Selo: **✦ NOVA VERSÃO** · **v1.10.1**
- Título: **Seu Brother ficou ainda melhor.**
- Subtexto: **Uma nova versão está pronta para você.**
- Resumo: **Mais fluidez** · **Evolução contínua**
- CTA: **Atualizar agora**
- Ações secundárias: **Ver novidades** e **Depois**
- Com `Ver novidades`, mostrar apenas benefícios em linguagem natural:
  - **Atualizações mais práticas**
  - **Interface e notificações refinadas**
  - **Melhorias de desempenho e estabilidade**

A API pode anunciar notas completas; o cartão só deve mostrar bullets curtos. Falhas exibem **Não conseguimos baixar agora. Tente novamente.** e opção **Baixar pelo site oficial**, jamais detalhes técnicos.

## 3. Estrutura de distribuição direta — sem raspar HTML

O manifesto canônico é `main/updates/stable.json` do repositório `timachado/Brother-Matrizes`. Campos `versionName`, `versionCode`, `sha256`, `downloadUrl` e `releaseNotes` já existentes devem continuar válidos.

**Novo campo opcional:** `downloadApkUrl`, contendo a URL HTTPS pública do APK comercial efetivamente publicado. **Não colocar URL fictícia no manifesto live.** Usar somente depois do download autenticado do arquivo real ter sido testado.

Exemplo esquemático, NÃO PUBLICAR LITERALMENTE:

```json
{
  "app": "Brother Matrizes",
  "channel": "stable",
  "applicationId": "com.timachado.brothermatrizes",
  "versionName": "1.10.1",
  "versionCode": 177,
  "downloadUrl": "https://timachado.ifree.page/aplicativos/brother-matrizes/",
  "downloadApkUrl": "https://github.com/timachado/Brother-Matrizes/releases/download/v1.10.1/<NOME_REAL_DO_APK>.apk",
  "sha256": "<SHA256_REAL_DO_ARQUIVO_ASSINADO>",
  "releaseNotes": "- Atualizações mais práticas\n- Visual renovado\n- Correções e estabilidade"
}
```

A URL de asset do GitHub Release acima só será válida após a T.I. Machado publicar efetivamente o arquivo assinado no repositório oficial (tag `v1.10.1`). Também pode ser hospedado por domínio próprio servindo diretamente `.apk` sem desafio JavaScript.

O app **1.10.1** obtém `downloadApkUrl` do manifesto oficial, restringe domínios/HTTPS e verifica SHA-256, versão, pacote e fingerprint X.509 antes de abrir o instalador Android. Autorizações do Android e Play Protect permanecem ativas. O servidor InfinityFree respondeu página HTML de desafio ao cliente HTTPS Android; extrair `?ti_app_download=...&_wpnonce=...` dessa página não é solução confiável.

**Importante:** a versão **1.10.0 já instalada** não pode receber lógica nova sem instalar atualização; é possível que esse salto ainda exija baixar pelo site uma única vez. Da 1.10.1 em diante, com `downloadApkUrl` real na próxima release, a atualização pode baixar dentro do app.

## 4. Checklist para liberar 1.10.1

- [ ] Build Android e testes unitários passam.
- [ ] Assinar com a chave comercial **original** (não criar outro JKS). Certificado X.509 SHA-256 esperado: `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`.
- [ ] Confirmar assinatura APK v2/v3, alinhamento ZIP, pacote, versão 177 e hash exato.
- [ ] Testar instalação 1.10.0 → 1.10.1 sem desinstalar e sem perder fontes/projetos.
- [ ] Testar visual do card, botões Ver novidades/Depois, notificação com monograma BM e logo expandida.
- [ ] Hospedar o binário direto e verificar GET HTTP 200 com bytes reais ZIP/APK para cliente Android nativo sem JavaScript.
- [ ] Confirmar SHA-256 do APK servido e atualizar WordPress + GitHub Raw mantendo mesma versão.
- [ ] Só depois publicar e disparar FCM uma única vez.

**Não mexer em** WooCommerce, Efí, licenças, Supabase, OAuth, fontes BM, simulador ou matrizes. **Não contornar** Play Protect. Registro Android Developer Console é independente da publicação.
