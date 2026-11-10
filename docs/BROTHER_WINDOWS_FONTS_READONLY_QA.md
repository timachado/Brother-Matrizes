# Brother Matrizes Windows — diagnóstico TTF/OTF sem importação

**Estado:** funcionalidade de QA da Biblioteca de Fontes. Não libera importação comercial, não gera pontadas nem altera assinaturas.

## O que foi implementado
- A tela **Fontes** conserva os presets reais do motor Android e os nomes legíveis.
- O comando **Analisar arquivo TTF/OTF (sem importar)** abre o seletor de arquivos do Windows e realiza apenas diagnóstico de leitura.
- A inspeção aceita somente arquivos regulares TTF/OTF com até **12 MiB**, sem symlink, e exige assinatura interna coerente com a extensão.
- O motor Skia retorna o **nome real da família tipográfica**, o total de glifos examinados, quantos possuem contorno e quantos não foram encontrados no texto sintético `Brother Matrizes 123`.
- A inspeção não copia, altera, move, registra nem exclui arquivos de fontes. Não consulta conta Google, não usa API, não reserva cotas e não grava histórico de uso.
- Esta análise **não equivale** a importação de fonte externa para o gerador de matrizes Satin do Android, que depende do fluxo comercial de autorização `brother-usage` e paridade comprovada de pontadas.

## Como verificar

1. Na pasta portátil de QA, execute `BrotherMatrizesQA.exe`.
2. Entre em **Fontes** e clique em **Analisar arquivo TTF/OTF (sem importar)**.
3. Selecione uma fonte TTF/OTF **de teste ou pública do Windows**, por exemplo uma fonte do próprio sistema. Confira nome da família, formato e contornos.
4. Teste com arquivo inválido e observe a rejeição. Verifique que a fonte de origem não mudou e que não foi criada nova fonte na Biblioteca.
5. Não insira credenciais comerciais nem utilize matrizes de clientes para esse teste.

## Evidências automatizadas
- `DesktopReadOnlyFontAnalyzerTest`: arquivo válido, preservação byte a byte, assinatura fraudada, extensão incompatível, arquivo corrompido e limite acima de 12 MiB.
- `DesktopFontGlyphProbeTest`: leitura de contornos Skia existente.
- `DesktopCommercialFontImportGateTest`: gate comercial permanece bloqueado por padrão.
- Workflow `brother-windows-font-readonly-qa.yml`: executa testes e captura a tela real de Fontes via `--capture-fonts-qa`.
- Workflow portátil `brother-windows-portable-qa.yml`: inclui os testes e executa o launcher real também nessa tela antes de criar o artefato de QA.

## Bloqueios para comercialização
- Verificar Supabase Auth → Redirect URLs para o callback local Windows.
- Homologar login real Google, DPAPI/refresh e a associação correta de autorização às funções `brother-entitlement` e `brother-usage`.
- Homologar geração de pontadas Satin para fonte importada e paridade com o aplicativo Android.
- Somente após esses critérios habilitar `DesktopCommercialFontImportGate` na interface e preparar distribuição comercial assinada.
