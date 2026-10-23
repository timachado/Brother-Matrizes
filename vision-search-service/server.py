"""Brother Vision: reverse image matching over voluntarily licensed catalog entries.

NO Telegram scraping, indexing of Telegram user content, or claims of global Telegram search.
Image bytes are discarded after inference. Supabase user JWT is verified server-side.
"""
from __future__ import annotations

import io
import json
import math
import os
import re
import secrets
import sqlite3
import threading
from pathlib import Path
from urllib.parse import urlparse

import httpx
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_IMAGE_BYTES = 4 * 1024 * 1024
MAX_PIXELS = 16_000_000
MAX_ENTRIES = 5_000  # Search V1; migrate to pgvector for a larger licensed collection.
TELEGRAM_POST = re.compile(r"^https://t\.me/[A-Za-z][A-Za-z0-9_]{4,31}/[1-9][0-9]*$")
FORMAT_OPTIONS = {"PES", "DST", "JEF", "EXP", "VP3", "XXX", "OTHER"}
DB_LOCK = threading.Lock()
MODEL_LOCK = threading.Lock()

app = FastAPI(title="Brother Vision — authorized visual search", version="0.1.0")


def connect_db() -> sqlite3.Connection:
    path = os.getenv("BM_VISION_DB", "/data/brother-vision.sqlite3")
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path, timeout=10.0)
    connection.row_factory = sqlite3.Row
    connection.execute("""
        CREATE TABLE IF NOT EXISTS licensed_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            telegram_url TEXT NOT NULL UNIQUE,
            preview_url TEXT NOT NULL,
            matrix_format TEXT NOT NULL,
            rights_holder TEXT NOT NULL,
            authorization_reference TEXT NOT NULL,
            embedding TEXT NOT NULL,
            approved INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """)
    connection.commit()
    return connection


def validated_https(value: str) -> str:
    url = urlparse(value)
    if url.scheme != "https" or not url.hostname or url.username or url.password:
        raise HTTPException(status_code=422, detail="A referência deve utilizar HTTPS.")
    if len(value) > 600:
        raise HTTPException(status_code=422, detail="URL muito longa.")
    return value


async def verified_user(authorization: str | None = Header(default=None)) -> str:
    base = os.getenv("BM_VISION_SUPABASE_URL", "").rstrip("/")
    key = os.getenv("BM_VISION_SUPABASE_PUBLISHABLE_KEY", "")
    if not base.startswith("https://") or not key:
        raise HTTPException(status_code=503, detail="Autenticação do catálogo não configurada.")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Entre na sua conta Google.")
    token = authorization[7:].strip()
    if not token or len(token) > 8192:
        raise HTTPException(status_code=401, detail="Sessão inválida.")
    try:
        async with httpx.AsyncClient(timeout=6.0, follow_redirects=False) as client:
            response = await client.get(
                f"{base}/auth/v1/user",
                headers={"apikey": key, "Authorization": f"Bearer {token}"},
            )
        if response.status_code != 200:
            raise HTTPException(status_code=401, detail="Sessão expirada ou inválida.")
        user_id = response.json().get("id")
        if not isinstance(user_id, str) or not user_id:
            raise HTTPException(status_code=401, detail="Sessão inválida.")
        return user_id
    except httpx.RequestError:
        raise HTTPException(status_code=503, detail="Verificação de conta indisponível.") from None


def require_admin(x_admin_token: str | None = Header(default=None)) -> None:
    expected = os.getenv("BM_VISION_ADMIN_TOKEN", "")
    if len(expected) < 32 or not x_admin_token or not secrets.compare_digest(x_admin_token, expected):
        raise HTTPException(status_code=403, detail="Administração não autorizada.")


async def decode_image(file: UploadFile) -> Image.Image:
    if file.content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=415, detail="Escolha uma foto JPEG, PNG ou WebP.")
    raw = bytearray()
    while True:
        chunk = await file.read(min(64 * 1024, MAX_IMAGE_BYTES + 1 - len(raw)))
        if not chunk:
            break
        raw.extend(chunk)
        if len(raw) > MAX_IMAGE_BYTES:
            raise HTTPException(status_code=413, detail="Imagem maior que 4 MB.")
    try:
        Image.MAX_IMAGE_PIXELS = MAX_PIXELS
        with Image.open(io.BytesIO(raw)) as image:
            if image.width * image.height > MAX_PIXELS:
                raise HTTPException(status_code=413, detail="Imagem muito grande.")
            image.load()
            normalized = ImageOps.exif_transpose(image).convert("RGB")
        return normalized
    except (UnidentifiedImageError, OSError, ValueError):
        raise HTTPException(status_code=422, detail="Arquivo de imagem inválido.") from None
    finally:
        for index in range(len(raw)):
            raw[index] = 0


