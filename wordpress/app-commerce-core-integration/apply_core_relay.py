#!/usr/bin/env python3
"""Apply optional Brother Matrizes Cloudflare relay to an ORIGINAL App Commerce Core 2.6.9-rc1 ZIP.

Refuses unverified versions, unknown plugin layouts, unsafe archives, and already-patched ZIPs.
No WordPress, Cloudflare or network calls. Never modifies input ZIP in place.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
from zipfile import ZipFile, ZipInfo, BadZipFile

EXPECTED_VERSION = "2.6.9-rc1"
MAIN_NAME = "ti-machado-app-commerce.php"
MODULE_REL = "includes/brother-matrizes/bm-cloudflare-catalog-relay.php"
MARKER = "BM_CLOUDFLARE_CATALOG_RELAY_LOADER_2026"
MAX_ENTRIES = 5000
MAX_UNCOMPRESSED = 150 * 1024 * 1024

LOADER = r"""
/* BM_CLOUDFLARE_CATALOG_RELAY_LOADER_2026
 * Opt-in-only Brother Matrizes catalog relay.
 * The new module checks both BM_CATALOG_SYNC_SECRET and
 * BM_CATALOG_SYNC_ENABLED === true; otherwise it does nothing.
 */
if (defined('ABSPATH') && function_exists('add_action')) {
    add_action('plugins_loaded', static function (): void {
        $bm_catalog_relay = __DIR__ . '/includes/brother-matrizes/bm-cloudflare-catalog-relay.php';
        if (is_readable($bm_catalog_relay)) {
            require_once $bm_catalog_relay;
        }
    }, PHP_INT_MAX);
}

