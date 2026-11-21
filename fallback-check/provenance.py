"""Assemble a compact report only after every focused hosted check has passed."""
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess
import sys

assert os.environ.get("GITHUB_ACTIONS") == "true"
candidate, baseline = map(Path, sys.argv[1:3])
markers = {
    "build/dusk-check/fresh-forward.log": "DUSK FRESH PASS order=forward",
    "build/dusk-check/fresh-reverse.log": "DUSK FRESH PASS order=reverse",
    "build/dusk-check/fresh-restart.log": "DUSK FRESH RESTART PASS",
    "build/dusk-check/seed-legacy.log": "DUSK LEGACY SEEDED",
    "build/dusk-check/legacy.log": "DUSK LEGACY PASS",
    "build/dusk-check/legacy-restart.log": "DUSK LEGACY RESTART PASS",
    "build/shrine-check/shoulder.log": "PETAL SHRINE SERVER PASS order=shoulder",
    "build/shrine-check/village.log": "PETAL SHRINE SERVER PASS order=village",
    "build/waterfall-check/server.log": "HEARTWATER WATERFALL PASS",
    "build/cave-client.log": "CAVE FLORA PALETTE PASS",
}
evidence = {}
for name, marker in markers.items():
    log = Path(name).read_text(errors="replace")
    assert marker in log, f"Missing success evidence: {marker}"
    assert "CHECK FAILED" not in log and "OutOfMemoryError" not in log, name
    evidence[name] = [line for line in log.splitlines() if " PASS" in line or "DUSK FRESH PLAN " in line
                      or "DUSK LEGACY SEEDED" in line]
cave_log = Path("build/cave-client.log").read_text(errors="replace")
for marker in ("JADE REACH FLUID CAPTIONS PASS", "JADE REACH BERRY CAPTIONS PASS",
               "CAVE VINE MODELS AND HARVEST PASS"):
    assert marker in cave_log, marker
report = {
    "source_sha": os.environ["GITHUB_SHA"],
    "baseline_source_sha": "e16af6a2a1a00cb68291b6c9611d4604a0b20171",
    "jar_filename": candidate.name,
    "jar_sha256": sha256(candidate.read_bytes()).hexdigest(),
    "baseline_jar_sha256": sha256(baseline.read_bytes()).hexdigest(),
    "workflow_run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "validation_environment": "Public GitHub-hosted ubuntu-24.04, Java 21, NeoForge 21.1.249",
    "evidence": evidence,
    "limitations": ["Dot reference screenshots and marked coordinates were not accessible in this conversation.",
                    "Automated fixtures use disposable CI worlds; no existing user world or server was accessed.",
                    "The cave-only route does not run bow or book checks."],
}
folder = Path("build/fallback-check")
folder.mkdir(parents=True, exist_ok=True)
(folder / "provenance.json").write_text(json.dumps(report, indent=2) + "\n")
patch = subprocess.check_output(["git", "diff", "--binary", report["baseline_source_sha"], "HEAD"])
(folder / "caves-dusk.patch").write_bytes(patch)
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write("## Cave and Dusk fallback validation\n\n")
    summary.write(f"Source: `{report['source_sha']}`\n\n")
    summary.write(f"Baseline: `{report['baseline_source_sha']}`\n\n")
    summary.write(f"JAR SHA-256: `{report['jar_sha256']}`\n\n")
    for lines in evidence.values():
        for line in lines:
            summary.write(f"- {line}\n")
print("CAVES DUSK PROVENANCE PASS")
