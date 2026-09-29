# Brother Matrizes 0.46.9

Aplicativo Android nativo para criar, visualizar, editar, simular e converter matrizes de bordado diretamente pelo celular.

## Simulação de Máquina

A v0.8 redesenha o simulador para se aproximar do comportamento visual de uma bordadeira real.

A tela mostra:
- a matriz completa de forma suave ao fundo;
- as pontadas já costuradas em destaque;
- a posição atual da agulha;
- o bloco/cor em execução;
- pontos concluídos;
- percentual;
- tempo restante;
- velocidade;
- consumo estimado de linha.

### Velocidade

O modo 1× usa aproximadamente **750 pontos por minuto** como referência.

Também existem 2× e 4× para acelerar a visualização.

Trocas de cor, cortes e saltos usam pausas diferentes para tornar a sequência mais parecida com o funcionamento de uma máquina.

## Fidelidade

O simulador não inventa preenchimento.

Se o arquivo contém satin/tatami, as pontadas densas aparecem preenchendo o desenho progressivamente.

Se a matriz contém somente ponto corrido, a simulação mostra ponto corrido.

Isso é importante para que a tela represente o que realmente será enviado para a máquina.

## Minha Conta

A v0.21 adiciona conta online sem transformar as matrizes locais em dados de nuvem.

Recursos:
- cadastro por nome, e-mail e senha;
- login/logout;
- sessão persistida no Android;
- nome do perfil editável;
- leitura do plano e status da assinatura;
- validade/renovação quando houver.

O backend usa tabelas **brother_matrizes_*** separadas de outros aplicativos no mesmo projeto Supabase e protege os dados com RLS. O cliente Android pode editar o próprio perfil, mas não pode conceder a si mesmo uma assinatura paga.

A integração do pagamento/portal de assinatura entra na etapa seguinte. **Minhas Matrizes** continua local e offline por padrão.

## Backup e restauração

A v0.20 permite transportar toda a biblioteca **Minhas Matrizes** em um único arquivo de backup.

Fluxo:
- abrir Minhas Matrizes;
- tocar em **Backup**;
- salvar em Downloads ou pendrive OTG;
- no mesmo ou em outro Android, tocar em **Restaurar**;
- selecionar o arquivo;
- as matrizes voltam para a biblioteca local.

O backup é validado antes da restauração e preserva os projetos internos usados para gerar novamente DST, PES e JEF.

## Minhas Matrizes

A v0.19 adiciona uma biblioteca local e offline.

O usuário pode:
- criar ou abrir uma matriz;
- tocar em **Salvar em Minhas Matrizes**;
- fechar o app;
- voltar depois e reabrir o projeto;
- simular ou editar novamente;
- enviar diretamente para a máquina por OTG ou Wi-Fi/app;
- excluir quando não precisar mais.

Os projetos ficam no armazenamento privado do Brother Matrizes. O formato interno preserva a sequência de pontadas, cores, bastidor, tecido e acabamento necessário para gerar novamente DST, PES ou JEF.

## Criar desenho/logo

A v0.18 fecha o terceiro pilar do Brother Matrizes: além de nomes e monogramas, o usuário pode criar uma matriz a partir de um **SVG simples** ou desenhar diretamente com o dedo.

Fluxo:
1. importar SVG ou desenhar;
2. escolher largura;
3. escolher ponto corrido ou Satin sobre o traço;
4. escolher cor, bastidor e DST/PES/JEF;
5. visualizar/simular;
6. enviar por Pendrive OTG ou Wi-Fi/app compatível.

O suporte SVG inicial entende caminhos com linhas e curvas Bézier, além de formas básicas. Esta versão não tenta preencher automaticamente logos complexos e não promete abrir todo SVG existente.

## Objetivo do Brother Matrizes

O Brother Matrizes não pretende ser um Wilcom no celular.

O fluxo principal é:
1. criar um nome, monograma ou matriz;
2. visualizar/simular;
3. gerar DST, PES ou JEF;
4. enviar o arquivo para a bordadeira.

## Enviar para a máquina

A v0.17 adiciona uma tela própria de transferência.

**Pendrive OTG**
- conecta o pendrive ao Android;
- toca em "Salvar no pendrive OTG";
- o seletor do Android abre;
- escolhe o armazenamento USB;
- o Brother Matrizes grava a matriz no destino escolhido.

**Wi-Fi / app da máquina**
- o Brother Matrizes gera a matriz no formato escolhido;
- abre o compartilhamento Android;
- o usuário escolhe o app da bordadeira, pasta de rede ou outro destino compatível.

O envio Wi-Fi direto não é universal: depende do protocolo/app oferecido pelo modelo da bordadeira. O Brother Matrizes não anuncia suporte direto a uma máquina sem validar esse protocolo.

## Acabamento de Máquina

A v0.16 adiciona uma etapa de acabamento aplicada à sequência final de pontadas.

Recursos:
- tie-in no começo do bloco;
- tie-off no fim do bloco;
- corte automático antes de saltos longos;
- limite de corte configurável;
- remoção de deslocamentos/comandos redundantes;
- análise de pontos longos;
- análise de saltos longos.

A otimização é propositalmente conservadora: ela limpa deslocamentos redundantes, mas não reordena cores ou blocos. Isso evita alterar a intenção visual e a sequência multicor definida pelo usuário.

O visualizador informa se existem pontos acima de 7 mm ou saltos acima de 12 mm. Esses valores são alertas de projeto do Brother Matrizes, não uma garantia universal de compatibilidade com qualquer máquina.

## Multicor por Letra e Bloco

A v0.15 permite escolher cores diferentes para letras do nome e iniciais do monograma.

