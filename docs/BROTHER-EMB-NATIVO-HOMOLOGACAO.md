# Brother Matrizes 1.10.8 — homologação de abertura EMB offline

**Fonte da ramificação:** commit comercial assinado e homologado
`19ca60e13ead6177aba7362a0ed526cd3beba7c7`.

**A ramificação é de QA, NÃO substitui o APK comercial atual.**

## O que realmente existe nesta versão
- O seletor de documentos Android abre arquivos EMB nativos OLE/CFB de família reconhecida (até 10 MB).
- Suporta nomes como `quiltin puma.EMB` e `coração. EMB .doc`.
- Verifica assinatura do container, FAT/MiniFAT, diretório, metadados padronizados OLEPS.
- Extrai em modo offline a miniatura JPEG incorporada (TrueView), quando presente.
- Exibe as contagens declaradas de pontos, cores, objetos, cortes, mudanças de cor, paradas e etapas de fio.
- A tela é propositalmente SEPARADA do simulador/conversor de máquina.
- PES, JEF e DST existentes não foram modificados.
- Arquivos não são enviados à internet nem publicados no GitHub.

## NÃO confundir com conversão
- **EMB → PES/JEF/DST:** ainda não implementado. O DesignDocument guarda dados nativos de desenho, sem sequência de coordenadas e comandos decodificada pelo nosso motor.
- Um JPEG TrueView não permite reconstruir por si só o bordado.
- Não se deve criar `.pes`/`.jef`/`.dst` falso por renomeação, nem modificar o EMB original.
- Sem uso de SDK, API ou código Wilcom.

## Testes e comercialização
O workflow `.github/workflows/brother-emb-readonly-homologation.yml` executa os testes da versão comercial via Gradle, sem assinar/instalar/publicar APK.
A nova opção EMB não é considerada pronta até esse workflow passar e o proprietário testar no Android a abertura dos dois arquivos reais.
O APK **1.10.8 comercial instalado permanece inalterado**.

## Próxima etapa para conversão fiel
Coletar pareamento do mesmo desenho em EMB + PES ou DST exportado legitimamente (duas versões EMB preferencialmente), comparar coordenadas, saltos, cortes, cores e limites, e validar em bordadeira antes de habilitar exportação.
