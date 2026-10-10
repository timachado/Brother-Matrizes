# Brother Matrizes — licenças assinadas via WordPress, sem Cloudflare

Estado: **protocolo e verificador Android em desenvolvimento, NÃO ativado em produção**.
Usar somente com WooCommerce/App Commerce Core da T.I. Machado na InfinityFree.
Não adicionar novo plugin nem desinstalar o Core existente.

## Motivação

O navegador Android abre o WordPress, mas um cliente HTTP REST direto do APK
é bloqueado pela infraestrutura InfinityFree Free (403/desafio HTML).
O APK não deve ganhar acesso Pro porque abriu o checkout, mostrou uma
confirmação de compra ou recebeu dados não assinados via link.

## Separação de responsabilidades

- WordPress / WooCommerce / App Commerce Core: conta de compra, pedido,
  titular da licença, revogação e emissão de comprovante criptográfico.
- Efí: pagamento; liberar direito apenas após comprovação confiável de
  liquidação no pedido WooCommerce, nunca pelo simples retorno à loja.
- Supabase: **login Google exclusivamente**; seu `auth.uid` é identidade
  do titular no Android, não a origem dos direitos comerciais.
- Android: valida comprovante público, identidade Google, aparelho e expiração.
  **Jamais guarda chave privada ou credenciais Efí.**
- Cloudflare: não usado.

## Protocolo de lease preparado no Android (bm1)

Assinatura: ECDSA P-256 / SHA-256, assinatura ASN.1 DER conforme
`openssl_sign(..., OPENSSL_ALGO_SHA256)` e Android
`Signature.getInstance("SHA256withECDSA")`.

Formato: `bm1.base64url(JSON_UTF8).base64url(DER_SIGNATURE)`.
A assinatura cobre os bytes ASCII **exatos** `bm1.<base64url(JSON_UTF8)>`.
O servidor nunca deve usar a entrada recebida do celular como chave pública.

Campos estritos de JSON:

| Campo | Regra |
|---|---|
| `v` | número inteiro `1` |
| `iss` | `ti-machado-app-commerce` |
| `aud` | `brother-matrizes-android` |
| `sub` | UUID da sessão Google/Supabase confirmado para o titular |
| `dev` | identificador atual do aparelho, `DeviceIdentity.current(context).deviceId` |
| `lic` | ID de licença opaco no WordPress, 8–128 caracteres seguros |
| `plan` | um dos quatro códigos Pro aprovados |
| `status` | `active` |
| `iat` | emissão, epoch Unix segundos |
| `nbf` | não válido antes, epoch segundos |
| `exp` | vencimento do **lease**, epoch segundos |

Limite do primeiro protocolo: **sete dias** entre emissão e expiração do lease.
Mesmo o plano vitalício precisa renovar seu lease periodicamente (o direito
comercial pode continuar vitalício). A revogação offline só entra em vigor
quando expira esse prazo. Não prometer revogação imediata sem servidor acessível.
O relógio do aparelho não é uma âncora anti-rollback confiável, portanto
necessita proteção adicional antes de qualquer liberação comercial.

## Requisitos de emissão no WordPress — ainda NÃO implementados

1. Gerar chave P-256 exclusiva do licenciamento, proteger a **chave privada**
   fora do diretório público do site e dos backups/ZIPs acessíveis. Incluir
   apenas a chave pública correspondente no aplicativo assinado.
2. Página autenticada (Chrome) vinculada à conta WooCommerce dona de pedido pago
   e não reembolsado; verificar vínculo ao **app Brother Matrizes #307**
   e ao produto #308 ou respectivos planos válidos, bem como limites de
   dispositivos e status da assinatura, tudo por API interna WordPress.
3. Identificar de modo criptográfico a sessão Google `sub` do APK antes
   de associá-la à conta WooCommerce. Um `sub` digitado livremente,
   e-mail coincidente, URL de sucesso ou número do pedido **não bastam**.
4. Vincular o aparelho por desafio com nonce único, prazo, proteção de replay
   e prova de posse de uma chave gerada em Android Keystore; não confiar em
   `dev` informado sozinho para autenticar um aparelho.
5. Uma vez confirmado o pedido, titular, plano e dispositivo, emitir a licença
   assinada pelo WordPress. O usuário pode copiar o código pelo navegador e
   importar no APK (ou futuro deep link autenticado e estritamente validado);
   não fazer POST direto do Android para a InfinityFree.
6. Criar trilha de auditoria e rate limits no WordPress, separação
   de privilégios e nonce CSRF; não colocar o token JWT do Supabase em URL,
   query string, logs de hospedagem ou páginas públicas.
7. Renovação, cancelamento, reembolso e expiração na Efí/WooCommerce devem
   atualizar o direito servidor. É necessário homologar confirmação de pagamento
   sem depender de webhooks recebidos pela InfinityFree. Caso não seja possível,
   manter a contratação automática desativada.
8. Testar vínculo entre WooCommerce/Google, duplicação do lease,
   transferência de aparelho, expiração, clock rollback, reembolso,
   estoque 50 do lançamento e autorização de uso/cotas.

## Estado efetivo do código

- `WordPressSignedLicense.verify(...)` verifica assinatura, contexto,
  plano e vigência, mas **não está conectado** a `AccountSnapshot`,
  `hasProAccess` ou telas de ativação.
- Não há chave pública real provisionada nem emissor WordPress implantado.
- Conta/checkout web via `WordPressWebStore` está disponível. Isso não concede Pro.
- Preservar app #307 e produto WooCommerce #308 em rascunho, com vendas desligadas,
  até teste de ponta a ponta e assinatura de produção.
