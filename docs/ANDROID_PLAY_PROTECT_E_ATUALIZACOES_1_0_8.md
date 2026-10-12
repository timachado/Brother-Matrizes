# Brother Matrizes — distribuição oficial fora da Google Play

## Objetivo
O aplicativo continua distribuído no site T.I. Machado. O processo de instalação não deve contornar Google Play Protect nem ocultar as permissões exigidas pelo Android.

## Identificação para verificação Android
- Aplicativo: **Brother Matrizes**
- Desenvolvedor/marca: **T.I. Machado**
- Pacote Android: `com.timachado.brothermatrizes`
- Certificado comercial de assinatura — SHA-256:
  `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`
- Site: `https://timachado.ifree.page/aplicativos/brother-matrizes/`
- Distribuição: APK comercial assinado e link oficial do site; sem nova chave de assinatura nas atualizações

## Processo de reconhecimento do desenvolvedor
Segundo a documentação oficial do Android (outubro de 2026), quem distribui apps exclusivamente fora da Google Play pode abrir conta no **Android Developer Console**, concluir a verificação de identidade e registrar os nomes dos pacotes e as respectivas chaves de assinatura. O processo não exige publicar o aplicativo na Play Store.

Guia oficial: https://developer.android.com/developer-verification/guides?hl=pt-BR

**Ações fora do código** (dependem da conta verificada do titular da T.I. Machado):
1. Entrar no Android Developer Console e concluir a verificação da identidade.
2. Registrar o pacote `com.timachado.brothermatrizes` com o certificado comercial original. O Google fornece as instruções aplicáveis à forma de comprovação.
3. Quando necessário, validar a propriedade do site T.I. Machado pelo Google Search Console.
4. Enviar um APK comercial legítimo para análise do Play Protect quando a interface do dispositivo oferecer a verificação; registrar eventual classificação indevida pelo procedimento oficial de contestação.

A verificação do desenvolvedor e o Play Protect são controles **distintos**. Registro do pacote, assinatura original, ausência de permissões desnecessárias e análise de segurança ajudam a reputação e integridade da distribuição, mas não garantem a ausência de um aviso no aparelho. Nunca solicitar ao usuário desativar o Play Protect.

Orientações oficiais: https://developers.google.com/android/play-protect/warning-dev-guidance?hl=pt-br

## Atualização dentro do aplicativo — versão 1.0.8
- `Atualizar agora` baixa APK do endereço oficial sem abrir o navegador.
- Link de download extraído dinamicamente do botão WordPress e restrito à hospedagem HTTPS oficial.
- A versão e o hash SHA-256 devem corresponder ao manifesto público `main/updates/stable.json`.
- Verificação local adicional: `applicationId`, `versionCode`, assinatura original.
- O Android solicita, quando aplicável, autorização para instalar apps originados do Brother Matrizes e a confirmação final.
- Se houver bloqueio pelo sistema ou Play Protect, respeitar a decisão e orientar o usuário a verificar o app, sem tentar contornar a proteção.
- O APK é armazenado apenas no cache privado do app e compartilhado com o instalador via FileProvider.

## Preparação para liberar cada versão
Confirme o APK comercial com a mesma assinatura, seu SHA-256, a disponibilidade pública do download WordPress e a versão idêntica no GitHub Raw. Verifique o fluxo em um Android real antes de enviar alertas FCM. Não altere os sistemas de licenças, pagamentos e cadastro como parte de uma atualização do instalador.