"""

class UnsafeCoreZip(ValueError):
    pass


def _validated_entries(archive: ZipFile):
    entries = archive.infolist()
    if len(entries) > MAX_ENTRIES:
        raise UnsafeCoreZip("Archive has too many entries")
    if sum(info.file_size for info in entries) > MAX_UNCOMPRESSED:
        raise UnsafeCoreZip("Archive exceeds uncompressed size limit")
    seen = set()
    roots = set()
    for info in entries:
        name = info.filename
        p = PurePosixPath(name)
        if (not name or name.startswith("/") or "\\" in name or "\0" in name
            or any(piece in ("", ".", "..") for piece in name.rstrip("/").split("/"))):
            raise UnsafeCoreZip("Unsafe archive path")
        if (info.external_attr >> 16) & 0o170000 == stat.S_IFLNK:
            raise UnsafeCoreZip("Symlinks are not allowed in source plugin ZIP")
        if name in seen:
            raise UnsafeCoreZip("Duplicate path in plugin ZIP")
        seen.add(name)
        roots.add(p.parts[0])
    if len(roots) != 1:
        raise UnsafeCoreZip("Expected a single WordPress plugin root directory")
    root = next(iter(roots))
    if root.lower().endswith(".php") or root.lower().endswith(".zip"):
        raise UnsafeCoreZip("Plugin ZIP needs a containing root folder")
    return entries, root


def _main_header(content: str):
    if not content.lstrip().startswith("<?php"):
        raise UnsafeCoreZip("Main plugin file is not PHP")
    for match in re.finditer(r"/\*[\s\S]*?\*/", content[:12000]):
        text = match.group(0)
        plugin = re.search(r"(?im)^\s*\*?\s*Plugin Name:\s*([^\r\n*]+)", text)
        version = re.search(r"(?im)^\s*\*?\s*Version:\s*([^\s\r\n*]+)", text)
        if plugin and version:
            name = plugin.group(1).strip()
            if "app commerce" not in name.lower():
                raise UnsafeCoreZip("Plugin Name does not identify App Commerce Core")
            if version.group(1).strip() != EXPECTED_VERSION:
                raise UnsafeCoreZip(
                    f"Expected App Commerce Core {EXPECTED_VERSION}; found {version.group(1)}"
                )
            return match.end()
    raise UnsafeCoreZip("Could not verify plugin name and version from main PHP header")


def patch(original: Path, output: Path, module: Path):
    if not original.is_file() or not module.is_file():
        raise UnsafeCoreZip("Original ZIP or relay PHP source is missing")
    if original.resolve() == output.resolve():
        raise UnsafeCoreZip("Output must be different from original input")
    module_bytes = module.read_bytes()
    if (b"BM_CATALOG_SYNC_SECRET" not in module_bytes
        or b"defined('BM_CATALOG_SYNC_ENABLED')" not in module_bytes
        or b"BM_CATALOG_SYNC_ENABLED === true" not in module_bytes):
        raise UnsafeCoreZip("Module must be disabled by default and HMAC gated")
    try:
        with ZipFile(original, "r") as source:
            entries, root = _validated_entries(source)
            main_path = root + "/" + MAIN_NAME
            target = root + "/" + MODULE_REL
            index = {info.filename: info for info in entries}
            if main_path not in index:
                raise UnsafeCoreZip("Expected App Commerce Core main file is missing")
            if target in index:
                raise UnsafeCoreZip("Catalog relay is already present; refusing double integration")
            if not any(name.startswith(root + "/includes/brother-matrizes/") for name in index):
                raise UnsafeCoreZip("Original ZIP does not include the Brother Matrizes Core module")
            raw = source.read(main_path)
            try:
                main = raw.decode("utf-8")
            except UnicodeDecodeError:
                raise UnsafeCoreZip("Main plugin PHP is not UTF-8")
            if MARKER in main:
                raise UnsafeCoreZip("Core ZIP is already patched")
            offset = _main_header(main)
            updated = (main[:offset] + "\n" + LOADER + main[offset:]).encode("utf-8")
            output.parent.mkdir(parents=True, exist_ok=True)
            fd, temp_path = tempfile.mkstemp(prefix=".bm-core-", suffix=".zip", dir=output.parent)
            os.close(fd)
            try:
                with ZipFile(temp_path, "w") as dest:
                    for item in entries:
                        data = updated if item.filename == main_path else source.read(item)
                        dest.writestr(item, data)
                    new = ZipInfo(filename=target)
                    new.compress_type = 8  # deflate
                    new.external_attr = (0o100644 << 16)
                    dest.writestr(new, module_bytes)
                with ZipFile(temp_path) as check:
                    if check.testzip() is not None:
                        raise UnsafeCoreZip("Output ZIP integrity check failed")
                    for item in entries:
                        if item.filename != main_path and hashlib.sha256(
                            source.read(item)
                        ).digest() != hashlib.sha256(check.read(item.filename)).digest():
                            raise UnsafeCoreZip("Original plugin file unexpectedly changed")
                    if check.read(target) != module_bytes:
                        raise UnsafeCoreZip("Embedded module source mismatch")
                    if MARKER.encode() not in check.read(main_path):
                        raise UnsafeCoreZip("Relay bootstrap not injected")
                os.replace(temp_path, output)
            finally:
                if os.path.exists(temp_path):
                    os.unlink(temp_path)
    except BadZipFile as e:
        raise UnsafeCoreZip("Invalid WordPress plugin ZIP") from e
    return output


def cli():
    arg = argparse.ArgumentParser(description=__doc__)
    arg.add_argument("original_zip", type=Path)
    arg.add_argument("output_zip", type=Path)
    arg.add_argument("--module", type=Path,
                     default=Path(__file__).with_name("bm-cloudflare-catalog-relay.php"))
    args = arg.parse_args()
    try:
        saved = patch(args.original_zip, args.output_zip, args.module)
    except UnsafeCoreZip as e:
        arg.exit(2, f"STOPPED SAFELY: {e}\n")
    print(f"STAGING ZIP CREATED: {saved}")
    print("This ZIP has NOT been installed. Backup and staging QA required.")


if __name__ == "__main__":
    cli()
