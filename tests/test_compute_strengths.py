import csv
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "compute_strengths.py"
MASTER = ROOT / "data" / "master_blocks.csv"
WEIGHTS = ROOT / "src/main/resources/data/unsupported/block_weights.json"
STRENGTHS = ROOT / "src/main/resources/data/unsupported/block_strengths.json"
WEIGHTS_DOC = ROOT / "docs" / "BLOCK_WEIGHTS.md"
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

    def test_geometry_and_named_mass_invariants(self):
        generator = load_generator()
        rows = {row["id"]: row for row in generator.read_rows(MASTER)}
        weights, _ = generator.generate(MASTER)

        self.assertEqual(float(rows["minecraft:stone_slab"]["volume_fraction"]), 0.5)
        self.assertEqual(float(rows["minecraft:stone_stairs"]["volume_fraction"]), 0.75)
        self.assertLessEqual(float(rows["minecraft:glass_pane"]["volume_fraction"]), 0.0625)
        self.assertEqual(float(rows["minecraft:oak_fence"]["volume_fraction"]), 0.2)
        self.assertEqual(float(rows["minecraft:white_carpet"]["volume_fraction"]), 0.0625)
        self.assertEqual(float(rows["minecraft:dandelion"]["volume_fraction"]), 0.01)
        self.assertEqual(float(rows["minecraft:oak_door"]["volume_fraction"]), 0.1875)
        self.assertEqual(float(rows["minecraft:cobblestone_wall"]["volume_fraction"]), 0.25)
        self.assertEqual(float(rows["minecraft:chest"]["volume_fraction"]), 0.875)
        self.assertEqual(float(rows["minecraft:lantern"]["volume_fraction"]), 0.05)
        self.assertEqual(float(rows["minecraft:scaffolding"]["volume_fraction"]), 0.15)
        self.assertEqual(weights["minecraft:lantern"], 5.0)
        self.assertEqual(weights["minecraft:iron_bars"], 50.0)
        self.assertEqual(weights["minecraft:glass_pane"], 40.0)
        self.assertEqual(weights["minecraft:player_head"], 8.0)
        self.assertEqual(weights["minecraft:rail"], 60.0)
        self.assertEqual(weights["minecraft:flower_pot"], 3.0)
        self.assertEqual(weights["minecraft:heavy_core"], 15000.0)
        self.assertEqual(weights["minecraft:anvil"], 2754.5)

    def test_thin_blocks_stay_below_mass_limit_unless_documented(self):
        generator = load_generator()
        rows = generator.read_rows(MASTER)
        weights, _ = generator.generate(MASTER)

        violations = [
            row["id"]
            for row in rows
            if Decimal(row["volume_fraction"]) < Decimal("0.25")
            and weights[row["id"]] > 100
            and "SPECIAL" not in row["notes"]
        ]

        self.assertEqual(violations, [])

    def test_weights_document_is_generated_from_master_csv(self):
        generator = load_generator()
        rows = generator.read_rows(MASTER)
        weights, _ = generator.generate(MASTER)

        self.assertEqual(WEIGHTS_DOC.read_text(), generator.render_weights_doc(rows, weights))


if __name__ == "__main__":
    unittest.main()
