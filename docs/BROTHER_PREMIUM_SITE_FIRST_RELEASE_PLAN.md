# Brother Matrizes Premium — publicação em duas fases

**Decisão do responsável — 20/10/2026:** a próxima versão comercial será disponibilizada **primeiro no site oficial da T.I. Machado e ao grupo de bordados**. A Google Play fica para os dias seguintes. Não antecipar a publicação na Play e **não publicar automaticamente** nenhum dos canais.

## Ordem de distribuição aprovada

1. **Homologação interna:** aprovar PR #188, imagem BM sem corte, Home sem janelas do sistema, QA Android e testes de regressão das funções existentes. O sucesso do build de debug não aprova sozinho a release.
2. **Preparar APK comercial:** definir uma nova versão e `versionCode` monotônico acima do APK público atual (1.10.10, `versionCode=186`; conferir estado real antes de atribuir o próximo número). Compilar variante **release**, assinar com a **mesma identidade comercial oficial** previamente utilizada, executar `zipalign`/`apksigner verify`, conferir fingerprint SHA-256 do certificado, ID de pacote `com.timachado.brothermatrizes`, hash SHA-256 do artefato e ausência de segredos.
3. **Validar atualização real:** instalar a release assinada **sobre a última versão pública assinada com o mesmo certificado**; conferir preservação de projetos, fontes, login Google, sessão, biblioteca, ferramentas PES/JEF/DST, licenças, assinatura, abertura de arquivos, simulador, editor, conversor, envio e atualizações. Se não for compatível, **bloquear o lançamento** e corrigir; não exigir desinstalação como solução.
4. **Publicar primeiro no site oficial:** atualizar o download Android em `https://timachado.ifree.page/` (preferencialmente apontando para um único APK oficial, hospedado em release verificada ou local equivalente), nome e versão, notas de atualização, tamanho, hash SHA-256 e instruções curtas de atualização. Confirmar pelo celular Android que o botão baixa exatamente o pacote assinado e íntegro.
5. **Distribuir ao grupo de bordados:** somente depois de conferir o link do site, divulgar o **mesmo endereço oficial**, a versão e as novidades. Evitar enviar APK diferente por WhatsApp; centralizar download e futuras atualizações no site.
6. **Google Play apenas posteriormente:** abrir etapa separada de preparação do AAB, ficha da loja, testes e publicação depois da validação inicial e de nova autorização. Não configurar upload automático nesta fase.

## Cuidados específicos da migração para Google Play

- Manter `applicationId`/nome de pacote oficial e `versionCode` crescente.
- Antes de usar Play App Signing, **verificar cuidadosamente** qual certificado assinará os APKs distribuídos pela Google Play e se ele é compatível com a identidade dos APKs já instalados do site. O certificado da *upload key* não substitui o certificado da *app signing key*.
- Se o certificado de assinatura entregue pela Play for diferente daquele da versão instalada pelo site, uma atualização normal poderá falhar. Resolver a estratégia com a Play **antes de publicar**, sem trocar silenciosamente a identidade de assinatura nem prometer atualização direta.
- A disponibilidade posterior na Play não significa que as instalações via site sejam automaticamente transferidas à loja; validar o caminho de atualização/distribuição.
- Não inserir .jks, senhas, Base64 da keystore ou segredos no GitHub, em artefatos públicos ou no chat.

## Segurança dos fluxos existentes

- O workflow `.github/workflows/android-production-sign.yml` é manual e gera um **candidato assinado privado**, não publica site nem loja.
- O workflow `.github/workflows/brother-11010-publish-commercial.yml` está **vinculado à versão antiga 1.10.10** e não deve ser reutilizado como se fosse o novo release premium.
- O workflow `.github/workflows/brother-ota-after-published.yml` pode ser acionado quando uma GitHub Release **pública** é publicada, atualizando o manifesto OTA. Verificar e autorizar essa mudança antes de publicar uma nova GitHub Release, pois a comunicação de atualização aos usuários pode começar nesse instante.
- Não alterar `updates/stable.json`, links comerciais, Firebase, Supabase, chaves, cobranças ou `main` até conclusão da homologação e aprovação de lançamento.

## Gate de publicação

**NÃO PUBLICAR** enquanto qualquer item estiver pendente:

- [ ] Home premium e launcher adaptativo aprovados por captura Android real, sem ANR e sem corte.
- [ ] Build release e regressões funcionais aprovados.
- [ ] Certificado de assinatura idêntico ao comercial anterior, com upgrade preservando dados.
- [ ] Versão/código novos confirmados, hash do APK registrado e integridade verificada.
- [ ] Site pronto e download Android testado (sem expor arquivos privados).
- [ ] Autorização explícita para publicar o APK no site, e depois comunicar ao grupo.
- [ ] Etapa Google Play mantida separada e posterior.
