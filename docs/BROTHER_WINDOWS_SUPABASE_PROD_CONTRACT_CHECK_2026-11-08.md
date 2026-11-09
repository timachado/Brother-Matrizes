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


## Contrato idempotente de importação de fontes Free V2 — 09/11/2026

**Auditoria somente leitura da implantação atual:** projeto oficial `dwpcddiramxlhavdmmyn` consultado como `ACTIVE_HEALTHY`; Edge Function `brother-usage` versão **7**, que NÃO implementa `reserve_v2`, `reserve_pro_v2`, `status_pro_v2`. O catálogo `pg_proc` de `public` mantém as RPCs legadas `brother_matrizes_reserve_import` e `brother_matrizes_finalize_import`, mas ainda não contém as operações V2 propostas. Nenhuma modificação de banco/Edge Function foi feita.

**Mudança no Windows, somente na PR de QA:** `DesktopCommercialFontImportGate` diferencia os gateways locais sintéticos do adaptador HTTP real `DesktopFontIdempotentUsageGateway`. Para um gateway real, exige journal durável e grava `FREE_V2` + UUIDv4 **antes de abrir conexão**; então chama `reserveFontOnce(accessToken, pending.localId)`, transmitindo `action=reserve_v2`, `operation=import_font` e `client_request_id`. Exige recibo `usage_<UUID>`, estado `reserved`, operação exata e booleano JSON `replayed`. Não chama `reserve` legado na importação real Windows. O caminho das fakes V1 continua nos testes isolados.

Se a resposta desaparecer ou o servidor antigo devolver 400, o marcador `RESERVE_UNKNOWN` permanece, não há fonte publicada nem nova reserva em retry. A reconciliação continua manual. Isso é intencional para não cobrar ou liberar matriz/fonte com estado incerto.

**Portas de habilitação QA:** `BROTHER_WINDOWS_QA_GOOGLE_AUTH=enabled`, chave pública, `BROTHER_WINDOWS_QA_REDIRECT_REVIEWED=enabled`, `BROTHER_WINDOWS_QA_FONT_V2_BACKEND_REVIEWED=enabled` e `BROTHER_WINDOWS_QA_FONT_IMPORT=enabled`, mais controlador Google validado. Os valores são declarações do operador e **não garantem implantação, autenticação real, assinatura ou cotas disponíveis**. Nunca habilitar contra produção V1. O pacote público tem essas opções desligadas.

**Limite comercial:** o novo adaptador é **Free V2 somente**. No código da Edge Function preparado na branch, `reserve_v2` retorna 503 para Pro; recibos **Pro V2** exigem ativação separada de RPC/Edge Function/entitlement. Não usar `pro_<UUID>` efêmero legado nem permitir importação Pro por fallback. O backend V2 e o OAuth de QA devem ser homologados primeiro em ambiente isolado, com aprovação explícita antes de mudanças no serviço oficial.

**Evidência automatizada adicionada:** `DesktopBrotherFontUsageGatewayTest` verifica payload V2, replays, recusa de respostas legadas/Pro e UUID errado; `DesktopCommercialFontFreeV2JournalTest` reproduz resposta perdida, confirma UUID persistido e nenhuma reserva repetida; `DesktopWindowsFontImportOptInTest` exige todas as aprovações. Sem conta real, HTTP de produção ou fonte de cliente.
