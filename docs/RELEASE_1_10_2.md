# Brother Matrizes 1.10.2 — candidato de lançamento

**Código Android:** `178` · **Versão:** `1.10.2` · **Pacote:** `com.timachado.brothermatrizes`

## Correção desta versão
- Consulta prioritária ao **manifesto oficial do GitHub**, evitando falsos avisos de “já atualizado” ou falha técnica quando a hospedagem WordPress retorna HTML de proteção, HTTP 403 ou cache antigo.
- Mantém a API WordPress como segunda opção se o manifesto GitHub estiver temporariamente indisponível.
- Mantém a verificação do SHA-256, pacote Android, versão e assinatura comercial original antes de iniciar a instalação.
- Preserva o card premium, notificações, fontes BM, bibliotecas, criação de matrizes e simulador aprovados na 1.10.1.
- Nenhuma alteração em WooCommerce, Efí, Supabase, Google OAuth, licenças ou dados de usuários.

## Lançamento seguro
1. [ ] Build + testes Android candidatos aprovados.
2. [ ] Upgrade 1.10.1 → 1.10.2 em emulador, **sem desinstalar**, com persistência de dados.
3. [ ] Compilar e assinar com GitHub Actions e keystore **original**, validando certificado `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`.
4. [ ] Publicar APK `Brother-Matrizes-v1.10.2-ASSINADO.apk` por GitHub Releases; validar download HTTP sem login e SHA-256.
5. [ ] Atualizar automaticamente `main/updates/stable.json` **apenas após** verificar arquivo público; WordPress 1.1.0 sincroniza versão e botão do App Commerce Core 307.
6. [ ] Testar instalação comercial no Android físico e opção Atualizar agora, com preservação dos dados.
7. [ ] Não emitir push Firebase até credencial específica e homologação de entrega; aviso fica desmarcado.

### Notas da versão para o usuário
- Atualizações mais rápidas e confiáveis.
- Melhorias na verificação de novas versões.
- Refinamentos de estabilidade e experiência.

**Atenção:** preparar candidato **não** significa publicar. A 1.10.1 continua pública até que a release assinada e o link sejam aprovados.
