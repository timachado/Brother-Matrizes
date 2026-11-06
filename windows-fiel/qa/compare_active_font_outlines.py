#!/usr/bin/env python3
"""Gate de comparação dos MESMOS bytes TTF Android x Windows.

Falha se contagem/topologia ou coordenadas diferirem. Um PASS nesta etapa
significa SOMENTE contornos compatíveis na fonte testada; não atesta matriz
comercial, kerning, fontes cursivas, guard ou bordadeira física.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "build" / "qa" / "font-parity"
ANDROID = BASE / "android" / "bm-parity-android-outline.tsv"
WINDOWS = BASE / "windows" / "bm-parity-windows-outline.tsv"
FONT = BASE / "android" / "bm-parity-font.ttf"
REPORT = BASE / "comparison-report.json"
MAX_ERROR_UNITS = 1.0  # 0,1 mm de geometria, não tolerância comercial.


def parse(path: Path):
    assert path.is_file() and path.stat().st_size < 16_000_000, path
    groups: dict[tuple[int, int], list[tuple[float, float]]] = {}
    header = None
    meta = None
    with path.open(encoding="utf-8", newline="") as fp:
        for row in csv.reader(fp, delimiter="\t"):
            if not row:
                continue
            if row[0] == "FONT":
                assert header is None and len(row) == 4, row
                header = row[1:]
            elif row[0] == "P":
                assert len(row) == 6, row
                glyph, contour, index = map(int, row[1:4])
                assert glyph in range(64) and contour in range(1000)
                points = groups.setdefault((glyph, contour), [])
                assert index == len(points), "Sequência de pontos não contígua"
                x, y = map(float, row[4:])
                assert math.isfinite(x) and math.isfinite(y)
                points.append((x, y))
            elif row[0] == "META":
                assert meta is None and len(row) == 4
                meta = row[1:]
            else:
                raise ValueError(f"Linha de QA desconhecida: {row[0]}")
    assert header and groups and meta, path
    return header, groups, meta


def bounds(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return [min(xs), min(ys), max(xs), max(ys)]



def compare_maria(font_sha: str) -> dict:
    """Caso multiglifo: mede kerning, shaping, topologia e erro geométrico."""
    result = {
        "case": "Maria",
        "passed": False,
        "issues": [],
        "max_point_distance_units": None,
    }
    android, contours_android, meta_android = parse(
        BASE / "android" / "bm-parity-android-outline-maria.tsv"
    )
    windows, contours_windows, meta_windows = parse(
        BASE / "windows" / "bm-parity-windows-outline-maria.tsv"
    )
    result["android_contours"] = len(contours_android)
    result["windows_contours"] = len(contours_windows)
    result["android_points"] = sum(len(p) for p in contours_android.values())
    result["windows_points"] = sum(len(p) for p in contours_windows.values())
    result["contextual_shaping_android"] = meta_android[-1]
    result["contextual_shaping_windows"] = meta_windows[-1]
    if android != windows or android[0] != font_sha or android[2] != "Maria":
        result["issues"].append("Fonte/parametros da palavra diferentes entre SO.")
    if contours_android.keys() != contours_windows.keys():
        result["issues"].append(
            "Agrupamento/ordem dos glifos mudou; possivel shaping contextual."
        )
    if meta_android[-1] != meta_windows[-1]:
        result["issues"].append(
            "A detecção de shaping contextual diverge entre Android e Windows."
        )
    max_distance = 0.0
    comparisons = 0
    for key in sorted(contours_android.keys() | contours_windows.keys()):
        aa, ww = contours_android.get(key, []), contours_windows.get(key, [])
        if len(aa) != len(ww):
            result["issues"].append(
                f"Contorno {key}: {len(aa)} pontos Android e {len(ww)} Windows."
            )
            continue
        if not aa:
            continue
        comparisons += 1
        local = max(
            math.hypot(ax - wx, ay - wy)
            for (ax, ay), (wx, wy) in zip(aa, ww)
        )
        max_distance = max(max_distance, local)
        if local > MAX_ERROR_UNITS:
            result["issues"].append(
                f"Contorno {key}: desvio geométrico {local:.3f} unidades."
            )
    result["compared_contours"] = comparisons
    result["max_point_distance_units"] = round(max_distance, 5)
    result["issues"] = result["issues"][:80]
    result["passed"] = comparisons > 0 and not result["issues"]
    return result



def compare_active_stitches() -> dict:
    """QA do kernel oficial em contornos equivalentes, sem o guard comercial.

    Exige mesmos comandos e cores; coordenadas podem diferir no máximo
    uma unidade (0,1mm) na fase inicial de diagnóstico.
    """
    report = {
        "case": "O - CleanRoomSatinEngine active kernel",
        "passed": False,
        "issues": [],
        "max_coordinate_delta_units": None,
    }

    def read(path: Path):
        lines = list(csv.reader(path.open(encoding="utf-8"), delimiter="\t"))
        assert lines and len(lines[0]) == 2 and lines[0][0] == "STITCHES"
        count = int(lines[0][1])
        assert count > 20 and count == len(lines) - 1
        parsed = []
        for index, row in enumerate(lines[1:]):
            assert len(row) == 6 and row[0] == "P"
            assert int(row[1]) == index
            x, y = int(row[2]), int(row[3])
            assert max(abs(x), abs(y)) <= 1_000_000
            parsed.append((x, y, row[4], int(row[5])))
        return parsed

    android = read(BASE / "android" / "bm-parity-android-kernel-o.tsv")
    windows = read(BASE / "windows" / "bm-parity-windows-kernel-o.tsv")
    report["android_commands"] = len(android)
    report["windows_commands"] = len(windows)
    if len(android) != len(windows):
        report["issues"].append(
            f"Comandos Satin: Android={len(android)} vs Windows={len(windows)}."
        )
    if len(android) == len(windows):
        changed = [
            i for i, (a, w) in enumerate(zip(android, windows))
            if a[2:] != w[2:]
        ]
        report["different_command_or_color_indices"] = changed[:30]
        if changed:
            report["issues"].append(
                f"Ordem/tipo/cor de {len(changed)} comandos diverge."
            )
        maxima = [
            max(abs(a[0] - w[0]), abs(a[1] - w[1]))
            for a, w in zip(android, windows)
        ]
        diff = max(maxima, default=0)
        report["max_coordinate_delta_units"] = diff
        if diff > MAX_ERROR_UNITS:
            report["issues"].append(
                f"Delta máximo de pontos Satin={diff} unidades."
            )
    report["passed"] = not report["issues"]
    return report


def main():
    report = {
        "gate": "Android active CleanRoom outlines vs Windows Skia - identical TTF",
        "tolerance_units": MAX_ERROR_UNITS,
        "passed": False,
        "note": "Esta etapa NÃO valida kerning, silhueta de matriz nem bordadeira.",
        "issues": [],
    }
    try:
        android, ag, am = parse(ANDROID)
        windows, wg, wm = parse(WINDOWS)
        font = FONT.read_bytes()
        sha = hashlib.sha256(font).hexdigest()
        report["font_sha256"] = sha
        report["android_header"] = android
        report["windows_header"] = windows
        report["android_contours"] = len(ag)
        report["windows_contours"] = len(wg)
        report["android_points"] = sum(len(v) for v in ag.values())
        report["windows_points"] = sum(len(v) for v in wg.values())
        if android != windows or android[0] != sha:
            report["issues"].append("Fonte ou parâmetros não são byte a byte iguais.")
        if ag.keys() != wg.keys():
            report["issues"].append("Índices/topologia dos contornos divergentes.")
        for key in sorted(ag.keys() | wg.keys()):
            aa, ww = ag.get(key, []), wg.get(key, [])
            if len(aa) != len(ww):
                report["issues"].append(
                    f"Glifo/contorno {key}: Android={len(aa)} vs Windows={len(ww)} pontos"
                )
            if aa and ww:
                ba, bw = bounds(aa), bounds(ww)
                if max(abs(x - y) for x, y in zip(ba, bw)) > MAX_ERROR_UNITS:
                    report["issues"].append(
                        f"Contorno {key}: caixas divergentes {ba} vs {bw}"
                    )
                if len(aa) == len(ww):
                    max_distance = max(
                        math.hypot(x1 - x2, y1 - y2)
                        for (x1, y1), (x2, y2) in zip(aa, ww)
                    )
                    report.setdefault("max_distance_by_contour_units", {})[
                        f"{key[0]}:{key[1]}"
                    ] = round(max_distance, 5)
                    if max_distance > MAX_ERROR_UNITS:
                        report["issues"].append(
                            f"Contorno {key}: desvio máximo {max_distance:.3f} unidades"
                        )
        report["issues"] = report["issues"][:80]
        report["satin_kernel_o"] = compare_active_stitches()
        if not report["satin_kernel_o"]["passed"]:
            report["issues"].append(
                "Sequencia Satin do motor ativo diverge do Android para O."
            )
        report["maria"] = compare_maria(sha)
        if not report["maria"]["passed"]:
            report["issues"].append(
                "Maria (multiglifo): equivalencia ainda nao comprovada."
            )
        report["passed"] = not report["issues"]
    except (ValueError, AssertionError, FileNotFoundError, OSError) as exc:
        report["issues"].append(f"Erro de integridade da QA: {exc}")
    finally:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
        print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
