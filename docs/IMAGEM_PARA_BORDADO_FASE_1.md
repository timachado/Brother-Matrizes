# Brother Matrizes — Imagem para Bordado (fase 1, experimental)

## Objetivo
Permitir selecionar imagens locais (PNG, JPG e WebP) em **Criar desenho/logo → Imagem**, extrair traços de uma cor e construir comandos reais de bordado (STITCH, JUMP, TRIM, END), passando pelo **mesmo pipeline de conversão/reabertura PES** das criações existentes.

## Uso
1. Recorte a imagem externamente para manter **somente o logo ou desenho**, preferencialmente em fundo branco ou PNG transparente.
2. Abra **Criar desenho/logo → Imagem → Importar PNG, JPG ou WebP**.
3. Ajuste contraste e seleção dos tons claros/escuros; transparência de PNG é respeitada automaticamente.
4. Defina largura (mm), cor da linha, bastidor e formato **PES**.
5. Inspecione prévia e área útil, toque em **Gerar matriz**, salve/compartilhe e reabra o PES antes de bordar.

## Escopo real da digitalização
- Preenchimento monocromático em faixas horizontais de pontos corridos, experimental. Espaçamento inicial de 0,45 mm; tamanho máximo de trecho de 2,5 mm.
- Processamento local no telefone, sem upload das fotos.
- Restrição de arquivos de até 12 MB quando o provedor informa tamanho, dimensões originais máximas de 12 mil px por lado, miniaturização para ~256 px e limite de comandos.
- Detecção simples por brilho ou canal alfa, remoção de margens de fundo, rejeição de imagem praticamente vazia ou escura por inteiro.
- Mantém os motores de leitura/exportação, a simulação, as licenças, os pagamentos, a identidade visual aprovada e a versão comercial 1.10.8 (184) sem alteração.

## Limitações e próximos componentes
**Não** afirmar que qualquer foto vira automaticamente uma matriz profissional. Fotografias, gradientes, inscrições minúsculas, cabelos finos, lettering cursivo com detalhes, logotipos multicoloridos e materiais brilhantes exigem vetorização adequada, segmentação de cores/ilhas, tipos de ponto (Satin/Tatami), underlay, compensação de puxamento, ordenamento otimizado, parâmetros de tecido e ajustes manuais.

Para o exemplo do roupão: preparar **duas artes recortadas** separadas (logotipo frontal; nome e monograma das costas), definindo cor(es), medidas e bastidor da máquina. Bordar em cetim preto exige estabilizador, densidade e testes em retalho antes de uso comercial.

## Aprovação e QA
- Esta branch é **protótipo técnico em revisão** e não altera a APK estável.
- Compilar e testar a suíte de Android/JUnit; garantir que PES abre na prévia e apresenta pontos válidos.
- Validar imagens PNG alfa, PNG opaco, JPG, inversão, imagens grandes, fundo escuro e erro amigável do SAF.
- Fazer teste real em dispositivo Android, confirmar formato PES produzido e testar em máquina Brother conforme modelo/bastidor/tecido.
- Não assinar/distribuir um APK debug como atualização pública; publicar apenas após teste de regressão e assinatura comercial original.
