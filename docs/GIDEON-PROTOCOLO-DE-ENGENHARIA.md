# Protocolo de Engenharia GIDEON — Brother Matrizes ORIGINAL
**Responsável:** T.I. Machado  
**Data de adoção proposta:** 2026-10-27  
**Estado:** proposta em branch isolada; só entra em vigor no `main` após revisão e merge explícito.

## 1. Fonte de verdade, escopo e isolamento
- Repositório oficial: `timachado/Brother-Matrizes`.
- Brother Matrizes original e Matrix Engine são projetos distintos. Não importar código, SDK, serviço, dependência ou fluxo do Matrix Engine sem uma decisão explícita e documentada do responsável.
- `main` representa a linha de código de referência, **não** prova o APK instalado ou a publicação comercial. Identificar a release entregue pelo APK assinado, `versionCode`, `versionName`, SHA-256, certificado e manifesto de distribuição/OTA.
- Nenhuma análise, issue ou PR dá autorização automática para publicar, atualizar OTA/FCM, alterar WordPress, Supabase, clientes ou bordadeiras.

## 2. Fluxo de branches
1. Abrir issue vinculada à funcionalidade/defeito, com comportamento esperado, reprodução e critérios de aceite.
2. Criar branch isolada a partir da base aprovada: `fix/<assunto>-AAAAMMDD`, `feature/<assunto>-AAAAMMDD`, `research/<assunto>-AAAAMMDD` ou `docs/<assunto>-AAAAMMDD`.
3. Alterar somente o escopo definido. Manter compatibilidade com projetos e matrizes já salvos, autenticação, assinaturas, exportação, simulador, comunicação OTA e segurança.
4. Abrir PR **draft** para `main` com links à issue e evidências do CI. Nunca mesclar PR automaticamente.
5. Só habilitar review/merge depois de resolver bloqueadores técnicos e obter aceite de homologação.

## 3. Barreiras para qualquer release
- **Revisão estática:** scripts existentes, dependências, permissões, segredos, propriedades e exclusão de dependências com Matrix Engine.
- **Compilação e regressão:** `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Falhas bloqueiam promoção.
- **Android real:** abrir/importar PES e DST, alterar nomes, gestos (arrastar, girar, escala), ajuste automático ao espaço livre, persistência, desfazer/refazer, simulador, exportar/reabrir e teste sem travamentos/ANR.
- **Máquina de bordar:** simular e bordar amostra real em retalho, conferindo orientação, trims, saltos, trocas de cor, densidade, limites de bastidor e comportamento físico; a aprovação do emulador não substitui esse teste.
- **Compatibilidade:** preservar projetos v1–v3; conferir que a versão interna v4 permaneça reeditável, e que exportações PES/DST não sejam incorretamente tratadas como arquivo de projeto editável.
- **Segurança:** build de QA com identificador exclusivo, sem credenciais privadas ou certificado comercial no CI, sem contornar login/plano na versão release.
- **Publicação:** etapa separada com autorização explícita, teste de upgrade e rollback. CI de QA jamais publica release ou modifica backend.

## 4. Evidências e registro técnico
Cada PR deve registrar:
- versão/commit base; branch, commit de alteração, responsável e issue;
- causa raiz ou hipótese marcada como não confirmada;
- arquivos alterados, compatibilidade e riscos;
- comandos de teste, URL/ID do CI e resultado **da revisão atual**;
- testes no aparelho e máquina: modelo, data, arquivo e resultado;
- hashes de artefatos quando gerados e sem publicar conteúdo de matrizes de clientes;
- checklist: aprovado, bloqueado ou não testado (nunca inferir sucesso).

## 5. Política de estabilidade
- `em desenvolvimento`: implementação sem aceite;
- `QA automatizado`: compilou e passou nos testes declarados;
- `homologado em Android`: gestos, desempenho e regressões validados no dispositivo;
- `homologado para bordado`: teste físico em retalho concluído;
- `comercial publicado`: distribuição efetiva demonstrada por artefato/registro de deploy.
Não confundir esses estados.

## 6. Branch protections recomendadas (não configuradas neste PR)
Configurar no GitHub via regras do repositório após verificar suporte do plano e permissões:
- PR obrigatório para `main`, sem push direto;
- checks obrigatórios de QA Android e revisão estática;
- bloqueio de force-push e exclusão de `main`;
- impedir merge de PR draft; preservar histórico e rollback.
Regras não são declaradas ativas até serem verificadas na plataforma.

## 7. Referências
- `docs/GIDEON-BASELINE-BROTHER-20261027.md`: inventário inicial.
- PR #200: editor de nomes em QA, **não homologado para produção**.
- `.github/workflows/gideon-android-qa.yml`: pipeline de QA proposto, somente leitura e build debug.
