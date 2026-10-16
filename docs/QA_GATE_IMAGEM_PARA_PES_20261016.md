# Gate de qualidade — Imagem para Bordado → PES (T.I. Machado)

## Regra de liberação
Não solicitar testes de APK no celular apenas porque a compilação passou.
O pacote continua em QA separada e a exportação/transferência PES permanece bloqueada.

### G1 — Integridade topológica (obrigatório)
- Conservar os contornos externos e contraformas (furos) do desenho original.
- Letras ramificadas devem virar várias seções Satin independentes, com saltos/trim; nenhuma pontada atravessa um furo ou outra letra.
- Testar O/T/L/C, traços finos, componentes isolados, cores e bastidores.
- Barrar pontadas com comprimento fora dos limites, trilhos invertidos e larguras impróprias.
- Testes unitários Gradle *todos verdes*, inclusive RasterSatinTopologyPlannerTest.

### G2 — Fidelidade visual (obrigatório)
- Adquirir amostra da logo PANOBIANCO original, em qualidade adequada, para teste de regressão; screenshots do simulador não substituem arquivo-fonte.
- Comparar contornos da imagem preparada contra a máscara efetiva de pontos, identificando regiões ausentes, letras distorcidas e falhas em cores.
- Aprovação visual *documentada* de todos os componentes (símbolo, PANOBIANCO, ACADEMIAS) antes de liberar APK de avaliação.
- Não mascarar falhas trocando a simulação por overlay de pixels ou pseudo-costuras.

### G3 — Compatibilidade do Android e app comercial (obrigatório)
- QA instala em paralelo; `com.timachado.brothermatrizes` e sua assinatura comercial permanecem intactos.
- Manter nomes tradicionais e a `SimulatorScreen` original; botão **Simular bordado**.
- Verificar memória, performance e usabilidade em Android no bastidor 130×180 e demais bastidores.
- Diferenciar prévia raster, trilhos sugeridos e pontos reais de matrizes aprovadas.

### G4 — Homologação PES / máquina física (posterior)
- Requer validação de underlay, densidade, arremates, tensão, tecido, agulha e costura em máquina Brother de verdade.
- Não habilitar PES, Viewer/editors de produção, transferência, recentes nem máquina enquanto essa homologação não ocorrer.

## Status (16/10/2026)
G1 em desenvolvimento na PR #109 (decomposição por eixo topológico). Testes O/T originalmente falharam no limite de largura física e estão em correção; falha deve permanecer visível no CI.
G2 pendente (arquivo-fonte PANOBIANCO não disponível como fixture verificada).
G3 parcial (guardas de CI já impedem modificação do simulador e pacote originais).
G4 pendente. **Sem solicitação de novo teste de APK ao usuário.**
