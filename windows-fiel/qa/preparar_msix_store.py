#!/usr/bin/env python3
"""Stage the existing Windows jpackage app-image for an UNSIGNED MSIX QA.

Name and Publisher are the exact values copied by the owner from Partner Center.
This remains an unsigned internal QA package; all three identity fields
were supplied by the owner from Partner Center. The final release version
must still be approved before submission.
Never sign, install, publish, or include customer/credential files here.
"""
import argparse
import base64
from io import BytesIO
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OFFICIAL_ICON = ROOT / "branding" / "brother_bm_launcher_safe.webp.base64"
STORE_NAME = "T.I.MachadoSoluesemTecnol.BrotherMatrizes"
STORE_PUBLISHER = "CN=353309CB-0CCB-4E2D-8F88-AF631DAA395F"
STORE_PUBLISHER_DISPLAY_NAME = "T.I. Machado Soluções em Tecnologia"
QA_VERSION = "1.0.0.0"  # provisional build; not a commercial submission
NS = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
UAP = "http://schemas.microsoft.com/appx/manifest/uap/windows10"
UAP10 = "http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
RESCAP = "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
for prefix, namespace in (("", NS), ("uap", UAP), ("uap10", UAP10), ("rescap", RESCAP)):
    ET.register_namespace(prefix, namespace)


def stage_msix_qa(app_image: Path, target: Path, icon_source: Path = OFFICIAL_ICON, *, store_candidate: bool = False) -> Path:
    source = app_image.resolve(strict=True)
    destination = target.resolve()
    if not source.is_dir() or destination == source or source in destination.parents or destination in source.parents:
        raise ValueError("Origem e destino devem ser pastas independentes.")
    if destination.exists():
        raise ValueError("Destino ja existe: recusar mistura de versoes anteriores.")
    launcher = source / "BrotherMatrizes.exe"
    if not launcher.is_file() or launcher.stat().st_size < 1024:
        raise ValueError("BrotherMatrizes.exe ausente ou invalido.")
    for folder in ("app", "runtime"):
        if not (source / folder).is_dir():
            raise ValueError(f"Pasta essencial do runtime ausente: {folder}")
    for entry in source.rglob("*"):
        if entry.is_symlink():
            raise ValueError("Links simbolicos nao sao permitidos no MSIX.")
        if entry.is_file() and entry.suffix.lower() in (".pfx", ".p12", ".pem", ".key"):
            raise ValueError("Chave privada ou certificado nao podem entrar no MSIX.")
    data = base64.b64decode(icon_source.read_text(encoding="ascii").strip(), validate=True)
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise ValueError("Icone fonte nao e o WebP oficial Android.")
    with Image.open(BytesIO(data)) as image:
        if image.format != "WEBP" or min(image.size) < 64:
            raise ValueError("WebP Android oficial invalido.")
        original = image.convert("RGBA")
    shutil.copytree(source, destination, symlinks=False)
    assets = destination / "Assets"
    assets.mkdir()
    edge = max(original.size)
    canvas = Image.new("RGBA", (edge, edge), (0, 0, 0, 0))
    canvas.alpha_composite(original, ((edge - original.width) // 2, (edge - original.height) // 2))
    logos = ((44, "Square44x44Logo.png"), (150, "Square150x150Logo.png"), (50, "StoreLogo.png"))
    for size, name in logos:
        canvas.resize((size, size), Image.Resampling.LANCZOS).save(assets / name)

    # Store candidate only changes manifest branding, never license logic or Android.
    # Still unsigned, never uploads to Store, needs independent MSIX install/WACK.
    display_name = "Brother Matrizes" if store_candidate else "Brother Matrizes Windows (QA MSIX)"
    description = (
        "Brother Matrizes para Windows."
        if store_candidate else "MSIX para testar empacotamento. Nao distribuir."
    )
    package = ET.Element(f"{{{NS}}}Package")
    ET.SubElement(package, f"{{{NS}}}Identity", {
        "Name": STORE_NAME, "Publisher": STORE_PUBLISHER,
        "Version": QA_VERSION, "ProcessorArchitecture": "x64",
    })
    props = ET.SubElement(package, f"{{{NS}}}Properties")
    for tag, value in (
        ("DisplayName", display_name),
        ("PublisherDisplayName", STORE_PUBLISHER_DISPLAY_NAME),
        ("Description", description),
        ("Logo", r"Assets\StoreLogo.png"),
    ):
        ET.SubElement(props, f"{{{NS}}}{tag}").text = value
    resources = ET.SubElement(package, f"{{{NS}}}Resources")
    ET.SubElement(resources, f"{{{NS}}}Resource", {"Language": "pt-BR"})
    dependencies = ET.SubElement(package, f"{{{NS}}}Dependencies")
    ET.SubElement(dependencies, f"{{{NS}}}TargetDeviceFamily", {
        "Name": "Windows.Desktop", "MinVersion": "10.0.19041.0",
        "MaxVersionTested": "10.0.26100.0",
    })
    capabilities = ET.SubElement(package, f"{{{NS}}}Capabilities")
    ET.SubElement(capabilities, f"{{{RESCAP}}}Capability", {"Name": "runFullTrust"})
    applications = ET.SubElement(package, f"{{{NS}}}Applications")
    app = ET.SubElement(applications, f"{{{NS}}}Application", {
        "Id": "BrotherMatrizes", "Executable": "BrotherMatrizes.exe",
        f"{{{UAP10}}}RuntimeBehavior": "packagedClassicApp",
        f"{{{UAP10}}}TrustLevel": "mediumIL",
    })
    ET.SubElement(app, f"{{{UAP}}}VisualElements", {
        "DisplayName": display_name,
        "Description": description,
        "Square150x150Logo": r"Assets\Square150x150Logo.png",
        "Square44x44Logo": r"Assets\Square44x44Logo.png",
        "BackgroundColor": "transparent",
    })
    ET.indent(package, space="  ")
    manifest = destination / "AppxManifest.xml"
    ET.ElementTree(package).write(manifest, encoding="utf-8", xml_declaration=True)
    parsed = ET.parse(manifest).getroot()
    assert parsed.find(f"{{{NS}}}Identity").attrib["Publisher"] == STORE_PUBLISHER
    assert parsed.find(f"{{{NS}}}Identity").attrib["Name"] == STORE_NAME
    assert parsed.find(f"{{{NS}}}Properties/{{{NS}}}PublisherDisplayName").text == STORE_PUBLISHER_DISPLAY_NAME
    assert parsed.find(f"{{{NS}}}Applications/{{{NS}}}Application").attrib["Executable"] == "BrotherMatrizes.exe"
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-image", required=True, type=Path)
    parser.add_argument("--stage", required=True, type=Path)
    parser.add_argument("--store-candidate", action="store_true", help="Store submission candidate, unsigned; no upload")
    args = parser.parse_args()
    try:
        staged = stage_msix_qa(args.app_image, args.stage, store_candidate=args.store_candidate)
        print("BROTHER_WINDOWS_MSIX_STAGING_OK", staged, STORE_NAME,
              "STORE-CANDIDATE" if args.store_candidate else "QA")
    except (OSError, ValueError, AssertionError) as error:
        print("BROTHER_WINDOWS_MSIX_QA_STAGING_FAIL:", error, file=sys.stderr)
        raise SystemExit(1)
