#!/usr/bin/env python3
"""Converte APENAS ícones vetoriais Android originais em SVG para Compose Desktop.

A geometria (pathData) é copiada sem redesenho e sem gerar ícones fictícios.
Os arquivos de branding JPEG/WEBP continuam sendo os bytes originais
base64 da versão comercial Android; não convertemos a arte em IA.
"""

from __future__ import annotations

import argparse
import base64
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "app/src/main/res/drawable"
ANDROID = "{http://schemas.android.com/apk/res/android}"

ICON_NAMES = (
    "ic_ms_home_rounded",
    "ic_ms_text_fields_rounded",
    "ic_ms_folder_open_rounded",
    "ic_ms_font_download_rounded",
    "ic_ms_account_circle_rounded",
    "ic_ms_play_arrow_rounded",
    "ic_ms_edit_rounded",
    "ic_ms_swap_horiz_rounded",
    "ic_ms_upload_rounded",
    "ic_premium_bell",
)


def android_icon_svg(name: str) -> str:
    root = ET.parse(DRAWABLE / (name + ".xml")).getroot()
    if root.tag != "vector":
        raise ValueError(f"Ícone oficial não é Android VectorDrawable: {name}")
    width = float(root.attrib[ANDROID + "viewportWidth"])
    height = float(root.attrib[ANDROID + "viewportHeight"])
    paths = []
    for child in root:
        if child.tag != "path":
            raise ValueError(f"XML do ícone contém recurso não suportado: {name}")
        data = child.attrib[ANDROID + "pathData"]
        if len(data) > 100_000 or not re.fullmatch(r"[a-zA-Z0-9 .,+eE-]+", data):
            raise ValueError(f"Comandos de caminho inesperados: {name}")
        # O painter do Android é branco e recebe cor do Compose Icon(tint).
        fill = child.attrib.get(ANDROID + "fillColor", "#FFFFFFFF")
        if fill.lower() == "@android:color/white":
            fill = "#FFFFFF"
        elif fill.startswith("#"):
            fill = "#" + fill[-6:]
        else:
            raise ValueError(f"Tinta de ícone não estática: {name}")
        alpha = child.attrib.get(ANDROID + "fillAlpha", "1")
        path_attributes = (f'd="{escape(data)}" fill="{fill}" fill-opacity="{alpha}"')
        paths.append(f"  <path {path_attributes}/>")
    if not paths:
        raise ValueError(f"Ícone oficial vazio: {name}")
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" '
            f'viewBox="0 0 {width:g} {height:g}">\n' +
            "\n".join(paths) + "\n</svg>\n")


def main() -> None:
    cli = argparse.ArgumentParser()
    cli.add_argument("--out", type=Path, required=True)
    args = cli.parse_args()
    dest = args.out.resolve() / "drawable"
    dest.mkdir(parents=True, exist_ok=True)
    for icon in ICON_NAMES:
        svg = android_icon_svg(icon)
        (dest / (icon + ".svg")).write_text(svg, encoding="utf-8")

    for original, target in [
        ("brother_bm_launcher_safe.webp.base64",
         "brother_bm_launcher_safe.webp"),
        ("brother_bm_premium_approved.webp.base64",
         "brother_bm_premium_approved.webp"),
        ("brother_home_hero.base64", "brother_home_hero-original.jpg"),
    ]:
        content = (ROOT / "branding" / original).read_text(encoding="utf-8")
        blob = base64.b64decode(content, validate=True)
        is_webp = target.endswith(".webp")
        if is_webp:
            if not (blob.startswith(b"RIFF") and blob[8:12] == b"WEBP"):
                raise ValueError("Arte BM não corresponde ao WebP original.")
        elif not blob.startswith(b"\xff\xd8"):
            raise ValueError("Banner original não é JPEG.")
        (dest / target).write_bytes(blob)
    print("BROTHER_ANDROID_ASSETS_ORIGINAIS_EXTRAIDOS")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, ET.ParseError) as exc:
        print(f"ERRO: {exc}", file=sys.stderr)
        sys.exit(1)
