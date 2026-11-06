# Brother Matrizes — roteiro de homologação Windows (QA, NÃO COMERCIAL)

**Status:** documento de execução MANUAL. As verificações abaixo **não foram
realizadas em equipamento físico**. Resultados devem ser preenchidos por uma
pessoa autorizada, em ambiente de testes. O CI comprova apenas as verificações
automatizadas que constarem como aprovadas no GitHub Actions.

## 1. Identificação e pré-condições

- [ ] Identificar a máquina **de teste** (edição e versão Windows, arquitetura,
      resolução, escalonamento 100%/125%/150%, memória RAM).
- [ ] Anotar o run ID, commit SHA e link do pacote QA baixado **do PR #225**.
- [ ] Extrair o ZIP, comparar os hashes EXE/MSI com `qa/SHA256SUMS.txt` e
      `qa/installer-manifest.json`. **O manifesto dentro do ZIP não é uma
      assinatura de autenticidade**: conferir também o download do repositório
      confiável e sua execução.
- [ ] Confirmar que a máquina não contém uma versão comercial ou dados de
      clientes que possam ser confundidos com testes.
- [ ] Registrar antes dos testes que os instaladores QA **não possuem assinatura
      Authenticode comercial**. Nunca orientar desativar antivírus/SmartScreen.
- [ ] Criar pasta isolada para matrizes artificiais, exportações e logs, sem
      credenciais, matrizes privadas ou projetos comerciais.
- [ ] Abrir a subpasta `qa/fixtures/` do ZIP QA: contém amostras sintéticas
      `QA_NOME_DST.dst`, `QA_NOME_PES_MAQUINA.pes`,
      `QA_NOME_PES_PE_DESIGN_PC_EXPERIMENTAL.pes`, `QA_NOME_JEF.jef`,
      `QA_PROJETO_ANDROID.flp` e `QA_LETRAS_CAMADAS.flp` +
      `QA_LETRAS_CAMADAS.bmlayers`.
- [ ] Verificar o `qa/fixtures/SHA256SUMS.txt` com `Get-FileHash -Algorithm
      SHA256` em cada arquivo listado. Esses checksums protegem somente
      integridade local: não substituem assinatura digital nem autenticação.
      **Jamais usar amostras da QA para bordado comercial.**

## 2. Instalação e abertura da interface

- [ ] Instalar MSI em máquina de teste com permissão adequada, sem avisos
      inesperados nem reinicialização forçada.
- [ ] Iniciar pelo atalho e verificar o título `Brother Matrizes Desktop — QA`.
- [ ] Confirmar que a tela inicial aparece sem erro e sem login presumido.
- [ ] Conferir se a janela inicial se adapta à área útil do monitor,
      **sem ultrapassar barra de tarefas e bordas do desktop**, especialmente
      em resoluções menores (ex.: 1024×768 e 1366×768).
- [ ] Conferir se o painel de pré-visualização e os controles laterais ficam
      acessíveis em 100%/125%/150% de escala. Registrar cortes,
      rolagens, botões inacessíveis ou campos parcialmente escondidos.
- [ ] Conferir no artefato de evidência QA as duas capturas: interface
      vazia e interface com matriz sintética `QA` (não são testes manuais).
- [ ] Com uma matriz sintética aberta no PC, testar zoom 1×, 2×, 4× e 8×,
      roda do mouse ancorada no cursor, deslocamento com arraste e botão
      Ajustar. Verificar que a prévia não invade outros painéis.
- [ ] Confirmar que apenas navegar pela prévia não altera pontos,
      dimensões, histórico de edições nem o hash do arquivo original.
- [ ] Verificar mensagens de erro legíveis e sem travamentos da interface.
- [ ] Fechar, reabrir, reiniciar o computador de teste e abrir novamente.
- [ ] Desinstalar pelo Windows e verificar a remoção do aplicativo/atalhos.

## 3. Arquivos, edição e integridade

- [ ] Em uma pasta autorizada com mais de 80 matrizes sintéticas, testar
      busca por nome com letras maiúsculas/minúsculas, filtros DST/PES/JEF/FLP,
      páginas anteriores/próximas e abertura de um arquivo após o 60º item.
- [ ] Abrir três matrizes sintéticas de teste: DST, PES e JEF.
- [ ] Comparar prévia, número aproximado de pontos, salto, cor e dimensões com
      uma ferramenta confiável. Registrar divergências sem corrigir o original.
- [ ] Testar desenho simples e matriz com múltiplas cores, saltos e cortes.
- [ ] Criar composição nova com até 10 letras: selecionar segunda letra,
      mover somente ela em X/Y, ampliar, girar e testar Desfazer/Refazer.
      Verificar JUMP entre glifos e único END; registrar discrepâncias
      de espaçamento e posicionamento antes de testar em bordadeira.
- [ ] Salvar composição individual com **dois arquivos novos**:
      `.flp` oficial e `.bmlayers` Windows, fechar e reabrir pelo novo botão.
      Verificar que cada letra recupera posição, escala e rotação anteriores.
- [ ] Abrir somente o FLP pelo leitor Android/Windows normal:
      as pontadas devem permanecer iguais mesmo sem o sidecar.
- [ ] Alterar uma cópia do FLP ou do arquivo auxiliar e tentar reabrir como
      camadas: associação SHA-256 deve bloquear a reedição divergente.
- [ ] Tentar usar nome de destino já existente: os arquivos devem permanecer
      inalterados.
