"""Read the actual Anvil entity region after a clean server stop and before the next JVM starts."""
from pathlib import Path
import gzip
import io
import math
import struct
import sys
import uuid
import zlib


class Nbt:
    def __init__(self, raw):
        self.stream = io.BytesIO(raw)

    def unpack(self, fmt):
        return struct.unpack(fmt, self.stream.read(struct.calcsize(fmt)))[0]

    def text(self):
        return self.stream.read(self.unpack(">H")).decode("utf-8", errors="replace")

    def value(self, kind):
        numbers = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if kind in numbers:
            return self.unpack(numbers[kind])
        if kind == 7:
            return self.stream.read(self.unpack(">i"))
        if kind == 8:
            return self.text()
        if kind == 9:
            element = self.unpack(">b")
            return [self.value(element) for _ in range(self.unpack(">i"))]
        if kind == 10:
            result = {}
            while child := self.unpack(">b"):
                name = self.text()
                result[name] = self.value(child)
            return result
        if kind in (11, 12):
            return [self.unpack(">i" if kind == 11 else ">q") for _ in range(self.unpack(">i"))]
        raise ValueError(f"Unexpected NBT type {kind}")

    def root(self):
        assert self.unpack(">b") == 10
        self.text()
        return self.value(10)


def uuid_text(value):
    return str(uuid.UUID(bytes=struct.pack(">4i", *value)))


def sections(path):
    data = path.read_bytes()
    for slot in range(1024):
        sector = int.from_bytes(data[slot * 4:slot * 4 + 3], "big")
        if not sector:
            continue
        offset = sector * 4096
        length, compression = struct.unpack_from(">IB", data, offset)
        payload = data[offset + 5:offset + 4 + length]
        raw = {1: gzip.decompress, 2: zlib.decompress, 3: bytes}[compression](payload)
        yield Nbt(raw).root()


def main():
    region, ledger = map(Path, sys.argv[1:])
    entries = dict(line.split("=", 1) for line in ledger.read_text().splitlines() if line and not line.startswith("#"))
    members = {entries[k]: k for k in ("male", "girl", "son", "daughter")}
    seen = {}
    for section in sections(region):
        for entity in section["Entities"]:
            member = members.get(uuid_text(entity["UUID"]))
            if not member:
                continue
            assert member not in seen, f"Duplicate saved guardian {member}"
            seen[member] = entity
            family = entity["OwlFamily"]
            print(f"OWL GUARDIAN RAW ENTITY {member} {uuid_text(entity['UUID'])} chunk={section['Position']} "
                  f"pos={entity['Pos']} motion={entity['Motion']} health={entity['Health']} "
                  f"female={entity['FemaleGuardian']} family={family}", flush=True)
    assert len(seen) == 4, f"Expected all four guardians in {region}; found {list(seen)}"
    with ledger.open("a", encoding="ascii") as output:
        for name, entity in seen.items():
            family = entity["OwlFamily"]
            baby = name in ("son", "daughter")
            assert entity["id"] == "uwudimension:blossom_warden", entity["id"]
            assert entity["FemaleGuardian"] == (name in ("girl", "daughter"))
            assert entity["PersistenceRequired"] and [math.floor(entity["Pos"][i]) >> 4 for i in (0, 2)] == [487, 331]
            assert family.get("RestUntil", 0) == int(entries[name + ".cooldown"])
            if baby:
                assert 0 < family["GrowthTicks"] <= int(entries[name + ".remaining"])
                assert uuid_text(family["Mother"]) == entries["girl"] and uuid_text(family["Father"]) == entries["male"]
            maximum = 1999.8 if baby else 6666
            assert maximum * .55 - .02 <= entity["Health"] < maximum - 1, (name, entity["Health"])
            print(f"{name}.savedHealth={entity['Health']}", file=output)
            print(f"{name}.savedGrowth={family.get('GrowthTicks', 0)}", file=output)
    print("OWL GUARDIAN RAW DISK PASS four exact UUIDs, positions, ancestry, wounded health, age, sex and cooldown")


if __name__ == "__main__":
    main()

