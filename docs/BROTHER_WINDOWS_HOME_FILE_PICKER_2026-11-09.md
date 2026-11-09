# Windows Home: abertura direta de matriz ao selecionar ferramenta

O componente Home do Android sempre exigiu `recent != null` para entrar
nas ferramentas Simulador, Editor, Converter e Enviar. O Windows já dispunha
dos respectivos handlers `openProject(destination)`, mas o componente
bloqueava esses callbacks antes de chegar à navegação.

A opção `openViaPickerWhenNoRecent` é **false por padrão** para todos os
consumidores Android existentes. Somente a composição Windows configura
`true`: ao tocar Simulador/Editor/Converter/Enviar sem matriz recente,
abre o seletor nativo `PES/DST/JEF/FLP`, com validação e sem criar cotas.

Nenhuma permissão Pro, importação externa, conta, arquivo ou backend é
alterado. Testes de Android e Windows continuam no GitHub Actions. Esta
correção não substitui OAuth/PKCE real, Free/Pro V2 homologados nem
assinatura digital Authenticode para publicação comercial.
