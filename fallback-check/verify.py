"""Validate the candidate against Playtest 9, only on the hosted CI runner."""
from argparse import ArgumentParser
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

BASELINE = "e16af6a2a1a00cb68291b6c9611d4604a0b20171"
JAVA = "src/main/java/"
RESOURCES = "src/main/resources/"
CHECK_PREFIXES = ("fallback-check/", "dusk-check/", "cave-flora-check/")
CHECK_FILES = {
    ".github/workflows/fallback-caves-dusk.yml",
    "lotus-client-check/client.gradle",
    "lotus-client-check/run.sh",
    "lotus-client-check/java/dev/uwu/lotusclientcheck/LotusClientProbe.java",
    "lotus-client-check/java/dev/uwu/lotusclientcheck/JadeFluidProbe.java",
    "lotus-client-check/java/dev/uwu/lotusclientcheck/CaveFloraProbe.java",
    "shrine-check/run-server.sh",
}


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def verify(candidate, baseline, version, classes):
    assert os.environ.get("GITHUB_ACTIONS") == "true", "Run checks on public GitHub Actions only"
    head = git("rev-parse", "HEAD")
    assert head == os.environ["GITHUB_SHA"], "Runner is not checking its advertised source SHA"
    git("diff", "--exit-code", "HEAD", "--", "src/main/java", "src/main/resources", "build.gradle")
    assert not git("ls-files", "--others", "--exclude-standard", "--", "src/main/java", "src/main/resources"), (
        "Untracked production source appeared during validation"
    )
    scope = json.loads(Path("fallback-check/scope.json").read_text())
    assert scope["baseline"] == BASELINE
    allowed = set(scope["production_files"])
    changed = set(git("diff", "--name-only", BASELINE, "HEAD").splitlines())
    unexpected = sorted(p for p in changed if p not in allowed
                        and p not in CHECK_FILES and not p.startswith(CHECK_PREFIXES))
    assert not unexpected, f"Files outside this workstream changed: {unexpected}"
    changed_resources = {p[len(RESOURCES):] for p in changed if p.startswith(RESOURCES)}
    changed_classes = {p[len(JAVA):].removesuffix(".java") for p in changed if p.startswith(JAVA)}

    def allowed_entry(name):
        return name in changed_resources or (
            name.endswith(".class") and name.removesuffix(".class").split("$", 1)[0] in changed_classes
        )

    with ZipFile(candidate) as new, ZipFile(baseline) as old:
        assert new.testzip() is None and old.testzip() is None, "Corrupt JAR"
        new_entries = [e.filename for e in new.infolist() if not e.is_dir()]
        old_entries = [e.filename for e in old.infolist() if not e.is_dir()]
        assert len(new_entries) == len(set(new_entries)), "Duplicate candidate JAR entries"
        assert len(old_entries) == len(set(old_entries)), "Duplicate baseline JAR entries"
        assert f'version="{version}"'.encode() in new.read("META-INF/neoforge.mods.toml")
        assert b'version="1.5.0-beta.13-combined-rc.2-playtest.9"' in old.read("META-INF/neoforge.mods.toml")
        preserved = 0
        altered = []
        for name in sorted(set(new_entries) | set(old_entries)):
            same = name in new_entries and name in old_entries and new.read(name) == old.read(name)
            if not same:
                assert allowed_entry(name), f"Packaged content outside cave/Dusk scope changed: {name}"
                altered.append(name)
            elif not allowed_entry(name):
                preserved += 1
        for path in Path(RESOURCES).rglob("*"):
            if path.is_file():
                name = path.relative_to(RESOURCES).as_posix()
                assert new.read(name) == path.read_bytes(), f"Resource differs from source: {name}"
        if classes:
            for name in new_entries:
                if name.endswith(".class") and name.startswith("dev/uwu/dimension/"):
                    assert new.read(name) == (classes / name).read_bytes(), f"Stale compiled class: {name}"
        for source in changed_classes:
            if Path(JAVA, source + ".java").exists():
                assert source + ".class" in new_entries, f"New code missing from JAR: {source}"
    evidence = Path("build/fallback-check")
    evidence.mkdir(parents=True, exist_ok=True)
    report = {
        "baseline_source_sha": BASELINE,
        "candidate_source_sha": head,
        "candidate_jar_sha256": sha256(candidate.read_bytes()).hexdigest(),
        "baseline_jar_sha256": sha256(baseline.read_bytes()).hexdigest(),
        "preserved_jar_entries_outside_scope": preserved,
        "changed_source_files": sorted(changed),
        "changed_jar_entries": altered,
    }
    (evidence / "scope.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"CAVES DUSK SCOPE PASS {preserved} unchanged packaged entries outside the explicit edit scope")
    print(f"CAVES DUSK SOURCE {head}")
    print(f"CAVES DUSK JAR SHA256 {report['candidate_jar_sha256']}")


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("candidate", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("version")
    parser.add_argument("--classes", type=Path)
    args = parser.parse_args()
    verify(args.candidate, args.baseline, args.version, args.classes)