O Brother Matrizes agrupa letras consecutivas da mesma cor no mesmo bloco. Quando a cor muda, a própria matriz recebe um comando **COLOR_CHANGE**.

Na Simulação de Máquina:
- a costura para no COLOR_CHANGE;
- o próximo bloco/cor é mostrado;
- o usuário toca em **Continuar** depois de trocar a linha.

PES e JEF carregam a sequência de cores. DST mantém a sequência de paradas/trocas, mas não possui uma paleta RGB completa como os formatos mais ricos.

## Texto Curvo e Ajuste por Letra

A v0.14 adiciona um motor de composição por letra.

Modos:
- Reto;
- Arco para cima;
- Arco para baixo.

Cada letra pode receber:
- deslocamento horizontal;
- deslocamento vertical;
- rotação independente;
- espaçamento próprio após a letra.

No modo curvo, a letra também acompanha a tangente do arco. Depois das transformações, a matriz inteira é recentralizada automaticamente antes da validação de bastidor e exportação.

## Biblioteca de Fontes

A v0.13 cria uma biblioteca nativa de fontes de bordado com 7 famílias:
- Fio Linha;
- Fio Compacta;
- Fio Larga;
- Fio Moderna;
- Fio Itálica;
- Fio Elegante;
- Fio Ornamental.

As famílias alteram a geometria que será convertida em pontadas: largura, espaçamento, inclinação, serifas e prolongamentos decorativos. A Fio Ornamental foi pensada especialmente para monogramas.

A Home possui uma tela própria de Biblioteca, com prévia Satin gerada pelo mesmo motor usado nos arquivos finais.

## Monogramas

A v0.12 adiciona **Criar Monograma** com 1, 2 ou 3 iniciais.

Estilos:
- **Linear** — todas as letras com proporção semelhante;
- **Clássico** — em 3 iniciais, a letra central recebe maior destaque;
- **Empilhado** — composição vertical compacta.

Cada inicial é gerada como Satin real e depois posicionada na composição. O monograma usa os mesmos controles de tecido, bastidor, densidade, compensação, short stitches e underlay disponíveis no Criar Nome.

O resultado pode ser:
- visualizado;
- simulado;
- editado;
- convertido;
- salvo;
- compartilhado em DST, PES ou JEF.

## Bastidores e Perfis de Tecido

A v0.11 adiciona bastidores genéricos:
- 100×100 mm;
- 130×180 mm;
- 140×200 mm;
- 160×260 mm;
- 200×300 mm.

O Brother Matrizes reserva uma margem de segurança padrão de 5 mm por lado. A prévia é desenhada na proporção física do bastidor e informa se a matriz ultrapassa a área segura. Quando ultrapassa, **Criar matriz** e **Simular agora** ficam bloqueados.

Perfis iniciais de tecido:
- Algodão;
- Malha / camiseta;
- Toalha / felpudo;
- Jeans / sarja;
- Boné estruturado.

Selecionar um tecido aplica uma sugestão inicial de densidade Satin, compensação de repuxo, underlay e short stitches. Esses valores continuam editáveis e devem ser confirmados com teste de costura.

## Satin Refinado

A v0.10 melhora a geração Satin com:
- suavização de normal/tangente em cantos;
- compensação manual de repuxo;
- short stitches no lado interno da curva;
- underlay central;
- underlay zigue-zague;
- opção de combinar os dois underlays.

A prévia, o simulador e os arquivos DST/PES/JEF usam a mesma sequência de pontadas.

## Criar Nome com Satin

A v0.9 adiciona **Satin real** aos nomes criados no Brother Matrizes.

O usuário pode escolher:
- **Ponto corrido**;
- **Satin**.

No Satin o Brother Matrizes gera:
- underlay central opcional;
- coluna de zigue-zague real;
- largura configurável;
- densidade configurável;
- centenas de pontadas quando necessário.

Essas são as mesmas pontadas enviadas para DST/PES/JEF e mostradas na Simulação de Máquina. O simulador não aplica um efeito visual falso.

## Criar Nome

A v0.7 continua disponível:
- Fio Linha;
- Fio Compacta;
- Fio Larga;
- altura;
- espaçamento;
- comprimento de ponto;
- cor;
- DST/PES/JEF;
- acentos do português.

As fontes atuais são de trajetória/ponto corrido. Uma etapa futura adicionará geração satin para nomes.

## Formatos liberados

- DST — Tajima
- PES — Brother/Baby Lock
- JEF — Janome/Elna

## Editor

- mover;
- redimensionar;
- girar;
- espelhar;
- centralizar;
- trocar cores.

## Conversor

**DST ⇄ PES ⇄ JEF**

## Validação no CI

Além dos testes unitários, o pipeline executa um smoke test instrumentado em Android API 35 para confirmar que o aplicativo inicia e que a navegação crítica até Minha Conta continua funcional.

O pipeline exige:
- parser DST;
- amostras reais PES/JEF;
- conversão e reabertura dos formatos;
- testes do editor;
- geração de nomes;
- exportação de nomes;
- testes de tempo da simulação;
- compilação Android.

## Compatibilidade

- Android 8.0+
- minSdk 26
- compileSdk/targetSdk 36
- JDK 17
- Gradle 9.6.0
- Jetpack Compose

## Downloads

Cada Release publica:
- APK direto;
- ZIP contendo o APK.

## Próximos passos

- monogramas;
- perfis personalizados de máquina/bastidor;
- biblioteca maior de fontes de bordado;
- expansão para VP3, EXP, XXX, U01 e TBF;
- validação de qualidade com amostras reais de costura.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
