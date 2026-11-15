"""Check release resources directly in the distributable jar before it is uploaded."""
import hashlib
import json
import struct
import sys
import tomllib
import zlib
from zipfile import ZipFile


# Pinned to the approved 64x64 artwork in commit 2a369be. Last digest is a
# row-major mask (one byte, 0/1, per pixel) of the exposed bowstring alone.
BOW_FRAMES = {
    "cupid_bow": (
        "ab89b84f53f27de85f29f56fe9ba34db7902912a7889634c5d2ee7afc1e62005",
        "1df148f5f413c3e87f5cb32882d7e8ccfa7011d0ec205ea25759f87275877685",
        "3752de98342db759ea9cb452c91c0b2ad62e106b979fababe33322aaf5b54e8e"),
    "cupid_bow_draw0": (
        "6d40e521b36278f49b1454ed088991f82ce43e7a10bd390791705633daacc470",
        "40fa5b47062379995c3f2520f6678a39bdfd5e7ffcea288006cbfa39fbdf99c1",
        "54dce0feba34f3ca818d668c1d84b16b8deb7282592f287c48da4807010011c0"),
    "cupid_bow_draw1": (
        "68a2111434d245e8bdcbee6e6a1162a4ba88baea2ecd03a85692d034bc0ad4c4",
        "6d1e629fed8d33887054737c3472d94f3cf17ca32630532e468f35c1466eea1e",
        "d1f511b0df1858937602961d636fe58dc76a249c593a0050cc483d37e4cfd60c"),
    "cupid_bow_draw2": (
        "30f86f603da64d5e7394d398dbf600015044b3f765a3aedf665b5821aab7d52d",
        "12f4fa513990fcd8696a3aeedd6ddd421604349df6b074aa838b4f612edbf001",
        "755470e02e8f9d0f646df7c74e77c994b75999e9e24bc2e4bab8de2d0e9859ac"),
}


