# Brother Matrizes — Fase 12: prévia real e foto original preservada

Continuidade da QA Android [PR #96](https://github.com/timachado/Brother-Matrizes/pull/96).
Nova ferramenta no Home **Ferramentas > Imagem para Bordado**.

## Resultado para quem usa
1. Importar PNG/JPG/WebP na ferramenta dedicada. **Não gerar matriz diretamente da foto inteira sem preparo**.
2. Arrastar o recorte sobre a foto; ligar/desligar remoção do tecido, regular a tolerância de cor.
3. Tocar em **Visualizar recorte e fundo**. A preparação executa em `Dispatchers.Default` e mostra a imagem resultante sobre quadriculado; quadriculado indica pixel transparente (não bordar).
4. Conferir se logo e letras foram preservadas. Mudar recorte, tolerância ou opção de fundo **invalida a prévia**. Respostas assíncronas antigas são descartadas se o usuário alterar a imagem/recorte.
5. Confirmar **Aplicar resultado à matriz**. Só então a nova imagem preparada entra no pipeline de cores/pontos e habilita a geração. Edições de camada associadas ao raster anterior são reiniciadas.
6. **Recomeçar da foto original** descarta o raster preparado sem apagar a fotografia carregada.
7. **Salvar projeto Satin** guarda a imagem já preparada + **a foto original** e opções de recorte e remoção, permitindo refazer a preparação após reabrir ou restaurar um backup ZIP.

## Compatibilidade dos documentos
O codec isolado `.bmrail` passa à **V3**, com seção opcional adicionada *após* todos os dados V1/V2. Continua abrindo arquivos V1/V2 sem introduzir uma falsa foto original: para esses documentos aparece um aviso de que só a imagem preparada havia sido salva. O `ProjectCodec` comercial da biblioteca .flp não é alterado.

A V3 registra:
- imagem editada e camadas/Satin como antes;
- original ARGB até 512×512 e label;
- retângulo normalizado (left/top/right/bottom);
- opção de remover bordas e tolerância da cor.
- Mesmo limite de arquivo `RasterEditProjectCodec.MAX_BYTES` (4 MB) e CRC32 do payload completo. O backup ZIP Satin mantém as verificações de versão, tamanho e integridade e inclui os novos campos automaticamente.

## Testes automatizados
- Round-trip de V3 e backup ZIP: preservar pixels de foto original, imagem preparada, retângulo e ajustes.
- Compatibilidade V2 sintetizada do formato anterior e V1 do teste já existente.
- Corrupção CRC / crop inválido / tolerância inválida falham sem carregar arquivos não confiáveis.
- Pipeline raster/PES mantém testes da fase 10 e instrumentação do emulador fase 11.

## Publicação
- [ ] Testes unitários + assembleDebug GitHub Actions
- [ ] Instrumentação Android API 35
- [ ] Revisão de UX com a foto real Panobianco no celular do desenvolvedor
- [ ] Matrizes reais e bordado físico em máquina Brother, em retalho
- [ ] Build comercial assinada com chave original e versionCode crescente, somente após QA

**Observação:** não há segmentação semântica universal de tecidos, nem extração profissional automática de caligrafia. A prévia ajuda a inspecionar a máscara, mas não comprova qualidade do bordado. A distribuição comercial v1.10.8 (184), simulador, fontes importadas, licenças e pagamentos permanecem intocados.
