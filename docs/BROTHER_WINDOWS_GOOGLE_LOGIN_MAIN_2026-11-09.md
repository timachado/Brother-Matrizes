# Brother Matrizes Windows: Google na Minha Conta normal

Conta Google integrada para o Windows com a chave pública do próprio
Supabase Brother Matrizes. A aplicação NÃO embute service_role, chave
secreta, token privado ou segredo Google.

Ao escolher **Entrar com Google**, o aplicativo consulta o estado público
do provedor, abre OAuth via navegador do Windows, exige PKCE + estado de
uso único e o redirect de loopback que o **Supabase precisa permitir**
em sua allowlist. Após verificar `/auth/v1/user`, salva a sessão no
DPAPI individual do usuário. Restaurar sessão exige nova verificação
no Supabase; nenhuma autenticação é executada no início do aplicativo.

A consulta da assinatura é SOMENTE LEITURA; nenhuma categoria Pro é
inferida do email/estado local. A mesma conta em Minha Conta habilita
a navegação nos catálogos DaFont/NetFontes com a reserva de pesquisa
DaFont sujeita ao mesmo servidor V1 do Android.

**NÃO autoriza importação comercial TTF/OTF/ZIP/Matrizes por si só.**
Para isso faltam staging isolado, backend V2 homologado e os controles
de idempotência ligados. O Supabase exigiu confirmação de custo para
abrir uma branch staging; nenhum gasto foi aprovado ou realizado.
A configuração exata da lista de Redirect URLs continua não verificável
pelo conector e o login real depende de a URL estar liberada no servidor.

Não alterar o Android, o Supabase V1, planos de clientes nem credenciais
existentes. A conta QA antiga permanece isolada e nunca é migrada
automaticamente para a conta Windows normal.
