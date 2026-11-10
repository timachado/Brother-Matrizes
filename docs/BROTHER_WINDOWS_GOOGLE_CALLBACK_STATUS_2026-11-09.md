# Brother Matrizes — confirmação OAuth recebida no Windows (09/11/2026)

A foto enviada pelo proprietário mostra a página local de callback OAuth:
`Autenticação recebida. Volte ao Brother Matrizes.`, no navegador do
computador. Essa evidência **confirma que a URL 127.0.0.1 foi alcançada
e o listener reconheceu o callback**. Ela **não** confirma que a
troca PKCE por tokens, a verificação do Google, a sessão DPAPI ou a
leitura da assinatura tenham sido concluídas.

A revisão muda o texto do navegador para
`Código do Google recebido. Volte ao Brother Matrizes. A validação
da conta e da assinatura continua no aplicativo.`

Em **Minha Conta**, a interface agora separa:
1. **Google autenticado:** PKCE concluído, identidade conferida via Supabase,
   credencial guardada no DPAPI do usuário Windows.
2. **Assinatura consultada:** leitura adicional em `brother-entitlement`,
   sem conceder direitos ou cotas em nível local.
3. Se o serviço de assinatura retornar erro HTTP ou a conexão falhar,
   o Google autenticado **continua protegido** e o cliente pode selecionar
   `Atualizar assinatura` sem reiniciar OAuth. Erro de identidade ou
   recibo inconsistente continua revogando a sessão, sem privilégio Pro.
4. A página de retorno não contém código OAuth, token, estado nem email.

Há testes sem rede real para o texto neutro de callback, expiração de
requisições e falha 503 do entitlement com preservação do DPAPI. Nenhum
callback real, token ou URL completa do proprietário é registrado no repo.
A importação TTF/OTF/ZIP continua aguardando autorização Free/Pro V2
no servidor de produção; não existe liberação de plano Pro pelo cliente.

Este ajuste será distribuído apenas após build e QA Windows passarem.
