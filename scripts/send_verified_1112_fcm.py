#!/usr/bin/env python3
"""Envia um unico aviso FCM da 1.11.2 somente depois de confirmar manifesto oficial."""
import json
import os
import urllib.request

from google.auth.transport.requests import Request
from google.oauth2 import service_account

PROJECT = "brother-matrizes---ti-machado"
TOPIC = "brother_matrizes_updates_stable"
EXPECTED_SHA = "d5ded13f571259475be763e57e365c3fc4d7f2854fe377a97033c9e752d9fb2b"

manifest_file = os.environ.get("BROTHER_VERIFIED_MANIFEST", "")
if not manifest_file:
    raise SystemExit("Manifesto verificado ausente: FCM bloqueado")
with open(manifest_file, encoding="utf-8") as f:
    manifest = json.load(f)
if not (
    manifest.get("applicationId") == "com.timachado.brothermatrizes"
    and manifest.get("versionCode") == 189
    and manifest.get("versionName") == "1.11.2"
    and manifest.get("sha256") == EXPECTED_SHA
    and "/releases/download/v1.11.2/" in manifest.get("downloadUrl", "")
):
    raise SystemExit("Manifesto nao corresponde a release oficial verificada: FCM bloqueado")

raw = os.environ.get("BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON", "")
if not raw:
    raise SystemExit("FCM nao configurado: BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON ausente")
config = json.loads(raw)
if config.get("project_id") != PROJECT:
    raise SystemExit("FCM recusado: projeto Firebase incorreto")
creds = service_account.Credentials.from_service_account_info(
    config, scopes=["https://www.googleapis.com/auth/firebase.messaging"]
)
creds.refresh(Request())

payload = json.dumps({
    "message": {
        "topic": TOPIC,
        "data": {
            "type": "update",
            "versionCode": "189",
            "versionName": "1.11.2",
            "title": "Brother Matrizes 1.11.2 disponível",
            "body": "Editor de nomes atualizado e melhorias na visualização. Abra o aplicativo para atualizar com segurança."
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
        raise SystemExit(f"FCM recusado: HTTP {response.status}")
    reply = json.load(response)
    if not str(reply.get("name", "")).startswith(f"projects/{PROJECT}/messages/"):
        raise SystemExit("FCM nao confirmou ID de mensagem")

print("FCM aceitou aviso 1.11.2 para topico oficial; entrega por aparelho depende do consentimento.")
