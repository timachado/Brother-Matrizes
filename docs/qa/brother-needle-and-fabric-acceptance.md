# Brother Matrizes — auditoria de agulha e aceite físico (QA)

**Nunca liberar PES/JEF/DST oriundo de Imagem para Bordado baseado apenas em prévia ou CI verde.** O pacote comercial 1.10.8, os leitores originais e o simulador tradicional não são alvos desta auditoria.

## Evidência técnica Brother

- [Brother PR1050X: causa de quebra da agulha](https://support.brother.com/g/b/faqend.aspx?c=us&faqid=faqh00000316_002&lang=en&prod=hf_pr1050xeus): três ou mais camadas de pontos sobrepostas/densidade muito fina podem quebrar a agulha.
- [Brother PR-600/620: ruptura da linha superior](https://support.brother.com/g/b/faqend.aspx?c=us&faqid=faqh00000318_000&lang=en&pfs=1&prod=hf_600_2eus): excluir pontos de pitch zero e acúmulos excessivos de pitch muito curto.
- [Brother: procedimentos de bordado](https://help.brother-usa.com/app/answers/detail/a_id/185249/~/embroidering-a-pattern): máquina move bastidor e troca cores; tecido preso no carro pode colidir com agulha. Conferir percurso livre do bastidor.
- [Brother: orientações para troca de agulha e estabilizador](https://support.brother.com/g/b/faqend.aspx?c=us&faqid=faqh00101096_010&lang=en&prod=hf_inovnq1400eeus): agulha, tensão, entretela e espessura do tecido também afetam risco.

## Gates de software na ferramenta experimental

1. Confirmar origem QA sem bytes PES exportáveis; número de pontos declarado consistente e comando END único.
2. Conferir cada STITCH: coordenadas dentro da área útil do bastidor; salto de entrada explícito; pitch zero/excessivamente curto ou longo rejeitado.
3. Sinalizar **três agulhadas no mesmo agrupamento de 0,3 mm**. A grade/limites são triagem conservadora definida pelo projeto e **não** valores técnicos universais publicados pela Brother.
4. Conferir JUMP, TRIM, COLOR_CHANGE e seções independentes; impedir costura diagonal automática entre letras desconectadas.
5. Rejeitar qualquer parte que falhe fidelidade visual dos **STITCHs reais** contra a imagem e rejeitar logo inteiro se uma parte não puder ser digitalizada corretamente.
6. Não conectar nem exportar Tatami/Fill até existir checagem de densidade física, sobreposição entre camadas, tipo de entretela e tecido.
7. Não aplicar esta triagem experimental em arquivos PES/JEF/DST **originais** abertos pelo usuário, nem alterar seu simulador aprovado.

## Gate físico obrigatório antes da primeira matriz comercial

- Anotar **modelo exato** da máquina Brother, firmware, tipo e tamanho do bastidor aceito, agulha, linha, tecido, entretela e orientação.
- Comparar o mesmo motivo **em PES confiável** com a saída experimental: ordem de cores, sequência, orientação, reversões, saltos, cortes, reforços, underlay e densidade.
- Inspecionar no display da máquina o percurso do bastidor e possíveis colisões; garantir espaço livre em toda a movimentação e fixação correta da entretela.
- Só após revisão especializada, em situação controlada, bordar em **retalho de teste** com supervisão presencial. Interromper ao primeiro sintoma de enrosco, ruído incomum, esforço do carro, ruptura ou deformação de agulha. Não forçar a continuidade.
- Registrar foto e vídeo do bordado real e comparar ao original e à simulação. Repetir com mudanças de escala, tecidos e linhas antes de considerar exportação.
- Manter **exportação PES e envio à máquina bloqueados** se faltar qualquer uma dessas evidências.

## Critério de honestidade

Uma compilação verde e uma auditoria geométrica não comprovam que a agulha não quebrará. Somente a validação específica da máquina, material e matrizes permitirá declarar o procedimento homologado. Não chamar uma prévia 2D de "seguro para bordar".
