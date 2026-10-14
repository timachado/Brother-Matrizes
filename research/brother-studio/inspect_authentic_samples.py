#!/usr/bin/env python3
"""PoC isolada: identificação de Wilcom EMB e validação de matrizes de referência.

NÃO converte EMB. NÃO modifica o aplicativo comercial, não usa credenciais,
não envia arquivos privados e não rebatiza extensões.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

import pyembroidery
import olefile

SAMPLES = Path(sys.argv[1] if len(sys.argv) > 1 else "public-samples")
OUTPUT = Path(sys.argv[2] if len(sys.argv) > 2 else "research-output")
OUTPUT.mkdir(parents=True, exist_ok=True)

NATIVE = [
    "random1-wilcom-e1.EMB",
    "random1-wilcom-e15.EMB",
    "random1-wilcom-e18.EMB",
    "random1-wilcom-e2.EMB",
    "random1-wilcom-e2006.EMB",
    "random1-wilcom.EMB",
]
MACHINE = [
    ("PES", "random1-wilcom-v6.PES"),
    ("PES", "random1-wilcom-v1.PES"),
    ("JEF", "random1-janome.jef"),
    ("DST", "random1-wilcom.DST"),
]

def evidence(path: Path) -> dict:
    blob = path.read_bytes()
    first = blob[:32]
    return {
        "filename": path.name,
        "bytes": len(blob),
        "sha256": hashlib.sha256(blob).hexdigest(),
        "header_hex": first.hex(" "),
        "header_ascii": "".join(chr(x) if 32 <= x < 127 else "." for x in first),
        "ole_container": blob.startswith(bytes.fromhex("d0cf11e0a1b11ae1")),
        "zip_container": blob.startswith(b"PK\x03\x04"),
        "pes_magic": blob.startswith(b"#PES"),
    }

result = {
    "experiment": "identificação real de EMB e validação independente de formatos de máquina",
    "output_is_emb_conversion": False,
    "wilcom_api_called": False,
    "source": "https://github.com/EmbroidePy/samples (public MIT reference)",
    "sample_wilcom_emb": [],
    "reference_machine_files": [],
}
for name in NATIVE:
    path = SAMPLES / name
    if not path.is_file():
        raise FileNotFoundError(f"Amostra EMB pública ausente: {name}")
    d = evidence(path)
    if d["ole_container"] and olefile.isOleFile(str(path)):
        with olefile.OleFileIO(str(path)) as compound:
            streams = ["/".join(x) for x in compound.listdir(streams=True, storages=False)]
        d["ole_streams"] = streams[:24]
        d["ole_stream_count"] = len(streams)
        d["wilcom_version_read_from_emb"] = None
    else:
        d["ole_streams"] = []
        d["ole_stream_count"] = 0
    d["classification"] = "arquivo de amostra denominado EMB; leitura proprietária NÃO validada"
    # Não aceitar renomear um formato distinto para EMB como conversão.
    if d["pes_magic"]:
        raise AssertionError(f"Amostra EMB é aparentemente PES renomeado: {name}")
    result["sample_wilcom_emb"].append(d)

for kind, name in MACHINE:
    path = SAMPLES / name
    if not path.is_file():
        raise FileNotFoundError(f"Matriz pública ausente: {name}")
    d = evidence(path)
    try:
        if kind == "PES":
            design = pyembroidery.read_pes(str(path))
        elif kind == "JEF":
            design = pyembroidery.read_jef(str(path))
        else:
            design = pyembroidery.read_dst(str(path))
        if design is None or not getattr(design, "stitches", None):
            raise ValueError("leitor não retornou pontos")
        stitched = [a for a in design.stitches if int(a[2]) & 0xFF == pyembroidery.STITCH]
        if not stitched:
            raise ValueError("não existem pontos de costura")
        d["readable"] = True
        d["entries"] = len(design.stitches)
        d["stitches"] = len(stitched)
        d["bounds_units"] = [
            min(a[0] for a in design.stitches),
            min(a[1] for a in design.stitches),
            max(a[0] for a in design.stitches),
            max(a[1] for a in design.stitches)
        ]
    except Exception as e:
        d["readable"] = False
        d["error"] = f"{type(e).__name__}: {str(e)[:160]}"
    d["format"] = kind
    result["reference_machine_files"].append(d)

native = result["sample_wilcom_emb"]
machines = result["reference_machine_files"]
result["native_distinct_sha256"] = len({x["sha256"] for x in native})
result["machine_validated_by_pyembroidery"] = sum(x["readable"] for x in machines)
result["limitations"] = [
    "Nenhuma conversão EMB -> PES/JEF/DST ocorreu, pois a API Wilcom requer credenciais próprias.",
    "A validação dos arquivos de máquina de referência não demonstra que foram convertidos por este script.",
    "O ZIP privado EMBs.zip enviado anteriormente não foi acessado neste experimento.",
    "Nome e cabeçalho não bastam para comprovar versão EMB, pontos, cores e fidelidade de conversão."
]
if len(native) != len(NATIVE) or sum(x["readable"] for x in machines) < 3:
    raise RuntimeError("Prova de conceito de identificação/validação incompleta")
(OUTPUT / "authentic_samples_evidence.json").write_text(
    json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
)
lines = [
    "# Brother Studio — evidências da PoC isolada",
    "",
    "**Status:** identificação de amostras EMB e leitura independente dos arquivos de máquina, NÃO uma conversão EMB.",
    "",
    "## Amostras EMB autênticas de referência",
    "| Nome | Tamanho | Streams internos OLE | SHA-256 |",
    "|---|---:|---|---|"
]
for x in native:
    lines.append(f"| {x['filename']} | {x['bytes']} | {', '.join(x['ole_streams'][:5])} | \`{x['sha256']}\` |")
lines.extend(["", "## Matrizes de máquina de referência", "| Formato | Arquivo | Leitura independente | Pontos de costura |",
              "|---|---|---|---:|"])
for x in machines:
    lines.append(f"| {x['format']} | {x['filename']} | {'Sim' if x['readable'] else 'Não'} | {x.get('stitches','—')} |")
lines.extend(["", "## Limitações obrigatórias", *("- " + x for x in result["limitations"]),
              "", "Nenhum arquivo convertido deve ser entregue antes de concluir teste com o serviço Wilcom licenciado."])
(OUTPUT / "RESULTADOS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(json.dumps({
    "emb_samples": len(native),
    "emb_hashes": result["native_distinct_sha256"],
    "reference_files_read": result["machine_validated_by_pyembroidery"],
    "actual_EMB_conversion": False
}, ensure_ascii=False))
