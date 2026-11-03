# Brother Matrizes → PE-DESIGN 11 (Windows) — homologação de compatibilidade

## Estado verificável (2026-11-03)
- PR de QA: https://github.com/timachado/Brother-Matrizes/pull/215 (NÃO COMERCIAL).
- GitHub Actions 41508567771: **success** em Kotlin/Android + leitura PES por `pyembroidery==1.5.1`.
- Artefato da execução: `pe-design-qa-results-NAO-COMERCIAL`, id `13171603034`.
- Arquivos de teste gerados:
  - `maria-editada-PE_DESIGN.pes` (498 comandos, 3 linhas de cor reportadas pelo verificador).
  - `cores-e-pontadas-PE_DESIGN.pes` (12 comandos, 2 cores).
- Ambas as amostras possuem cabeçalho `#PES0060`, offset PEC, blocos `CEmbOne` e `CSewSeg` e foram lidas pela biblioteca independente.
- **Não equivale a teste no software proprietário PE-DESIGN.**

## Validação no PC com PE-DESIGN 11
1. Abrir a execução de Actions 41508567771 do repositório; baixar o artefato `pe-design-qa-results-NAO-COMERCIAL` e extrair `app/build/pe-design-fixtures/`.
2. No PE-DESIGN 11 (Windows), abrir `maria-editada-PE_DESIGN.pes` **diretamente**, sem conversor intermediário.
3. Verificar se contornos, posições e pontos do nome **Maria** aparecem juntamente à matriz de base, sem deslocamento, giro inesperado, arquivo corrompido ou perda de cores.
4. Verificar mudanças de cor na sequência; comparar visualização/contagem de pontos e dimensões com o relatório do app e o fixture.
5. Salvar outra cópia no PE-DESIGN como PES, fechar, reabrir e conferir integridade.
6. Repetir com `cores-e-pontadas-PE_DESIGN.pes` e com uma matriz PES real editada no Brother Matrizes **após disponibilizar APK de QA separado**, para validar nomes, reposicionamentos e bastidor no uso final.
7. Conferir que o perfil independente **Enviar para máquina** continua exportando formato de bordadeira já aprovado e que DST/JEF não sofreram regressão.

## Critérios de aprovação
- O PE-DESIGN 11 abre e permite salvar os PES de QA sem erros.
- Geometria, sequência de bordado e paleta são coerentes e preservadas.
- Matriz importada e nome editado são visíveis como **pontadas**; PES não promete objeto de texto com fonte original reeditável.
- A versão comercial 1.11.7, pacote, assinatura, dados, assinaturas, pagamentos, OTA e WordPress permanecem intactos até homologação.

## Se houver falha
Registrar a mensagem exata do PE-DESIGN, versão do programa, arquivo que falhou, print da visualização e comportamento ao salvar/reabrir. Corrigir no perfil `PE_DESIGN_PC` **sem alterar** o perfil `BROTHER_MACHINE`. Não realizar merge ou release antes de nova validação.
