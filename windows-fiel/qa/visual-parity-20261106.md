# QA visual Android 1.11.8 × Windows — 06/11/2026

PR #226, branch Windows fiel. Não é homologação comercial.

## Imagens verificadas

- Android: [baseline oficial #42217845468](https://github.com/timachado/Brother-Matrizes/actions/runs/42217845468), com Home e Criar Nome reais.
- Windows: [CI #42279175040](https://github.com/timachado/Brother-Matrizes/actions/runs/42279175040), artefato 13534137835, com Home compacta (404×641), ampla (904×641), Criar Nome (404×641) e Editor de Nomes (904×641).

## Inspeção direta dos PNGs

**Home:** cabeçalho/logotipo BM, banner de bordado, CTA dourado Criar Nome, cartões Ferramentas e cinco itens na navegação inferior correspondem na hierarquia e identidade de cores. O Windows mostra menos cartões porque sua janela tem apenas 641 px de conteúdo; não confundir recorte com ausência da funcionalidade.

**Criar Nome:** matriz MARIA vermelha sobre bastidor, cinco abas Texto/Fonte/Tamanho/Cor/Mais, nome digitado, botões Simular e Criar matriz, barra inferior compatíveis visualmente. A referência Android é muito mais alta e exibe o grupo de opções do formato sem scroll; a janela Windows compacta mostra a parte superior dessa seção.

**Editor de Nomes Windows:** são visíveis título, Voltar/Desfazer/Refazer, canvas com nome dourado, resumo de 431 pontos e 35,4×12,0 mm, abas Nome/Letra/Bastidor e Aplicar nome. O campo de edição fica parcialmente abaixo da dobra; validar rolagem em QA.

**Limite:** nenhuma imagem Android anterior utilizava a mesma matriz sintética TM+Maria de NameEditor. Uma instrumentação exclusiva Android foi adicionada para gerar este golden. Capturar PNG válido não prova paridade pixel a pixel, operação de máquina, cotas ou fontes comerciais.

## Pendente

Após gerar o golden Editor Android, comparar região de título, matriz, tamanho de bastidor, pontos, texto e usabilidade. Manter TTF/OTF comercial não habilitado no Windows até autenticação e motor nativo aprovados. Nunca publicar EXE/ZIP/MSI desta revisão.
