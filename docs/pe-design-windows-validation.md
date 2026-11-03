# Integração Brother Matrizes → PE-DESIGN 11 (Windows)

## Incidente relatado em 03/11/2026

O usuário relatou que o comando **Salvar PES para PE-DESIGN Windows** da versão comercial 1.11.8 grava um `.pes`, mas o PE-DESIGN 11 apresenta **tela branca**. Isso invalida qualquer alegação de compatibilidade comprovada.

A versão 1.11.8 usava `#PES0060` com blocos CEmbOne/CSewSeg gerados pela biblioteca EmbroideryIO. Os testes automatizados verificaram integridade dos pontos usando EmbroideryIO e pyembroidery, **não** a renderização no software proprietário Windows.

O usuário não tem um computador Windows disponível. Não transferir a responsabilidade de homologar a funcionalidade a ele.

## Correção candidata conservadora (não publicada)

- O perfil `PE_DESIGN_PC` passa a escrever **PES v1 completo** (`#PES0001`), com CEmbOne, CSewSeg e PEC. É uma hipótese de compatibilidade retroativa, e **não prova** de abertura no PE-DESIGN 11.
- Diferenciar PES v1 **completo para PC** do PES v1 **truncado para a máquina**. O modo `BROTHER_MACHINE` mantém o mesmo gerador, deslocamento PEC e correções já aprovadas. DST e JEF permanecem intactos.
- O aplicativo informa a opção **Layout & Editing → Início → Importar padrões → Do arquivo**, sem afirmar que a compatibilidade já foi homologada.
- Testes Kotlin devem comparar conteúdo/contêiner, verificar quantidade de pontos, dimensões e fontes, e bloquear regressões do arquivo de bordadeira.
- O leitor independente pyembroidery deve validar o PES v1 completo, blocos de edição, offset PEC, pontadas não degeneradas e paleta de cores.
- Manter a versão estável, OTA, APK assinado e notificações **1.11.8** intactos. Nenhuma publicação automática.

## Alternativa interoperável existente

A Brother documenta suporte à importação de **DST** no PE-DESIGN 11, através de *Importar padrões*. O Brother Matrizes já exporta DST. Esse caminho permite trabalhar com pontadas; não cria letras nem objetos/fontes nativos editáveis do PE-DESIGN.

Referências oficiais:
- https://origin.supportbrothercom.brother.co.jp/g/s/hf/htmldoc/ped/im/ped11/en/PED11_EN/pages/11_1102474.html
- https://support.brother.com/g/b/faqend.aspx?faqid=faqh00100057_002&lang=en&prod=hf_design11eas
- https://help.brother-usa.com/app/answers/detail/a_id/79899/~/i-cannot-open-my-pes-design-on-my-new-software-version

## Gate comercial

A aprovação automatizada apenas confirma **validade estrutural e intercâmbio entre bibliotecas**. Para declarar compatibilidade com PE-DESIGN 11, será necessária execução real no Windows licenciado, por QA interno, parceiro técnico ou cliente piloto voluntário, sem depender do proprietário.

Validação mínima: abrir e importar, visualizar matriz e texto, conferir tamanho e cores, salvar, fechar e reabrir. Caso a versão 1.11.8 seja divulgada enquanto este incidente estiver aberto, não anunciar compatibilidade garantida com PE-DESIGN 11.
