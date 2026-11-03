#!/usr/bin/env python3
import hashlib
import importlib.util
import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("natives", ROOT / "scripts/check-natives.py")
natives = importlib.util.module_from_spec(spec)
spec.loader.exec_module(natives)


class BuildContractsTest(unittest.TestCase):
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
        for name, count in (("build.yml", 2), ("release.yml", 1)):
            text = (ROOT / ".github/workflows" / name).read_text()
            self.assertEqual(count, text.count("run: python3 scripts/check-natives.py"))
            self.assertEqual(count, text.count("            app/libs/natives-sha256.json"))
            self.assertNotRegex(text, r"if:.*\n\s+run: python3 scripts/check-natives.py")

    def test_cache_corruption_or_manifest_substitution_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in natives.FILES:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            (root / "scripts").mkdir()
            pin = {"aarSha256": hashlib.sha256((root / natives.FILES[0]).read_bytes()).hexdigest()}
            (root / "scripts/native-versions.json").write_text(json.dumps(pin))
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