class OpenClipEncoder:
    """Loads model once, from a reviewed local checkpoint; never fetches Telegram content."""
    def __init__(self) -> None:
        checkpoint = os.getenv("BM_VISION_MODEL_CHECKPOINT", "")
        if not checkpoint or not Path(checkpoint).is_file():
            raise RuntimeError("Arquivo de pesos OpenCLIP não configurado.")
        import open_clip
        import torch

        self.torch = torch
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(
            "ViT-B-32", pretrained=checkpoint, device="cpu"
        )
        self.model.eval()

    def encode(self, image: Image.Image) -> list[float]:
        with MODEL_LOCK, self.torch.inference_mode():
            input_tensor = self.preprocess(image).unsqueeze(0)
            features = self.model.encode_image(input_tensor)
            values = features[0].float().cpu().tolist()
        return normalize(values)


def normalize(values: list[float]) -> list[float]:
    if len(values) != 512 or not all(math.isfinite(x) for x in values):
        raise ValueError("Representação visual inválida.")
    norm = math.sqrt(sum(x * x for x in values))
    if norm <= 1e-12:
        raise ValueError("Representação visual sem informação.")
    return [x / norm for x in values]


def encoder() -> OpenClipEncoder:
    instance = getattr(app.state, "vision_encoder", None)
    if instance is not None:
        return instance
    with MODEL_LOCK:
        instance = getattr(app.state, "vision_encoder", None)
        if instance is None:
            try:
                instance = OpenClipEncoder()
            except Exception:
                raise HTTPException(status_code=503, detail="Motor visual ainda não configurado.") from None
            app.state.vision_encoder = instance
    return instance


@app.get("/health")
def health() -> dict:
    with connect_db() as conn:
        count = conn.execute("SELECT count(*) FROM licensed_items WHERE approved=1").fetchone()[0]
    return {"service": "brother-vision", "authorized_items": count, "global_telegram_search": False}


@app.post("/v1/catalog/items", dependencies=[Depends(require_admin)])
async def ingest(
    image: UploadFile = File(...),
    title: str = Form(...),
    telegram_url: str = Form(...),
    preview_url: str = Form(...),
    matrix_format: str = Form(...),
    rights_holder: str = Form(...),
    authorization_reference: str = Form(...),
    authorized_for_visual_index: bool = Form(...),
) -> dict:
    if not authorized_for_visual_index:
        raise HTTPException(status_code=403, detail="Consentimento explícito obrigatório.")
    if not 2 <= len(title.strip()) <= 150 or not 2 <= len(rights_holder.strip()) <= 150:
        raise HTTPException(status_code=422, detail="Título ou titular inválido.")
    if not 5 <= len(authorization_reference.strip()) <= 160:
        raise HTTPException(status_code=422, detail="Comprovação de autorização obrigatória.")
    if matrix_format.upper() not in FORMAT_OPTIONS:
        raise HTTPException(status_code=422, detail="Formato de matriz não suportado.")
    if not TELEGRAM_POST.fullmatch(telegram_url):
        raise HTTPException(status_code=422, detail="Indique uma publicação pública válida do Telegram.")
    validated_https(preview_url)
    photo = await decode_image(image)
    try:
        vector = normalize(encoder().encode(photo))
    finally:
        photo.close()
    with DB_LOCK, connect_db() as conn:
        try:
            cursor = conn.execute(
                """INSERT INTO licensed_items
                (title, telegram_url, preview_url, matrix_format, rights_holder,
                 authorization_reference, embedding) VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (
                    title.strip(), telegram_url, preview_url, matrix_format.upper(),
                    rights_holder.strip(), authorization_reference.strip(), json.dumps(vector),
                ),
            )
            conn.commit()
        except sqlite3.IntegrityError:
            raise HTTPException(status_code=409, detail="Publicação já cadastrada.") from None
    return {"id": cursor.lastrowid, "status": "indexed"}


@app.delete("/v1/catalog/items/{item_id}", dependencies=[Depends(require_admin)])
def revoke(item_id: int) -> dict:
    with DB_LOCK, connect_db() as conn:
        cursor = conn.execute("DELETE FROM licensed_items WHERE id=?", (item_id,))
        conn.commit()
    if not cursor.rowcount:
        raise HTTPException(status_code=404, detail="Referência não localizada.")
    return {"deleted": True}


@app.post("/v1/search/image")
async def search_image(
    image: UploadFile = File(...), user_id: str = Depends(verified_user)
) -> dict:
    del user_id  # Validated only for authorization; never stored or logged.
    photo = await decode_image(image)
    try:
        vector = normalize(encoder().encode(photo))
    finally:
        photo.close()
    with connect_db() as conn:
        rows = conn.execute(
            """SELECT id, title, telegram_url, preview_url, matrix_format, embedding
            FROM licensed_items WHERE approved=1 ORDER BY id DESC LIMIT ?""",
            (MAX_ENTRIES,),
        ).fetchall()
    if not rows:
        return {"catalog_count": 0, "matches": []}
    ranked = []
    for row in rows:
        try:
            candidate = json.loads(row["embedding"])
            score = sum(a * b for a, b in zip(vector, candidate))
        except (ValueError, TypeError):
            continue
        if score >= 0.55:
            ranked.append({
                "id": row["id"], "title": row["title"],
                "telegram_url": row["telegram_url"], "preview_url": row["preview_url"],
                "format": row["matrix_format"], "relevance": round(score, 4),
            })
    ranked.sort(key=lambda result: result["relevance"], reverse=True)
    return {"catalog_count": len(rows), "matches": ranked[:8]}
