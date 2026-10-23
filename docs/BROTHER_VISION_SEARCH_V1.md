# Brother Vision Search — integração incremental (Android)

Status: proposta de código em branch isolada; **não é publicação comercial**.

## Entregue nesta branch
- Entrada “Pesquisar por imagem” na tela existente “Buscar Matrizes”.
- Seleção de imagem via seletor Android e recepção de imagem via Compartilhar/Enviar.
- Classificação **local** com modelo do Google ML Kit, sem upload de foto para a nuvem.
- Até três termos de pesquisa, priorizados por confiança, traduzidos quando disponíveis e **editáveis**.
- Busca final aberta no Telegram oficial com o mecanismo já existente; nenhum acesso novo aos chats.
- Importação PES/DST/JEF e respectivas cotas continuam inalteradas.
- Sem novas permissões Android, credenciais ou coleta de conversas.

## Não implementado — limitações reais
- Não é integração direta com o aplicativo Google Lens nem a API de busca reversa do Google.
- Não compara pixels com todas as imagens ou mensagens do Telegram.
- Não acessa nem percorre canais/grupos privados, não baixa arquivos automaticamente.
- Não garante que uma correspondência por palavra-chave seja a matriz exata.

O Telegram restringe extração e indexação de conteúdo para treinamento/desenvolvimento/operação de sistemas de IA:
https://core.telegram.org/api/terms
https://telegram.org/tos/content-licensing

Uma pesquisa visual real com retornos de arquivo PES/DST/JEF só deve usar catálogo **próprio/autorizado**, criado por titulares dos desenhos ou contribuições voluntárias com consentimento explícito e mecanismo de exclusão. Links para Telegram poderão ser exibidos como referências, respeitadas as permissões e termos.

## Verificação antes de publicar
1. Executar testes unitários e compilação debug, sem instalar sobre a versão oficial.
2. No Android, testar seleção de JPEG, PNG, foto grande, foto inválida e cancelamento.
3. Compartilhar uma imagem pelo Telegram/galeria para Brother Matrizes: deve abrir “Buscar Matrizes” e oferecer termos, **não cobrar cota de importação**.
4. Verificar que importar PES, DST, JEF e arquivos ZIP ainda segue o fluxo anterior.
5. Confirmar que o termo é editável e que abrir Telegram requer os mesmos controles de conta/cota anteriores.
6. Validar a interface em tela pequena e com fonte acessível.
7. Checar aprovação e testes Android antes de qualquer merge e release.

Dependência nova: `com.google.mlkit:image-labeling:17.0.9` (modelo embarcado).


## Fase 2 — busca por semelhança visual no catálogo pessoal
- Impressão digital dHash 64-bit calculada localmente a partir da foto escolhida.
- Cadastro **voluntário** de uma foto de referência por matriz **já salva**; a interface solicita confirmação explícita.
- Guarda apenas o identificador da matriz e um hash visual no armazenamento privado do app; a foto original não é copiada para o catálogo.
- A imagem pesquisada é comparada com referências previamente cadastradas; exibe até cinco resultados a partir do limiar heurístico de 72%.
- Abre os projetos correspondentes pela função da biblioteca atual, sem importação nem cobrança de nova cota.
- Permite remover referências, sem apagar a matriz; imagens com pouco detalhe são rejeitadas.
- Esta busca é local, experimental e não cobre imagens de qualquer canal do Telegram. Fotos desconhecidas não ficam automaticamente disponíveis para comparação.

O percentual exibido representa uma medida matemática baseada na distância entre hashes, **não** uma probabilidade calibrada de que a matriz seja a mesma. dHash pode falhar com recortes, rotações, fotos de tecido ou desenhos parecidos; não deve ser anunciado como reconhecimento visual universal. Para pesquisa em catálogo amplo, é preciso obter fontes licenciadas e testar representações de imagem mais robustas.

### QA adicional
- Cadastrar a referência de uma matriz salva e confirmar que a mesma foto aparece no topo dos resultados.
- Testar imagem diferente, foto sem detalhes, exclusão de referência e troca de uma referência existente.
- Confirmar que referência não aparece para projeto não mais presente na biblioteca (nenhuma busca por conteúdo externo).
- Testar o encaminhamento de arquivos PES/DST/JEF/ZIP e as quotas de Telegram sem mudanças.
- Verificar desempenho com dezenas e centenas de projetos em celulares Android de entrada.
- Validar a permanência das referências entre reinicializações e a legibilidade em telas pequenas.
