# Brother Matrizes 1.10.10 (186) — candidata para homologação do checkout Google

## Estado da publicação

- **NÃO PUBLICAR** esta candidata como release comercial antes dos testes de pagamento completos.
- Última release pública intacta: **1.10.9 (185)**; `updates/stable.json` continua nela.
- Nova candidata: **1.10.10 (186)** com `-PbrotherCheckoutHandoff=true` apenas no workflow de homologação.
- APK mantém pacote `com.timachado.brothermatrizes`. Assinar exclusivamente com o certificado original SHA-256:
  `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`.
- Não alterar PES, DST, JEF, simulação da máquina, bordados, arquivos do usuário, cotas, WooCommerce nem Efí.

## Protocolo atual

1. Google autenticado no Supabase do Brother Matrizes.
2. Android envia token apenas no cabeçalho HTTPS para a função `brother-checkout-handoff`.
3. Supabase valida usuário Google e emite bilhete cifrado AES-256-GCM, validade 45 segundos.
4. Plugin WordPress `Brother Matrizes — Login Integrado de Checkout` **0.1.2-rc3** abre sessão WordPress normal, valida uso único e seleciona o plano.
5. WooCommerce confirma produto, preço e estoque: Mensal #329; Anual #330; Vitalício #331; Lançamento #308.
6. Se houver outro plano Brother no carrinho, troca pelo selecionado; produtos externos só são trocados após confirmação explícita.
7. Só recibo verificado de pagamento Efí/WooCommerce libera acesso PRO. Nunca conceder licença com base em uma URL, retorno do navegador ou cliente Android.

## QA necessário antes de liberar

- [x] Plugin 0.1.2-rc3 testado e aprovado pelo desenvolvedor no fluxo até checkout.
- [x] Integração de chave Supabase/WordPress com `ready: true`; usuário validou redirecionamento ao checkout.
- [ ] Testar com conta Google de cliente **não administradora**, login persistente e recuperação de sessão.
- [ ] Verificar se os quatro planos exibem o valor e as condições corretas diretamente no checkout.
- [ ] **Mensal e anual:** verificar recorrência Efí e métodos válidos; não aceitar cobrança comum como assinatura por engano.
- [ ] Simular pagamento no ambiente seguro Efí (sandbox), conferindo criação de pedido, assinatura, evento e concessão da licença.
- [ ] Validar cancelamento, vencimento, estorno, estoque, plano vitalício e restauração da licença no aplicativo.
- [ ] Conferir que Bíblia EBD, produtos de outros clientes, pedidos e licenças não foram afetados.
- [ ] Validar SHA e assinatura comercial original no APK 186.
- [ ] Aprovar liberação comercial e **só então** publicar release GitHub; atualizar manifest OTA via workflow oficial.

## Pipelines independentes

- `.github/workflows/qa-brother-11010-checkout-candidate.yml` compila **sem chave e sem publicar**; APK unsigned não deve ser instalado.
- `.github/workflows/brother-11010-checkout-sign-qa.yml` tenta assinar candidato no ambiente protegido `brother-release-signing` e **não publica release**. A política de aprovação/branches do GitHub deve ser respeitada. Se o ambiente impedir execução, não remover a proteção: revisar configuração com autorização do proprietário.
- O plugin instalado no WordPress não precisa ser substituído novamente se continuar a versão 0.1.2-rc3.

## Observações

Outra linha de trabalho contém um candidato 1.10.10 de **geometria de fontes**. Não juntar mudanças de motor de bordado nesta candidata sem revisão/testes específicos; a alocação final de versionCode deve ser reconciliada antes de criar tag.
