# Relatório de prontidão comercial — Brother Matrizes Windows

**Data da auditoria:** 07/11/2026  
**Referência:** Android comercial 1.11.8 (versionCode 195)  
**Escopo:** PR #226 — `feature/windows-fiel-android-1-11-8-20261106`  
**Classificação:** QA interna; PR aberta como draft, sem merge para `main`

## Resumo executivo

**Restam 9 portões de aceitação comercial.** Estes são *marcos verificáveis de
produto, segurança e liberação*, não nove compilações ou nove incrementos de
versão. Uma etapa só sai de aberta quando todas as suas condições de aceite
têm evidência verificável e são aprovadas. O avanço real de código/QA não
significa que uma etapa já esteja homologada comercialmente.

**Portões fechados:** 0/9. **Portões ainda abertos:** 9/9.
Há código e testes de QA substanciais em vários desses portões, mas nenhum
possui evidência integral de liberação de produção. Não atribuir percentual
de prazo nem prometer data antes da homologação.

### O que já está demonstrado em QA

- Compose Desktop Kotlin com Home, Criar Nome, Monograma, Desenho, Editor
  de Nomes, Editor de Matriz, Viewer, Simulador, Converter, Biblioteca,
  Fontes QA, Minha Conta QA, transferência local e gerenciamento básico FLP.
  A presença de uma tela não certifica paridade total de seus fluxos.
- Portable `BrotherMatrizesQA.exe` com runtime completo, build e captura
  executadas em Windows runner, sem assinatura ou distribuição comercial.
- Leitura real de matrizes PES/DST/JEF pelo motor Android; FLP v11
  reaberto e backups testados com exemplos sintéticos.
- Inventário ZIP, RAR/RAR5 e 7Z; extração seletiva privada e sink
  de validação ainda isolados da UI/conta comercial.
- Reserva gratuita idempotente V2 vinculada ao UUID do journal:
  Deno, Kotlin e PostgreSQL 16 descartável, incluindo concorrência.
- Journal local de operações pendentes e FileLock entre JVMs independentes;
  **não** é reconciliador comercial completo.
- OAuth Google/PKCE, DPAPI e verificação de entitlement têm testes
  simulados, não login Google comercial ponta a ponta homologado.