def bow_rgba(png):
    """Decode the noninterlaced 8-bit RGBA textures used here without an image-library dependency."""
    assert png[:8] == bytes.fromhex("89504e470d0a1a0a")
    assert struct.unpack(">IIBBBBB", png[16:29]) == (64, 64, 8, 6, 0, 0, 0), "Unexpected bow texture encoding"
    pos = 8
    compressed = bytearray()
    while pos < len(png):
        size, kind = struct.unpack(">I4s", png[pos:pos+8])
        if kind == b"IDAT":
            compressed.extend(png[pos+8:pos+8+size])
        pos += size+12
    raw = zlib.decompress(compressed)
    stride = 64*4
    assert len(raw) == 64*(stride+1)
    previous = bytes(stride)
    pixels = bytearray()
    for row in range(64):
        offset = row*(stride+1)
        mode = raw[offset]
        line = bytearray(raw[offset+1:offset+1+stride])
        assert mode in range(5)
        for col in range(stride):
            left = line[col-4] if col >= 4 else 0
            up = previous[col]
            corner = previous[col-4] if col >= 4 else 0
            if mode == 4:
                paeth = left+up-corner
                prediction = min((left, up, corner), key=lambda value: abs(paeth-value))
            else:
                prediction = (0, left, up, (left+up)//2)[mode]
            line[col] = (line[col]+prediction) & 255
        pixels.extend(line)
        previous = line
    return pixels


def bow_masks(jar, art, name):
    base = art + "textures/item/" + name
    png = jar.read(base+".png")
    png_hash, art_hash, string_hash = BOW_FRAMES[name]
    assert hashlib.sha256(png).hexdigest() == png_hash, name+" approved PNG changed"
    original = bow_rgba(png)
    assert hashlib.sha256(original).hexdigest() == art_hash, name+" approved pixels changed"
    masks = {}
    combined = bytearray(len(original))
    for part in ("body", "string"):
        rgba = bow_rgba(jar.read(base+"_"+part+".png"))
        mask = bytes(int(alpha > 0) for alpha in rgba[3::4])
        if part == "string":
            assert hashlib.sha256(mask).hexdigest() == string_hash, name+" exposed bowstring changed"
        for n, present in enumerate(mask):
            if present:
                at = n*4
                assert combined[at+3] == 0, name+" body/string overlap"
                combined[at:at+4] = rgba[at:at+4]
        masks[part] = {(n%64, n//64) for n, value in enumerate(mask) if value}
    assert combined == original, name+" body and string fail to reproduce the approved RGBA"
    return masks


def bow_geometry(model, masks, name):
    """Each surface pixel keeps its approved UV, and every exposed pixel edge gets exactly one cap."""
    assert model["parent"] == "uwudimension:item/cupid_bow_handheld" and "display" not in model
    normals = {"up": (0,-1), "down": (0,1), "west": (-1,0), "east": (1,0)}
    boundaries = {
        part: {(side,x,y) for x,y in pixels for side,(dx,dy) in normals.items() if (x+dx,y+dy) not in pixels}
        for part,pixels in masks.items()
    }
    covered = {part: set() for part in masks}
    sheets = {part: 0 for part in masks}
    depths = {"body": (7.75,8.25), "string": (7.9375,8.0625)}
    for part in masks:
        assert model["textures"][part] == "uwudimension:item/"+name+"_"+part
    for element in model["elements"]:
        faces = element["faces"]
        parts = {face["texture"] for face in faces.values()}
        assert len(parts) == 1 and parts <= {"#body","#string"}, name
        part = parts.pop()[1:]
        x0,y0,z0 = element["from"]
        x1,y1,z1 = element["to"]
        assert (z0,z1) == depths[part] and "rotation" not in element, (name,part,element)
        if set(faces) == {"north","south"}:
            assert (x0,y0,x1,y1) == (0,0,16,16)
            assert faces["south"]["uv"] == [0,0,16,16] and faces["north"]["uv"] == [16,0,0,16]
            assert all(f.get("rotation",0)==0 for f in faces.values())
            sheets[part] += 1
            continue
        assert len(faces)==1 and set(faces) <= normals.keys(), (name,element)
        side, face = next(iter(faces.items()))
        assert face.get("rotation",0)==0 and "cullface" not in face
        # Positions in the source image use a top-left origin. Model coordinates use the bottom left.
        grid = (4*x0,64-4*y1,4*x1,64-4*y0)
        assert all(0 <= v <= 64 and v==int(v) for v in grid)
        left,top,right,bottom = map(int,grid)
        u0,v0,u1,v1 = [4*v for v in face["uv"]]
        if side in ("up","down"):
            assert top==bottom and left<right
            y = top if side=="up" else top-1
            assert (u0,u1)==(left,right) and y<v0<=v1<y+1, (name,side,face)
            run = [(side,x,y) for x in range(left,right)]
        else:
            assert left==right and top<bottom
            x = left if side=="west" else left-1
            assert (v0,v1)==(top,bottom) and x<u0<=u1<x+1, (name,side,face)
            run = [(side,x,y) for y in range(top,bottom)]
        assert covered[part].isdisjoint(run), (name,part,"overlapping edge caps")
        covered[part].update(run)
    for part in masks:
        assert sheets[part]==1 and covered[part]==boundaries[part], (name,part,"missing or extra faces")


def bow_transforms(parent):
    # Effective original transforms including vanilla item/bow and item/generated defaults.
    assert "parent" not in parent and not parent.get("elements") and parent["gui_light"]=="front"
    expected = {
        "firstperson_righthand": ([0,-46,29],[-1.25,4,-.85],[.54,.54,.28]),
        "firstperson_lefthand": ([0,46,-29],[-1.25,4,-.85],[.54,.54,.28]),
        "thirdperson_righthand": ([-80,252,-40],[-1,-2,2.5],[.9,.9,.9]),
        "thirdperson_lefthand": ([-80,-272,40],[-1,-2,2.5],[.9,.9,.9]),
        "ground": ([0,0,0],[0,2,0],[.5,.5,.5]),
        "head": ([0,180,0],[0,13,7],[1,1,1]),
        "fixed": ([0,180,0],[0,0,0],[1,1,1])
    }
    assert set(parent["display"]) == set(expected)
    for view, values in expected.items():
        pose = parent["display"][view]
        assert (pose.get("rotation",[0,0,0]),pose.get("translation",[0,0,0]),pose.get("scale",[1,1,1])) == values, view


def check_jar(jar):
    paths = set(jar.namelist())
    data = "data/uwudimension/"
    art = "assets/uwudimension/"

    def read(path):
        return json.loads(jar.read(path))

    def image(path, size=None):
        png = jar.read(art + "textures/" + path)
        assert png[:8] == b"\x89PNG\r\n\x1a\n", path
        if size:
            assert struct.unpack(">II", png[16:24]) == size, path

    assert jar.testzip() is None
    for file in sorted(paths):
        if file.endswith(".json"):
            read(file)
    assert {p for p in paths if "/dimension/" in p and p.startswith("data/") and p.endswith(".json")} == {
        data + "dimension/sakura_reach.json"}
    assert not any(p.startswith("dev/uwu/test/") for p in paths)
    assert not any(p.startswith("data/minecraft/worldgen/biome/") for p in paths)
    meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
    assert meta["mods"][0]["version"] == "1.5.0-beta.2"
    locale = read(art + "lang/en_us.json")
    bow = read(art + "models/item/cupid_bow.json")
    assert [o["model"].split("/")[-1] for o in bow["overrides"]] == [f"cupid_bow_draw{i}" for i in range(3)]
    bow_transforms(read(art+"models/item/cupid_bow_handheld.json"))
    for frame in BOW_FRAMES:
        masks = bow_masks(jar,art,frame)
        bow_geometry(read(art+f"models/item/{frame}.json"),masks,frame)
    print("CUPID GEOMETRY PASS all four approved RGBA maps, string masks, thin shells and perimeter UVs")
    image("entity/cupid_heart_arrow.png", (64, 64))
    for particle in ("pastel_heart", "cupid_impact_outline"):
        assert read(art + "particles/" + particle + ".json")["textures"]
    for family in ("petalweave", "nightbriar"):
        for part in ("helmet", "chestplate", "leggings", "boots"):
            name = family + "_" + part
            assert locale["item.uwudimension." + name]
            assert read(data + "recipe/" + name + ".json")["result"]["id"] == "uwudimension:" + name
            image("item/" + name + ".png", (16, 16))
        for layer in (1, 2):
            image(f"models/armor/{family}_layer_{layer}.png", (64, 32))
    for family in ("petal_house", "dusk_house"):
        banner = read(data + "recipe/" + family + "_standard.json")["result"]["components"]
        assert len(banner["minecraft:banner_patterns"]) == 3
        assert json.loads(banner["minecraft:item_name"])["translate"] in locale
    for glyph in read(art + "font/field_sketches.json")["providers"]:
        image(glyph["file"].split(":", 1)[1])
    print(f"SAKURA RELEASE ASSETS PASS {len(paths)} entries")


if __name__ == "__main__":
    with ZipFile(sys.argv[1]) as package:
        check_jar(package)
