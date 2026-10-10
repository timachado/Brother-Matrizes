# Brother Matrizes — primeira assinatura de produção pelo celular Android

Guia revisado em 10/10/2026. **Funciona sem computador**: o JDK do Termux cria a identidade criptográfica no próprio Android; o GitHub Actions compila e assina o APK usando os segredos cadastrados pelo proprietário.

**Pré-condição obrigatória:** verifique se alguma versão `release` de `com.timachado.brothermatrizes` já foi distribuída *fora do GitHub*. A auditoria em `docs/AUDITORIA_CHAVE_PRODUCAO_2026-10-10.md` não encontrou assinatura oficial concluída no histórico GitHub, mas **não consegue verificar APKs enviados diretamente a outras pessoas**. Se já foi entregue um APK release, NÃO gere nova chave sem investigar a assinatura desse APK.

## 1. Instale Termux apenas de fonte oficial

- F-Droid: https://f-droid.org/packages/com.termux/
- Projeto: https://github.com/termux/termux-app

A página oficial do Termux orienta usar o APK do F-Droid ou os arquivos oficiais do GitHub; evite versões abandonadas/terceirizadas da Play Store. Mantenha as atualizações da mesma origem. Não é necessário acesso root.

## 2. JDK e geração local da keystore

Abra o Termux e execute:

```sh
pkg update
pkg install openjdk-17
```

Se os pacotes solicitarem confirmação, leia a lista antes de aceitar.

**Somente se tiver certeza de que nunca existiu chave de produção anterior:** no Termux, execute o bloco inteiro:

```sh
KEYDIR="$HOME/Brother-Matrizes-Production-Key"
KEYFILE="$KEYDIR/brother-matrizes-production.jks"
mkdir -p "$KEYDIR" &&
chmod 700 "$KEYDIR" &&
if [ -e "$KEYFILE" ]; then
  echo "Chave existente detectada. Nao sobrescrever!"
else
  keytool -genkeypair -v \
    -storetype JKS \
    -keystore "$KEYFILE" \
    -alias brother-matrizes-production \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000
fi
```

O `keytool` pedirá **senha da keystore**, confirmação, dados do certificado e **senha da chave** (pode usar uma senha diferente se for solicitado). Não envie essas senhas, capturas de tela com dados secretos ou arquivos de chave pelo chat. Guarde as senhas em gerenciador seguro.

Quando o comando terminar:

```sh
keytool -list -v \
  -keystore "$HOME/Brother-Matrizes-Production-Key/brother-matrizes-production.jks" \
  -alias brother-matrizes-production
```

O `SHA256` do certificado é público e pode ser informado para corrigir o `EXPECTED_CERT_SHA` do workflow **apenas depois da validação explícita da nova identidade**. **Não alterar esse fingerprint apenas para fazer o build passar.**

## 3. Converter localmente para GitHub secret e fazer backup

```sh
KEYDIR="$HOME/Brother-Matrizes-Production-Key"
base64 < "$KEYDIR/brother-matrizes-production.jks" | tr -d '\n' \
  > "$KEYDIR/brother-matrizes-production.base64.txt"
chmod 600 "$KEYDIR/brother-matrizes-production.jks" "$KEYDIR/brother-matrizes-production.base64.txt"
```

O arquivo `.base64.txt` **é tão sensível quanto a keystore**. Não poste no GitHub, chat, e-mail ou aplicativos de mensagem.

Faça pelo menos duas cópias seguras da **keystore JKS** em locais independentes; para que o Android permita acessar o arquivo com um gerenciador, depois de autorizar acesso de armazenamento, copie-o temporariamente para a pasta Downloads e transfira para seu cofre seguro:

```sh
termux-setup-storage
# Após conceder a permissão do Android:
cp "$HOME/Brother-Matrizes-Production-Key/brother-matrizes-production.jks" \
  "$HOME/storage/downloads/"
```

Após confirmar as cópias em local seguro, elimine cópias desnecessárias da pasta Downloads. **Nunca apague a única keystore.** O Termux contém o original até que seja deliberadamente protegido em outro lugar. Atenção: desinstalar Termux ou limpar os dados poderá apagar essa cópia original.

## 4. GitHub pelo navegador Android

Acesse https://github.com/timachado/Brother-Matrizes/settings/secrets/actions, autenticado na conta proprietária do repositório. No Chrome Android, ative **Site para computador** se faltar o menu.

Em `Settings → Secrets and variables → Actions → New repository secret`, cadastre:

| Secret | Valor produzido localmente |
| --- | --- |
| `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64` | conteúdo integral de `brother-matrizes-production.base64.txt` (uma linha) |
| `BROTHER_MATRIZES_RELEASE_STORE_PASSWORD` | senha da keystore |
| `BROTHER_MATRIZES_RELEASE_KEY_ALIAS` | `brother-matrizes-production` |
| `BROTHER_MATRIZES_RELEASE_KEY_PASSWORD` | senha da chave |

Se precisar copiar a Base64 pelo navegador Android, transfira **somente temporariamente** o arquivo texto para Downloads, abra-o num editor local, `Selecionar tudo → Copiar`, cole no campo Secret, e exclua a cópia temporária em Downloads. Não inclua o texto Base64 em uma captura de tela.

**Ao gerar uma chave nova, não execute o workflow assinado enquanto o fingerprint de controle ainda apontar para o certificado anterior**. Informe apenas o `SHA256` *público* do novo certificado e validaremos o novo valor conscientemente. Não é possível mudar a assinatura do APK debug existente sem reinstalação.

## 5. Teste final

Após validação da nova identidade, execute manualmente `Actions → Build Signed Brother Matrizes Production APK`, na branch `release/brother-matrizes-1.0.0-prep`. Verifique o `apksigner`, o SHA-256 do APK e a instalação/atualização num aparelho de testes. **Não desinstale a beta instalada no celular antes de exportar seus projetos/fontes e confirmar os backups; o Android rejeita atualização entre assinaturas diferentes.**

A assinatura não valida os pedidos pagos, renovações ou o funcionamento da bordadeira real; mantenha esses testes como bloqueadores do lançamento estável.
