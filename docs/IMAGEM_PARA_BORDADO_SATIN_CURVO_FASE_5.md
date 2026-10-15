# Brother Matrizes — Fase 5: Satin curvo para faixas contínuas (pesquisa)

## Implementação
Sobre as fases 1–4 (PR #83, #85, #87, #88), a aba **Criar desenho/logo → Imagem → Multicor** ganhou o seletor **Satin curvo experimental** por camada. O objetivo é suportar desenhos formados por faixas com curvas suaves (não ramificadas), sem modificar os mecanismos comerciais já aprovados.

### Funcionamento real
1. Identifica componentes da cor selecionada por conectividade de quatro vizinhos.
2. Mede a espessura e a posição do eixo em seções transversais sucessivas.
3. Reamostra o eixo ao longo do **comprimento real do arco**, com passo configurável de 0,4–1,0 mm.
4. Constrói dois trilhos transversais à tangente da curva, a partir da espessura detectada e da compensação lateral de 0–0,3 mm.
5. Adiciona sustentação central opcional, costura Satin alternando os lados e separa ilhas distintas por comandos de corte.
6. Entrega comandos reais ao MultiColorRasterDigitizer e ao fluxo de exportação e reabertura PES existente.

### Critérios de rejeição intencional
- Furos/contraformas, regiões ramificadas, seções desconexas, ângulos muito bruscos, curvas com tangente quase paralela ao corte de amostragem, espessura local fora do intervalo 0,8–5,4 mm e pontadas Satin maiores que 7,5 mm.
- Excesso de ilhas, dimensão incompatível, trajetória degenerada ou densidade imprópria.

**É uma etapa experimental e limitada.** A geometria não representa um motor completo para fontes cursivas. A palavra *Sabrina Freitas*, com ligações e bifurcações, requer ainda linhas-guia/rails vetoriais **editáveis manualmente**, decomposição das letras em seções e preservação das conexões. A foto do roupão não pode ser usada diretamente como molde de matriz comercial sem reconstruir a arte.

### Garantias de compatibilidade
A branch de pesquisa não altera o simulador original, a engine de fontes comercial, a versão 1.10.8 (184), o package name, as licenças, os pagamentos, os caminhos DaFont/NetFontes nem o writer/reader PES/JEF/DST.

### Validações obrigatórias
- [ ] Testes unitários Android no GitHub Actions: curva suave, densidade, camada de preenchimento junto de Satin, buracos/ramificação e reabertura PES com troca de cor.
- [ ] Android assembleDebug e validação de assinatura de diagnóstico.
- [ ] Importação e prévia em Android real com arte limpa.
- [ ] Prova de bordado físico, especialmente em tecido acetinado com estabilização adequada.
- [ ] Editor vetorial/manual e casos com nome cursivo completo; testes antes de qualquer merge/publicação.

A saída é um PES estruturalmente válido quando o pipeline aprova; qualidade estética e comportamento mecânico devem ser homologados na máquina.
