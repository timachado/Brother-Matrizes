# Brother Matrizes Desktop — Windows (QA, NÃO COMERCIAL)

Este é um **novo projeto Windows isolado** do Android comercial. Não altera arquivos de `app/`, `supabase/` ou os workflows de publicação Android.

## Escopo técnico inicial

- Janela desktop com identidade Brother Matrizes e ferramentas de importação via seletor de arquivos Windows.
- Abertura **somente de DST** com verificação de tamanho, comandos, END e limites.
- Pré-visualização de pontadas e métricas da matriz (somente leitura).
- Testes do leitor DST e empacotamento de instalador EXE/MSI em runner Windows.
- Nenhum upload das matrizes para serviços remotos.

**Esta QA não equivale à versão Android completa.** Ela não oferece ainda editor completo, PES, JEF, conversor, criação de nome, monogramas, biblioteca, login, assinaturas, backup, notificações, exportação ou PE-DESIGN. A execução do empacotamento não significa que os recursos estejam concluídos nem que um instalador tenha sido validado em um computador real.

## Contrato de paridade obrigatório antes do lançamento comercial

A implementação Windows deve preservar:
- todos os recursos Android efetivamente aprovados;
- o comportamento do motor de pontadas, conversões e integridade dos arquivos;
- criação e edição de nomes, fontes, monogramas e desenhos;
- prévia e simulação, cores, densidade, bastidor, formatos e acabamento;
- biblioteca, arquivos locais, backup/restauração e fluxo de exportação;
- autenticação, licenças, planos e cotas sem novas permissões ou bypass;
- atualizações e rotas comerciais com adaptação segura à plataforma;
- compatibilidade real com formatos e leitores de terceiros, incluindo PE-DESIGN 11, a validar no Windows.

Recursos vinculados a Android (OTG, compartilhamento, FCM e APIs de arquivo) exigem substitutos Windows equivalentes onde tecnicamente viável, sem simular funcionalidades.

**Critério de release**: matriz de paridade aprovada, migração do núcleo comum reutilizável, regressão Android verde, testes Windows com arquivos reais e hardware Windows, pacote assinado e checagem de segurança. Separar versões e dados até migração segura.

## Desenvolvimento

No diretório `windows/` com JDK 17 e Gradle 8.14:

```shell
gradle test run
```

Empacotar somente no Windows:

```shell
gradle packageExe packageMsi
```

O pipeline `brother-windows-desktop-qa.yml` compila, testa e tenta empacotar no `windows-latest`, guardando artefatos **QA**. Não publica Release comercial.

A interface de QA é somente leitura para DST: nenhuma edição é gravada e o arquivo original não é alterado.
