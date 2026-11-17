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


def rgba_pixels(png, size):
    """Decode the noninterlaced 8-bit RGBA textures used here without an image-library dependency."""
    assert png[:8] == bytes.fromhex("89504e470d0a1a0a")
    width,height=(size,size) if isinstance(size,int) else size
    assert struct.unpack(">IIBBBBB", png[16:29]) == (width,height,8,6,0,0,0), "Unexpected texture encoding"
    pos = 8
    compressed = bytearray()
    while pos < len(png):
        chunk_size, kind = struct.unpack(">I4s", png[pos:pos+8])
        if kind == b"IDAT":
            compressed.extend(png[pos+8:pos+8+chunk_size])
        pos += chunk_size+12
    raw = zlib.decompress(compressed)
    stride = width*4
    assert len(raw) == height*(stride+1)
    previous = bytes(stride)
    pixels = bytearray()
    for row in range(height):
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


def understory(jar, art, data, read, locale):
    species = ("short_grass","tall_grass","lace_fern","star_clover","rain_bell","wood_aster","frost_sorrel")
    silhouettes = set()
    for region in ("petal","dusk"):
        expected = {"uwudimension:"+region+"_"+name for name in species}
        assert set(read(data+"tags/block/"+region+"_understory.json")["values"]) == expected
        for name in species:
            key=region+"_"+name
            assert locale["block.uwudimension."+key]
            variants=read(art+"blockstates/"+key+".json")["variants"]
            tall=name=="tall_grass"
            assert set(variants)==({"half=upper","half=lower"} if tall else {""})
            silhouette=bytearray()
            for prop in (("half=upper","half=lower") if tall else ("",)):
                model=read(art+"models/"+variants[prop]["model"].split(":")[1]+".json")
                assert model["render_type"]=="minecraft:cutout" and model["parent"]=="minecraft:block/cross"
                tex=model["textures"]["cross"].split(":")[1]
                pixels=rgba_pixels(jar.read(art+"textures/"+tex+".png"),16)
                alpha=pixels[3::4]
                assert set(alpha)=={0,255} and 8<=alpha.count(255)<=170, key
                silhouette.extend(alpha)
            assert bytes(silhouette) not in silhouettes, "Repeated plant silhouette: "+key
            silhouettes.add(bytes(silhouette))
            itemtex=read(art+"models/item/"+key+".json")["textures"]["layer0"].split(":")[1]
            rgba_pixels(jar.read(art+"textures/"+itemtex+".png"),16)
            assert read(data+"loot_table/blocks/"+key+".json")["pools"]
    print("NATIVE UNDERSTORY ASSETS PASS 14 distinct silhouettes, two tall halves, transparent 16px foliage, matching items and loot")


def bow_masks(jar, art, name):
    base = art + "textures/item/" + name
    png = jar.read(base+".png")
    png_hash, art_hash, string_hash = BOW_FRAMES[name]
    assert hashlib.sha256(png).hexdigest() == png_hash, name+" approved PNG changed"
    original = rgba_pixels(png, 64)
    assert hashlib.sha256(original).hexdigest() == art_hash, name+" approved pixels changed"
    masks = {}
    combined = bytearray(len(original))
    for part in ("body", "string"):
        rgba = rgba_pixels(jar.read(base+"_"+part+".png"), 64)
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
    assert model["parent"] == "uwudimension:item/cupid_bow_handheld"
    if name != "cupid_bow":
        assert set(model["display"]) == {"firstperson_righthand", "firstperson_lefthand"}
        for hand, sign in (("right",1),("left",-1)):
            assert model["display"][f"firstperson_{hand}hand"] == {"rotation":[0,-90*sign,25*sign],"translation":[1.13,3.2,1.13],"scale":[.68,.68,.28]}

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


