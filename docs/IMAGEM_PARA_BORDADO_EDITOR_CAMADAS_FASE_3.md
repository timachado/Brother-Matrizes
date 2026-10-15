# Brother Matrizes — Editor de camadas de bordado (fase 3 / protótipo)

## Contexto e objetivo
Evolução incremental das branches experimentais de importação raster monocor (PR #83) e multicor (PR #85). Implementada sobre a base técnica da versão comercial 1.10.8 (184) **sem alterar a versão publicada**, simulador, motores de leitura/exportação PES/JEF/DST ou rotinas de login/licença/pagamentos/NetFontes.

## O que o editor efetivamente implementa
Ao importar uma **arte recortada** (PNG/JPG/WebP) em **Criar desenho/logo → Imagem → Multicor**, o aplicativo:
1. Detecta até quatro cores e reutiliza a separação em memória quando o usuário edita a configuração.
2. Exibe uma lista de camadas com amostras de cores, em **ordem real de costura**.
3. Permite mover camadas para cima/baixo, ocultar/reativar individualmente.
4. Permite substituir a cor de linha de **cada camada** por uma das cores da paleta do app e recuperar a cor identificada no arquivo.
5. Permite alterar o espaçamento das fileiras entre 0,4 mm e 1 mm de forma independente por camada.
6. Recalcula os comandos reais STITCH/JUMP/TRIM/COLOR_CHANGE para cada configuração, compactando os índices das camadas ativas; imagens com uma camada restante não geram troca de cor fictícia.
7. Encaminha para a prévia e para o pipeline de exportação e reabertura PES já existentes; mantém JEF/DST como formatos alternativos.

**Escopo da edição:** parâmetros da sessão atual; não há ainda gravação do projeto com máscaras, edições, ordem, paleta e histórico de desfazer/refazer persistidos no disco.

## Testes adicionados
- Alterar ordem das cores: verificar ordem dos pontos por cor e lista de linhas exportadas no PES.
- Ocultar camada: verificar índices compactados e zero mudanças de cor.
- Exigir pelo menos uma camada; rejeitar itens duplicados e parâmetros inválidos.
- Alterar individualmente a densidade por camada e observar a variação na quantidade de pontos.
- Reabrir a matriz após ajustes com `GeneratedMatrixPipeline`, sem mudanças nos motores existentes.

## Cuidados e limitações
O preenchimento atual continua **experimental, baseado em linhas alternadas**. Não é sistema Tatami/Satin de qualidade comercial; ainda faltam contornos vetoriais revisáveis, rails Satin, underlay, compensação de puxamento, orientação dos pontos, otimização de sequências, seleção de tecido/cetim, ruído na segmentação, respeito à escala de letras finas e composição de múltiplos elementos num só projeto.

Para o exemplo do roupão, usar a arte original do logo Panobianco (preferivelmente vetor ou PNG sem fundo) **separada da fotografia**; a pessoa, as dobras do cetim e sombras não fazem parte do bordado. As costas exigem um módulo específico de texto cursivo/monograma com Satin e geometria fiel; não usar processamento de foto inteira.

### Gate de publicação
- [ ] Testes automatizados JUnit e compilação debug
- [ ] Testes manuais em Android (importação, preview, seleção/reordenação, exportação, reabertura)
- [ ] PES bordado fisicamente com cor correta, estabilizador e bastidor real
- [ ] Backups de estado e validação de regressões antes de merge
- [ ] Versão comercial assinada pela chave original, nunca pela assinatura debug
