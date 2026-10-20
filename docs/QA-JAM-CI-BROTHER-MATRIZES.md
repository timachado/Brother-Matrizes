# Brother Matrizes — QA contínuo, Jam e distribuição sem reinstalações

## Objetivo

**O operador não deve instalar um APK a cada evolução de Imagem para Bordado.**
Todas as alterações passam primeiro por GitHub Actions: testes JUnit, integridade
do motor de pontos, compilação do aplicativo e relatório de falhas. Bugs reproduzidos
automaticamente não exigem nenhuma ação no telefone.

## Jam: evidência de bugs reais, não um executor de testes Android

- A equipe Jam conectada foi consultada em 2026-10-20 e não possuía gravações,
  pastas ou domínios de captura registrados para Brother Matrizes.
- Para falhas de interface que **não** apareçam nos testes automáticos, um Jam
  gravado no Android pode ser usado para reproduzir passos e examinar telas.
  Ele exige uma gravação real ou link Jam; não acessa o APK instalado por conta
  própria e **não** substitui logcat, testes de instrumentação ou registros
  específicos do processo Android.
- Não registrar vídeos contendo tokens, senhas, chaves, dados bancários ou
  informações privadas do cliente. O aplicativo não deve publicar credenciais
  e os dados de teste devem ser anônimos.
- Conectar posteriormente cada gravação a um teste de regressão verificável,
  com um erro esperado antes da correção e sucesso depois.

## Uma só identidade QA Android

- Aplicativo comercial: `com.timachado.brothermatrizes`. Não alterar
  package, assinatura, versão nem distribuição da versão de produção aqui.
- Aplicativo QA: **identificador FIXO**
  `com.timachado.brothermatrizes.qaphysicalgate`,
  rótulo de instalação **Brother Matrizes QA**. O nome do pacote é mantido
  igual ao último QA V6; não criar novos sufixos por PR.
- O CI pode montar APKs com assinatura debug temporária apenas para verificar
  que compilam, mas **esses APKs não serão distribuídos**.
- Para atualizar por cima, dois APKs precisam ter **o mesmo applicationId,
  a mesma chave de assinatura e versionCode compatível**. No GitHub Actions,
  o versionCode da variante debug cresce com `GITHUB_RUN_NUMBER`, enquanto
  a versão comercial permanece `184 / 1.10.8`.
- Assinaturas debug temporárias de runners diferentes NÃO são equivalentes.
  A equipe deve cadastrar uma **chave própria e permanente apenas de QA** em
  GitHub Actions Secrets; nunca usar a chave de distribuição comercial nem
  colocar a chave privada no repositório, vídeo Jam ou mensagem do ChatGPT.

### Quatro segredos de QA (configuração única pelo administrador GitHub)

- `BROTHER_QA_KEYSTORE_BASE64`: keystore QA completo codificado em base64.
- `BROTHER_QA_STORE_PASSWORD`: senha do keystore QA.
- `BROTHER_QA_KEY_ALIAS`: alias da chave QA.
- `BROTHER_QA_KEY_PASSWORD`: senha da chave QA.

Após configurados, o acionamento manual de
`.github/workflows/qa-parallel-install-image-bordado.yml`
compila um APK assinado com a mesma chave e publica o artefato
`Brother-Matrizes-QA-Estavel`. Este é o **único** APK que deve ser
distribuído para atualização in-place da versão QA. Não enviar um APK
manual se a assinatura não estiver configurada. A primeira transição de
assinatura a partir de um QA V6 anterior pode exigir **uma única**
substituição do app de testes; atualizações subsequentes usam a mesma chave.

## Rotina

1. Em cada alteração, a CI roda todos os testes JUnit e compila Android
   (sem instalação pelo operador).
2. Se houver falha, a PR fica experimental e o artefato
   `Brother-Matrizes-QA-Testes-Automaticos` reúne relatórios. Corrigir no
   repositório, repetir CI; **não pedir APK ao operador**.
3. Só quando houver um conjunto de mudanças relevante e validado, solicitar
   a compilação manual do QA assinado e fazer uma única atualização por cima.
4. Se o erro for somente visual e não houver reprodução automatizada,
   anexar um Jam específico e transformar sua reprodução em teste de
   regressão no código antes de publicar outra build.
5. Imagem para Bordado é experimental. Métricas topológicas, pixels
   preservados e anotações visuais não autorizam exportação PES nem validam
   parâmetros de máquina Brother sem prova física.

**Nota:** esta rotina elimina o ciclo de novos APKs por cada relatório, mas
não transforma Jam em acesso remoto ao aparelho e não torna uma chave
temporária equivalente a uma assinatura QA persistente.
