# Brother Matrizes — Etapa 4: colunas Satin retas (pesquisa)

## Implementação
Continuidade das PRs #83, #85 e #87. O modo **Imagem → Multicor → Camadas** tem, para cada cor, dois tipos de bordado:
- **Preenchimento**: mantém os pontos em faixas alternadas da etapa anterior;
- **Satin reto (experimental)**: tenta construir rails para componentes estreitos, contínuos e predominantemente horizontais ou verticais, usando uma máscara independente por cor.

Cada camada escolhida para Satin aceita densidade/passo de 0,4 a 1 mm, compensação lateral de 0 a 0,3 mm e sustentação central opcional. São comandos reais STITCH/JUMP/TRIM, enviados ao mesmo gerador/reabridor PES já existente, sem modificar qualquer motor de simulação.

## Salvaguardas da geometria
- Segmentação por componentes 4-conectados; não une ilhas distintas com costuras.
- Exige direção dominante (comprimento >= 2,4 x espessura), comprimento >= 3 mm, largura local entre 0,8 e 5,5 mm em todas as seções da coluna.
- Cada seção tem que ser contínua (sem furos, ramificações ou contraformas), e o centro não pode fazer curva acentuada.
- Rail inicial e final dependem da máscara de origem, da escala do desenho e de compensação lateral limitada.
- Sustentação central caminha pelo eixo, depois o Satin alterna entre os rails. JUMP e TRIM separam componentes. Máximo de 130.000 comandos por geração.
- Se uma camada não atender aos critérios, **a geração falha com orientação para voltar a Preenchimento ou redesenhar o elemento**; não tenta adivinhar rails em letras ramificadas.

## O que esta etapa NÃO entrega
- Nem Satin geral em curvas, nem reconstrução vetorial de trilhos editáveis, nem identificação tipográfica/completa de lettering cursivo, nem underlay combinado com underlay perimetral, nem tatami profissional.
- Não deve ser anunciada como digitalização de foto inteira. O logotipo deve estar recortado em arte limpa e com fundo transparente ou uniforme.
- O exemplo de *Sabrina Freitas* continua exigindo fonte/vetor e engenharia específica para curvas, ligações e ornamentos; esse Satin reto não serve para reproduzir com fidelidade o nome cursivo.
- Não contém ainda editor de pontos de contorno com handles de curva; esse será um desenvolvimento posterior, preferencialmente em geometria vetorial explícita.
- A etapa não traz calibração aprovada de cetim, estabilizador ou tecido nem teste físico em máquina Brother.

## Critérios de QA
- Testes JVM para barra vertical/horizontal, rejeição de máscaras com furos, efeito da densidade e sustentação e reabertura PES com duas cores.
- Android assembleDebug em branch isolada; confirmar controles e previews em dispositivo real.
- Teste físico com bastidor e retalho para comprovar densidade, pontos, comprimento, acabamento e resistência.
- Comparar arquivo PES e foto do bordado físico. Não distribuir o APK de diagnóstico sobre a versão assinada 1.10.8.
- Manter aplicação, package name, versionCode 184, motores PES/JEF/DST, simulador, login, pagamentos e todas as funcionalidades aprovadas intactas.
