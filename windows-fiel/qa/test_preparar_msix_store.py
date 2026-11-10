"""Offline tests for QA-only MSIX staging: never require a Store account."""
import base64
from io import BytesIO
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from PIL import Image
from preparar_msix_store import (
    stage_msix_qa, NS, UAP10, STORE_NAME, STORE_PUBLISHER,
    STORE_PUBLISHER_DISPLAY_NAME,
)


class StageMsixQaTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / "app-image" / "BrotherMatrizes"
        self.app.mkdir(parents=True)
        (self.app / "BrotherMatrizes.exe").write_bytes(b"MZ" + b"0" * 4096)
        for folder in ("app", "runtime"):
            (self.app / folder).mkdir()
            (self.app / folder / "fixture.dat").write_bytes(b"qa")
        image = Image.new("RGBA", (128, 128), (12, 40, 200, 255))
        out = BytesIO()
        image.save(out, format="WEBP")
        self.icon = self.root / "official.webp.base64"
        self.icon.write_text(base64.b64encode(out.getvalue()).decode("ascii"))
        self.stage = self.root / "stage"

    def test_manifest_visual_assets_and_java_runtime(self):
        stage_msix_qa(self.app, self.stage, self.icon)
        parsed = ET.parse(self.stage / "AppxManifest.xml").getroot()
        identity = parsed.find(f"{{{NS}}}Identity")
        self.assertEqual(identity.attrib["Name"], STORE_NAME)
        self.assertEqual(STORE_NAME, "T.I.MachadoSoluesemTecnol.BrotherMatrizes")
        self.assertEqual(identity.attrib["Publisher"], STORE_PUBLISHER)
        self.assertEqual(STORE_PUBLISHER, "CN=353309CB-0CCB-4E2D-8F88-AF631DAA395F")
        display = parsed.find(f"{{{NS}}}Properties/{{{NS}}}PublisherDisplayName")
        self.assertIsNotNone(display)
        self.assertEqual(display.text, STORE_PUBLISHER_DISPLAY_NAME)
        self.assertEqual(display.text, "T.I. Machado Soluções em Tecnologia")
        app = parsed.find(f"{{{NS}}}Applications/{{{NS}}}Application")
        self.assertEqual(app.attrib["Executable"], "BrotherMatrizes.exe")
        self.assertEqual(app.attrib[f"{{{UAP10}}}RuntimeBehavior"], "packagedClassicApp")
        self.assertTrue((self.stage / "runtime" / "fixture.dat").is_file())
        for name, size in (("Square44x44Logo.png", 44), ("Square150x150Logo.png", 150), ("StoreLogo.png", 50)):
            with Image.open(self.stage / "Assets" / name) as image:
                self.assertEqual(image.size, (size, size))

    def test_missing_runtime_rejected(self):
        (self.app / "runtime" / "fixture.dat").unlink()
        (self.app / "runtime").rmdir()
        with self.assertRaisesRegex(ValueError, "runtime"):
            stage_msix_qa(self.app, self.stage, self.icon)

    def test_existing_staging_rejected(self):
        self.stage.mkdir()
        with self.assertRaisesRegex(ValueError, "ja existe"):
            stage_msix_qa(self.app, self.stage, self.icon)

    def test_inside_source_path_rejected(self):
        with self.assertRaisesRegex(ValueError, "independentes"):
            stage_msix_qa(self.app, self.app / "new-stage", self.icon)

    def test_private_key_rejected(self):
        (self.app / "app" / "private.pfx").write_bytes(b"never distribute")
        with self.assertRaisesRegex(ValueError, "Chave privada"):
            stage_msix_qa(self.app, self.stage, self.icon)

    def test_invalid_icon_rejected(self):
        self.icon.write_text(base64.b64encode(b"not-webp").decode("ascii"))
        with self.assertRaisesRegex(ValueError, "WebP"):
            stage_msix_qa(self.app, self.stage, self.icon)


if __name__ == "__main__":
    unittest.main()
