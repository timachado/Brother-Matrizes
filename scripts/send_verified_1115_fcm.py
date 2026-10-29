#!/usr/bin/env python3
"""Envio FCM 1.11.5 somente apos assinatura, release e OTA confirmadas."""
import json
import os
import re
import urllib.request

from google.auth.transport.requests import Request
from google.oauth2 import service_account

PROJECT = "brother-matrizes---ti-machado"
TOPIC = "brother_matrizes_updates_stable"
expected_sha = os.environ.get("BROTHER_RELEASE_SHA256", "").strip()
manifest_file = os.environ.get("BROTHER_VERIFIED_MANIFEST", "")

if not re.fullmatch(r"[a-f0-9]{64}", expected_sha):
    raise SystemExit("Hash assinado ausente ou invalido: notificacao bloqueada")
if not manifest_file:
    raise SystemExit("Manifesto verificado ausente: notificacao bloqueada")
with open(manifest_file, encoding="utf-8") as f:
    manifest = json.load(f)
apk_url = (
    "https://github.com/timachado/Brother-Matrizes/releases/download/"
    "v1.11.5/Brother-Matrizes-1.11.5-OFICIAL-COMERCIAL-ASSINADO.apk"
)
if not (
    manifest.get("applicationId") == "com.timachado.brothermatrizes"
    and manifest.get("channel") == "stable"
    and manifest.get("versionCode") == 192
    and manifest.get("versionName") == "1.11.5"
    and manifest.get("sha256") == expected_sha
    and manifest.get("downloadUrl") == apk_url
    and manifest.get("downloadApkUrl") == apk_url
):
    raise SystemExit("Manifesto nao corresponde ao APK comercial verificado")

raw = os.environ.get("BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON", "")
if not raw:
    raise SystemExit("Firebase FCM nao configurado: segredo ausente")
config = json.loads(raw)
if config.get("project_id") != PROJECT:
    raise SystemExit("Projeto Firebase incorreto: envio bloqueado")
creds = service_account.Credentials.from_service_account_info(
    config, scopes=["https://www.googleapis.com/auth/firebase.messaging"]
)
creds.refresh(Request())
payload = json.dumps({
    "message": {
        "topic": TOPIC,
        "data": {
            "type": "update",
            "versionCode": "192",
            "versionName": "1.11.5",
            "title": "Brother Matrizes 1.11.5 disponível",
            "body": (
                "Posicione a matriz e o nome separadamente; salve e reabra "
                "o PES mantendo o layout no Brother Matrizes. Atualize pelo app."
            ),
        },
        "android": {"priority": "high"},
    }
}).encode("utf-8")
req = urllib.request.Request(
    f"https://fcm.googleapis.com/v1/projects/{PROJECT}/messages:send",
    data=payload,
    headers={
        "Authorization": f"Bearer {creds.token}",
        "Content-Type": "application/json; charset=utf-8",
    },
    method="POST",
)
with urllib.request.urlopen(req, timeout=30) as response:
    if response.status != 200:
        raise SystemExit(f"FCM recusou solicitacao: HTTP {response.status}")
    reply = json.load(response)
    if not str(reply.get("name", "")).startswith(f"projects/{PROJECT}/messages/"):
        raise SystemExit("FCM nao confirmou identificador de mensagem")
print("FCM aceitou notificacao 1.11.5 para o topico oficial; entrega individual nao verificada.")
