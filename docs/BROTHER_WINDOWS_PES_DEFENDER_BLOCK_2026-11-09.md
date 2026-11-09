# PES de nome detectado pelo Windows Defender — investigação e proteção

Em 09/11/2026 o proprietário enviou uma foto com o bloqueio pelo
Windows de `C:\\Users\\Edvaldo\\Desktop\\nome-Edvaldo-convertido.pes`:
"Não foi possível concluir a operação com êxito porque o arquivo contém
um vírus ou software possivelmente indesejado."

## Evidências e limites

- O Windows sinalizou **o arquivo PES**, não necessariamente o launcher EXE.
- O nome/tipo exato de ameaça, ações do Defender, SHA256 e bytes do PES **não
  foram recebidos**. Não é possível afirmar malware ou falso positivo.
- Não desativar Defender/SmartScreen, não adicionar exclusões ou renomear
  a extensão para contornar a detecção.
- Solicitar apenas os detalhes do evento em Segurança do Windows >
  Proteção contra vírus e ameaças > Histórico de proteção, principalmente
  nome da detecção, status, hora e caminho. Não divulgar arquivos do cliente.
- Se o arquivo gerado por ferramenta própria for comprovadamente legítimo,
  registrar uma contestação técnica com o arquivo e nome de detecção pelo
  portal Microsoft Security Intelligence, com consentimento adequado.

## Mudança isolada na branch Windows

O botão exportar antes usava `Files.write(... CREATE_NEW)`, sem verificação
posterior. `DesktopVerifiedMatrixExport` reutiliza a gravação segura
`DesktopMachineTransferFile.saveNew` (sem sobrescrever), confere cabeçalhos
PES conhecidos `#PES0001` / `#PES0060`, lê de volta os bytes exatos,
reabre pelo parser Android e exige bordado não vazio. Em caso de falha
retorna erro de integridade/segurança em vez de sucesso, sem bypass
do antivírus. Isso NÃO garante que um arquivo seja limpo nem corrige a
detecção do Defender por si só.

Testes offline criam o nome sintético "Edvaldo" nos perfis PES Brother e
PE-DESIGN, sem qualquer arquivo ou conta comercial de cliente, e preservam
DST/JEF. O Windows Defender real só pode ser diagnosticado pelo evento e
amostra de arquivo devidamente autorizada. Android comercial, cotas
Free/Pro e Supabase de produção permanecem inalterados.

Fontes oficiais:
- https://support.microsoft.com/pt-br/windows/security/windows-security/protection-history-in-the-windows-security-app
- https://www.microsoft.com/en-us/wdsi/filesubmission
