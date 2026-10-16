# Brother Matrizes — Compatibilidade PES sem modelo universal fictício

## Decisão técnica

PES é um **formato de dados de bordado**. A extensão por si só não informa quais versões do arquivo, tamanho de bastidor, limite de armazenamento, quantidade de pontos e recursos físicos a máquina de destino aceita.

Requisitos de produto:
- Um único motor de geometria e comandos pode atender várias máquinas, mas com perfis de compatibilidade separados quando documentados.
- O destino não especificado **não** deve ser rotulado "Universal Brother/PES homologado".
- Nunca inferir automaticamente versão PES aceita ou área útil do equipamento pelo fato de o dispositivo ler arquivos PES.
- O diagnóstico `RasterPesTargetCompatibility.assess` inspeciona APENAS as prévias `QA_*` de Imagem para Bordado. O resultado registra avisos e mantém `exportPermitted=false` incondicionalmente. Os leitores/simuladores originais de PES/JEF/DST não são alterados.
- Futuramente a etapa de exportação precisa ser separada, com codificador PES testado por fixtures reais, verificação de parse round-trip, perfil de máquina identificado e teste físico de costura.

## Referências oficiais Brother

- [Innov-is XP2: embroidery data not recognized](https://support.brother.com/g/b/sp/faqend.aspx?c=us_ot&faqid=faqh00101388_000&lang=en&prod=hf_inovxp2eus): admite formatos específicos, restringe dimensões e avisa sobre compatibilidade de dados.
- [PE-DESIGN: escolher bastidor compatível com a máquina](https://support.brother.com/g/b/faqend.aspx?c=in&faqid=faqh00000029_000&lang=en&prod=hf_design7eas): bastidor selecionado maior que a área admitida provoca rejeição.
- [PE-DESIGN 11 para Innov-is 4000D: tamanho de arquivo](https://support.brother.com/g/b/faqend.aspx?c=my&faqid=faqh00101653_000&lang=en&prod=hf_inov4000deas): certas versões PES aumentam o tamanho de dados; limites de transferência variam conforme o equipamento.

## Evidência exigida para perfil real

1. Modelo e firmware exatos, informados pelo operador.
2. Referência do manual Brother para área útil, bastidores suportados, versão do PES e limites aplicáveis (sem inventar valores).
3. Pontos, JUMP, TRIM, COLOR_CHANGE, END e mapeamento de cores verificados no fluxo de dados real.
4. Codificador PES com versão pretendida e teste de leitura e simulação do arquivo exportado; **não** basta serializar coordenadas sem cabeçalho.
5. Auditoria de agulha, densidade e sobreposições, seguida de prova de bordado sob supervisão com tecido, linha e entretela especificados.

## Estado da implementação

- `PesTargetProfile.unknown()`: padrão conservador para usuário que só informa "máquina PES universal".
- `PesTargetProfile` com limites informados: rejeita geometria fora da área útil e pontos/trocas de cor acima dos tetos; não promete compatibilidade.
- `RasterPesTargetCompatibility`: verifica comandos e bounds de uma trajetória QA, diferencia metadados documentados de não verificados e nunca libera exportação.
- A versão comercial 1.10.8 e o simulador de matrizes PES/JEF/DST já existentes permanecem intocados.
