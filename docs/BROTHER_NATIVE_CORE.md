# Brother Matrizes — Native Core

Esta branch é a baseline técnica para novos desenvolvimentos do Brother Matrizes.

## Infraestrutura obrigatória

As próximas versões devem preservar:

- autenticação Google via Supabase;
- licenças/entitlements separadas de telemetria;
- `BrotherPresenceHeartbeat` ativo no ciclo de vida principal;
- `BrotherPresenceClient` usando a Edge Function `brother-presence`;
- heartbeat somente com sessão Google autenticada;
- atualização imediata ao abrir/retomar e periódica enquanto o app estiver em primeiro plano;
- nenhuma falha de telemetria pode bloquear login, licença, checkout ou uso do aplicativo;
- `device_id` pseudonimizado/estável;
- nenhuma senha, token privado ou segredo comercial armazenado no painel.

## Regra para novas versões

Toda nova feature/fix/release do Brother Matrizes deve partir desta baseline (ou de uma versão posterior que já a contenha).

Não recriar o heartbeat manualmente a cada release. Ele faz parte do núcleo do aplicativo.

## Backend

A presença é gravada em `brother_matrizes_devices` através da Edge Function autenticada `brother-presence`.

Pagamentos e licenças continuam independentes desse fluxo.
