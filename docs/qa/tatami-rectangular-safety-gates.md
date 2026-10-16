# Tatami retangular — gates de segurança antes da máquina Brother

## Escopo desta PR de QA

O novo motor gera comandos reais JUMP/STITCH/END para **retângulos de cor sólida** de 7 a 48 mm na imagem de entrada. É uma primeira variante de preenchimento tipo Tatami, não um digitalizador universal nem uma implementação certificada de bordado comercial.

- Fileiras alternadas, espaçamento de QA ~0,78 mm e ponto longitudinal de QA ~2,15–2,4 mm; conexões entre fileiras permanecem **dentro do próprio bloco**.
- Não há passagem costurada através de vazios, furos, letras ou áreas com pigmentos diferentes. Perfis não retangulares são recusados.
- O mesmo objeto passa pela avaliação geométrica dos pontos, auditoria de agulhadas e validação de bastidor.
- A saída experimental não contém bytes PES; a **exportação PES segue proibida** e o modo tradicional de abertura e simulação de matrizes importadas não foi alterado.
- Não há ainda **underlay/entretela, compensação de repuxo específica, escolha de ângulo, planejamento de sobreposição entre camadas, adaptação ao tecido** ou teste em máquina Brother. Esses elementos são indispensáveis antes da homologação.

## Casos de teste que DEVEM permanecer aprovados

1. Bloco retangular de tinta sólida: gerar múltiplas fileiras com STITCHs válidos, baixa porcentagem fora da máscara e sem ponto zero.
2. Bloco retangular com contraforma (letra O e similares): recusar simulação Tatami; nenhuma ligação cruzando o furo.
3. Bloco com canto recortado ou contorno curvo: recusar.
4. Bloco acima do limite: recusar sem "forçar" um preenchimento mais denso.
5. Arte com três partes (Satin, Tatami e corrido): montar sequência completa com saltos, cortes, troca de cor e END único, sem exportar PES.
6. Teste físico em retalho, máquina/agulha/linha/tecido/entretela registrados: **ainda pendente**. Não colocar arquivo na máquina antes desse gate.

## Observação crítica

Espaçamentos e passos citados aqui são escolhas provisórias do protótipo e **não limites publicados pela Brother nem indicação de segurança para um equipamento real**. Uma auditoria 2D não reproduz deformação do tecido, estabilidade do bastidor, tensão da linha e potencial de colisão da agulha. Compatibilidade PES da saída segue condicionada a um modelo e validação de fato.

Referência central do protocolo de máquina: [brother-needle-and-fabric-acceptance.md](brother-needle-and-fabric-acceptance.md).
