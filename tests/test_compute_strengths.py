import csv
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "compute_strengths.py"
MASTER = ROOT / "data" / "master_blocks.csv"
WEIGHTS = ROOT / "src/main/resources/data/unsupported/block_weights.json"
STRENGTHS = ROOT / "src/main/resources/data/unsupported/block_strengths.json"
REGISTRY = ROOT / "data" / "vanilla_blocks_26.2.txt"


def load_generator():
    spec = importlib.util.spec_from_file_location("compute_strengths", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load {SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ComputeStrengthsTest(unittest.TestCase):
    def test_master_csv_generates_committed_resources(self):
        generator = load_generator()

        weights, strengths = generator.generate(MASTER)

        self.assertEqual(weights, json.loads(WEIGHTS.read_text()))
        self.assertEqual(strengths, json.loads(STRENGTHS.read_text()))
        self.assertEqual(set(weights), set(strengths))

    def test_check_rejects_stale_generated_file(self):
        with tempfile.TemporaryDirectory() as directory:
            stale_weights = Path(directory) / "block_weights.json"
            stale_strengths = Path(directory) / "block_strengths.json"
            stale_weights.write_text("{}\n")
            stale_strengths.write_text(STRENGTHS.read_text())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--check",
                    "--weights-output",
                    str(stale_weights),
                    "--strengths-output",
                    str(stale_strengths),
                ],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("block_weights.json is stale", result.stderr)

    def test_registry_audit_has_no_unexplained_missing_ids(self):
        generator = load_generator()
        with MASTER.open(newline="") as stream:
            rows = list(csv.DictReader(stream))

        missing, extra = generator.audit_registry(rows, REGISTRY)

        self.assertEqual(missing, [])
        self.assertIn("minecraft:chain", {row["id"] for row in rows})
        self.assertTrue(all(row["category"] == "ignored" for row in rows if row["id"] in generator.IGNORED_IDS))
        self.assertIsInstance(extra, list)


if __name__ == "__main__":
    unittest.main()
