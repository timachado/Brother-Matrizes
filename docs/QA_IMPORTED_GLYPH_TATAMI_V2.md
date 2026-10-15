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

## Execução verificada nesta homologação

GitHub Actions da branch, commit `fc40940598b581af3ba602a23672e849b640d3ae`:
- Run: https://github.com/timachado/Brother-Matrizes/actions/runs/38673890433
- Resultado: **SUCCESS**, checagem de base/simulador, `:app:testDebugUnitTest` e `:app:assembleDebug`.
- Nenhuma release ou APK comercial foi publicado.

## Fontes reais — reprodução Kotlin/JVM local

Fonte original em ZIP enviada pelo usuário. Contornos extraídos por `fontTools`
em JVM local, não pelo `AndroidSkiaOutlineExtractor`. Os números descrevem
**somente o planejador experimental**, não o APK Android nem a qualidade de
costura numa máquina real.

| Fonte | M: comandos / saltos | MARIA: comandos / saltos |
|---|---:|---:|
| Anak Manja TTF | 1.768 / 20 | 6.353 / 92 |
| Anak Manja OTF | 1.764 / 14 | 6.335 / 91 |
| Love TTF | 1.341 / 12 | 3.954 / 42 |
| Bertilda TTF | 1.280 / 15 | 3.795 / 39 |
| Bertilda OTF | 1.279 / 14 | 3.785 / 49 |

O número de saltos NÃO é o único critério para aprovação; a qualidade de
topologia, fidelidade dos glifos, underlay, trims, sobreposições e acabamento
precisa ser analisada em cada caso.

**Próximo bloqueio técnico:** validar a extração Android Skia de cada
TTF/OTF, o comportamento visual no APK e a exportação/reabertura em
PES/JEF/DST. Manter a flag desativada.

## QA de 15/10/2026 — fontes reais e acabamento

Fontes extraídas localmente dos ZIPs fornecidos pelo desenvolvedor, sem
publicá-las no GitHub. `MARIA` em aproximadamente 43 mm de altura,
planejador Kotlin recompilado no ambiente de diagnóstico:

| Fonte | Comandos geométricos | Saltos | IoU raster estimado | Saltos acima de 12 mm |
|---|---:|---:|---:|---:|
| Anak Manja TTF | 6.353 | 92 | 97,05% | 9 |
| Anak Manja OTF | 6.335 | 91 | 97,03% | 8 |
| Love 11 TTF | 3.954 | 42 | 93,41% | 10 |
| Bertilda TTF | 3.795 | 39 | 93,13% | 11 |
| Bertilda OTF | 3.785 | 49 | 93,04% | 13 |

Todos os STITCH medidos ficaram abaixo de 3 mm. Foram detectados deslocamentos
JUMP de até 93,7 mm **sem corte no stream bruto do planejador**, portanto foi
necessário aplicar somente no caminho QA o `MachineFinishing` existente com
`trimJumpThresholdMm = 6f`, tie-in/out ativados e cortes automáticos.

Os resultados de IoU são comparações raster aproximadas em JVM/fontTools,
**não** equivalem ao bordado físico nem garantem shaping Skia idêntico.

### Teste end-to-end protegido

- `-PbrotherTatamiQa=true` faz `BuildConfig.ENABLE_IMPORTED_TATAMI_QA`
  valer `true` apenas na compilação explicitamente solicitada.
- Sem a propriedade, todas as compilações normais mantêm o novo fluxo
  **desabilitado**, a versão 1.10.8 e o Satin oficial.
- O emulador API 35 executa agora `CleanRoomSatinEngine.generate` com
  fonte real do sistema e exige `machineFinishing`, STITCHs e corte de
  deslocamentos longos.
- Foram adicionados testes unitários de TRIM e conversão PES/JEF/DST
  após acabamento.
- **Não habilitar no APK comercial** sem CI integral aprovado, revisão
  visual em Android com os TTF/OTF reais e bordado de teste físico.

### Pontos ainda necessários

1. Provar fidelidade dos glifos reais no Android (fontes não enviadas ao CI).
2. Validar em máquina os arremates e a densidade de preenchimento.
3. Projetar seleção **Satin para traços finos / Tatami para regiões largas**.
4. Separar disponibilização de APK de QA dos canais públicos da versão comercial.
