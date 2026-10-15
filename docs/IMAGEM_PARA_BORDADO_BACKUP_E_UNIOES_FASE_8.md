# Brother Matrizes — Fase 8: backup de projetos Satin e uniões controladas

Base experimental: fases 1–7 (PRs #83, #85, #87, #88, #89, #91 e #92). Versão comercial 1.10.8 / versionCode 184 e motor original de simulação permanecem intocados.

## A. Backup e restauração dos documentos editáveis

- A área **Criar desenho/logo → Imagem → Projetos editáveis Satin** oferece os botões **Exportar backup ZIP** e **Restaurar backup ZIP**.
- O ZIP inclui todos os projetos **já salvos** no repositório Satin local (formato .bmrail), incluindo imagem de origem, camadas, linhas, ordem e seções manuais. Antes de exportar, salvar as últimas edições do projeto.
- Exportação e importação são feitas via seletor nativo do Android (Storage Access Framework), sem permissões amplas de leitura de armazenamento e sem expor a chave de assinatura.
- O backup tem manifesto identificado, verificação de entrada de diretórios/caminhos, validação do CRC32 existente de cada documento, limites por entrada, limite de descompactação de 64 MB e máximo de 100 projetos.
- **Restauração não substitui projetos existentes:** usa IDs novos. Antes de escrever arquivos, verifica todos os documentos do ZIP. Em caso de falha durante a gravação, tenta desfazer apenas os arquivos recém-criados.
- O sistema antigo de biblioteca e backup **.flp** não foi modificado. Este ZIP é próprio para projetos editáveis **.bmrail**; um backup .flp anterior não passa a conter esses projetos automaticamente.

## B. Conexões de seções de caligrafia

- O botão **Conectar à seção anterior (até 1,5 mm)** aparece ao editar a segunda seção ou posteriores da mesma camada Satin.
- O controle é opcional e **desativado por padrão**. Para seções distantes, o gerador mantém TRIM e JUMP.
- Quando ativado, compara o último ponto efetivamente costurado da seção anterior às duas extremidades do rail inicial. Permite continuidade somente com distância até 15 unidades de máquina = 1,5 mm.
- Em união válida, aproxima a linha à borda selecionada com STITCH e continua a coluna Satin sem o corte ou salto intermediários.
- A sustentação central da seção conectada é omitida para evitar retroceder e deixar deslocamentos ocultos. Permanecem disponíveis underlay e compensação na primeira seção e nas seções independentes.
- Se uma união for impossível, **a geração falha com mensagem de orientação**. É melhor separar/reordenar ou mover as seções no editor do que criar pontos invisíveis cruzando partes da letra.

## C. Compatibilidade de projetos

- Versão do documento editável sobe de V1 para V2 **sem alterar a biblioteca .flp**.
- V1 permanece legível, interpretando a conexão entre seções como desativada.
- V2 grava e restaura o campo de continuidade de cada seção.
- Testes JVM verificam V1 -> V2, round-trip das uniões, exportação/restauração ZIP, arquivos corrompidos ou com caminho malicioso, preservação dos projetos existentes, uniões permitidas e recusadas.

## D. Homologação necessária

- [ ] CI unit tests Android e assembleDebug (apenas QA)
- [ ] Android real: salvar alterações; exportar ZIP no seletor; fechar o app; importar ZIP; reabrir cada documento; conferir rails, cores e ordem
- [ ] Testar conexões ativadas/desativadas e PES reaberto
- [ ] Prova real de bordado em máquina Brother, conferindo linha, subpontos, cortes e tecido cetim
- [ ] Antes de qualquer distribuição comercial, assinar com a chave original de produção, incrementar versionCode e testar atualização sobre a instalação existente

**Limitações:** não há reconhecimento automático universal de lettering cursivo e não foi bordado o exemplo "Sabrina Freitas" fisicamente. A ligação de seções requer que o operador alinhe a geometria e não cria automaticamente desenho vetorial fiel de uma fotografia.
