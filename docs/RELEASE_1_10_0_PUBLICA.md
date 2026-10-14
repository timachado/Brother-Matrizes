# Brother Matrizes v1.10.0 — Lançamento público (Android)

**Canal:** stable / T.I. Machado
**Pacote:** `com.timachado.brothermatrizes`
**Versão:** `1.10.0` / `versionCode=176`
**Assinatura:** sempre a chave comercial original da T.I. Machado. Nunca criar outra chave.

## Novidades para usuários
- **Central de novidades premium** no Início, com o número da nova versão, resumo das melhorias e botão para atualizar.
- **Notificações Android e Firebase Push** preservadas; o card é atualizado ao entrar no app e quando um aviso novo é recebido.
- **Atualizar agora** baixa o APK oficial diretamente no aplicativo, sem redirecionar para o navegador, para aparelhos que já usam o novo atualizador.
- **Instalação verificada**: arquivo HTTPS do site T.I. Machado, hash SHA-256 comparado ao manifesto oficial do GitHub, pacote, código da versão e certificado comercial verificados antes de solicitar a instalação ao Android.
- **Biblioteca de fontes BM**: terminologia corrigida, fontes TTF/OTF, criação de nomes, simulador e exportação preservados.
- **Melhorias de estabilidade**, sem alterar licenças, planos, pagamentos WooCommerce/Efí, conta Supabase nem dados locais.

## Para publicar com segurança
1. CI de compilação, testes unitários e upgrade em emulador deve passar.
2. APK **release assinado** com certificado SHA-256 `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`; apksigner v2/v3 e hash do APK precisam ser validados.
3. Instalar a 1.10.0 sobre a 1.0.9 em aparelho Android real **sem desinstalar**, conferir dados e recursos principais.
4. Carregar este mesmo APK no produto/aplicativo do WordPress T.I. Machado, conferir download público e SHA do arquivo efetivamente servido; não modificar licenças e opções de pagamento.
5. Atualizar a versão e hash no endpoint WordPress `/wp-json/tim-brother-updates/v1/latest?channel=stable`. A versão 1.0.7 estava com SHA antigo 1.0.5 — **não repetir este erro**.
6. Atualizar `main/updates/stable.json` somente **após** o APK estar disponível no site e ser validado, mantendo `downloadUrl` oficial. O código público 176 precisa ser maior que 173/174/175.
7. Somente após a publicação e conferência, usar o painel de notificações WordPress com release `1.10.0`/`176`. Não disparar sem verificar que o GitHub/Firebase aceitou e não repetir alertas acidentalmente.
8. Verificar mensagens Play Protect em aparelho real. Registro do desenvolvedor/comprovação de posse do pacote no Android Developer Console é **etapa separada**, sem garantia de supressão da análise do Play Protect. Não desativar ou contornar mecanismos de segurança.

## Gate de lançamento
Até os itens 1 a 6 estarem concluídos, manter o manifesto oficial da versão pública anterior. O APK de teste não é automaticamente uma distribuição pública.

**Importante:** o novo card de novidades e o download direto exigem que a pessoa já tenha a nova versão do código instalada. Versões 1.0.7/1.0.8 não adquirem essas funções retroativamente.
