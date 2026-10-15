# Brother Matrizes — Fase 6: Trilhos Satin manuais (protótipo)

## Objetivo
Permitir corrigir a geometria de colunas Satin *com o dedo no Android* em artes raster recortadas, visando detalhes que o algoritmo automático não consegue interpretar com fidelidade.

## Implementado
- Novo tipo de costura por camada: **Satin manual**. O usuário pode iniciar seis pares de trilhos estimados na caixa da camada e movê-los individualmente sobre a máscara da cor, no preview.
- **Duas bordas por seção:** trilho A (dourado) e B (azul). A distância entre A e B representa a largura local da coluna e seus pares conectam o caminho ao longo da arte.
- **Várias seções:** adicionar uma seção para bordar um traço separado, navegar entre seções, inserir pares intermediários, retirar o último par e desfazer alterações.
- **Sem perda de outras camadas:** o editor compartilha ordem, cor da linha, densidade, compensação e underlay por camada com o modelo da etapa 3.
- **Interpolação suave:** trilhos interpolados por Catmull-Rom; reamostragem por comprimento real da linha central; aplicação de pontos Satin alternados e sustentação central, com corte entre seções.
- **Verificações:** requer largura entre 0,8 e 7,0 mm na seção, eixo não degenerado, trilhos não cruzados/invertidos, densidade e comprimento de pontada seguros; falha explicitamente se a geometria for imprópria.
- Conecta saída real de STITCH / JUMP / TRIM ao pipeline PES/JEF/DST existente, sem modificar leitor/escritor ou simulação.

## Instruções de teste
1. Em **Criar desenho/logo → Imagem → Multicor**, importe um PNG com logotipo isolado e sem fundo complexo.
2. Selecione a cor, toque em **Editar trilhos Satin manualmente**, ajuste os pontos dourados e azuis no preview superior.
3. Se necessário, adicione novos pares, use desfazer ou adicione uma nova seção para outro traço da mesma cor. Ao inserir uma seção, ela nasce sobreposta: **é obrigatório reposicioná-la**.
4. Configure passo Satin, compensação e underlay. Retorne ao preview de pontos e confira os limites do bastidor.
5. Gere o PES e reabra no aplicativo; **não borde diretamente sem teste em tecido e máquina Brother**.

## Limites reais
- **Não existe importação automática de letras cursivas perfeitas:** os trilhos são inicialmente estimados só pela caixa da cor e demandam ajuste manual. Uma palavra completa como “Sabrina Freitas” requer segmentar cada parte do traço, definir conexões e revisar o tipo de ponto.
- O preview tátil mostra a máscara e controles, não a foto inteira do roupão. Importar imagem recortada original.
- Não há ainda *Bezier handles* independentes, panorâmica/zoom, recorte seletivo do traço, auto-detecção de junções, projeto persistido dos trilhos, nem integração com a biblioteca de glifos. A interpolação Catmull-Rom tem controles por seção, não vetorização universal.
- Ainda não homologado em cetim preto, máquina Brother e bastidor físico. Compilação debug não é release comercial.

## Compatibilidade
Branch de pesquisa separada, com main e PRs #83, #85, #87, #88, #89 preservadas. Base do produto público 1.10.8/versionCode 184. Não modificar o simulador original, motor de fontes, importações PES/JEF/DST, compra, licenças, login, DaFont ou NetFontes.

## QA obrigatório
- [ ] Gradle unit tests Android
- [ ] Gradle assembleDebug com archive
- [ ] Android real com toque no canvas, arraste, inserção e desfazer
- [ ] Reabertura PES de duas cores, correção de trilhos cruzados e limites reais
- [ ] Prova de bordado em máquina Brother com estabilizador adequado
