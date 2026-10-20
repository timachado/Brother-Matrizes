# QA de fontes do Brother Matrizes — teste Android sem reinstalações

## Problema investigado
Os testes instrumentados GreatVibesStartDiagnosticTest comparam a saída do
motor Satin contra vídeos de referência para a família tipográfica **Great Vibes**.
Para essa comparação geométrica, o arquivo exato
`GreatVibes-Regular.ttf` deve estar presente em
`app/src/androidTest/assets/` e possuir licença de uso compatível.

Na infraestrutura CI inspecionada, o recurso não está presente. A falha ao
abrir o arquivo é **falta da massa de testes** — não é, por si só, evidência
de defeito no gerador de pontos.

## Política de QA aplicada
1. Com a fonte original disponível, os dois testes Great Vibes executam
   normalmente as comparações previstas. Os limiares não foram afrouxados.
2. Sem a fonte exata, ambos são reportados como **ignorados por pré-condição**
   (JUnit Assumption), com mensagem explícita. **Não são aprovados**.
   Fontes do Android NÃO são substitutos para os números do vídeo.
3. O novo `ImportedFontSystemSmokeTest` é **obrigatório** no emulador:
   importa uma TTF do sistema, gera texto Satin para `Maria` e `Priscila`,
   valida pontos e dimensões positivas, exporta/relê **DST sintético de QA**
   pelo pipeline canônico e mantém a autorização de PES da ferramenta
   experimental de imagens explicitamente `false`.
4. Uma conversão de fonte sintética para DST não valida um bordado físico
   em máquina Brother, nem aprova exportação PES de `Imagem para Bordado`.
5. Para recuperar a comparação real Great Vibes, a fonte correta poderá
   ser fornecida pela pessoa com direito de uso e incluída de maneira
   apropriada no ambiente de testes privado. **Não incorporar arquivos
   de fonte do cliente em logs, PR ou mensagens** sem autorização.

Os resultados instrumentados são publicados no GitHub Actions; não é
necessário instalar outra cópia do aplicativo no telefone do usuário.