def workshop_resources(jar, art, read):
    crops = ("heartberry", "sakura_rice", "starfruit", "frostpetal", "sweetbean", "glowginger", "vanilla_bean")
    mature = set()
    for crop in crops:
        states = read(art+"blockstates/"+crop+"_crop.json")["variants"]
        sprites = set()
        for age in range(8):
            model = states[f"age={age}"]["model"].split(":",1)[1]
            plant = read(art+"models/"+model+".json")
            parent = read(art+"models/"+plant["parent"].split(":",1)[1]+".json")
            assert parent["ambientocclusion"] and all(e.get("shade", True) for e in parent["elements"]), crop
            texture = plant["textures"]["crop"].split(":",1)[1]
            pixels = bytes(rgba_pixels(jar.read(art+"textures/"+texture+".png"),16))
            assert {0,255} == set(pixels[3::4]), (crop,age)
            sprites.add(pixels)
            if age==7: mature.add(pixels)
        assert len(sprites)==8,crop
    assert len(mature)==len(crops)
    for theme in ("heart_neon", "dusk_heart"):
        face = rgba_pixels(jar.read(art+"textures/block/"+theme+"_heart.png"),16)
        for hanging in (False,True):
            model = read(art+"models/block/"+theme+"_lantern"+("_hanging" if hanging else "")+".json")
            exposed = set()
            for el in model["elements"]:
                if el["faces"].get("south",{}).get("texture")!="#heart": continue
                x,y,x2,y2=el["faces"]["south"]["uv"]
                assert y2==y+1
                exposed.update((xx,y) for xx in range(x,x2))
                assert el["faces"]["north"]["uv"]==[x2,y,x,y+1]
            assert exposed=={(n%16,n//16) for n,a in enumerate(face[3::4]) if a}
            # A through hole with twin lobes, a notch, and a tapering tip, even under the mounting hardware.
            for x,y in [(4,4),(11,4),(5,6),(8,6),(10,7),(8,9)]:
                xx,yy=x+.5,14.5-y-(0 if hanging else 1)
                assert not any(e["from"][0]<=xx<e["to"][0] and e["from"][1]<=yy<e["to"][1] for e in model["elements"])
            assert (8,4) in exposed and (8,10) in exposed
    roster = read(art+"counter_roster.json")
    assert len(roster)==133
    # Retuned legacy variants deliberately share material/shape art. All still need
    # valid textures; the independently obtainable counters and named relics must
    # each have a distinct sprite.
    for entry in roster:
        rgba_pixels(jar.read(art+"textures/item/"+entry["id"]+".png"),32)
    chest=read("data/uwudimension/loot_table/chests/mirror_court.json")
    names=[entry["name"].split(":",1)[1] for pool in chest["pools"] for entry in pool["entries"]
           if entry.get("name","").startswith("uwudimension:counter_")]
    assert len(names)==29 and len(set(names))==29, "Expected 29 direct counter sprites"
    names += "blossom_greatsword ribbonhare_scepter bellheart_axe lockkeeper_axe vesper_scythe axolotl_whip frost_whip rift_whip satin_whip rose_dagger lantern_spear tidelace_trident blue_hour_wand ripple_bow glitch_bow".split()
    images={bytes(rgba_pixels(jar.read(art+"textures/item/"+name+".png"),32)) for name in names}
    assert len(images)==len(names), "Repeated directly obtainable weapon artwork"
    print("SAKURA WORKSHOP ART PASS 56 stages, two open lanterns, 133 valid counters and",len(images),"distinct direct weapon sprites")


def owl_residents(jar, art, read, locale):
    geom=read(art+"geometry/guardian_bones.json")
    width,height=geom["width"],geom["height"]
    bones={bone["name"]:bone["cubes"] for bone in geom["bones"]}
    assert bones.keys()=={"basket","head","left","right","left_root","right_root"}
    faces=[c for c in bones["head"] if c["name"]=="warm_face"]
    assert len(faces)==1
    unit=width//16
    tile=faces[0]["tile"]
    u,v=(tile%4)*4*unit+unit,(tile//4)*2*unit+unit
    skins=[rgba_pixels(jar.read(art+"textures/entity/female_uwu_guardian"+f+".png"),(width,height)) for f in ("","_half","_blink")]
    for i in range(3):
        for j in range(i):
            changed=[p for p in range(width*height) if skins[i][p*4:p*4+4]!=skins[j][p*4:p*4+4]]
            assert len(changed)>12 and all(u<=p%width<u+unit and v<=p//width<v+unit for p in changed), "Owl blink spills off face or is invisible"
    neutral=read(art+"models/item/uwu_guardian_spawn_egg.json")
    assert [o["predicate"]["custom_model_data"] for o in neutral["overrides"]]==[1,2]
    names=[o["model"].split("/")[-1] for o in neutral["overrides"]]
    names += [family+suffix+"_spawn_egg" for family in ("petal","dusk") for suffix in ("_keeper","_zombie_villager")]
    egg_art=set()
    for name in names:
        assert locale["item.uwudimension."+name]
        assert read(art+"models/item/"+name+".json")["textures"]["layer0"]=="uwudimension:item/"+name
        rgba=rgba_pixels(jar.read(art+"textures/item/"+name+".png"),16)
        assert 0 in rgba[3::4] and 255 in rgba[3::4]
        egg_art.add(bytes(rgba))
    assert len(egg_art)==6, "Duplicate resident egg art"
    jobs=("armorer","butcher","cartographer","cleric","farmer","fisherman","fletcher","leatherworker","librarian","mason","shepherd","toolsmith","weaponsmith")
    for family in ("petal","dusk"):
        for job in [""]+["/"+job for job in jobs]:
            name=family+"_keeper"+job+".png"
            assert jar.read(art+"textures/entity/"+name)!=jar.read(art+"textures/entity/undead/"+name), name
    print("OWL RESOURCES PASS | three face frames, six distinct eggs, all 28 living and undead outfit pairs")


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
    assert meta["mods"][0]["version"] == "1.5.0-beta.12-dev"
    locale = read(art + "lang/en_us.json")
    understory(jar, art, data, read, locale)
    workshop_resources(jar, art, read)
    owl_residents(jar, art, read, locale)
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
    for key in ("cupid_draw_heart","cupid_impact_outline"):
        assert read(art + "particles/" + key + ".json")["textures"] == ["uwudimension:cupid_ripple"]
    ripple=rgba_pixels(jar.read(art+"textures/particle/cupid_ripple.png"),64)
    alpha=ripple[3::4]
    assert 150 < sum(a>0 for a in alpha) < 650, "Ripple needs one thin perimeter"
    assert not any(alpha[y*64+x] for y in range(24,40) for x in range(24,40)), "Ripple center must be fully transparent"
    assert any(alpha[y*64+x] for y in range(6,18) for x in range(10,25)), "Left lobe"
    assert any(alpha[y*64+x] for y in range(6,18) for x in range(40,55)), "Right lobe"
    assert any(alpha[y*64+x] for y in range(52,59) for x in range(29,35)), "Heart point"
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
