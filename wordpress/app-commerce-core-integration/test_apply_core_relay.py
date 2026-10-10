"""Fail-closed tests for patching the exact version 2.6.9-rc1 only."""
import sys, tempfile, unittest
from pathlib import Path
from zipfile import ZipFile
sys.path.insert(0, str(Path(__file__).parent))
from apply_core_relay import patch, UnsafeCoreZip, MAIN_NAME, MODULE_REL, MARKER

SOURCE = Path(__file__).with_name("bm-cloudflare-catalog-relay.php")

class PatchTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.dir=Path(self.tmp.name)
        self.source=self.dir/"original.zip"
        self.out=self.dir/"staging.zip"
    def tearDown(self):
        self.tmp.cleanup()
    def fixture(self, version="2.6.9-rc1", with_brother=True, extra={}):
        header=f"""<?php
/*
Plugin Name: T.I. Machado — App Commerce Core
Version: {version}
*/
defined('ABSPATH') || exit;
echo "original";
"""
        content={
            "ti-machado-app-commerce/"+MAIN_NAME:header.encode(),
            "ti-machado-app-commerce/includes/efi-bank/checkout.php":b"<?php // preserve Efí",
            "ti-machado-app-commerce/assets/frontend.js":b"existing UI",
            "ti-machado-app-commerce/includes/biblia-ebd/license.php":b"<?php // keep EBD",
        }
        if with_brother: content["ti-machado-app-commerce/includes/brother-matrizes/routes.php"]=b"<?php // keep commercial routes"
        content.update(extra)
        with ZipFile(self.source,"w") as z:
            for name,data in content.items(): z.writestr(name,data)
        return content
    def test_copies_existing_code_unchanged_except_bootstrap(self):
        original=self.fixture()
        patch(self.source,self.out,SOURCE)
        with ZipFile(self.out) as z:
            self.assertIsNone(z.testzip())
            for name,data in original.items():
                if name.endswith("/"+MAIN_NAME):
                    self.assertIn(MARKER.encode(),z.read(name))
                else: self.assertEqual(z.read(name),data)
            module=z.read("ti-machado-app-commerce/"+MODULE_REL)
            self.assertIn(b"defined('BM_CATALOG_SYNC_ENABLED')",module)
            self.assertIn(b"BM_CATALOG_SYNC_ENABLED === true",module)
    def test_refuses_other_version(self):
        self.fixture("2.6.8")
        with self.assertRaises(UnsafeCoreZip): patch(self.source,self.out,SOURCE)
        self.assertFalse(self.out.exists())
    def test_refuses_future_version(self):
        self.fixture("2.7.0")
        with self.assertRaises(UnsafeCoreZip): patch(self.source,self.out,SOURCE)
    def test_refuses_plugin_without_brother(self):
        self.fixture(with_brother=False)
        with self.assertRaises(UnsafeCoreZip): patch(self.source,self.out,SOURCE)
    def test_refuses_path_traversal(self):
        self.fixture(extra={"../traversal.php":b"bad"})
        with self.assertRaises(UnsafeCoreZip): patch(self.source,self.out,SOURCE)
    def test_refuses_double_install(self):
        self.fixture()
        patch(self.source,self.out,SOURCE)
        with self.assertRaises(UnsafeCoreZip):
            patch(self.out,self.dir/"again.zip",SOURCE)
    def test_source_unchanged(self):
        self.fixture()
        before=self.source.read_bytes()
        patch(self.source,self.out,SOURCE)
        self.assertEqual(self.source.read_bytes(),before)

if __name__=="__main__":
    unittest.main()
