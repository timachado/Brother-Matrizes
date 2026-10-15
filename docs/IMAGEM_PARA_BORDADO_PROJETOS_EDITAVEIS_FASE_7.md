# Brother Matrizes — Etapa 7: projetos Satin editáveis persistentes (protótipo)

## Objetivo
Retomar a edição de nomes e logotipos após encerrar e abrir novamente o Android, preservando a **imagem original** e o trabalho manual em todas as seções. Inclui comandos de composição de várias seções: reordenar, excluir, deslocar a seção inteira sem mudar a largura e desfazer.

## Recursos na branch QA
1. **Criar desenho/logo → Imagem → Multicor:** aparece a área "Projetos editáveis Satin".
2. O usuário pode definir um nome e tocar em **Salvar projeto** ou **Salvar alterações**.
3. **Reabrir projetos** apresenta documentos locais do aparelho; carregar restaura a imagem original, largura da matriz, bastidor, formato (PES/JEF/DST), cores, ordem e visibilidade das camadas, parâmetros Satin e todos os rails manuais.
4. **Salvar uma cópia como novo projeto** cria outro documento, sem sobrescrever a versão anterior.
5. No editor Satin manual, cada seção pode ser movida na ordem de costura, excluída (se restar ao menos uma) e deslocada em quatro direções por incrementos de 1% da arte. O arquivo preserva essas mudanças.
6. A geração do PES continua usando o pipeline original e só costura se a geometria passar na validação.

## Compatibilidade com Biblioteca de Projetos
A biblioteca comercial atual usa ProjectCodec / ProjectStore, com extensão .flp, para matrizes de pontos. **Não alteramos nem migramos esse formato**. As novas edições são armazenadas separadamente no diretório privado do app, sob brother_matrizes_editable_satin_v1, em arquivos .bmrail. Tais arquivos não fazem parte ainda da exportação/backup da biblioteca .flp existente. O PES gerado é independente do novo arquivo de edição.

## Estrutura e salvaguardas
- V1 binário versionado, CRC32 do conteúdo, gravação atômica via AtomicFileWriter.
- Armazena bytes ARGB originais (até 512×512), configurações por camada, até 80 seções por cor e até 100 pares de rails por seção, com limites no total.
- Máximo 4 MB por documento e 100 projetos locais. Leitura limitada por tamanho e validação de índices/coordenadas; IDs gerados por UUID (evita caminhos provenientes do nome digitado).
- Carregamento e gravação fora da thread principal (Dispatchers.IO); mensagens de erro não substituem os dados correntes.
- O formato local é um documento de edição e **não** substitui o arquivo PES de máquina.

## Testes
- Round-trip preserva pixels/label, cores/ordem, densidade, underlay, compensação, bastidor e múltiplas seções.
- Arquivo alterado/truncado falha no checksum; caminhos inválidos, dimensões e rails fora da área são rejeitados.
- Salvar sobre o mesmo ID não cria cópia; "Salvar como novo" cria outro arquivo e mantém anterior.

## Limitações pendentes antes da versão pública
- Ainda não há salvar automático silencioso, busca/sincronização em nuvem nem exportação/backup da sessão .bmrail (não assumir que o backup comercial .flp cobre essas edições).
- A composição manual exige ajustar cada trilho. Detecção automática de letras cursivas ramificadas e ligações perfeitas de "Sabrina Freitas" **ainda não existem**.
- Faltam zoom/pan no canvas, handles Bézier independentes, editor de segmentação de contornos e máscaras, otimização completa de pontos e perfis profissionais de cetim.
- Sem homologação no dispositivo e sem amostra bordada em máquina Brother.
- Versão comercial 1.10.8 (184), simulador original, fontes, licenças, assinaturas e atualização por assinatura continuam intactos; branches de pesquisa permanecem isoladas.

## Critérios para publicação
- [ ] Testes unitários aprovados no CI.
- [ ] Android assembleDebug no CI, sem alterações ao código protegido.
- [ ] Testes em Android real: salvar, fechar app, reabrir, restaurar, alterar seção e exportar PES novamente.
- [ ] Backup/exportação do novo documento de edição ou aviso explícito da limitação na UI.
- [ ] PES reaberto e bordado em retalho da máquina Brother, com ajuste de estabilizador.
- [ ] Pacote comercial assinado pela chave de produção original e com versionCode crescente, só após validações.
