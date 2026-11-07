#!/usr/bin/env python3
"""Converte EXATAMENTE o WebP premium original do Android em ICO Windows.

Não redesenha arte; não gera variações de marca ou ícones por IA.
Pillow é usado somente no build de QA e não é enviado ao Windows final.
"""

from __future__ import annotations

import argparse
import base64
from io import BytesIO
from pathlib import Path
import struct
import sys

from PIL import Image, UnidentifiedImageError

ROOT = Path(__file__).resolve().parents[2]
OFFICIAL_WEBP = ROOT / "branding" / "brother_bm_launcher_safe.webp.base64"
SIZES = (16, 24, 32, 48, 64, 128, 256)


def create_icon(output: Path) -> None:
    source = base64.b64decode(OFFICIAL_WEBP.read_text(encoding="ascii"), validate=True)
    if not (len(source) > 128 and source[:4] == b"RIFF" and source[8:12] == b"WEBP"):
        raise ValueError("A arte premium original do Android não é WebP.")
    image = Image.open(BytesIO(source))
    image.load()
    if image.width < 256 or image.height < 256 or image.width != image.height:
        raise ValueError("O ícone oficial deve ser quadrado e >=256px.")
    if image.format != "WEBP":
        raise ValueError("Conteúdo não é a imagem WebP Android aprovada.")
    original = image.convert("RGBA")
    output.parent.mkdir(parents=True, exist_ok=True)
    original.save(
        output,
        format="ICO",
        sizes=[(edge, edge) for edge in SIZES],
        append_images=[],
    )
    encoded = output.read_bytes()
    if len(encoded) < 1024 or encoded[:4] != b"\x00\x00\x01\x00":
        raise ValueError("ICO Windows gerado está vazio ou inválido.")
    count = struct.unpack_from("<H", encoded, 4)[0]
    if count < 5:
        raise ValueError("ICO Windows sem suficientes resoluções para o Explorer.")
    with Image.open(output) as verified:
        if verified.format != "ICO" or max(verified.size) < 128:
            raise ValueError("ICO Windows não pôde ser reaberto.")
    print("BROTHER_WINDOWS_PREMIUM_ICON_FROM_ANDROID_OK", output, len(encoded))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    create_icon(args.out.resolve())


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, UnidentifiedImageError) as exc:
        print("BROTHER_WINDOWS_PREMIUM_ICON_FAIL:", exc, file=sys.stderr)
        raise SystemExit(1)
