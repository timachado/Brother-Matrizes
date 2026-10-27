# GIDEON — Inventário técnico de base / Brother Matrizes original
**Data da consulta:** 2026-10-27  
**Natureza:** auditoria não invasiva do GitHub (sem execução de aplicativo nem contato com produção).

## Repositório e identidade
- Repositório `timachado/Brother-Matrizes` — branch padrão `main`.
- Commit de base observado no PR #200: `0a5fbc4f4ee3975cc168b17296ffcf6b5bdfa5c4` (verificar se continua HEAD antes de qualquer futura mesclagem).
- `app/build.gradle.kts` em `main`: `applicationId=com.timachado.brothermatrizes`, `versionName=1.11.1`, `versionCode=188`, `minSdk=26`, `targetSdk=36`.
- `README.md` ainda declara `1.10.8` como referência comercial. O `CHANGELOG.md` inicia em `0.46.10`: histórico documental divergente, **não corrigir números por suposição**.
- A versão instalada em clientes, certificado efetivamente usado, manifesto OTA atual e última publicação comercial **não foram auditados**. Não afirmar que `1.11.1` está distribuída só por constar no Gradle.

## Editor de nomes — situação
- PR [#200](https://github.com/timachado/Brother-Matrizes/pull/200) está **draft**, contra `main`, branch `feature/editor-nomes-molduras-20261026`.
- Foram encontrados testes no workflow da branch `.github/workflows/name-editor-qa.yml`: testes unitários, emulador Android 35 e geração de APK debug `com.timachado.brothermatrizes.nameqa` sem publicação.
- No momento da auditoria, execução `40445087059` do SHA `6c03964c5f881086c187a613efb261a662e7f3fb` estava **in_progress**. Não declarar aprovado sem reconsulta.
- Feedback de usuário em Android real: travamentos, atraso/controles difíceis ao mover, girar e escalar nome; encaixe automático conforme cada matriz insuficiente. **Bloqueador P0**.
- A matriz `MOLDURA FLORES BORBOLETAS 13X18.pes` recebeu análise geométrica documentada no PR; não foi homologado fluxo ponta a ponta no Android físico + bordadeira.
- PR #200 não deve ser mesclado, usado em OTA ou distribuído aos clientes até eliminar bloqueadores e cumprir gates físicos.

## Critérios de aceite P0 editor
1. Interação fluida sem travamentos/ANR; medir duração de frame e operações pesadas no Android alvo.
2. Gestos de arrastar/girar/dimensionar previsíveis, sem recálculo caro bloqueando a thread principal.
3. Encaixe inteligente na região livre real: respeitar margens e densidade de pontadas, permitir ajuste manual e apresentar recusa segura se não couber.
4. Não modificar a moldura original nem regiões costuradas; preservar STITCH/TRIM/JUMP/COLOR_CHANGE.
5. Trocar Maria→Sophia no projeto salvo e reabrir mantendo a camada editável v4; manter compatibilidade com v1–v3.
6. Exportar/reabrir PES/DST, verificar orientação e sequência real de bordado; testar em retalho na Brother.

## Próxima sequência
- Reconsultar CI do head atual do PR #200 e logs dos jobs falhos.
- Capturar perfil de desempenho/ANR em Android real, com o mesmo arquivo PES, sem mandar matriz do cliente a repositório público.
- Corrigir causa raiz em branch do editor; reexecutar testes de unidade/CI/dispositivo/bordadeira.
- Só após validação considerar PR pronta e plano de release comercial com backup e rollback.

**Nota:** criar documentos ou um pipeline em branch de engenharia não altera `main`, produção, APK publicado, OTA, Supabase, WordPress ou matrícula de clientes.
