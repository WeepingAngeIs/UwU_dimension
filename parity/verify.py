"""Verify the combined release against the exact contents of the three tested JARs."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
from zipfile import ZipFile


METADATA = "META-INF/neoforge.mods.toml"
VERSION = re.compile(rb'(?m)^version="([^"\r\n]+)"(?=\r?$)')


def metadata_identity(contents):
    fields = list(VERSION.finditer(contents))
    if len(fields) != 1:
        raise ValueError("Expected exactly one mod version field")
    start, end = fields[0].span(1)
    normalized = contents[:start] + b"@COMBINED_VERSION@" + contents[end:]
    return contents[start:end].decode("utf-8"), sha256(normalized).hexdigest()


def entry_digest(digests):
    combined = sha256()
    for name, digest in sorted(digests.items()):
        combined.update(name.encode("utf-8") + b"\0" + bytes.fromhex(digest))
    return combined.hexdigest()


def fingerprints(archive):
    entries = set()
    digests = {}
    for entry in archive.infolist():
        if entry.filename in entries:
            raise ValueError(f"Duplicate JAR entry: {entry.filename}")
        entries.add(entry.filename)
        if not entry.is_dir():
            digests[entry.filename] = sha256(archive.read(entry)).hexdigest()
    return digests


def verify(jar, version, class_root, reference):
    with ZipFile(jar) as archive:
        digests = fingerprints(archive)
        installed, metadata_digest = metadata_identity(archive.read(METADATA))
        if installed != version or metadata_digest != reference["metadata"]:
            raise ValueError("The metadata must match the tested JARs except for the specified version")
        digests.pop(METADATA)

        for name, feature in reference["features"].items():
            for entry, expected in feature["entries"].items():
                if digests.pop(entry, None) != expected:
                    raise ValueError(f"{name}: expected exact tested contents for {entry}")
            for entry in feature["removed"]:
                if entry in digests:
                    raise ValueError(f"{name}: obsolete entry still present: {entry}")
            print(f"{name} parity pass ({len(feature['entries'])} files, {len(feature['removed'])} removals)")

        baseline = reference["unchanged"]
        digest = entry_digest(digests)
        if digest != baseline["sha256"] or len(digests) != baseline["count"]:
            raise ValueError(f"Unexpected change in unmodified Grips entries ({len(digests)} files, {digest})")
        print(f"Unchanged Grips parity pass ({len(digests)} files)")

        if class_root is not None:
            classes = {path.relative_to(class_root).as_posix(): path for path in class_root.rglob("*.class")}
            jar_classes = {entry.filename for entry in archive.infolist() if entry.filename.endswith(".class")}
            if set(classes) != jar_classes:
                raise ValueError("Compiled class names do not match the combined JAR")
            for name, path in classes.items():
                if path.read_bytes() != archive.read(name):
                    raise ValueError(f"Compiled class differs from the combined JAR: {name}")
            print(f"Combined JAR matches compiled classes ({len(classes)} classes)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("version")
    parser.add_argument("--classes", type=Path)
    args = parser.parse_args()
    reference = json.loads(Path(__file__).with_name("reference.json").read_text(encoding="utf-8"))
    try:
        verify(args.jar, args.version, args.classes, reference)
    except (KeyError, OSError, ValueError) as failure:
        parser.exit(1, f"Combined parity failed: {failure}\n")
    print(f"COMBINED PARITY PASS sha256={sha256(args.jar.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
