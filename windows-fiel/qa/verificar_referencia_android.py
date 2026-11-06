#!/usr/bin/env python3
"""Verificação estática da fonte oficial Android 1.11.8 (NÃO testa visual).

Não modifica arquivos. Não faz rede. Destina-se somente ao novo fluxo
Windows fiel, separado da QA Windows reprovada.
"""

from __future__ import annotations

import base64
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/timachado/brothermatrizes"
RES = ROOT / "app/src/main/res/drawable"


def read(relative: str) -> str:
    file = ROOT / relative
    if not file.is_file():
        raise ValueError(f"Fonte Android obrigatória ausente: {relative}")
    return file.read_text(encoding="utf-8")


def require(check: bool, reason: str) -> None:
    if not check:
        raise ValueError(reason)


def validate() -> None:
    gradle = read("app/build.gradle.kts")
    require('versionName = "1.11.8"' in gradle, "Versão Android diferente de 1.11.8.")
    require("versionCode = 195" in gradle, "Código Android diferente de 195.")

    home = read("app/src/main/java/com/timachado/brothermatrizes/HomeScreen.kt")
    main = read("app/src/main/java/com/timachado/brothermatrizes/MainActivity.kt")
    theme = read(
        "app/src/main/java/com/timachado/brothermatrizes/ui/theme/BrotherMatrizesTheme.kt"
    )

    sections = [
        'testTag("brother_home_scroll")',
        "GridCells.Fixed(2)",
        "brother_bm_premium_approved",
        "BrotherUpdateHomeCard(",
        "PremiumHeroCard()",
        'title = "Criar nome"',
        '"Ferramentas"',
        'title = "Biblioteca de fontes"',
        'title = "Abrir matriz"',
        'title = "Simulador"',
        'title = "Editor"',
        'title = "Converter"',
        'title = "Enviar"',
        '"Recente"',
        '"Comunidade e novidades',
        "brother_home_hero",
    ]
    for required in sections:
        require(required in home, f"Home Android perdeu um componente aprovado: {required!r}")

    # Ordem documentada da Home (não aprova screenshots nem funcionalidade).
    anchors = [
        "BrotherUpdateHomeCard(",
        "PremiumHeroCard()",
        'title = "Criar nome"',
        '"Ferramentas"',
        'title = "Biblioteca de fontes"',
        'title = "Abrir matriz"',
        'title = "Simulador"',
        'title = "Editor"',
        'title = "Converter"',
        'title = "Enviar"',
    ]
    positions = [home.index(a) for a in anchors]
    require(positions == sorted(positions), "A ordem principal dos cartões da Home mudou.")

    nav = main.split("bottomBar = {", 1)
    require(len(nav) == 2, "Barra inferior Android não encontrada.")
    nav_block = nav[1].split(") { padding ->", 1)[0]
    require('testTag("brother_premium_bottom_bar")' in nav_block,
            "Navegação premium Android ausente.")
    nav_labels = ['Text("Início")', 'Text("Criar")',
                  'Text("Biblioteca")', 'Text("Fontes")', 'Text("Conta")']
    require(all(label in nav_block for label in nav_labels),
            "Uma das cinco ações de navegação Android desapareceu.")
    require([nav_block.index(label) for label in nav_labels] ==
            sorted(nav_block.index(label) for label in nav_labels),
            "A ordem das cinco abas do Android foi alterada.")

    routes = [
        "Home", "CreateName", "CreateMonogram", "CreateDrawing",
        "FontLibrary", "ProjectLibrary", "TelegramSearch", "ArchiveImport",
        "Account", "Community", "Transfer", "Viewer", "Simulator",
        "Converter", "NameEditor", "Editor",
    ]
    for route in routes:
        require(f"Screen.{route}" in main,
                f"Rota Android oficial ausente no roteador: {route}")

    for token in [
        "0xFF08131D", "0xFF101D28", "0xFF102A40",
        "0xFFE6BE70", "0xFFF7F4EE", "0xFFA9B3BC",
        "BrotherMatrizesTheme(", "RoundedCornerShape",
    ]:
        require(token in theme, f"Token visual original ausente: {token}")

    for image, magic, length in [
        ("branding/brother_bm_premium_approved.webp.base64", b"RIFF", 1000),
        ("branding/brother_home_hero.base64", b"\xff\xd8", 1000),
        ("branding/brother_bm_launcher_safe.webp.base64", b"RIFF", 1000),
    ]:
        decoded = base64.b64decode(read(image), validate=True)
        require(len(decoded) > length and decoded.startswith(magic),
                f"Recurso binário de marca inválido: {image}")
        if image.endswith(".webp.base64"):
            require(decoded[8:12] == b"WEBP",
                    f"Imagem da marca não é WEBP válido: {image}")

    for resource in [
        "ic_ms_home_rounded.xml", "ic_ms_text_fields_rounded.xml",
        "ic_ms_folder_open_rounded.xml", "ic_ms_font_download_rounded.xml",
        "ic_ms_account_circle_rounded.xml", "ic_premium_bell.xml",
        "ic_ms_play_arrow_rounded.xml", "ic_ms_edit_rounded.xml",
        "ic_ms_swap_horiz_rounded.xml", "ic_ms_upload_rounded.xml",
    ]:
        require((RES / resource).is_file(),
                f"Ícone Android oficial não encontrado: {resource}")

    screenshot = read(
        "app/src/androidTest/java/com/timachado/brothermatrizes/"
        "PremiumHomeScreenshotTest.kt"
    )
    require("captureHomeWithApprovedEmbroideryHero" in screenshot,
            "Teste de captura da Home Android não encontrado.")
    require("premium-home-screenshot.png" in screenshot,
            "Teste de captura deixou de gerar imagem de referência.")

    print("BROTHER_ANDROID_1118_REFERENCE_OK")
    print("Fonte, 16 rotas, navegação, composição e recursos originais conferidos.")
    print("AVISO: aprovação estática NÃO comprova paridade Windows ou teste físico.")


if __name__ == "__main__":
    try:
        validate()
    except (ValueError, OSError, base64.binascii.Error) as exc:
        print(f"BROTHER_ANDROID_1118_REFERENCE_FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