Evidências:
- [CI V2, 3/3 jobs aprovados #42405719472](https://github.com/timachado/Brother-Matrizes/actions/runs/42405719472)
- [Windows Portable QA #42405263745](https://github.com/timachado/Brother-Matrizes/actions/runs/42405263745)
- [Contrato visual e inventário de 16 rotas](../windows-fiel/BASELINE-ANDROID-1.11.8.md)
- [Relatório V2](BROTHER_WINDOWS_RESERVA_IDEMPOTENTE_V2_RESULTADOS_QA.md)
- [OAuth Windows](BROTHER_WINDOWS_OAUTH_GOOGLE_HOMOLOGACAO_QA.md)

## Atualização de engenharia — recibos Pro V2

- Implementado helper isolado
  `supabase/functions/brother-usage/pro-receipt-v2.ts`.
- [CI de recibos Pro V2 #42408075964](https://github.com/timachado/Brother-Matrizes/actions/runs/42408075964):
  `completed/success`, **10 testes Deno aprovados** com banco
  e autoridade de licença simulados. O servidor comercial **não**
  importa esse helper e mantém Pro V2 desabilitado.
- O portão comercial #2 permanece **aberto** até existir uma
  tabela durável de recibos Pro e RPCs de reserva/finalização
  idempotentes testadas em PostgreSQL descartável e staging, bem
  como auditoria de privilégios e implantação autorizada.
- Contagem inalterada: **9 portões abertos / 0 homologados**.

## Nove portões de conclusão

| Nº | Portão | Situação 07/11/2026 | Evidência necessária para fechar |
|---:|---|---|---|
| 1 | Reserva gratuita V2 em staging e implantação controlada | **QA isolada aprovada; implantação pendente** | Migração oficial revisada, RLS/grants auditados, protocolo V1 Android intacto, testes de concorrência em staging, rollback planejado e implantação separadamente autorizada. |
| 2 | Idempotência e recibos duráveis do plano Pro | **Parcial: recibos e RPCs Pro V2 aprovados em PostgreSQL descartável; staging e implantação pendentes** | Autoridade de assinatura consultada no servidor; recibos Pro persistidos por UUID/conta/operação; repetição não concede plano, crédito ou reserva adicional; casos de cancelamento/expiração e concorrência aprovados. |
| 3 | Finalização de cota e reconciliação remota idempotente | **Parcial: journal e status de reserva em QA** | Decisão de sucesso/falha gravada server-side e recuperável, consulta autenticada de tentativas sem resposta, resposta repetida sem dupla cobrança, testes de perda de conexão e contratos Free/Pro. |
| 4 | Diário de propriedade e recuperação de arquivos após falhas | **Parcial: rollback no processo e FileLock** | Journal de ownership persistido antes de salvar matriz/fonte, gravar em pasta temporária privada, commit durável, recuperação sem apagar arquivos alheios, simulações de corte de energia e concorrência entre processos. |
| 5 | Login Google Windows e licenciamento real | **QA simulada, homologação real pendente** | Redirect allowlist confirmada no Supabase, navegador externo + PKCE + DPAPI + getUser em conta exclusiva de QA, logout/expiração, vínculo da identidade com entitlement real, sem tokens em claro. |
| 6 | Paridade funcional/visual de todas as 16 rotas Android e integração comercial na UI | **Parcial: telas principais existentes** | Checklist por rota visual, navegação, ações reais, operações e estados; fluxos FontLibrary/ArchiveImport/Account/TelegramSearch/Community e outras diferenças implementados; importação e licença conectadas só após portões 1–5; aprovação do proprietário. |
| 7 | Interoperabilidade e testes reais de bordado | **Parcial: parsers e FLP com testes sintéticos** | Amostras autorizadas PES/DST/JEF/TTF/OTF/RAR/RAR5, equivalência de pontos/cores/bastidores, PE-DESIGN pertinente, exportações/USB e testes em bordadeira física sem modificar o acervo original. |
| 8 | Segurança, desempenho, permissões e redistribuição de dependências | **Pendente para comercial** | SBOM e licenças (incluindo junrar/UnRAR), scanners de dependências, prova de ACL privada e ausência de tokens em logs, testes Win10/11 em hardware real, integridade, backup, recuperação, acessibilidade e desempenho. |
| 9 | Pacote oficial assinado, aceitação final e publicação | **Pendente** | Identidade Brother Matrizes oficial, nome/ícone definitivos, EXE portátil autocontido e SHA256, assinatura de código/SmartScreen, QA de cliente e aceite documentado; publicação controlada no site T.I. Machado, notas de versão e notificações somente após aprovação. |

## Ordem de execução recomendada

1. **Backend seguro:** concluir portões 2–3, mantendo Free V2
   em staging (#1), sem mexer na produção até existir rollback e
   resultado de homologação.
2. **Recuperação e autenticação:** concluir #4 e #5; somente depois
   integrar cobrança/importação à interface Windows.
3. **Produto e interoperabilidade:** fechar #6 e #7, verificando
   as 16 rotas e equipamentos reais.
4. **Release:** auditoria #8, pacote e aceite #9. A liberação no site
   exige aprovação explícita separada.

## Dependências e decisões pendentes

- A função SQL V2 está **apenas em documentação**, não é uma migração
  aplicada; a Edge Function da produção não foi atualizada.
- Pro V2 não possui recibo idempotente; permanece desativado.
- Não existe comprovação de login OAuth Google real com callback
  de loopback aprovado no painel Supabase.
- A versão Windows ainda não comprova paridade completa com as
  16 rotas da versão Android; prévias ou screenshots não bastam.
- Testes de processo encerrado em CI não substituem queda de energia.
- Não marcar um portão concluído com base em `build SUCCESS` isolado.
- A PR #226 é **draft** na `main`; publicação no site, GitHub Release,
  assinatura e notificação a clientes não foram executadas.

**Política de atualização:** depois de cada entrega, registrar evidência
e alterar exclusivamente o portão correspondente, sem declarar os demais
concluídos. O número de portões pode mudar se surgir bloqueador material
em QA real; isso deve ser explicitamente informado.

*Contagem desta auditoria: 9 abertas / 0 homologadas para distribuição comercial.*

## Atualização de QA — recibos Pro V2 em PostgreSQL 16

- [GitHub Actions #42408650490](https://github.com/timachado/Brother-Matrizes/actions/runs/42408650490): **dois jobs aprovados**, incluindo PostgreSQL 16 descartável e Deno (10 testes, zero falhas).
- Implementadas [tabela RLS e funções SQL QA](../qa/sql/brother_pro_v2_receipts_ephemeral.sql) de reserva/finalização Pro, com replay por UUIDv4, quatro conexões concorrentes e decisão imutável quando a finalização é repetida.
- O [script SQL isolado](../.github/scripts/brother_pro_v2_ephemeral_pg.sh) também verifica recusa de outra conta, operação, plano, reserva expirada, acesso de anon/authenticated e colisão entre chaves Free/Pro.
- O código **não** está conectado à Edge Function comercial; nenhuma migração Supabase foi criada ou aplicada. Falta integrar a autoridade de licenças e homologar em staging antes de implantar.
- **Contagem comercial inalterada: 9 portões abertos, zero homologados.**
