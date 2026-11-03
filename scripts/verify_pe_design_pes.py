#!/usr/bin/env python3
"""QA independente de PES v1 COMPLETO para intercambio com PE-DESIGN 11.

A leitura por pyembroidery NÃO equivale à abertura no aplicativo Windows.
"""
from pathlib import Path
import sys
import pyembroidery


def main() -> int:
    fixture_dir = Path(sys.argv[1])
    files = sorted(fixture_dir.glob("*-PE_DESIGN.pes"))
    if len(files) < 2:
        raise SystemExit(f"Faltam fixtures PC PES editado/com cores: {files}")
    for file in files:
        data = file.read_bytes()
        assert data.startswith(b"#PES0001"), f"{file}: esperado PES v1 completo"
        pec_offset = int.from_bytes(data[8:12], "little")
        assert 22 < pec_offset < len(data) - 512, f"{file}: offset PEC invalido"
        metadata = data[12:pec_offset]
        assert b"CEmbOne" in metadata, f"{file}: falta CEmbOne"
        assert b"CSewSeg" in metadata, f"{file}: falta CSewSeg"
        assert data[pec_offset:pec_offset+3] == b"LA:", f"{file}: cabecalho PEC invalido"
        result = pyembroidery.read(str(file))
        assert result is not None, f"{file}: leitor externo nao abriu"
        stitches = [(x, y) for x, y, cmd in result.stitches
                    if (cmd & pyembroidery.COMMAND_MASK) == pyembroidery.STITCH]
        assert len(stitches) > 4, f"{file}: sem pontadas visiveis"
        assert len(result.threadlist) >= 1, f"{file}: sem paleta de cores"
        x_min = min(p[0] for p in stitches)
        x_max = max(p[0] for p in stitches)
        y_min = min(p[1] for p in stitches)
        y_max = max(p[1] for p in stitches)
        assert x_max > x_min and y_max > y_min, f"{file}: desenho degenerado"
        assert max(x_max - x_min, y_max - y_min) < 32000, (
            f"{file}: extensao suspeita para contagem de coordenadas PES")
        print(f"PES v1 completo legivel por pyembroidery: {file.name}; "
              f"pontadas={len(stitches)}, cores={len(result.threadlist)}, "
              f"extensao={x_max-x_min}x{y_max-y_min} unidades")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
