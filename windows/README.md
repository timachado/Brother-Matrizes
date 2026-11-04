# Brother Matrizes Desktop — Windows (QA, NÃO COMERCIAL)

Este é um **novo projeto Windows isolado** do Android comercial. Não altera arquivos de `app/`, `supabase/` ou os workflows de publicação Android.

## Escopo técnico inicial

- Janela desktop com identidade Brother Matrizes e ferramentas de importação via seletor de arquivos Windows.
- Abertura de **DST, PES e JEF** usando os parsers oficiais do aplicativo Android, com verificação de tamanho, comandos, END e limites.
- Pré-visualização de pontadas e métricas; ajustes geométricos experimentais de escala, rotação, deslocamento e espelhamento em memória.\n- Exportação experimental DST/PES/JEF usando o conversor Android original, com releitura da saída e gravação **apenas de novos arquivos** (sem sobrescrever existentes).\n- Perfil PES para PE-DESIGN PC marcado como experimental; sem garantia de abertura no PE-DESIGN 11.
- Motor Kotlin oficial do Android reutilizado no build Desktop, sem copiar versões divergentes para o repositório (leitura DST, modelos, edição, geração de nomes e conversor).\n- Testes cruzados do leitor DST, editor, nomes e conversão PES/DST e empacotamento de instalador EXE/MSI em runner Windows.
- Nenhum upload das matrizes para serviços remotos.

**Esta QA não equivale à versão Android completa.** Ela não oferece ainda editor completo na interface, abertura PES/JEF, interface de conversão, tela de criação de nome, monogramas, biblioteca, login, assinaturas, backup, notificações, exportação ou PE-DESIGN. A execução do empacotamento não significa que os recursos estejam concluídos nem que um instalador tenha sido validado em um computador real.

## Motor Kotlin comum (em integração)\n\nO build da pasta `windows/` inclui uma lista **explícita** de arquivos Kotlin oficiais do diretório Android `app/src/main/java/com/timachado/brothermatrizes/core/embroidery/`. O Gradle copia esses fontes para `windows/build/generated/official-core/kotlin/` apenas durante a compilação do Desktop. Nenhum arquivo do Android é alterado.\n\nOs componentes compilados (incluindo `DstParser`, `EmbroideryIoParser`, `MatrixEditor`, `TextMatrixGenerator` e `MatrixConverter`) estão parcialmente conectados à interface (leitura, transformação geométrica e exportação), mas não representam o editor Android completo. O Android usa o mesmo código-fonte original, eliminando divergência de implementação nesta parte do núcleo. A dependência `EmbroideryIO` usa a variante JVM para Windows e a variante Android continua inalterada no aplicativo.\n\nOs testes automatizados de PES verificam geração e assinatura do cabeçalho, **não** garantem compatibilidade com o PE-DESIGN 11 instalado no Windows.\n\n## Contrato de paridade obrigatório antes do lançamento comercial

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

A interface QA permite abrir DST/PES/JEF, ajustar geometria e exportar uma nova cópia. A matriz original nunca é substituída.\n\n## Segurança do armazenamento\n\n- Importação local limitada a 8 MiB, validações de comandos/pontadas e leitores do núcleo oficial.\n- Operações pesadas usam `Dispatchers.IO` ou `Dispatchers.Default` para evitar bloquear a interface.\n- Exportação é validada por releitura com parser do formato gerado.\n- Arquivos existentes são protegidos por `CREATE_NEW`; não existe modo sobrescrever nesta QA.\n- Saídas são experimentais e não devem ser enviadas a máquinas de clientes sem homologação de costura e formato.\n\n**Pendente:** comprovar compatibilidade dos executáveis em Windows real e abrir PES PC dentro do PE-DESIGN 11.
