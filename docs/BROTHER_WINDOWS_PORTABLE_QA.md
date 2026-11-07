# Brother Matrizes Windows — pacote portátil de QA (não comercial)

## Objetivo
Gerar um aplicativo Windows autocontido para testes técnicos, **sem instalador**, sem alterações no registro e sem atalhos automáticos. O executável da pasta de QA chama-se `BrotherMatrizesQA.exe`.

Este procedimento **não** aprova a publicação comercial do produto nem sua distribuição aos clientes.

## Como o pacote é produzido
- Pipeline exclusivo: `.github/workflows/brother-windows-portable-qa.yml`.
- Windows Actions com JDK 17, Gradle 8.14 e Compose Desktop.
- Tarefa `createDistributable` produz uma imagem `app-image` de pasta; **não executa** `packageExe`, `packageMsi` nem gera instalador.
- A verificação exige o launcher Windows, a pasta `app`, o runtime `runtime`, testes de backup FLP, leitura/recuperação Android, conta em QA e abertura direta.
- O próprio executável deve abrir a Home e Minha Conta no runner Windows e criar duas capturas de QA válidas antes do upload do pacote.
- Após aprovação, o pacote é disponibilizado **somente** como artefato temporário da execução privada do GitHub Actions, por sete dias. Nenhuma ação publica no website ou GitHub Releases.

## Obter e testar em um PC autorizado
1. Acesse a PR #226 do repositório privado e a execução do workflow **Windows executavel portable interno (QA)**.
2. Quando o job estiver **Success**, encontre a seção **Artifacts** do GitHub Actions e obtenha `Brother-Matrizes-Windows-PORTABLE-QA-NÃO-COMERCIAL`. Se o job falhar ou esse artifact não aparecer, **não há binário validado**.
3. No Windows de QA, extraia o ZIP **com todas as subpastas**. Não execute `BrotherMatrizesQA.exe` isolado de `runtime` e `app`.
4. Verifique o `SHA256` do launcher contra `LEIA-ME-QA.txt`, que contém também a revisão do Git usada para o pacote.
5. Abra `BrotherMatrizesQA.exe` para avaliar a Home, Criar Nome, Viewer, Biblioteca, Fontes e Minha Conta. Faça testes com **arquivos sintéticos ou cópias**; não substitua acervos de clientes.
6. Um alerta do Windows para aplicativo **não assinado** é esperado nesta fase, mas nunca desabilite antivírus, SmartScreen ou outras proteções de forma global para contorná-lo. Se a execução for bloqueada, registre o bloqueio como pendência da homologação.

## Limitações deliberadas
- A tela comercial permanece explicitamente identificada como **QA**. Google OAuth real requer verificação da allowlist de redirecionamento; nenhuma credencial privada está embutida.
- Importação de fontes TTF/OTF, cotas e assinatura comercial **não estão liberadas**; o port do motor de bordado ainda depende de equivalência comprovada.
- A recuperação de backup completo do Android se restringe aos arquivos FLP em **pasta nova**. Fontes, licenças, configurações e tokens não são restaurados.
- A pasta de QA não configura associações de arquivo, atalhos ou ícones de distribuição comercial.
- O executável não tem assinatura digital nesta fase. O ícone final e a distribuição no site T.I. Machado dependem de homologação.
- O pacote não deve ser chamado de versão final, definitiva, completa ou comercial.

## Critério para avançar
Todos os testes Windows relevantes devem passar **na mesma revisão**, incluindo execução real da pasta portátil. Em seguida serão necessárias inspeção manual em PC Windows e validação de recursos comerciais e Google, antes da decisão de assinar e disponibilizar uma versão comercial.
