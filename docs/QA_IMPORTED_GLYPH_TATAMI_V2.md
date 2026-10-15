# QA — preenchimento experimental de fontes importadas TTF/OTF

**Origem:** tag oficial `release/brother-matrizes-1.10.8`, versionCode 184.

**Status:** código experimental integrado sob flag `QA_ONLY_ENABLED = false`.
A implementação oficial Satin permanece sendo utilizada. **NÃO MESCLAR / NÃO PUBLICAR** antes de homologação.

## Mudanças isoladas

- `ImportedGlyphTatamiPlanner.kt`: planejador de preenchimento baseado em contornos e winding non-zero.
- `ImportedGlyphTatamiAdapter.kt`: ponte para comandos STITCH/JUMP; não gera END, TRIM, cores, underlay, tie-in/tie-off.
- `CleanRoomSatinEngine.kt`: ramo de integração experimental, inativo por padrão; Satin oficial preservado.
- `ImportedGlyphTatamiPlannerTest.kt`: regressões para faixas contínuas, vazados, comprimento de ponto e comandos.
- `.github/workflows/brother-imported-tatami-v2-qa.yml`: executa build e testes **apenas nesta branch**; não assina comercialmente nem publica releases.

## Proteções

- `MachineSimulationCanvas.kt` e `SimulatorScreen.kt` idênticos aos originais SHA do Git.
- VersionName 1.10.8 e versionCode 184 preservados para NÃO sugerir uma versão comercial.
- Não criar APK comercial nem usar assinatura original nesta branch.
- Nunca instalar build debug sobre a instalação comercial: pode haver diferença de certificado; não desinstalar sem backup.
- Fontes fornecidas pelo usuário **não foram incluídas no repositório**.

## Evidência parcial

O protótipo isolado compilou em Kotlin/JVM com stubs e passou smoke tests
(geometria com vazado, topologia e adapter). Os números do pacote externo V2
para Anak Manja TTF, `MARIA`, foram 125 saltos antes / 92 após.
Esse comparativo **não comprova** acabamento real de máquina nem correção visual
no Android; a seleção Satin/Tatami ainda precisa de validação.

## Bloqueios de homologação

1. Executar `:app:testDebugUnitTest :app:assembleDebug` no Android CI.
2. Reproduzir com Anak Manja TTF/OTF, Love TTF e Bertilda TTF/OTF no Android;
   registrar contornos, spans e sequência de pontos por letra.
3. Comparar pixel a pixel/contorno a contorno com a prévia original.
4. Projetar e testar a política de seleção híbrida Satin/Tatami, densidade,
   compensação, underlay, tie-in/off e TRIM sem costuras em vazados.
5. Testar exportação e reabertura PES/JEF/DST e bordado real em máquina Brother.

**Somente após aprovação manual** poderá haver candidato instalável
com versionCode superior e assinatura comercial original.
