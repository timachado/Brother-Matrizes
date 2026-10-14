# Brother Matrizes — janela de lançamento 10/10/2026, 12h BRT

## Tipo de lançamento aprovado tecnicamente nesta branch
Pré-lançamento **beta gratuito de demonstração**, sem contratação de Pro, sem promessa
de licença paga ou disponibilidade de todos os recursos comerciais.
Qualquer anúncio de lançamento definitivo/comercial exige nova homologação.

### Ponto de corte
- APK `0.50.2-beta5` (versionCode 157), branch `refactor/brother-matrizes-clean-0469`.
- Verificar **GitHub Actions Android APK**: segurança, testes unitários, lint,
  PHP signer QA, APK compilado/alinhado e testes API 35 sem falhas.
- Antes de distribuir ao público fora de testes privados, **assinar com a chave
  definitiva**, verificar certificado e instalação de atualização sem apagar dados.
  APK `debug` do GitHub Actions é **somente para QA** (certificado debug mutável
  conforme o runner). A build release não assinada também não serve ao público.
- Testar em dispositivo Android real: abrir, conta, importação TTF/OTF,
  importação ZIP e RAR e visualização de matrizes, criação de nome, exportação
  PES/DST/JEF, armazenamento local, simulação de pontadas. Confirmar fisicamente
  em bordadeira real as matrizes geradas (não há teste físico automatizado).
- Assegurar que rascunhos WordPress #307 e WooCommerce #308 permaneçam
  não publicados; não ativar pedidos, assinaturas e licenciamento Pro.
- Conferir que `BM_CATALOG_SYNC_ENABLED = false` e que o app usa somente
  WordPress/WooCommerce para contas comerciais (via navegador).
- Login Google Supabase mantém sua responsabilidade única por autenticação.

## Preview offline para contornar bloqueio REST da InfinityFree
A build beta usa `BuildConfig.BETA_LOCAL_IMPORTS=true` para permitir importações
locais com limites **gratuitos por dispositivo**: 3 fontes e 5 matrizes no mês UTC,
conforme cotas gratuitas atualmente estabelecidas no plugin.
Não requer resposta da API de cotas, não confere licença Pro e não sincroniza
contadores com WordPress. O contador em SharedPreferences é adequado a
**demonstração, não a proteção antifraude**: limpar dados/reinstalar pode zerá-lo;
o relógio do dispositivo pode ser manipulado. Não usar essa implementação para
planos comerciais, Trial Pro ou lógica de pagamentos.
Reservas de operação são finalizadas somente no sucesso. Limites são
explicitados na tela Minha Conta.
Quando houver backend comercial acessível e validação completa, definir
`BETA_LOCAL_IMPORTS=false` e promover novo APK com migração de política.

## Bloqueadores para um lançamento comercial definitivo às 12h
1. Efí → confirmação de pedido no WooCommerce precisa de teste real e suporte de
   callback/webhook na hospedagem, o que é problemático na InfinityFree grátis.
2. Vinculação autenticada entre comprador WooCommerce, Google `sub` e
   prova de posse do dispositivo (não confiar em e-mail ou deep link).
3. Emissor PHP isolado está homologado localmente mas sem controller,
   chave de assinatura em produção, vínculo/verificação comercial, formulário ou
   canal de entrega; o verificador Android ainda **não libera Pro**.
4. Quotas pagas precisam de autorização confiável; contador local beta não serve
   como fonte de verdade para faturamento.
5. Falta validar a assinatura definitiva de distribuição e testar a atualização
   sobre instalações anteriores com preservação de Biblioteca e fontes.
6. Verificar canal de distribuição e antivírus/Play Protect em instalação real.
7. Homologação na máquina de bordar para arquivos PES/DST/JEF de referência.

## Página/arte/aviso honesto de divulgação
Nome: Brother Matrizes — beta de lançamento.
Status: demonstração gratuita, sujeito a melhorias e limites de importação.
Assinaturas e licenças Pro: em preparação, não abertas.
Orientação: baixar somente pela fonte oficial indicada pela T.I. Machado.
Não publicar links da versão debug como download público definitivo.

## Posição sobre infraestrutura
WordPress InfinityFree permanece no ar, sem substituição ou perda dos pedidos
existentes. O GitHub hospeda o projeto e artefatos de QA, não habilita compras.
Cloudflare descontinuada. Supabase somente login com Google.
