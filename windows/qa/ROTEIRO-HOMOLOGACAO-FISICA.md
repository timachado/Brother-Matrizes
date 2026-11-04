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
- [ ] Salvar composição individual em FLP novo e reabrir: os pontos devem
      permanecer iguais; os objetos por letra NÃO são reeditáveis após reabrir.
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
