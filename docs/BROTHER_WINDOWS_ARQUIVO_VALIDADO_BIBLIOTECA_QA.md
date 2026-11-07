# Brother Matrizes Windows — validação real antes da Biblioteca (QA)

**Branch:** `feature/windows-fiel-android-1-11-8-20261106`
([PR #226](https://github.com/timachado/Brother-Matrizes/pull/226)).
**Base Android comercial:** 1.11.8. Não atualizar `main`, Android, licenças,
Supabase, cliente, site nem publicar uma versão comercial nesta etapa.

## Finalidade

A extração de ZIP, 7Z e RAR/RAR5 foi implementada separadamente como
`DesktopArchiveSelectiveStager`, que entrega apenas um arquivo privado
de até 24 MB **após a reserva de quota server-side** no
`DesktopArchiveCommercialImportGate`.

Esta etapa introduziu a implementação de
`DesktopArchiveValidatedLibrarySinkFactory` para substituir o sink
falso usado anteriormente em testes. Ela permanece **não conectada à
interface Windows**. O fluxo comercial segue desativado e sem
credenciais.

## Validação e gravação por formato

### Matrizes PES, DST e JEF

1. Verifica que a entrada possui extensão e tamanho da autorização
   original e não é link simbólico.
2. Carrega o arquivo via `DesktopEmbroideryFiles.open`, que utiliza
   os **parsers reais do Android** `EmbroideryLoader` e recusa dados
   ilegíveis, ausência de pontadas, encerramento incorreto ou
   contorno inválido.
3. Converte o desenho validado para FLP v11 pelo `ProjectCodec`
   original Android, faz round-trip para conferir a preservação
   de pontadas/formato e só então grava.
4. Grava em **pasta de Biblioteca escolhida e existente**, com
   identificador determinístico por SHA-256 dos bytes originais.
   Usa `StandardOpenOption.CREATE_NEW`: não sobrescreve FLP.
5. Matrizes idênticas são detectadas e não consomem cota no contrato
   de finalização `success=false`. Conteúdo divergente no mesmo
   destino é recusado.
6. Falhas de persistência deixam a transação sinalizada para
   rollback. O arquivo validado pode ser reaberto via
   `DesktopFlpProjectFiles` e listado com `DesktopProjectCatalog`.

### Fontes TTF e OTF

1. Verifica extensão e tamanho autorizados da entrada.
2. Executa `DesktopReadOnlyFontAnalyzer.inspect`, incluindo
   assinatura OpenType/TrueType e leitura de contornos Skia.
3. Utiliza `DesktopImportedFontLibrary.importFile`, o armazenador
   Windows já testado para SHA, deduplicação e integridade.
4. Não associa automaticamente a fonte ao editor de nomes Android:
   a geração comercial de pontadas de fontes importadas continua
   dependendo do motor/homologação da função.

### Segurança e rollback

- Nenhum caminho interno do ZIP/RAR/7Z é usado como destino de gravação.
- O hash da origem impede extrair um pacote substituído depois da prévia.
- O sink mantém registro **somente do item criado pela transação**.
  A reversão não altera arquivos de cliente já existentes.
- Após commit completo, a reversão verifica o digest da gravação;
  se o arquivo foi alterado por outro processo, recusa excluí-lo.
- O registro e a reversão funcionam apenas **durante o processo atual**.
  Falhas de energia, concorrência entre processos e reconciliação de
  reservas remotas ainda exigem um journal durável + idempotência no
  servidor antes de habilitar esta capacidade para clientes.

## Testes de aceitação

`DesktopArchiveValidatedLibrarySinkTest` verifica:

- Geração de matrizes reais PES, DST e JEF pelo conversor oficial Android,
  leitura pelo carregador oficial, gravação e reabertura em FLP v11,
  deduplicação e presença no catálogo de projetos.
- Preservação dos arquivos ZIP originais e de FLPs anteriores.
- PES com conteúdo inválido: recusa e finalização de cota simulada
  com `success=false`.
- Falha de confirmação no servidor simulado: rollback do novo FLP,
  preservando projetos antigos.
- TTF real do runner Windows: Skia, armazenamento, deduplicação e
  preservação dos bytes originais da fonte do sistema.
- Falso TTF com magic number válido mas dados inválidos:
  recusa por Skia, sem fonte persistida.

Workflow:
[QA Windows — biblioteca validada](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-validated-library-qa.yml).

**Critério adicional:** nenhuma classe de UI pode instanciar o
`DesktopArchiveValidatedLibrarySinkFactory`, pois o OAuth real e a
cota real não foram homologados. A QA só injeta tokens e cotas falsas.

## Bloqueadores para disponibilização comercial

Login Google Windows/PKCE homologado e consentimento do usuário,
chaves públicas em runtime, validação do plano no servidor,
idempotência `reserve/finalize` após desligamento abrupto,
diário transacional local durável, testes em múltiplas instâncias,
homologação de fontes OTF reais e de matrizes autorizadas por
clientes, e integração do gerenciamento de fontes e matrizes
importadas ao fluxo de UI aprovado.

**Atenção:** código implementado não equivale a publicação. É
necessário consultar o resultado das Actions antes de declarar QA
concluída e a execução de um launcher Windows antes de oferecer
novo pacote portátil.

## Evidência CI — 07/11/2026

- [Biblioteca validada #42390275211](https://github.com/timachado/Brother-Matrizes/actions/runs/42390275211):
  `completed/success` na revisão `6480889`. Compilação
  Kotlin Desktop, testes com matrizes PES/DST/JEF geradas pelo motor
  Android, armazenamento FLP v11, TTF real do Windows validada pelo
  Skia, prevenção de duplicatas, arquivos falsos e rollback com
  servidor de cotas simulado. Nenhuma chamada de produção.
- [Relatórios dos testes #13581236674](https://github.com/timachado/Brother-Matrizes/actions/runs/42390275211/artifacts/13581236674),
  `brother-windows-validated-library-test-reports`, aproximadamente
  3,6 KB, apenas XMLs de testes sintéticos.
- [Portable QA #42390566337](https://github.com/timachado/Brother-Matrizes/actions/runs/42390566337):
  este novo pipeline incluirá os validadores reais na regressão do
  pacote completo. Seu resultado deve ser verificado antes de
  anunciar um novo launcher para teste.

