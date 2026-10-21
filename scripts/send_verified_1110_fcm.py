#!/usr/bin/env python3
"""Envia UMA notificacao FCM somente para a release oficial 1.11.0 ja no manifesto.
Nao recebe chave via argumento, nao registra tokens/segredos no log.
"""
import json
import os
import urllib.request

from google.auth.transport.requests import Request
from google.oauth2 import service_account

EXPECTED_PROJECT = "brother-matrizes---ti-machado"
TOPIC = "brother_matrizes_updates_stable"

raw = os.environ.get("BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON", "")
if not raw:
    raise SystemExit("FCM nao configurado: BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON ausente")

config = json.loads(raw)
if config.get("project_id") != EXPECTED_PROJECT:
    raise SystemExit("FCM recusado: projeto Firebase nao corresponde ao aplicativo oficial")

creds = service_account.Credentials.from_service_account_info(
    config,
    scopes=["https://www.googleapis.com/auth/firebase.messaging"],
)
creds.refresh(Request())

payload = json.dumps({
    "message": {
        "topic": TOPIC,
        "data": {
            "type": "update",
            "versionCode": "187",
            "versionName": "1.11.0",
            "title": "Brother Matrizes: atualizacao 1.11.0 disponivel",
            "body": "Novo visual Premium e melhorias. Abra o aplicativo para atualizar com seguranca.",
        },
        "android": {"priority": "high"},
    }
}).encode("utf-8")

req = urllib.request.Request(
    f"https://fcm.googleapis.com/v1/projects/{EXPECTED_PROJECT}/messages:send",
    data=payload,
    headers={
        "Authorization": f"Bearer {creds.token}",
        "Content-Type": "application/json; charset=utf-8",
    },
    method="POST",
)
with urllib.request.urlopen(req, timeout=30) as response:
    if response.status != 200:
        raise SystemExit(f"FCM recusado: status HTTP {response.status}")
    reply = json.load(response)
    if not str(reply.get("name", "")).startswith(
        f"projects/{EXPECTED_PROJECT}/messages/"
    ):
        raise SystemExit("FCM nao confirmou ID de mensagem valida")

print("FCM aceitou o aviso da 1.11.0 no topico oficial. Entrega individual depende do Android e do opt-in.")
