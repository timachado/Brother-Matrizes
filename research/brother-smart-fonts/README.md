# Motor Inteligente de Fontes — prova de conceito isolada

**Escopo:** examinar capacidade de reconhecer recursos OpenType em fontes baixadas do DaFont e testar o alinhamento futuro entre prévia e bordado. Sem mudanças visuais, servidor, limites, licenças ou publicação de APK.

## Amostras privadas fornecidas pelo proprietário (NÃO versionadas)

- `bertilda.zip`: `bertilda.otf` 34.712 bytes (OpenType CFF, 233 glifos) e `bertilda.ttf` 92.324 bytes (TrueType glyf, 233 glifos). Ambas declaram família Bertilda Regular, porém são arquivos distintos. Features GSUB: `aalt`, `liga`, `ss01`, `ss02`, `ss03`.
- `love_11.zip`: `Love.ttf` 43.044 bytes (TrueType glyf, 155 glifos). Features GSUB: `aalt`, `liga`.
- ZIPs validados; **nenhuma fonte copiada para o GitHub, nenhuma conversão PES criada, nenhuma transferência para Supabase**.
- HarfBuzz local confirmou que a palavra `Maria` admite dois conjuntos de glifos alternativos em ambas as famílias. O recurso Bertilda `ss03` altera `tt`, mas não altera `Maria`.
- A Bertilda apresenta valor técnico OS/2 `fsType=4`. Respeitar licença efetiva/embeddings e não redistribuir binários.

## Entrega desta pesquisa

`OpenTypeFeatureCatalog.kt`: leitor local, somente leitura, de recursos declarados em GSUB com limites de tamanho, verificação SFNT e offsets. Não faz shaping e **ainda não é ligado à interface**. JUnit usa fontes **sintéticas** geradas em memória.

## Arquitetura proposta para uma versão experimental posterior

1. Ao importar ZIP, manter separadas as variantes TTF e OTF e armazenar os metadados internos da família; a própria fonte permanece local e com seu ID.
2. Expor um seletor simples em `Criar Nome`: Padrão, Alternativo 1, Alternativo 2 **somente quando presentes e visivelmente diferentes no texto atual**.
3. O shaper deve identificar os glifos e posições para o texto *inteiro*, aplicando a opção escolhida. A prévia e a extração dos contornos precisam usar **exatamente a mesma configuração**, usando `Paint.setFontFeatureSettings` e `TextView.setFontFeatureSettings` em Android 21+ ou HarfBuzz integrado se necessário.
4. Após o contorno tipográfico, preservar regras atuais de segurança na digitalização Satin, sobretudo pontos de `i/j`, ligaturas, swashes e saltos; comparar imagem prévia com contorno usado para gerar os pontos. Em caso de impossibilidade, explicar e não gerar resultado enganoso.
5. Reabrir o PES/JEF/DST gerado e validar estruturalmente antes de exportar; homologação no celular e, para garantia de costura, teste numa bordadeira real.
6. Não criar autenticação paralela, não cobrar cota adicional só por prévia, não enviar fontes pessoais a terceiros.

## Status

PoC de identificação **sim**. PoC de variação de glifos com HarfBuzz **sim**. Aplicação dos glifos alternativos no APK **não**. Equivalência da digitalização Satin **não**. APK comercial permanece 1.10.8.
