#!/usr/bin/env python3
"""Generate Unsupported block mass and strength resources from one master CSV."""

from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = ROOT / "data/master_blocks.csv"
DEFAULT_WEIGHTS = ROOT / "src/main/resources/data/unsupported/block_weights.json"
DEFAULT_STRENGTHS = ROOT / "src/main/resources/data/unsupported/block_strengths.json"
DEFAULT_REGISTRY = ROOT / "data/vanilla_blocks_26.2.txt"
SAFETY_FACTOR = Decimal("100")
KG_PER_MPA = Decimal("101971")
MASS_SCALE = Decimal("1")

IGNORED_IDS = frozenset(
    {
        "minecraft:air",
        "minecraft:barrier",
        "minecraft:bubble_column",
        "minecraft:cave_air",
        "minecraft:end_gateway",
        "minecraft:end_portal",
        "minecraft:fire",
        "minecraft:frosted_ice",
        "minecraft:jigsaw",
        "minecraft:lava",
        "minecraft:light",
        "minecraft:moving_piston",
        "minecraft:nether_portal",
        "minecraft:piston_head",
        "minecraft:redstone_wire",
        "minecraft:soul_fire",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:test_block",
        "minecraft:test_instance_block",
        "minecraft:tripwire",
        "minecraft:void_air",
        "minecraft:water",
    }
)


def rounded_decimal(value: Decimal, places: str) -> float:
    return float(value.quantize(Decimal(places), rounding=ROUND_HALF_UP))


def capacity(strength_mpa: str) -> float:
    value = Decimal(strength_mpa) * KG_PER_MPA / SAFETY_FACTOR
    return float(value.quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def max_cantilever(mass: float, shear: float) -> int:
    if mass <= 0 or shear <= 0:
        return 0
    return max(0, math.floor((math.sqrt(1 + 8 * shear / mass) - 1) / 2))


def max_arch_span(mass: float, carry: float) -> int:
    if mass <= 0 or carry <= 0:
        return 0
    return math.floor(math.sqrt(8 * carry / mass))


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    ids = [row["id"] for row in rows]
    if len(ids) != len(set(ids)):
        raise ValueError("master CSV contains duplicate block ids")
    return rows


def generate(path: Path = DEFAULT_INPUT) -> tuple[dict[str, float], dict[str, dict[str, float | int]]]:
    weights: dict[str, float] = {}
    strengths: dict[str, dict[str, float | int]] = {}
    for row in read_rows(path):
        block_id = row["id"]
        mass = rounded_decimal(
            Decimal(row["density_kg_m3"]) * Decimal(row["volume_fraction"]) * MASS_SCALE,
            "0.1",
        )
        carry = capacity(row["C_MPa"])
        shear = capacity(row["S_MPa"])
        tension = capacity(row["T_MPa"])
        weights[block_id] = mass
        strengths[block_id] = {
            "C": carry,
            "S": shear,
            "T": tension,
            "m": mass,
            "maxArchSpan": max_arch_span(mass, carry),
            "maxCantilever": max_cantilever(mass, shear),
        }
    return weights, strengths


def rendered(data: object) -> str:
    return json.dumps(data, indent=2, sort_keys=True) + "\n"


def audit_registry(
    rows: list[dict[str, str]], registry_path: Path = DEFAULT_REGISTRY
) -> tuple[list[str], list[str]]:
    registry_ids = {
        line.strip()
        for line in registry_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }
    csv_ids = {row["id"] for row in rows}
    missing = sorted(registry_ids - csv_ids)
    extra = sorted(csv_ids - registry_ids)
    return missing, extra


def check_file(path: Path, expected: str) -> bool:
    if not path.exists() or path.read_text(encoding="utf-8") != expected:
        print(f"{path.name} is stale; run tools/compute_strengths.py", file=sys.stderr)
        return False
    return True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--weights-output", type=Path, default=DEFAULT_WEIGHTS)
    parser.add_argument("--strengths-output", type=Path, default=DEFAULT_STRENGTHS)
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--audit", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    weights, strengths = generate(args.input)
    weights_text = rendered(weights)
    strengths_text = rendered(strengths)

    ok = True
    if args.check:
        ok &= check_file(args.weights_output, weights_text)
        ok &= check_file(args.strengths_output, strengths_text)
    else:
        args.weights_output.write_text(weights_text, encoding="utf-8")
        args.strengths_output.write_text(strengths_text, encoding="utf-8")

    if args.audit:
        rows = read_rows(args.input)
        missing, extra = audit_registry(rows, args.registry)
        if missing:
            ok = False
            print("Missing registry ids:", file=sys.stderr)
            print("\n".join(missing), file=sys.stderr)
        else:
            print("Registry audit: zero missing ids")
        if extra:
            print(f"CSV-only compatibility rows: {len(extra)}")
            print("\n".join(extra))

    if args.check and ok:
        print(f"Generated resources match {args.input.relative_to(ROOT)}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
