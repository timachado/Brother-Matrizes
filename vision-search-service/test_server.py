import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image

import server


def photo(color: tuple[int, int, int]) -> bytes:
    image = Image.new("RGB", (128, 128), color=color)
    output = io.BytesIO()
    image.save(output, format="JPEG")
    return output.getvalue()


class FakeEncoder:
    def encode(self, image: Image.Image) -> list[float]:
        red, green, blue = image.getpixel((20, 20))
        vector = [0.0] * 512
        vector[0 if red > blue else 1] = 1.0
        return vector


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BM_VISION_DB", str(tmp_path / "index.db"))
    monkeypatch.setenv("BM_VISION_ADMIN_TOKEN", "safe-test-admin-key-that-is-long-enough-00001")
    server.app.state.vision_encoder = FakeEncoder()
    server.app.dependency_overrides[server.verified_user] = lambda: "fake-user"
    with TestClient(server.app) as client:
        yield client
    server.app.dependency_overrides.clear()
    del server.app.state.vision_encoder


def allowed(owner="Titular da matriz", approved="true"):
    return {
        "title": "Girassol bordado",
        "telegram_url": "https://t.me/canalmatrizes/124",
        "preview_url": "https://example.org/girassol.jpg",
        "matrix_format": "DST",
        "rights_holder": owner,
        "authorization_reference": "consentimento-expresso-2026-10-23",
        "authorized_for_visual_index": approved,
    }


def store(client: TestClient, data=None, color=(245, 120, 10), token="safe-test-admin-key-that-is-long-enough-00001"):
    return client.post(
        "/v1/catalog/items",
        data=data or allowed(),
        files={"image": ("photo.jpg", photo(color), "image/jpeg")},
        headers={"X-Admin-Token": token},
    )


def search(client, color=(245, 120, 10)):
    return client.post(
        "/v1/search/image",
        files={"image": ("input.jpg", photo(color), "image/jpeg")},
    )


def test_no_catalog_does_not_invent_matches(client):
    answer = search(client)
    assert answer.status_code == 200
    assert answer.json() == {"catalog_count": 0, "matches": []}


def test_true_visual_match_from_authorized_catalog(client):
    created = store(client)
    assert created.status_code == 200, created.text
    matches = search(client).json()
    assert matches["catalog_count"] == 1
    assert len(matches["matches"]) == 1
    assert matches["matches"][0]["title"] == "Girassol bordado"
    assert matches["matches"][0]["telegram_url"] == "https://t.me/canalmatrizes/124"
    assert matches["matches"][0]["format"] == "DST"


def test_different_photo_does_not_return_weak_match(client):
    assert store(client).status_code == 200
    answer = search(client, color=(10, 20, 245))
    assert answer.status_code == 200
    assert answer.json()["matches"] == []


def test_explicit_owner_consent_is_required(client):
    response = store(client, data=allowed(approved="false"))
    assert response.status_code == 403


def test_private_group_link_is_rejected(client):
    data = allowed()
    data["telegram_url"] = "https://t.me/c/123456/123"
    assert store(client, data=data).status_code == 422


def test_admin_token_and_revocation(client):
    assert store(client, token="wrong").status_code == 403
    created = store(client)
    item_id = created.json()["id"]
    response = client.delete(
        f"/v1/catalog/items/{item_id}",
        headers={"X-Admin-Token": "safe-test-admin-key-that-is-long-enough-00001"},
    )
    assert response.status_code == 200
    assert search(client).json()["matches"] == []


def test_bad_image_is_rejected(client):
    response = client.post(
        "/v1/search/image",
        files={"image": ("evil.jpg", b"not a jpg", "image/jpeg")},
    )
    assert response.status_code == 422
