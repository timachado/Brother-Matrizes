import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[1] / "scripts/send_verified_1116_fcm.py"
spec = importlib.util.spec_from_file_location("fcm1116", SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class TestFcm1116Guards(unittest.TestCase):
    def setUp(self):
        self.release = {"tag_name": mod.TAG, "draft": False, "prerelease": False,
            "published_at": "2026-10-31T23:13:17Z", "assets": [{"name":mod.APK_NAME,
              "state":"uploaded", "digest":"sha256:"+mod.EXPECTED_SHA,
              "size":13817946, "browser_download_url":mod.APK_URL}]}
        self.manifest = {"applicationId":mod.PACKAGE,"channel":"stable","versionName":mod.VERSION,
            "versionCode":mod.CODE,"sha256":mod.EXPECTED_SHA,"downloadUrl":mod.APK_URL,
            "downloadApkUrl":mod.APK_URL,"publishedAt":self.release["published_at"]}
        self.run = {"id":41251177886,"head_sha":mod.RELEASE_HEAD,"status":"completed","conclusion":"success"}
        self.ota = {"id":41251599002,"status":"completed","conclusion":"success"}
    def verify(self):
        return mod.assert_verified(self.manifest,self.release,self.run,self.ota)
    def test_valid_state(self): self.assertTrue(self.verify())
    def test_wrong_version(self):
        self.manifest["versionCode"] = 192
        with self.assertRaises(AssertionError): self.verify()
    def test_wrong_hash(self):
        self.release["assets"][0]["digest"] = "sha256:" + "a"*64
        with self.assertRaises(AssertionError): self.verify()
    def test_missing_ota_success(self):
        self.ota["conclusion"] = "failure"
        with self.assertRaises(AssertionError): self.verify()
    def test_wrong_release_target(self):
        self.release["assets"][0]["browser_download_url"] = "https://example.com/fake.apk"
        with self.assertRaises(AssertionError): self.verify()
    def test_wrong_commercial_commit(self):
        self.run["head_sha"] = "deadbeef"
        with self.assertRaises(AssertionError): self.verify()
if __name__=="__main__":unittest.main()
