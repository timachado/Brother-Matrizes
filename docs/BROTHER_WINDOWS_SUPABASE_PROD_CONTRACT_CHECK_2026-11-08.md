# Brother Matrizes Windows — auditoria somente leitura do contrato Supabase

**Data:** 08/11/2026 (BRT)  
**PR:** #226 (branch Windows QA)  
**Produção:** não modificar, não executar SQL, não publicar Edge Function automaticamente.

## Evidências confirmadas na conexão Supabase autorizada

- Projeto oficial de referência: `dwpcddiramxlhavdmmyn`; estado consultado `ACTIVE_HEALTHY`.
- A Edge Function **implantada** `brother-usage` estava ativa, versão **7**, na consulta somente leitura.
- O código implantado valida as operações HTTP `reserve`, `finalize` e `status`.
- Nessa versão implantada, `status` aceita apenas `{"action":"status"}` e devolve **consumo agregado mensal**, não um identificador individual `request_key`.
- A versão implantada **não oferece** as ações `reserve_v2`, `reserve_pro_v2`, `status_pro_v2` nem consulta `status` por chave/ operação.
- O arquivo `supabase/functions/brother-usage/index.ts` da branch contém implementação proposta e aditiva para essas ações, juntamente com auxiliares e testes; **código na branch não é implantação**.
- Portanto, teste/compilação Windows QA bem-sucedidos **não comprovam** paridade comercial com o serviço que está no ar.
- Nenhum token, dado de cliente, tabela de cotas, plano, política ou configuração de autenticação foi modificado na auditoria.

## Correção aplicada ao Windows

`DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled` exige dois fatores **de configuração de QA**: opt-in explícito Google com chave pública e `BROTHER_WINDOWS_QA_KNOWN_QUOTA_STATUS_V2=enabled`. Isso é independente do opt-in da importação comercial.

O botão remoto de `Minha Conta` fica **desabilitado por padrão**, com explicação de incompatibilidade do contrato publicado. Mesmo depois de autorizado em QA, o diagnóstico ainda exige sessão Google verificada, registro local de cota existente e clique explícito; ele jamais reserva, finaliza, libera fonte nem apaga marker.

O sinalizador é uma barreira de operador, **não uma detecção automática de implantação**. Só deve ser habilitado quando um responsável confirmar, em ambiente isolado e com conta QA, o protocolo do servidor e a lista de Redirect URLs. Nunca tratar a presença da variável como prova de backend pronto.

## Caminho de homologação ainda pendente

1. Revisar e testar `reserve_v2`, consulta por chave e recibos Pro V2 no backend descartável/staging, com proprietário, categoria, nonce/idempotência e permissões verificadas; manter o caminho Android V1 intacto.
2. Testar as RPCs/Edge Functions propostas isoladamente; revisar RLS, grants, advisors, integridade e idempotência de falhas de rede e finalização.
3. Obter aprovação específica antes de fazer qualquer mudança aditiva no serviço oficial. Esta etapa **não executa** alteração de produção.
4. Confirmar Redirect URLs e executar login PKCE com uma conta Google **dedicada de QA** no Windows. Nenhuma conta pessoal deve ser usada em testes automáticos.
5. Só depois executar E2E controlado com créditos/cotas explicitamente autorizados, validar o retorno do servidor, a permanência do journal e a paridade do Android comercial.
6. Permanecem pendentes assinatura do executável, testes completos em Windows real e publicação no site da T.I. Machado.

**Status desta revisão:** documentação e trava do Windows implementadas; homologação do servidor e publicação comercial pendentes.
