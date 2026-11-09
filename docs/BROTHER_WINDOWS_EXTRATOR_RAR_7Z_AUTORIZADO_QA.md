# Brother Matrizes Windows — Extrator ZIP / RAR / RAR5 / 7Z (QA restrito)

Revisão: 09/11/2026. PR #226, ramo `feature/windows-fiel-android-1-11-8-20261106`. **Não é homologação comercial nem autorização de publicação.**

## Funcionalidade implementada em código

A tela `ArchiveExtractorScreen` compilada diretamente da fonte Android conserva seleção, prévia, senha, cancelar e visualização. Quando a **configuração de QA de conta e backend V2** for explicitamente autorizada, um botão separado permite solicitar importação **individual** de matriz PES/DST/JEF ou fonte TTF/OTF com consentimento informado e possível reserva de cota.

A ponte `DesktopWindowsArchiveAuthorizedImport` suporta ZIP, RAR/RAR5 e 7Z, conferindo:

1. Pacote regular local, extensão e assinatura originais compatíveis, arquivo não simbólico.
2. Item selecionado pertence exatamente à prévia do Android.
3. SHA-256 da cópia privada criada pela tela Android corresponde ao arquivo original no Windows.
4. Nova inspeção Windows com senha **somente em memória**, rejeitando alterações e entradas não permitidas **antes** do pedido de cota.
5. Persistência de UUIDv4 e protocolo Free V2 no journal antes da operação de reserva.
6. `DesktopArchiveSelectiveImportFactory` passa senha transitória ao `DesktopArchiveSelectiveStager` somente após autorização. O stager usa senha para revalidar RAR/7Z e abrir junrar/SevenZ, respeitando 32 MiB de limite de dicionário/memória, 24 MiB por item, diretório temporário privado, nome aleatório e limpeza após operação.
7. Sink de saída valida arquivos pela implementação do Android, grava matriz FLP verificada ou fonte TTF/OTF com comprovação antes/depois da publicação e rollback apenas do arquivo recém-criado.
8. Finalização e resposta de quota devem ser confirmadas; estado incerto bloqueia novas reservas e exige reconciliação manual.

A senha é transformada em `CharArray` no clique, passada à transação e sobrescrita no `finally` do fluxo de interface. **Senha não entra em GitHub Actions, journal, logs de aplicativo, artefatos ou estado persistente de cotas.** A UI original Android mantém seu campo de senha durante a sessão de prévia; não confundir isso com armazenamento persistente.

## Bloqueios preservados

O callback de importação **não existe na UI padrão**: apenas QA explícito, com Google/PKCE revisado, chave publicável, `BROTHER_WINDOWS_QA_ARCHIVE_IMPORT=enabled`, `BROTHER_WINDOWS_QA_ARCHIVE_V2_BACKEND_REVIEWED=enabled` e backend autorizador disponível. As flags não criam cotas/planos e **não provam deploy real**. A Edge Function oficial em produção continua sem `reserve_v2`; não habilitar contra produção.

Testes adicionados/ampliados: `DesktopWindowsArchiveAuthorizedImportTest` executa seleção 7Z original Android, gravação FLP via fake Free V2 + journal e confere ausência de alteração do arquivo; `DesktopWindowsFontImportOptInTest` prova que callbacks e opções permanecem fechados sem opt-in. Os testes anteriores validam ZIP, caminho inseguro e extração seletiva de 7Z.

**Ainda não homologado:** 7Z criptografado com senha real, RAR4/RAR5 criptografado com fixtures reais de matrizes, casos de arquivo multimídia com senha incorreta, login Google no servidor de staging, migrar cotas Free/Pro V2, QA com PC e hardware. Os testes de código isolados não substituem esses critérios. Nenhum APK, cliente, site ou Supabase de produção é alterado nesta etapa.
