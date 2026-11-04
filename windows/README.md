# Brother Matrizes Desktop — Windows QA (NÃO COMERCIAL)

Este módulo Windows está isolado do aplicativo Android comercial. Os arquivos oficiais em `app/`, `supabase/` e os pipelines de publicação Android **não são modificados**.

## Recursos disponíveis nesta etapa de QA

- Interface Compose Desktop para Windows, empacotada como EXE/MSI por GitHub Actions.
- Importação local de **DST, PES e JEF**, usando os parsers Kotlin oficiais do Android.
- Visualização técnica de pontadas, cores, limites, dimensões e contagem.
- **Sequência visual manual** de comandos (cursor de progresso). Não simula tempo real, tensão, trajetória de máquina ou qualidade de bordado.
- Ajustes geométricos em memória: escala, rotação, deslocamento e espelhamento, com restauração do original.
- **Criação de nova matriz de nome** pelo `TextMatrixGenerator` oficial, inicialmente com fontes integradas e estilos ponto corrido ou Satin.\n- **Monogramas de 1 a 3 iniciais** com os estilos Linear, Clássico e Empilhado, usando o `MonogramGenerator` oficial Android e preservando a matriz aberta anteriormente.
- **Biblioteca local de arquivos existentes**: usuário escolhe explicitamente a pasta; listagem não recursiva DST/PES/JEF, abre matrizes sem movê-las. A pasta é lembrada em preferências locais do Windows para a próxima abertura, mas **não é varrida automaticamente**: exige clique em Atualizar pasta. Há opção Esquecer pasta, sem apagar nenhum arquivo. Não faz upload, indexação geral nem sincronização. Exibe até 60 entradas por vez.
- Conversão/exportação experimental DST/PES/JEF pelo `MatrixConverter` oficial, com releitura do arquivo gerado antes de gravar.
- Perfil PES PE-DESIGN PC experimental. **Ainda não comprovado no PE-DESIGN 11 real.**
- Exportação apenas para arquivo **novo** (`CREATE_NEW`); não substitui arquivos anteriores.
- Testes automatizados de leitura, edição, conversão, proteção contra sobrescrita, biblioteca local, persistência de seleção, geração de nomes e monogramas.

## Motor Kotlin comum

O build `windows/` compila fontes selecionados diretamente de
`app/src/main/java/com/timachado/brothermatrizes/core/embroidery/`, usando uma
cópia de build em `windows/build/generated/official-core/kotlin/` que não altera
o Android. Inclui `DstParser`, `EmbroideryIoParser`, `MatrixEditor`,
`TextMatrixGenerator`, `MonogramGenerator`, `MatrixConverter` e dependências. O Windows usa a
variante JVM de EmbroideryIO; o Android mantém sua dependência própria.

## Limitações e bloqueadores para publicação comercial

**Ainda não há paridade com todas as funções Android.** Pendente:
editor completo de objetos e letras, fontes TTF/OTF importadas, opções avançadas de monogramas,
biblioteca inteligente e organização persistente, projetos editáveis/backup,
simulador de máquina, ferramentas avançadas de digitalização, integração de
conta, licenças, cotas e planos, notificações, atualização segura, integrações
específicas do Android com equivalentes Windows quando aplicável.

Esta QA não possui autenticação nem libera recursos pagos de uma versão
comercial. Ela **não pode ser distribuída como produto comercial**, nem usada
para bordados de clientes sem homologação.

## Segurança

- Leitura local com tamanho máximo de 8 MiB por matriz e limites de comandos.
- Biblioteca somente leitura de uma pasta explicitamente escolhida; não percorre
  subpastas, não segue atalhos simbólicos e limita listagem a 500 matrizes.
- Abertura, geração e gravação de arquivos são processadas fora da thread da UI.
- Originais não são regravados; criação/exportação validada e com proteção
  contra sobrescrita de arquivos preexistentes.
- Formatos gravados têm validação de releitura. Não substitui verificação com
  software e hardware de bordado reais.

## Compilação

Dentro de `windows/`, com JDK 17 e Gradle 8.14:

```shell
gradle test compileKotlin
gradle packageExe packageMsi
```

O workflow `.github/workflows/brother-windows-desktop-qa.yml` roda em
`windows-latest` e guarda o instalador **QA** em artefatos de duração limitada.
Nenhuma publicação automática de release comercial.

## Critérios mínimos antes de produção

1. Matriz de paridade funcional com a versão Android aprovada.
2. CI do Windows e regressão Android aprovados.
3. Homologação PES no **PE-DESIGN 11** e prova de abertura/edição real.
4. Instalação, abertura e segurança verificadas num computador Windows.
5. Pacotes assinados e release comercial autorizado separadamente.

O PR da expansão deve continuar em **rascunho** até essas etapas.
