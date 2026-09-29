# Brother Matrizes — assinatura de produção

A chave definitiva de produção **não deve ser adicionada ao repositório**. O Brother Matrizes possui um workflow manual separado em `.github/workflows/android-production-sign.yml`, que só funciona quando os secrets de assinatura estiverem configurados.

## Geradores locais prontos

O repositório inclui dois scripts que **não armazenam senha** e criam a chave fora da pasta do projeto:

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

Os scripts também criam uma representação Base64 local para facilitar o cadastro do secret `BROTHER_RELEASE_KEYSTORE_BASE64`. Essa cópia continua sendo sensível.

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

- `BROTHER_RELEASE_KEYSTORE_BASE64` — conteúdo Base64 completo da keystore.
- `BROTHER_RELEASE_STORE_PASSWORD` — senha da keystore.
- `BROTHER_RELEASE_KEY_ALIAS` — alias da chave, por exemplo `brother-matrizes-production`.
- `BROTHER_RELEASE_KEY_PASSWORD` — senha da chave.

O workflow não imprime esses valores e cria a keystore somente no armazenamento temporário do runner.

## 4. Executar a assinatura

Executar manualmente o workflow **Build Signed Production APK**.

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
