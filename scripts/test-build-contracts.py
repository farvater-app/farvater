import hashlib
import importlib.util
import json
import re
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("natives", ROOT / "scripts/check-natives.py")
natives = importlib.util.module_from_spec(spec)
spec.loader.exec_module(natives)


class BuildContractsTest(unittest.TestCase):
    def test_hev_source_and_recursive_dependencies_must_match_clean_pins(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, module = root / "source", root / "module"
            def git(path, *args):
                return subprocess.check_output(["git", "-C", str(path),
                    "-c", "user.name=Fixture", "-c", "user.email=fixture@example.test",
                    "-c", "protocol.file.allow=always", *args], text=True, stderr=subprocess.DEVNULL).strip()
            for path in (source, module):
                path.mkdir()
                git(path, "init", "-q")
                (path / "code.c").write_text("synthetic source\n")
                git(path, "add", "code.c")
                git(path, "commit", "-qm", "Fixture")
            git(source, "submodule", "add", str(module), "third-part/library")
            git(source, "commit", "-qam", "Dependency")
            (root / "scripts").mkdir()
            pin = {"hevCommit": git(source, "rev-parse", "HEAD"),
                "hevSubmodules": {"third-part/library": git(module, "rev-parse", "HEAD")}}
            (root / "scripts/native-versions.json").write_text(json.dumps(pin))
            natives.verify_hev_source(source, root)
            for path in (source / "code.c", source / "third-part/library/code.c"):
                original = path.read_text()
                path.write_text("modified source\n")
                with self.assertRaises(ValueError):
                    natives.verify_hev_source(source, root)
                path.write_text(original)
            unexpected = source / "unexpected.c"
            unexpected.write_text("additional build input\n")
            with self.assertRaises(ValueError):
                natives.verify_hev_source(source, root)
            unexpected.unlink()
            dependency = source / "third-part/library"
            (dependency / "code.c").write_text("new dependency revision\n")
            git(dependency, "commit", "-qam", "Changed dependency")
            git(source, "commit", "-qam", "Changed root")
            pin["hevCommit"] = git(source, "rev-parse", "HEAD")
            (root / "scripts/native-versions.json").write_text(json.dumps(pin))
            with self.assertRaises(ValueError):
                natives.verify_hev_source(source, root)

    def native_fixture(self, root, missing=None, wrong=None):
        module = "v1.0.0-0.20260101000000-0123456789ab"
        for name in natives.FILES:
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(name.encode())
        aar = root / natives.FILES[0]
        with zipfile.ZipFile(aar, "w") as archive:
            for abi in natives.ABIS:
                if abi == missing:
                    continue
                version = "v0.0.0" if abi == wrong else module
                archive.writestr(f"jni/{abi}/libgojni.so",
                    b"\x7fELF" + f"dep\tgithub.com/xtls/xray-core\t{version}\th1:synthetic\n".encode())
        (root / "scripts").mkdir(exist_ok=True)
        pin = {"aarSha256": hashlib.sha256(aar.read_bytes()).hexdigest(), "xrayModuleVersion": module}
        (root / "scripts/native-versions.json").write_text(json.dumps(pin))

    def test_android_arm64_requires_16_kib_load_segments(self):
        import struct
        from android_elf import verify_elf

        library = bytearray(120)
        library[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<H", library, 18, 183)
        struct.pack_into("<Q", library, 32, 64)
        struct.pack_into("<HH", library, 54, 56, 1)
        struct.pack_into("<I", library, 64, 1)
        struct.pack_into("<Q", library, 112, 16384)
        verify_elf(library, "arm64-v8a")
        with self.assertRaises(ValueError):
            verify_elf(library, "x86_64")
        struct.pack_into("<Q", library, 112, 4096)
        with self.assertRaises(ValueError):
            verify_elf(library, "arm64-v8a")

    def test_correct_hash_with_missing_abi_is_rejected(self):
        for abi in natives.ABIS:
            with self.subTest(abi=abi), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.native_fixture(root, missing=abi)
                with self.assertRaises(ValueError):
                    natives.verify_aar(root)

    def test_core_revision_is_verified_for_every_abi(self):
        for abi in natives.ABIS:
            with self.subTest(abi=abi), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.native_fixture(root, wrong=abi)
                with self.assertRaises(ValueError):
                    natives.verify_aar(root)

    def test_actions_are_pinned_and_jobs_have_explicit_permissions(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            text = path.read_text()
            self.assertIn("permissions: {}", text, path.name)
            for action in re.findall(r"uses:\s*(\S+)", text):
                self.assertRegex(action, r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$", path.name)
            jobs = text.split("\njobs:\n", 1)[1]
            for job in re.split(r"\n(?=  [\w-]+:\n)", jobs):
                self.assertRegex(job, r"\n    permissions:\n      contents: (read|write)\n", path.name)

    def test_release_version_is_validated_as_data(self):
        text = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertIn('"-PfarvaterVersion=$TAG"', text)
        self.assertNotIn("-PfarvaterVersion=${{", text)
        self.assertIn("TAG: ${{ needs.check.outputs.tag }}", text)
        lines = text.splitlines()
        start = next(i for i, line in enumerate(lines) if 'TAG="${GITHUB_REF_NAME##*/}"' in line)
        script = "\n".join(line.strip() for line in lines[start:start + 2])
        for name in ("v1.0.31", "release/v1.0.31", "test/v1.0.31-beta5"):
            self.assertEqual(0, subprocess.run(["bash", "-c", script], env={"GITHUB_REF_NAME": name}).returncode)
        for name in ("v01.0.31", "v1.0", "v1.0.31-beta0", "v1.0.31;true", "v1.0.31$(true)", "v1.0.31\nINJECTED=yes"):
            self.assertNotEqual(0, subprocess.run(["bash", "-c", script], env={"GITHUB_REF_NAME": name}).returncode)

    def test_cache_always_verifies_digests(self):
        development = (ROOT / ".github/workflows/build.yml").read_text()
        self.assertEqual(2, development.count("run: python3 scripts/check-natives.py"))
        self.assertEqual(2, development.count("            app/libs/natives-sha256.json"))
        self.assertNotRegex(development, r"if:.*\n\s+run: python3 scripts/check-natives.py")

        signed = (ROOT / ".github/workflows/release.yml").read_text()
        self.assertNotIn("actions/cache@", signed)
        self.assertNotIn("cache-hit", signed)
        self.assertIn("cache-disabled: true", signed)
        self.assertIn("gradle --no-build-cache", signed)
        self.assertIn("run: ./scripts/fetch-natives.sh", signed)
        self.assertIn("run: python3 scripts/check-natives.py", signed)
        self.assertIn('[[ "$signer" == "$expected_cert" ]]', signed)

    def test_android_signer_formats_check_the_same_single_certificate(self):
        lines = (ROOT / ".github/workflows/release.yml").read_text().splitlines()
        start = next(i for i, line in enumerate(lines) if "'Number of signers: 1'" in line)
        script = "\n".join(line.strip() for line in lines[start:start + 3])
        digest = "ab" * 32
        for label in ("Signer #1", "V3.0 Signer:"):
            for count, actual, expected_status in ((1, digest, 0), (1, "00" * 32, 1), (2, digest, 1)):
                schemes = f"Number of signers: {count}\n{label} certificate SHA-256 digest: {actual}"
                status = subprocess.run(["bash", "-c", script],
                    env={"expected_cert": digest, "schemes": schemes}, stdout=subprocess.DEVNULL).returncode
                self.assertEqual(expected_status, status)

    def test_cache_corruption_or_manifest_substitution_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.native_fixture(root)
            natives.verify(root, record=True)
            natives.verify(root)
            for name in natives.FILES:
                path = root / name
                data = path.read_bytes()
                path.write_bytes(data + b"changed")
                with self.assertRaises(ValueError):
                    natives.verify(root)
                path.write_bytes(data)
            aar = root / natives.FILES[0]
            original = aar.read_bytes()
            manifest = root / "app/libs/natives-sha256.json"
            aar.write_bytes(b"old AAR with matching old manifest")
            stale = json.loads(manifest.read_text())
            stale[natives.FILES[0]] = hashlib.sha256(aar.read_bytes()).hexdigest()
            manifest.write_text(json.dumps(stale))
            with self.assertRaises(ValueError):
                natives.verify(root)
            with self.assertRaises(ValueError):
                natives.verify(root, record=True)
            aar.write_bytes(original)
            natives.verify(root, record=True)
            manifest.write_text(json.dumps({"../../outside": "00" * 32}))
            with self.assertRaises(ValueError):
                natives.verify(root)


if __name__ == "__main__":
    unittest.main()
