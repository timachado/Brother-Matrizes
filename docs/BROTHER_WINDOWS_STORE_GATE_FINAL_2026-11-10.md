# Brother Matrizes Windows — pendências antes da submissão Microsoft Store

**Data:** 10/11/2026
**Branch:** `feature/windows-fiel-android-1-11-8-20261106`
**PR:** https://github.com/timachado/Brother-Matrizes/pull/226 (rascunho, sem merge)
**Produto na Store:** Brother Matrizes, em rascunho. O titular informou que selecionou publicação manual.

## Evidência concluída

- Name da Store: `T.I.MachadoSoluesemTecnol.BrotherMatrizes`
- Publisher: `CN=353309CB-0CCB-4E2D-8F88-AF631DAA395F`
- PublisherDisplayName: `T.I. Machado Soluções em Tecnologia`
- Microsoft Partner Center: conta criada; produto e primeira submissão em rascunho.
- Build do candidato MSIX x64 unsigned passou no GitHub Actions:
  https://github.com/timachado/Brother-Matrizes/actions/runs/42773674295
- Artifact da revisão: `Brother-Matrizes-MSIX-STORE-CANDIDATE-UNSIGNED-REVIEW`, id `13742549617`, expira 17/11/2026.
- O proprietário já aprovou a QA funcional anterior; **não exigir repetição dessa aprovação**.
- Capturas reais QA Windows foram disponibilizadas e a Store aceitou as imagens segundo o proprietário.
- Nenhum merge à `main`, assinatura Android, backend produtivo, publicação ou upload automático à Store.

## Bloqueios técnicos constatados (não confundir QA de empacotamento com release)

1. O manifesto do candidato já exibe **Brother Matrizes**, porém o executável inicia janela `Brother Matrizes — Windows (QA interna)` em `windows-fiel/src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt`. Corrigir **apenas em modo de compilação de release**, sem ocultar estado QA em uma versão que não esteja homologada.
2. `windows-fiel/build.gradle.kts` usa `version = "0.0.1-fidelidade-qa"`. A identidade MSIX tem versão provisória `1.0.0.0`; reconciliar versões de aplicativo/pacote para distribuição conforme política Store.
3. Existem controles Windows explicitamente não homologados, como importação TTF/OTF sujeita a identidade/cota; mensagens de código ainda dizem que recursos estão indisponíveis no Windows. Não liberar mecanismos comerciais ou contornar autenticação para eliminar mensagens.
4. `BrotherPlansScreen` é configurada em modo `displayOnly`, com checkout direcionado ao navegador oficial. Validar declaração de compras digitais, disponibilidade e capacidades reais antes do envio.
5. O MSIX gerado por MakeAppx foi reaberto e contém launcher e runtime, mas **a instalação/execução do aplicativo como MSIX assinado em ambiente de teste** não está comprovada. Rodar teste de empacotamento em runner Windows isolado com certificado de QA efêmero, sem usar credenciais da T.I. Machado nem tocar produção; só então validar redirecionamentos, permissões, escrita de projetos e WACK.
6. Conferir versão comercial final, ícones/logotipos, capturas e conteúdo da listagem com as funcionalidades finais realmente disponíveis; não substituir a QA anterior por evidência falsa.
7. A modalidade Individual/Company da conta Microsoft e elegibilidade para publicação em atividade profissional não foi confirmada em captura. Confirmar na conta/suporte quando necessário, sem compartilhar documento pessoal por chat.
8. Somente após resolver os bloqueios, obter decisão explícita do proprietário para **carregar o pacote final** e uma decisão **separada** para `Enviar para certificação`. Publicação continua manual.

## Próximo trabalho de engenharia

- Mapear versão release isolada do app Windows (sem remover avisos QA do build QA).
- Corrigir as integrações Windows realmente pendentes ou declarar limitações de maneira compatível com o produto; preservar funcionalidade aprovada.
- Implementar teste MSIX instalado em ambiente efêmero, sem publicação.
- Gerar artefato final unsigned para **upload manual** à Store após validação.
