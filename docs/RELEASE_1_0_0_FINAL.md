# Brother Matrizes — versão 1.0.0

Data: 10/10/2026. Android package: `com.timachado.brothermatrizes`; `versionCode = 166`. Branch: `release/brother-matrizes-1.0.0`.

## Funcionalidades preservadas
Esta versão mantém os recursos de matrizes e fontes, biblioteca, criação de nomes, simulação, integração com arquivos compactados, conta Google e teste Pro sem cartão que foram incorporados nas versões candidatas anteriores. A compilação de produção utiliza o controle de importações por conta, não o contador local de beta.

## Melhorias da candidata final
O backup completo de biblioteca passou a exportar fontes importadas TTF/OTF junto dos projetos. Backups antigos contendo somente matrizes continuam aceitos. A nova assinatura de produção é a mesma da rc1, rc2 e rc3 (SHA-256 do certificado `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`).

## Testes pendentes e limitações assumidas
- O desenvolvedor não precisa fazer uma assinatura comercial do próprio app para lançar. Isso **não comprova** o funcionamento da compra paga até o reconhecimento de Pro: esse fluxo exige testes posteriores com WooCommerce e Efí.
- A instalação da 1.0.0 por cima de uma beta `debug` é bloqueada pelo Android quando os certificados diferem. Antes de desinstalar a beta, faça backup de matrizes e guarde as fontes originais TTF/OTF.
- O novo backup completo só está presente na 1.0.0; a beta antiga exporta apenas matrizes.
- Verificações físicas com bordadeira e homologação de compras/renovação/cancelamentos são testes a acompanhar em manutenção futura.
- Nenhum plano Pro é concedido por ser desenvolvedor ou por editar dados locais; só uma licença válida confirmada no servidor deve liberar recursos comerciais.

## Distribuição
A compilação de CI prepara o pacote `Brother-Matrizes-1.0.0-ALIGNED-UNSIGNED.apk`. Ele **não deve ser instalado ou distribuído antes de assinatura**. A assinatura final e a comparação do certificado são feitas fora do repositório com a mesma chave de produção previamente gerada. Guardar a chave fora do GitHub e manter cópias privadas de segurança.

## Próximas versões
Patches 1.0.x poderão corrigir erros reportados e concluir homologações pendentes sem trocar a chave de assinatura de produção.
