#!/usr/bin/env python3
"""QA de PES exportado para PE-DESIGN com um leitor Python independente."""
from pathlib import Path
import sys
import pyembroidery

def main() -> int:
    fixture_dir = Path(sys.argv[1])
    files = sorted(fixture_dir.glob('*-PE_DESIGN.pes'))
    if len(files) < 2:
        raise SystemExit(f'Faltam fixtures PC PES editado/com cores: {files}')
    for file in files:
        data = file.read_bytes()
        assert data.startswith(b'#PES0060'), f'{file}: nao e PES v6'
        pec_offset = int.from_bytes(data[8:12], 'little')
        assert 22 < pec_offset < len(data) - 512, f'{file}: offset PEC invalido'
        assert b'CEmbOne' in data and b'CSewSeg' in data, f'{file}: faltam blocos PES completos'
        result = pyembroidery.read(str(file))
        assert result is not None and len(result.stitches) > 4, f'{file}: sem pontos validos'
        assert len(result.threadlist) >= 1, f'{file}: sem paleta de cores'
        print(f'PES de PC verificado por pyembroidery: {file.name}; comandos={len(result.stitches)}, cores={len(result.threadlist)}')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
