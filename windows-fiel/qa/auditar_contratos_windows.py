#!/usr/bin/env python3
"""Auditoria estática independente de Gradle, Maven, rede e contas reais.

Não substitui os testes Kotlin nem a homologação E2E. Detecta regressões
documentáveis em rotas de origem Android, isolamento comercial e UI Windows.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/timachado/brothermatrizes"
WIN = ROOT / "windows-fiel/src/main/kotlin/com/timachado/brothermatrizes"
PLAT = WIN / "desktop/platform"
WORKFLOW = ROOT / ".github/workflows/brother-windows-portable-qa.yml"
errors = []

def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"Arquivo obrigatório ausente: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def require(name: str, yes: bool) -> None:
    if yes:
        print(f"[OK] {name}")
    else:
        errors.append(name)
        print(f"[FALHA] {name}", file=sys.stderr)

android = read(JAVA / "FontLibraryScreen.kt")
create = read(JAVA / "CreateNameScreen.kt")
editor = read(JAVA / "NameEditorScreen.kt")
archive = read(JAVA / "ArchiveExtractorScreen.kt")
gradle = read(ROOT / "windows-fiel/build.gradle.kts")
host = read(WIN / "desktop/FielMain.kt")
font_ui = read(WIN / "desktop/DesktopFontCatalogScreen.kt")
bridge = read(PLAT / "DesktopCreatorPlatform.kt")
safety = read(PLAT / "DesktopFontImportSafety.kt")
gate = read(PLAT / "DesktopCommercialFontImportGate.kt")
optin = read(PLAT / "DesktopWindowsAccountQaOptIn.kt")
target = read(PLAT / "DesktopBrotherBackendTarget.kt")
gateway = read(PLAT / "DesktopBrotherFontUsageGateway.kt")
journal = read(PLAT / "DesktopArchiveQuotaJournal.kt")
workflow = read(WORKFLOW)

require("Telas Android de fonte, nome, editor e extrator existem",
        all((android, create, editor, archive)))
require("Gradle gera cartões Android e extrator a partir da fonte original",
        all(f"val {task} by tasks.registering" in gradle for task in
            ("copyAndroidNativeFontGallery", "copyAndroidExtractorScreen")))
require("Prévia de fontes usa o motor de ponto original",
        "TextMatrixGenerator" in android and
        "OriginalAndroidNativeFontCard(preset)" in
        read(WIN / "desktop/DesktopFontCatalogScreen.kt"))
require("Host Windows inclui as telas originais",
        all(name in host for name in
            ("CreateNameScreen(", "ArchiveExtractorScreen(", "NameEditorScreen(")))
require("Criar Nome e Editor Android solicitam ImportedFontMatrixGenerator",
        "ImportedFontMatrixGenerator" in create and
        "ImportedFontMatrixGenerator" in editor and
        "CleanRoomSatinEngine.generate(" in bridge)
require("Biblioteca revalida integridade da fonte antes de gerar bordado",
        all(s in bridge for s in
            ("MessageDigest.getInstance(\"SHA-256\")", "Files.isSymbolicLink",
             "safeId.matches", "check(verify(selected, root))")))
require("Importador não é oferecido por padrão nem sem autenticação",
        "importGate: DesktopCommercialFontImportGate? = null" in font_ui and
        "if (importGate != null)" in font_ui and
        "BROTHER_WINDOWS_QA_GOOGLE_AUTH" in optin and
        "BROTHER_WINDOWS_QA_FONT_IMPORT" in optin and
        "qaEnvironment.verifiedAccessTokenForQa()" in optin)
require("Usuário confirma consumo de cota antes de importar TTF/OTF",
        all(s in font_ui for s in
            ("DesktopReadOnlyFontAnalyzer.inspect(selected)",
             "Confirmar importação e possível desconto da cota",
             "Cancelar sem consumir cota")))
require("Backend Windows tem o mesmo Supabase oficial do Android",
        'OFFICIAL_REF = "dwpcddiramxlhavdmmyn"' in target and
        "brother-usage" in gateway and
        "import_font" in gateway and
        "instanceFollowRedirects = false" in gateway and
        "conn.disconnect()" in gateway)
require("Reserva real exige journal antes de acesso de rede",
        "usage !is DesktopBrotherFontUsageGateway || journal != null" in gate and
        "journal?.begin(selected)" in gate and
        gate.index("journal?.begin(selected)") < gate.index("usage.reserveFont(token)"))
require("Registro de cotas exige confirmação e bloqueia fontes pendentes",
        all(s in gate for s in ("journal.verifyOutput(", ".finalizing(",
                                  ".completed(")) and
        all(s in safety for s in ("journalFor(", ".listPending()",
                                    "fileNameSha256", "Files.isSymbolicLink")))
require("Falha após declarar publicação preserva journal antes de finalize",
        "if (importFailure != null && created == null &&" in gate and
        "pending?.output != null" in gate and
        gate.index("pending?.output != null") < gate.index("usage.finalizeFont(token, reservation, confirmed)") and
        "Publicação da fonte incerta" in gate and
        ".completed(" in gate)
require("Teste de crash protege fase RESERVED sem recibo de propriedade",
        "assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVED, pending.stage)" in
        read(ROOT / "windows-fiel/src/test/kotlin/com/timachado/brothermatrizes/desktop/DesktopFontImportJournalSafetyTest.kt"))
require("Pasta de journal não permite symlink nem permissões POSIX excessivas",
        all(s in safety for s in ("Files.createDirectory(", "ownerOnly",
                                    "Files.getPosixFilePermissions",
                                    "Files.isSymbolicLink(location)")))
require("Testes de importação e cotas estão na seleção obrigatória do portátil",
        all(t in workflow for t in
            ("*DesktopCreatorImportedFontBridgeTest",
             "*DesktopFontImportJournalSafetyTest",
             "*DesktopWindowsFontImportOptInTest",
             "*DesktopWindowsSharedAccountQuotaQaTest")))
require("Testes portáteis mantêm importação comercial desabilitada por padrão",
        "BROTHER_WINDOWS_QA_GOOGLE_AUTH: disabled" in workflow and
        "BROTHER_WINDOWS_QA_FONT_IMPORT: enabled" not in workflow)
require("Nenhum serviço/credencial privilegiada foi incorporado ao gate",
        "sb_secret_" not in gate and
        "service_role" not in gate and
        "http://" not in gate and
        "https://" not in gate)


# Duplicate explicit Kotlin imports previously blocked both the portable EXE
# and quota QA before any test could run. Catch this cheaply before Gradle.
# Source files only: no network, credentials, or generated Android code.
duplicate_imports = []
for kotlin_source in (WIN / "desktop").rglob("*.kt"):
    if "build" in kotlin_source.parts:
        continue
    seen_imports = set()
    for number, line in enumerate(kotlin_source.read_text(encoding="utf-8").splitlines(), 1):
        cleaned = line.strip()
        if not cleaned.startswith("import "):
            continue
        if cleaned in seen_imports:
            duplicate_imports.append(f"{kotlin_source.relative_to(ROOT)}:{number}")
        else:
            seen_imports.add(cleaned)
require("Importações Kotlin explícitas sem duplicatas na camada Windows",
        not duplicate_imports)
if duplicate_imports:
    print("Arquivos com import repetido: " + ", ".join(duplicate_imports),
          file=sys.stderr)

if errors:
    print(f"CONTRATOS_WINDOWS_FALHARAM: {len(errors)} problema(s)", file=sys.stderr)
    sys.exit(1)
print("CONTRATOS_WINDOWS_AUDITORIA_ESTATICA_OK (não equivale a E2E)")
