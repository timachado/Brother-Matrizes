#!/usr/bin/env python3
"""One-time FCM announcement for an already published Brother Matrizes 1.11.6.

This command NEVER creates/releases an APK or alters the OTA manifest.
The caller must first check that the announcement audit marker does not exist.
"""
import json
import os
import urllib.request
from pathlib import Path

PROJECT = "brother-matrizes---ti-machado"
TOPIC = "brother_matrizes_updates_stable"
PACKAGE = "com.timachado.brothermatrizes"
VERSION = "1.11.6"
CODE = 193
TAG = f"v{VERSION}"
APK_NAME = f"Brother-Matrizes-{VERSION}-OFICIAL-COMERCIAL-ASSINADO.apk"
APK_URL = f"https://github.com/timachado/Brother-Matrizes/releases/download/{TAG}/{APK_NAME}"
EXPECTED_SHA = "9ab26113b03ffaf70638f12f720a694777fd1bb29a809a90513a7abd8cb672bd"
RELEASE_HEAD = "445c6655fa63610ea773ff9b8c1dc527e1ce75dc"


def assert_verified(manifest, release, commercial_run, ota_run):
    """Fail closed before using any Firebase credentials."""
    assert release.get("tag_name") == TAG
    assert release.get("draft") is False and release.get("prerelease") is False
    assert release.get("published_at")
    assets = release.get("assets")
    assert isinstance(assets, list) and len(assets) == 1, "Expected exactly one release asset"
    asset = assets[0]
    assert asset.get("name") == APK_NAME and asset.get("state") == "uploaded"
    assert asset.get("digest") == f"sha256:{EXPECTED_SHA}", "Release digest mismatch"
    assert asset.get("size", 0) >= 10000000, "APK unexpectedly small"
    assert asset.get("browser_download_url") == APK_URL
    assert commercial_run.get("id") == 41251177886
    assert commercial_run.get("head_sha") == RELEASE_HEAD
    assert commercial_run.get("status") == "completed" and commercial_run.get("conclusion") == "success"
    assert ota_run.get("id") == 41251599002
    assert ota_run.get("status") == "completed" and ota_run.get("conclusion") == "success"
    assert manifest.get("applicationId") == PACKAGE and manifest.get("channel") == "stable"
    assert manifest.get("versionName") == VERSION and manifest.get("versionCode") == CODE
    assert manifest.get("sha256") == EXPECTED_SHA
    assert manifest.get("downloadUrl") == APK_URL and manifest.get("downloadApkUrl") == APK_URL
    assert manifest.get("publishedAt") == release.get("published_at")
    return True


def send(manifest, release, commercial_run, ota_run, service_account_json):
    assert_verified(manifest, release, commercial_run, ota_run)
    if not service_account_json:
        raise RuntimeError("Firebase service account is not configured")
    config = json.loads(service_account_json)
    if config.get("project_id") != PROJECT:
        raise RuntimeError("Unexpected Firebase project: notification blocked")
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
    creds = service_account.Credentials.from_service_account_info(
        config, scopes=["https://www.googleapis.com/auth/firebase.messaging"])
    creds.refresh(Request())
    payload = {
        "message": {
            "topic": TOPIC,
            "data": {
                "type": "update",
                "versionCode": str(CODE),
                "versionName": VERSION,
                "title": "Brother Matrizes 1.11.6 disponível",
                "body": "Novo editor de nomes e monogramas, rolagem corrigida e controles independentes. Atualize pelo aplicativo.",
            },
            "android": {"priority": "high"},
        }
    }
    request = urllib.request.Request(
        f"https://fcm.googleapis.com/v1/projects/{PROJECT}/messages:send",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {creds.token}", "Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"FCM rejected the message: HTTP {response.status}")
        result = json.load(response)
    name = result.get("name", "")
    if not isinstance(name, str) or not name.startswith(f"projects/{PROJECT}/messages/"):
        raise RuntimeError("FCM response has no message identifier")
    return name


def main():
    path = os.environ.get("BROTHER_VERIFIED_DIR", "")
    if not path:
        raise SystemExit("Verified metadata directory missing")
    root = Path(path)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    release = json.loads((root / "release.json").read_text(encoding="utf-8"))
    commercial = json.loads((root / "commercial_run.json").read_text(encoding="utf-8"))
    ota = json.loads((root / "ota_run.json").read_text(encoding="utf-8"))
    assert_verified(manifest, release, commercial, ota)
    print("GitHub Release, APK digest, commercial run and OTA verified.")
    name = send(manifest, release, commercial, ota, os.environ.get("BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON", ""))
    (root / "fcm_receipt.json").write_text(json.dumps({
        "versionName": VERSION,
        "versionCode": CODE,
        "sha256": EXPECTED_SHA,
        "topic": TOPIC,
        "fcmMessage": name,
        "status": "accepted_by_fcm",
        "note": "FCM acceptance is not proof of delivery to all subscribers.",
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("FCM accepted a 1.11.6 topic notification. Individual delivery cannot be confirmed.")


if __name__ == "__main__":
    main()
