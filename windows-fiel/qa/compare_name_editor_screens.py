#!/usr/bin/env python3
"""Auditoria visual entre golden Android e captura Windows, sem liberação.

A comparação NÃO é uma prova de paridade pixel a pixel entre dispositivos
com tamanhos, escalas e densidades diferentes. Fontes e dados de clientes
não são utilizados.
"""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from PIL import Image, ImageDraw, UnidentifiedImageError

MAX_BYTES = 16 * 1024 * 1024
BACKGROUND = (12, 27, 43)
WHITE = (239, 243, 247)
GOLD = (209, 174, 99)


def read_image(path: Path, *, android: bool) -> Image.Image:
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"PNG QA ausente ou não confiável: {path}")
    if not (10_000 <= path.stat().st_size <= MAX_BYTES):
        raise ValueError(f"Tamanho de PNG QA inválido: {path}")
    try:
        with Image.open(path) as check:
            if check.format != "PNG":
                raise ValueError(f"Imagem não PNG: {path}")
            check.verify()
        with Image.open(path) as src:
            image = src.convert("RGB")
            image.load()
            w, h = image.size
            min_w, min_h = ((320, 560) if android else (700, 600))
            if not min_w <= w <= 2500 or not min_h <= h <= 4000:
                raise ValueError(f"Resolução QA incompatível: {path} ({w}x{h})")
            return image.copy()
    except (OSError, UnidentifiedImageError) as exc:
        raise ValueError(f"Imagem QA corrompida: {path}") from exc


def metrics(image: Image.Image) -> dict:
    w, h = image.size
    samples = [image.getpixel((x, y))
               for y in range(5, h - 5, 7)
               for x in range(5, w - 5, 7)]
    if len(samples) < 500:
        raise ValueError("Poucos pixels para auditoria.")
    palette = Counter((r // 16, g // 16, b // 16) for r, g, b in samples)
    if len(palette) < 20:
        raise ValueError(f"Captura aparentemente vazia: {len(palette)} tons.")
    n = len(samples)
    ratios = {
        "blue_night_ratio": sum(1 for r, g, b in samples
                                if r <= 65 and g <= 100 and b <= 145
                                and b >= r * 0.65) / n,
        "gold_accent_ratio": sum(1 for r, g, b in samples
                                 if r >= 115 and g >= 65
                                 and r > g * 1.15 and g > b * 1.12) / n,
        "light_surface_ratio": sum(1 for r, g, b in samples
                                   if min(r, g, b) >= 180) / n,
    }
    return {
        "width": w, "height": h, "sampled_pixels": n,
        "quantized_colors": len(palette),
        **{name: round(value, 5) for name, value in ratios.items()},
    }


def fit(image: Image.Image, max_w: int, max_h: int) -> Image.Image:
    scale = min(max_w / image.width, max_h / image.height)
    if scale <= 0 or not math.isfinite(scale):
        raise ValueError("Escala de comparação inválida.")
    return image.resize((max(1, round(image.width * scale)),
                         max(1, round(image.height * scale))),
                        Image.Resampling.LANCZOS)


def side_by_side(left: Image.Image, right: Image.Image, path: Path,
                 title: str, left_title: str, right_title: str) -> None:
    margin, gap, header = 24, 28, 88
    board = Image.new(
        "RGB",
        (left.width + right.width + 2 * margin + gap,
         max(left.height, right.height) + 2 * margin + header),
        BACKGROUND,
    )
    draw = ImageDraw.Draw(board)
    draw.text((margin, margin), title, fill=WHITE)
    draw.text((margin, margin + 28), left_title, fill=GOLD)
    draw.text((margin + left.width + gap, margin + 28), right_title, fill=GOLD)
    board.paste(left, (margin, header))
    board.paste(right, (margin + left.width + gap, header))
    path.parent.mkdir(parents=True, exist_ok=True)
    board.save(path, format="PNG", optimize=True)
    if path.stat().st_size < 10_000:
        raise ValueError(f"Painel de revisão aparentemente vazio: {path}")


def compare(android_png: Path, windows_png: Path, out: Path) -> dict:
    android = read_image(android_png, android=True)
    windows = read_image(windows_png, android=False)
    am, wm = metrics(android), metrics(windows)
    out.mkdir(parents=True, exist_ok=True)

    side_by_side(
        fit(android, 460, 870), fit(windows, 940, 870),
        out / "editor-nomes-tela-completa.png",
        "BROTHER MATRIZES / Editor de Nomes / comparação para revisão humana",
        f"ANDROID original {android.width}x{android.height}",
        f"WINDOWS Compose {windows.width}x{windows.height}",
    )
    android_top = android.crop(
        (0, 0, android.width, min(android.height, round(android.width * 1.05)))
    )
    side_by_side(
        fit(android_top, 760, 650), fit(windows, 940, 650),
        out / "editor-nomes-primeira-dobra.png",
        "BROTHER MATRIZES / Primeira dobra / viewports diferentes",
        "Android: região superior",
        "Windows: conteúdo da janela",
    )

    stats = {
        "status": "IMAGENS_REAIS_VALIDAS_REVISAO_VISUAL_PENDENTE",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "input": {
            "android": {"filename": android_png.name, **am},
            "windows": {"filename": windows_png.name, **wm},
        },
        "absolute_color_ratio_differences": {
            key: round(abs(float(am[key]) - float(wm[key])), 5)
            for key in ("blue_night_ratio", "gold_accent_ratio",
                        "light_surface_ratio")
        },
        "fixture": "TM + Maria; parâmetros conferidos em teste Kotlin no repositório",
        "limitations": [
            "Resolução e densidade de pixels não são iguais nas duas plataformas.",
            "Cores normalizadas não provam fidelidade visual.",
            "Rolagem, ações interativas e layout fora da primeira dobra não homologados.",
            "TTF/OTF comercial, autenticação, cotas e bordadeira real não testados.",
        ],
        "manual_review_required": [
            "Título Editar nome, Voltar, Desfazer e Refazer",
            "Canvas, bastidor e matriz sem cortes",
            "Abas Nome / Letra / Bastidor",
            "Texto, medidas e pontos exibidos",
            "Rolagem, campos de edição e Aplicar nome",
        ],
    }
    (out / "metricas-e-limites.json").write_text(
        json.dumps(stats, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (out / "LEIA-ME-QA.md").write_text(
        "# Comparação visual — Brother Matrizes Windows\n\n"
        "**Isto não é homologação de fidelidade ou pacote distribuível.**\n\n"
        "Origem: golden real Android e captura real Windows da mesma fixture "
        "sintética TM + Maria. As proporções de cor são somente diagnósticas.\n\n"
        "Arquivos: editor-nomes-tela-completa.png; "
        "editor-nomes-primeira-dobra.png; metricas-e-limites.json.\n\n"
        "Pendente: revisão de hierarquia visual, campo de nomes, rolagem, "
        "fontes comerciais, licenças e uso na máquina. Não criar EXE/MSI/ZIP.\n",
        encoding="utf-8",
    )
    return stats


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android", type=Path, required=True)
    parser.add_argument("--windows", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = compare(args.android, args.windows, args.output)
    print("BROTHER_NAME_EDITOR_VISUAL_AUDIT_ARTIFACTS_OK")
    print(json.dumps({
        "status": report["status"],
        "android": report["input"]["android"],
        "windows": report["input"]["windows"],
        "color_differences": report["absolute_color_ratio_differences"],
        "manual_review_items": len(report["manual_review_required"]),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
