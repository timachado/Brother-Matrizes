# Brother Matrizes Desktop — piloto técnico interno 01 (Windows)

**Estado: preparação de QA, NÃO é distribuição comercial.** Documento destinado
a pessoas **previamente autorizadas** pela T.I. Machado a testar em computadores
próprios de homologação. A versão atual **não tem login/licenciamento comercial
Windows implementado** e a restrição de acesso depende da **distribuição
controlada** do pacote. Não fornecer o ZIP a clientes finais nem divulgar
publicamente. Os instaladores QA **não são assinados digitalmente**.

## Origem única do pacote

- PR de homologação: https://github.com/timachado/Brother-Matrizes/pull/225
- A versão e o identificador EXATOS devem ser retirados do **último workflow
  aprovado desse PR**, nunca presumidos pelos nomes do EXE.
- Artefato GitHub Actions privado: `brother-windows-installer-QA-NAO-COMERCIAL`
  (retenção usual 7 dias). Um link antigo pode expirar.
- O ZIP gerado pelo workflow atual deve conter `exe/`, `msi/`,
  `qa/installer-manifest.json`, `qa/SHA256SUMS.txt` e o kit sintético
  `qa/fixtures/` com seus próprios checksums. Contém ainda o verificador
  `qa/verify-beta-package.ps1` e os documentos de homologação. O kit só
  está confirmado quando o workflow específico do commit passar.
- O campo `sourceCommit` dos novos manifestos deve apontar para **o SHA de
  origem do PR** e `workflowRunId` para o run que produziu os binários.
  GitHub Actions usa outro SHA para o commit temporário de merge no PR; não
  confundir os dois. Se não corresponder à página do workflow, **rejeitar**.

## Antes de iniciar

1. A pessoa autorizada utiliza **somente um PC Windows de teste** sem
   aplicativo comercial Brother Matrizes instalado, sem projetos de clientes,
   e registra versão/arquitetura do Windows e escala de tela.
2. O responsável baixa o ZIP no GitHub privado, verifica quem recebeu uma
   cópia e registra ID do workflow, SHA do código e hash do MSI.
3. Extrai **todo o ZIP** numa única pasta, preservando `exe/`, `msi/` e
   `qa/`. Não execute o EXE/MSI diretamente do ZIP.
4. Usa PowerShell somente para **verificar**, sem instalar nada automaticamente:

   ```powershell
   .\qa\verify-beta-package.ps1 -ExpectedCommit "<SHA-40-caracteres-do-PR>" -ExpectedRunId "<ID-do-workflow>"
   ```

   Se a política local não autorizar execução de scripts, usar `Get-FileHash
   -Algorithm SHA256` para cada instalador e conferir manualmente o manifesto.
   **Não desabilite SmartScreen, antivírus, políticas de execução ou UAC.**
   Se houver bloqueio de reputação de arquivo não assinado, registrar o
   bloqueio e não prosseguir sem autorização e ambiente gerenciado.
   O verificador confirma também os oito arquivos listados em
   `qa/fixtures/SHA256SUMS.txt`, se presentes. Para o candidato desta
   versão, exigir a mensagem `BROTHER_WINDOWS_SYNTHETIC_FIXTURES_SHA256_OK`.
   Se ela não aparecer, o kit de testes não está confirmado; não iniciar
   homologação de formatos com arquivos ausentes.

5. Escolher **um** instalador (de preferência MSI) por PC. Não instalar EXE e
   MSI simultaneamente. Evitar privilégios elevados além dos estritamente
   solicitados pelo Windows.

## Roteiro de teste do piloto

O avaliador segue também o
[`ROTEIRO-HOMOLOGACAO-FISICA.md`](ROTEIRO-HOMOLOGACAO-FISICA.md).

- [ ] A aplicação instala e desinstala corretamente no PC de teste.
- [ ] A interface abre sem cortar controles em 1024×768 ou 1366×768,
      respeitando a área útil, e rola pelo painel lateral.
- [ ] Abrir arquivos **sintéticos**, sem dados privados, em DST/PES/JEF.
- [ ] Testar prévia, conversão de cópias novas e recusa de sobrescrita.
- [ ] Gerar um nome curto, um monograma e usar somente fontes com licença.
- [ ] Testar escala, expansão H/V, rotação, espelhamento, desfazer/refazer,
      restauração e isolamento entre documentos.
- [ ] Salvar/abrir FLP de teste, comparar pontadas e formatos.
- [ ] Reabrir a aplicação e confirmar estabilidade, inclusive após reiniciar
      o PC de teste.
- [ ] Testar PE-DESIGN 11 **apenas se disponível com licença oficial**,
      registrando comparação PES e diferenças observadas.
- [ ] Executar costura de teste **apenas com operador habilitado** e máquina
      de homologação; NÃO presumir que o CI valida o bordado real.
- [ ] Remover a QA ao fim do piloto e confirmar que o software comercial,
      se existente em outros ambientes, não foi alterado.

## Evidências e proteção de dados

- **Não** incluir nomes de clientes, matrizes privadas, e-mails, tokens,
  certificados, senhas, chaves de produção nem documentos internos em logs.
- Capturas de tela apenas do programa e matrizes sintéticas. Compartilhar
  evidências exclusivamente pelos canais privados autorizados.
- Aplicativo QA funciona **offline e sem conta comercial**. Não adicionar
  telemetria ou acesso a Supabase/servidores nesta etapa.
- Registrar incidentes no PR ou em relatório privado sem dados pessoais.
  Não pedir ao participante alteração de políticas de segurança.

## Modelo de relatório do piloto

Copiar e preencher em relatório privado (sem dados pessoais):

```text
ID do teste: PILOTO-WIN-01-____
GitHub run: ____
Commit do PR: ____
SHA256 MSI: ____
Windows (edição/versão/arquitetura): ____
Resolução / escala DPI: ____
Instalação e desinstalação: PASSOU | FALHOU | NÃO EXECUTADO
Tela e navegação: PASSOU | FALHOU | NÃO EXECUTADO
DST/PES/JEF: PASSOU | FALHOU | NÃO EXECUTADO
FLP, proteção de sobrescrita: PASSOU | FALHOU | NÃO EXECUTADO
Editor H/V / desfazer-refazer: PASSOU | FALHOU | NÃO EXECUTADO
PE-DESIGN 11: PASSOU | FALHOU | NÃO EXECUTADO
Costura real: PASSOU | FALHOU | NÃO EXECUTADO
Erro reproduzível (passos e dados sintéticos): ____
Gravidade: BLOQUEADOR | ALTA | MÉDIA | BAIXA
Resultado final: APROVADO PARA CONTINUAR PILOTO | BLOQUEADO
```

## Critérios de decisão

**Autorização para teste interno:** CI da revisão do candidato aprovado,
manifesto íntegro, equipamento controlado, responsável identificado
internamente, uso de dados de teste e concordância explícita de participar.
Um resultado `success` no CI não equivale a liberação aos clientes.

**Expansão para beta com participantes externos:** somente após concluir
testes no primeiro PC físico e estabelecer como controlar licenças,
expiração, distribuição, suporte e revogação de acesso. A falta de login
Windows é um bloqueador; distribuição manual não substitui licenciamento.

**Versão comercial pública:** exige, além disso, revisão de segurança,
assinatura Authenticode, critérios de estabilidade, compatibilidade
PE-DESIGN 11 e bordadeira, aprovação do produto e plano de atualização.

**Não fazer merge, release nem distribuição pública com este documento.**
