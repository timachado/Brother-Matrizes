# Brother Matrizes — assinatura de produção

> **Situação em 10/10/2026:** ensaio `#38073302039` aprovado em testes unitários e Android Lint, mas interrompido por `Missing Brother release keystore`. Nenhum APK de produção foi assinado. O segredo necessário só pode ser incluído pelo titular autorizado no GitHub; **não compartilhe chaves ou senhas no ChatGPT.**

## Etapa zero: localizar a chave ORIGINAL e validar o certificado

O workflow exige o certificado SHA-256 (sem dois-pontos):
`663d4338f085730a87efbf7a2bd3b0ccb91d7dc3696e625afa9c35a0e47c0403`.

Antes de cadastrar os secrets, procure a keystore original (`.jks` ou `.keystore`) em seu computador/cofre seguro. Em máquina local com JDK:

```bash
keytool -list -v -keystore /caminho/para/chave-original.jks
```

Confira no resultado `SHA256` e o `Alias name`. O certificado precisa ser **exatamente** o esperado no workflow (desconsiderando `:` e maiúsculas), não basta o arquivo existir. Se o fingerprint for diferente, **não renomeie, substitua, crie outra chave nem altere o fingerprint fixado** para forçar a compilação; é preciso investigar a identidade de distribuição anterior. Use o alias que `keytool` indicar, não um valor inventado.

Se você nunca criou uma chave e não existe identidade de produção já adotada, os geradores abaixo podem criar a **primeira** keystore, mas isso produzirá outro fingerprint e exigirá decisão explícita sobre a identidade oficial antes de alterar o workflow. APKs debug instalados anteriormente não são atualizáveis em linha para um APK assinado por outra chave. Faça backup/exportação dos arquivos do Brother Matrizes antes de trocar de canal.

## Cadastro seguro no GitHub

Abra https://github.com/timachado/Brother-Matrizes/settings/secrets/actions, entre em **Repository secrets → New repository secret** e cadastre os quatro nomes exatos indicados abaixo. GitHub armazena os valores cifrados; eles não devem ser escritos no repositório ou em mensagens do chat. Para a keystore, converta o arquivo **localmente** para Base64 e cole o conteúdo integral no secret próprio.

Depois de cadastrar, execute manualmente **Actions → Build Signed Brother Matrizes Production APK → Run workflow**, selecionando a branch `release/brother-matrizes-1.0.0-prep`. O workflow validará o certificado automaticamente e manterá o APK assinado apenas como artefato privado de QA — não haverá publicação na loja/site.

A chave definitiva de produção **não deve ser adicionada ao repositório**. O Brother Matrizes possui um workflow manual separado em `.github/workflows/android-production-sign.yml`, que só funciona quando os secrets de assinatura estiverem configurados.

## Geradores locais prontos

O repositório inclui dois scripts que **não armazenam senha** e criam uma **nova identidade**, fora da pasta do projeto. **Não os execute se já existe uma keystore original de produção:**

- Windows PowerShell: `scripts/create_production_keystore.ps1`
- Linux/macOS: `scripts/create_production_keystore.sh`

Por padrão, ambos usam `Brother-Matrizes-Production-Key` dentro da pasta pessoal do usuário. Eles não sobrescrevem uma keystore já existente.

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\create_production_keystore.ps1
```

Linux/macOS:

```bash
bash scripts/create_production_keystore.sh
```

Os scripts também criam uma representação Base64 local para facilitar o cadastro do secret `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64`. Essa cópia continua sendo sensível.

## 1. Criar a keystore fora do repositório

Em um computador confiável com JDK instalado:

```bash
keytool -genkeypair -v \
  -keystore brother-matrizes-production.jks \
  -alias brother-matrizes-production \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Use senhas fortes e exclusivas. Não salve a keystore, senha ou arquivo Base64 dentro da pasta do projeto.

Faça pelo menos duas cópias seguras da keystore em locais independentes. Perder essa chave pode impedir atualizações compatíveis da mesma distribuição do aplicativo.

## 2. Converter a keystore para Base64

Linux/macOS:

```bash
base64 < brother-matrizes-production.jks | tr -d '\n' > brother-matrizes-production.base64.txt
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("brother-matrizes-production.jks")) | Set-Content -NoNewline brother-matrizes-production.base64.txt
```

O arquivo Base64 continua sendo material secreto e deve ser protegido como a própria keystore.

## 3. Secrets exigidos no GitHub Actions

Configurar no repositório:

- `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64` — conteúdo Base64 completo da keystore.
- `BROTHER_MATRIZES_RELEASE_STORE_PASSWORD` — senha da keystore.
- `BROTHER_MATRIZES_RELEASE_KEY_ALIAS` — alias da chave, por exemplo `brother-matrizes-production`.
- `BROTHER_MATRIZES_RELEASE_KEY_PASSWORD` — senha da chave.

O workflow não imprime esses valores e cria a keystore somente no armazenamento temporário do runner.

## 4. Executar a assinatura

Executar manualmente o workflow **Build Signed Brother Matrizes Production APK**.

**Atenção à identidade de assinatura:** o workflow compara o SHA-256 do certificado com o fingerprint aprovado `663d4338f085730a87efbf7a2bd3b0ccb91d7dc3696e625afa9c35a0e47c0403`. Uma nova keystore gera outro fingerprint e será rejeitada até que haja validação e aprovação explícitas da identidade de distribuição; não altere o valor de controle apenas para o workflow passar. Se existe uma keystore anteriormente usada para distribuir o app, use a mesma para garantir atualizações sem perda de dados.

Se algum secret não estiver cadastrado, o workflow falha com mensagem de ausência; **não publique keystore nem senhas no chat ou repositório**.

Antes de assinar, ele roda:

- checagem estática de segurança;
- testes unitários;
- Android Lint;
- build release sem assinatura.

Depois ele:

1. decodifica a keystore temporariamente;
2. aplica `zipalign`;
3. assina com `apksigner`;
4. verifica a assinatura;
5. gera o SHA-256 do certificado;
6. gera o SHA-256 do APK;
7. publica somente um artifact privado/temporário do workflow;
8. apaga a keystore temporária do runner.

O workflow **não cria GitHub Release e não publica o APK assinado automaticamente**.

## 5. Validação obrigatória antes da RC

Com a chave definitiva:

1. instalar uma build assinada;
2. criar a build seguinte com a mesma chave;
3. instalar a segunda por cima da primeira sem desinstalar;
4. confirmar que Biblioteca, fontes e dados locais permanecem;
5. guardar o SHA-256 do certificado em local seguro;
6. somente depois definir o canal oficial de distribuição.

A assinatura de produção não substitui os testes físicos em bordadeira real.