- [ ] Gerar nome com fonte integrada, além de monograma de 1 a 3 iniciais.
- [ ] Importar fonte TTF/OTF **de uso autorizado**, testar contornos com furos
      (ex.: `O`), cursivas e espaços. Registrar caracteres rejeitados.
- [ ] Abrir matrizes que cabem e que excedem os bastidores 100×100,
      130×180 e 200×300 mm; verificar margens, avisos de possível
      orientação alternativa e ausência de mudança automática de pontos.
- [ ] Alterar RGB de uma linha com formato hexadecimal e verificar prévia,
      preservação das pontadas, Desfazer/Refazer e persistência no FLP.
      Comparar cores exportadas em PES/JEF; não presumir suporte a paleta no DST.
- [ ] Editar escala geral, expansão H/V independente, rotação, deslocamento,
      espelhamento, Desfazer, Refazer e Restaurar original.
- [ ] Verificar que **Desfazer/Refazer afeta só a sessão atual** e que abrir outro
      documento reinicia o histórico.
- [ ] Salvar como **novo** projeto FLP, fechar e reabrir. Conferir pontos/cores.
- [ ] Converter cópias novas para DST/PES/JEF, sem sobrescrever arquivos fonte.
- [ ] Tentar salvar sobre arquivo que já exista: deve recusar sobrescrita.
- [ ] Testar entradas inválidas, arquivo truncado e arquivos acima dos limites,
      conferindo recusa segura sem quebrar a tela.

## 4. Compatibilidade PE-DESIGN 11 — somente com software oficial disponível

- [ ] Abrir no PE-DESIGN 11 um PES **de teste** produzido pela QA Windows.
- [ ] Comparar bastidor, limites, cores, pontos de salto e orientações.
- [ ] Abrir no Brother Matrizes Windows um PES criado pelo PE-DESIGN 11 e
      comparar resultados sem sobrescrever o arquivo original.
- [ ] Registrar avisos do PE-DESIGN, atributos ausentes ou mudanças de pontos.
- [ ] Não presumir compatibilidade industrial só porque o parser reabre o PES.

## 5. Teste de bordado real — equipe habilitada e máquina de teste

- [ ] Escolher uma matriz sintética simples com bastidor compatível.
- [ ] Inspecionar fisicamente tecido, agulha, linha, estabilizador e bastidor,
      com operador habilitado e procedimento seguro da máquina.
- [ ] Confirmar dimensões, orientação, cor, acabamento e posição no bastidor.
- [ ] Testar nomes Satin apenas com validação prévia do operador; comparar
      densidade, sobreposição, saltos e arremates.
- [ ] Anotar modelo/firmware da bordadeira, tempo, resultado, fotos autorizadas
      e eventuais ajustes necessários.
- [ ] Em caso de comportamento inesperado, interromper o teste na máquina.
- [ ] Nunca usar bordados de clientes como material de ensaio inicial.

## 6. Registro dos resultados

Preencher uma cópia destas informações por pacote e computador:

| Item | Valor |
|---|---|
| GitHub Actions / commit | A preencher |
| SHA-256 do MSI testado | A preencher |
| Windows / arquitetura | A preencher |
| Resolução / escala | A preencher |
| PE-DESIGN 11 disponível? | Sim / Não |
| Bordadeira, modelo e firmware | A preencher |
| Instalação, abertura e desinstalação | PASSOU / FALHOU / NÃO EXECUTADO |
| DST/PES/JEF / FLP / fontes | PASSOU / FALHOU / NÃO EXECUTADO |
| Expansão H/V / Desfazer/Refazer | PASSOU / FALHOU / NÃO EXECUTADO |
| PE-DESIGN 11 | PASSOU / FALHOU / NÃO EXECUTADO |
| Costura real | PASSOU / FALHOU / NÃO EXECUTADO |
| Evidências autorizadas e incidentes | Links ou nomes de arquivo |

**Critérios de bloqueio comercial:** qualquer falha crítica de integridade
ou salvamento; comportamento de bordado não comprovado; falta de homologação
de licenças/autenticação, assinatura de código ou distribuição; risco de
segurança sem avaliação. Não fazer merge/publicação comercial com campos
críticos marcados como `NÃO EXECUTADO`.

## 7. Preparação para pendrive — validação MANUAL PENDENTE

- [ ] Usar uma mídia USB de testes sem arquivos importantes; não utilizar
      projetos ou matrizes de clientes.
- [ ] Abrir matriz sintética e acessar a nova área Transferir.
- [ ] Conferir recomendação de bastidor e formato PES; selecionar um
      bastidor insuficiente e confirmar bloqueio.
- [ ] Escolher DST e conferir avisos de cores. Cancelar a confirmação e
      verificar que nenhum arquivo foi gravado.
- [ ] Selecionar o pendrive pelo seletor do Windows e criar novo PES.
      Confirmar conteúdo, tamanho e SHA-256 informados.
- [ ] Repetir o destino existente: a segunda gravação deve ser recusada
      e o arquivo original deve manter o hash.
- [ ] Ejetar pelo mecanismo oficial do Windows. O aplicativo não ejeta
      nem controla o dispositivo.
- [ ] Com operador habilitado e bordadeira de testes, importar a cópia
      pelo menu oficial da máquina e verificar formato, bastidor, cor,
      pontos, orientação e eventuais requisitos de pastas.
- [ ] Registrar modelo, firmware e resultados. **Não** classificar este
      fluxo como envio direto USB/Wi-Fi nem como versão comercial.
