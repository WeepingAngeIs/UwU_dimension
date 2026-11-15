"""Make Moonveil wood and mistgrass resources using original native-resolution art.

Run with Python, Pillow, and the official Minecraft 1.21.1 client JAR path.
Only vanilla state orientations, parent model references and standard loot rules are read.
"""
import argparse
import hashlib
import json
import math
import random
import re
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ART = ROOT / "assets/uwudimension"
DATA = ROOT / "data/uwudimension"
NS = "uwudimension:"
IDS = ("log", "wood", "stripped_log", "stripped_wood", "planks", "leaves", "stairs", "slab",
       "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "table", "stool", "bookshelf")
TIPS = ("An axe reveals the tree's plum heartwood.", "Dark, ridged bark wraps every face.",
        "Bare heartwood shows its winding grain.", "Smooth, stripped grain on every face.",
        "Boards sawn from the giant grove tree.", "Use shears to gather the slate-blue canopy.",
        "Carved steps for winding tree stairs.", "Half-height boards for landings and shelves.",
        "Low branch rails for a canopy walk.", "A swinging gate in carved heartwood.",
        "A leaf-shaped window lights the landing.", "A lattice hatch for overhead branches.",
        "A short redstone pulse, even from an arrow.", "Footsteps and loose items trigger the plate.",
        "A trestle table cut from Moonveil wood.", "Right-click to sit. Sneak to stand.",
        "Nearby enchanting tables draw power from these books.")


def name(kind):
    return kind.replace("stripped_", "stripped_moonveil_") if kind.startswith("stripped_") else "moonveil_" + kind


def jsonfile(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, ensure_ascii=True, indent=2) + "\n"
    if not path.exists() or path.read_text("utf-8-sig") != text:
        path.write_text(text, encoding="utf-8")


def rng(key):
    return random.Random(int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "big"))


def texture(key, image, folder="block"):
    image.save(ART / "textures" / folder / (key + ".png"))


def color(rgb, delta):
    return tuple(min(255,max(0,v+delta)) for v in rgb)


def bark(peeled=False):
    r = rng("moonveil bark" + str(peeled))
    im = Image.new("RGB", (16, 16))
    # Branchless sections tile vertically; broad, displaced ridges break up solemn dark brown.
    offsets = ((0, 0, 0, -1, -1, -1, -1, 0, 0, 0, 0, 0, 1, 1, 0, 0),
               (0, 0, 1, 1, 1, 1, 0, 0, -1, -1, -1, 0, 0, 0, 0, 0))
    heights = (0, 9, 5, 2, -7, -10, 0, 5, 9, 12, 2, -5, -1, 7, 3, -4)
    for y in range(16):
        for x in range(16):
            shift = offsets[(x // 8) % 2][y]
            base = (119,89,104) if peeled else (63,56,65)
            depth = heights[(x + shift) % 16]
            im.putpixel((x,y), color(base, depth+r.choice([-4,-2,0,0,2,4])))
    d = ImageDraw.Draw(im)
    if peeled:
        d.line([(3,4),(4,5),(4,9),(3,10),(2,9),(2,7)],fill=(107,78,95))
        d.line([(3,6),(3,8)],fill=(137,102,114))
        d.line([(10,11),(10,13),(11,14),(11,15)],fill=(140,110,120))
    else:
        d.line([(4,3),(5,4),(5,8),(4,10),(2,9),(2,6)],fill=(48,44,53))
        d.line([(3,5),(4,5),(4,8)],fill=(82,71,78))
        d.line([(10,2),(10,4),(9,5)],fill=(82,77,81))
        d.line([(12,11),(11,12),(11,14)],fill=(54,64,66));d.point((12,12),fill=(82,85,81))
    return im


def end_grain(peeled=False):
    im = Image.new("RGB",(16,16)); r=rng("moonveil rings")
    for y in range(16):
        for x in range(16):
            distance = ((abs(x-7.0)/1.05)**2.6+(abs(y-8.0)/1.10)**2.6)**(1/2.6)
            edge = min(x,y,15-x,15-y)
            rings = (-8,7,2,-4,8,1,-8,5,8,-6)
            shade = rings[int(distance+.22*math.sin(x*1.3+y*.45))%len(rings)] + r.choice((-2,0,0,2))
            base = (148,109,119)
            if not peeled and edge==0: base=(63,55,64)
            if not peeled and edge==1 and distance>7: base=(94,72,84)
            im.putpixel((x,y),color(base,shade))
    d=ImageDraw.Draw(im)
    d.line([(12,13),(11,12),(11,11)],fill=(124,89,103));d.point((7,8),fill=(104,79,94))
    return im


def boards():
    r=rng("moonveil plank");im=Image.new("RGB",(16,16));d=ImageDraw.Draw(im)
    # Staggered butt joints and a couple of long timber pores per board.
    for y in range(16):
        row=y//4; dy=y%4
        for x in range(16):
            tint=(117,90,105) if row%2 else (124,95,109)
            shade=(-16,7,0,-4)[dy]+r.choice((-3,-1,0,0,2,3))
            im.putpixel((x,y),color(tint,shade))
    for row,joint in enumerate((3,11,6,14)):
        y=row*4
        d.line((joint,y+1,joint,y+3),fill=(91,69,88))
        d.line((joint+1,y+1,joint+1,y+2),fill=(137,107,118))
    for x,y,n in [(6,2,4),(3,6,3),(9,10,3),(2,14,5)]:
        d.line((x,y,x+n,y),fill=(139,106,118))
    return im


def leaf_tile():
    r=rng("moonveil blue canopy")
    im=Image.new("RGBA",(16,16),(47,70,89,255)); px=im.load()
    for y in range(16):
        for x in range(16):
            px[x,y]=(*color((53,76,96),r.randrange(-7,6)),255)
    # Small lance-like leaves overlap in varied diagonal clusters; tiny holes show sky in Fancy.
    for x,y,length in [(1,0,3),(8,0,3),(13,1,4),(4,2,4),(10,3,3),(0,4,3),(6,5,4),(12,6,4),
                        (2,8,4),(9,8,4),(15,9,3),(5,10,3),(11,11,4),(0,12,3),(7,13,4),(14,14,3),(3,15,3)]:
        shift=r.choice((-1,1))
        for i in range(length):
            xx=(x+i*shift)%16; yy=(y+i//2)%16
            px[xx,(yy+1)%16]=(42,58,77,255)
            px[xx,yy]=r.choice(((66,91,111,255),(68,95,115,255),(71,98,117,255),(62,89,108,255)))
        px[(x+shift)%16,y%16]=r.choice(((79,107,125,255),(87,108,128,255)))
    for x,y in ((4,0),(11,1),(0,2),(14,4),(9,5),(3,6),(4,6),(7,8),(12,9),(2,10),(5,12),(10,14),(15,15)):
        px[x,y]=(0,0,0,0)
    return im


def detailing(planks):
    deep=(82,65,83);rail=(159,126,135);edge=(181,149,151);silver=(185,185,182);gold=(210,179,136)
    upper=Image.new("RGBA",(16,16));d=ImageDraw.Draw(upper)
    d.rectangle((0,0,15,15),outline=deep,width=2)
    d.line((2,1,2,14),fill=rail);d.line((13,1,13,14),fill=rail);d.line((3,0,12,0),fill=edge)
    for points in ([(3,13),(5,7),(9,3),(12,2),(12,5),(10,9),(5,12),(3,13)],
                   [(4,12),(10,6),(12,3)]):
        d.line(points,fill=rail,width=2)
    d.line([(6,8),(9,5),(11,4)],fill=edge)
    lower=planks.convert("RGBA");d=ImageDraw.Draw(lower)
    d.rectangle((0,0,15,15),outline=deep,width=2);d.rectangle((3,2,12,12),outline=rail)
    d.line([(5,10),(8,7),(10,5)],fill=(164,128,138));d.line((12,2,12,4),fill=gold);d.point((13,3),fill=silver)
    texture("moonveil_door_top",upper);texture("moonveil_door_bottom",lower)
    icon=Image.new("RGBA",(16,16));icon.paste(upper.resize((8,8),Image.Resampling.NEAREST),(4,0));icon.paste(lower.resize((8,8),Image.Resampling.NEAREST),(4,8))
    texture("moonveil_door",icon,"item")
    hatch=upper.copy();d=ImageDraw.Draw(hatch)
    d.line([(3,3),(5,4),(9,8),(11,11)],fill=rail,width=2)
    d.line((1,3,2,3),fill=silver);d.line((1,12,2,12),fill=silver)
    texture("moonveil_trapdoor",hatch)
    shelf=planks.copy();d=ImageDraw.Draw(shelf);r=rng("moonveil bookbinding")
    for y in (1,9):
        d.rectangle((1,y,14,y+5),fill=(65,53,67))
        x=2
        while x<14:
            width=r.choice((1,2));h=r.choice((3,4,5));c=r.choice(((121,161,168),(156,117,133),(183,158,125),(120,115,145),(97,137,129)))
            d.rectangle((x,y+6-h,x+width-1,y+5),fill=c);d.point((x,y+5),fill=color(c,18));x+=width+1
        d.line((0,y+6,15,y+6),fill=rail)
    texture("moonveil_bookshelf",shelf)


def turf():
    # Neutral hand-pixelled monochrome turf takes its blue/teal hue from the block tint.
    r=rng("mistgrass fine botanical grain");im=Image.new("RGB",(16,16));px=im.load()
    for y in range(16):
        for x in range(16):
            value=r.choice((198,203,208,212,216,219,222,225,228,231))
            px[x,y]=(value,value,value)
    for x,y in ((1,3),(5,0),(10,2),(13,6),(2,9),(7,6),(8,13),(14,11),(3,14),(11,8),(5,11)):
        px[x,y]=(193,194,193);xx=(x+1)%16;yy=(y-1)%16
        px[xx,yy]=(220,223,221)
    texture("sakura_grass_top",im)
    side=Image.new("RGBA",(16,16)); px=side.load()
    depths=(3,4,3,3,5,4,3,2,3,4,4,3,2,4,3,3)
    for x,depth in enumerate(depths):
        for y in range(depth):
            v=(r.randrange(194,226) if y<depth-1 else r.randrange(167,196))
            px[x,y]=(v,v,v,255)
    texture("sakura_grass_overlay",side)


def add_tag(domain, kind, key, values):
    path=ROOT/"data"/domain/"tags"/kind/(key+".json")
    data=json.loads(path.read_text("utf-8-sig")) if path.exists() else {"replace":False,"values":[]}
    for value in values:
        if value not in data["values"]: data["values"].append(value)
    jsonfile(path,data)


def wood_resources(game):
    def vanilla(path):
        return json.loads(game.read(path+".json"))
    def convert(value):
        raw=json.dumps(value)
        return json.loads(re.sub(r'minecraft:(block/|item/|blocks/)?(stripped_)?cherry_',lambda m:NS+(m[1] or "")+(m[2] or "")+"moonveil_",raw))
    kinds=IDS[:-3]
    for kind in kinds:
        key=name(kind); standard=key.replace("moonveil","cherry")
        states=vanilla("assets/minecraft/blockstates/"+standard); item=vanilla("assets/minecraft/models/item/"+standard)
        refs=set(re.findall(r'"minecraft:block/(\w+)"',json.dumps([states,item])))
        for ref in refs:
            model=convert(vanilla("assets/minecraft/models/block/"+ref))
            if kind in ("door","trapdoor"): model["render_type"]="minecraft:cutout"
            # Let ItemBlockRenderTypes select solid on Fast versus cutout-mipped on Fancy/Fabulous.
            jsonfile(ART/"models/block"/(ref.replace("cherry","moonveil")+".json"),model)
        jsonfile(ART/"blockstates"/(key+".json"),convert(states))
        jsonfile(ART/"models/item"/(key+".json"),convert(item))
        loot=convert(vanilla("data/minecraft/loot_table/blocks/"+standard))
        if kind=="leaves":
            alternatives=loot["pools"][0]["entries"][0]
            loot["pools"][0]["entries"]=[alternatives["children"][0]]
        jsonfile(DATA/"loot_table/blocks"/(key+".json"),loot)
    def box(start,end,texture):
        return {"from":start,"to":end,"faces":{f:{"texture":"#"+texture} for f in ("up","down","north","south","east","west")}}
    for kind in ("table","stool","bookshelf"):
        key=name(kind)
        if kind=="bookshelf":
            model={"parent":"minecraft:block/cube_column","textures":{"end":NS+"block/moonveil_planks","side":NS+"block/moonveil_bookshelf"}}
            source=json.dumps(vanilla("data/minecraft/loot_table/blocks/bookshelf")).replace("minecraft:bookshelf",NS+key).replace("minecraft:blocks/bookshelf",NS+"blocks/"+key)
        else:
            seat=kind=="stool"
            inset,far,height,edge=(4,12,7,3) if seat else (2,14,14,1)
            elements=[box([edge,height,edge],[16-edge,height+2,16-edge],"boards")]
            for x in (inset,far-2):
                for z in (inset,far-2): elements.append(box([x,0,z],[x+2,height,z+2],"leg"))
            model={"parent":"minecraft:block/block","textures":{"boards":NS+"block/moonveil_planks","leg":NS+"block/stripped_moonveil_log","particle":NS+"block/moonveil_planks"},"elements":elements}
            source=json.dumps(vanilla("data/minecraft/loot_table/blocks/cherry_planks")).replace("minecraft:cherry_planks",NS+key).replace("minecraft:blocks/cherry_planks",NS+"blocks/"+key)
        jsonfile(ART/"models/block"/(key+".json"),model)
        jsonfile(ART/"models/item"/(key+".json"),{"parent":NS+"block/"+key})
        jsonfile(ART/"blockstates"/(key+".json"),{"variants":{"":{"model":NS+"block/"+key}}})
        jsonfile(DATA/"loot_table/blocks"/(key+".json"),json.loads(source))
    poles=[NS+name(k) for k in ("log","wood","stripped_log","stripped_wood")]
    members={"logs_that_burn":poles,"planks":[NS+name("planks")],"leaves":[NS+name("leaves")]}
    for tag,key in (("wooden_stairs","stairs"),("wooden_slabs","slab"),("wooden_fences","fence"),
                    ("fence_gates","fence_gate"),("wooden_doors","door"),("wooden_trapdoors","trapdoor"),
                    ("wooden_buttons","button"),("wooden_pressure_plates","pressure_plate")):
        members[tag]=[NS+name(key)]
    for reg in ("block","item"):
        add_tag("uwudimension",reg,"moonveil_logs",poles)
        for tag,ids in members.items():add_tag("minecraft",reg,tag,ids)
    add_tag("minecraft","block","mineable/axe",[NS+name(k) for k in IDS if k!="leaves"])
    add_tag("minecraft","block","mineable/hoe",[NS+name("leaves")])
    add_tag("minecraft","block","enchantment_power_provider",[NS+name("bookshelf")])
    add_tag("c","block","bookshelves",[NS+name("bookshelf")]);add_tag("c","item","bookshelves",[NS+name("bookshelf")])


def crafting():
    symbol={"P":{"item":NS+name("planks")},"V":{"item":NS+name("slab")},
            "S":{"item":"minecraft:stick"},"B":{"item":"minecraft:book"}}
    def make(kind,count,ingredients,pattern=None):
        key=name(kind)
        data={"type":"minecraft:crafting_shaped" if pattern else "minecraft:crafting_shapeless","category":"building","result":{"id":NS+key,"count":count}}
        if pattern: data.update(pattern=pattern,key=ingredients)
        else: data["ingredients"]=ingredients
        jsonfile(DATA/"recipe/moonveil"/(key+".json"),data)
        ingredient=next(iter(ingredients.values())) if pattern else ingredients[0]
        unlock=ingredient.get("item") or "#"+ingredient["tag"]
        recipe=NS+"moonveil/"+key
        jsonfile(DATA/"advancement/recipes/building_blocks/moonveil"/(key+".json"),{
            "parent":"minecraft:recipes/root","criteria":{"has_ingredient":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":unlock}]}},
                "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":recipe}}},
            "requirements":[["has_ingredient","has_the_recipe"]],"rewards":{"recipes":[recipe]}})
    make("planks",4,[{"tag":NS+"moonveil_logs"}])
    for kind,source in (("wood","log"),("stripped_wood","stripped_log")):
        make(kind,3,{"L":{"item":NS+name(source)}},["LL","LL"])
    shapes={"stairs":(4,["P  ","PP ","PPP"]),"slab":(6,["PPP"]),"fence":(3,["PSP","PSP"]),"fence_gate":(1,["SPS","SPS"]),
            "door":(3,["PP","PP","PP"]),"trapdoor":(2,["PPP","PPP"]),"pressure_plate":(1,["PP"]),
            "table":(1,["PPP","S S","S S"]),"stool":(1,[" V ","S S"]),"bookshelf":(1,["PVP","BBB","PPP"])}
    for kind,(count,pattern) in shapes.items():
        symbols=sorted(set("".join(pattern))-{" "})
        make(kind,count,{s:symbol[s] for s in symbols},pattern)
    make("button",1,[symbol["P"]])


def soil_resources():
    # Copy the native Petal block's vanilla compatibility into its Dusk counterpart.
    old="sakura_grass_block";new="dusk_grass_block"
    for sub in ("blockstates","models/block","models/item"):
        contents=json.loads((ART/sub/(old+".json")).read_text().replace(old,new).replace("sakura_grass_top","dusk_grass_top").replace("sakura_grass_overlay","dusk_grass_overlay"))
        jsonfile(ART/sub/(new+".json"),contents)
    loot=(DATA/"loot_table/blocks"/(old+".json")).read_text().replace(old,new)
    jsonfile(DATA/"loot_table/blocks"/(new+".json"),json.loads(loot))
    for path in (ROOT/"data/minecraft/tags").rglob("*.json"):
        entry=json.loads(path.read_text("utf-8-sig"))
        if NS+old in entry.get("values",[]) and NS+new not in entry["values"]:
            entry["values"].append(NS+new);jsonfile(path,entry)
    add_tag("uwudimension","block","grove_turf",[NS+old,NS+new])
    add_tag("uwudimension","worldgen/biome","dusk_turf",[NS+"velvet_highlands",NS+"vesper_hollows"])
    # Different blade placement, with a few soft lilac accents already in the texture before biome tint.
    turf_top=Image.open(ART/"textures/block/sakura_grass_top.png").transpose(Image.Transpose.ROTATE_90)
    for x,y in [(3,2),(9,9),(14,13),(1,12),(7,4)]:
        turf_top.putpixel((x,y),(226,213,228))
    texture("dusk_grass_top",turf_top)
    side=Image.open(ART/"textures/block/sakura_grass_overlay.png").transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    texture("dusk_grass_overlay",side)


def main():
    cli=argparse.ArgumentParser(description=__doc__);cli.add_argument("minecraft_jar",type=Path);args=cli.parse_args()
    for peeled in (False,True):
        key=name("stripped_log" if peeled else "log")
        texture(key,bark(peeled));texture(key+"_top",end_grain(peeled))
    p=boards();texture(name("planks"),p);texture(name("leaves"),leaf_tile());detailing(p);turf();soil_resources()
    with zipfile.ZipFile(args.minecraft_jar) as game: wood_resources(game)
    crafting()
    lang_path=ART/"lang/en_us.json";lang=json.loads(lang_path.read_text("utf-8-sig"))
    for kind,tip in zip(IDS,TIPS,strict=True):
        key=name(kind);lang["block.uwudimension."+key]=key.replace("_"," ").title();lang["tip.uwudimension."+key]=tip
    lang["block.uwudimension.dusk_grass_block"]="Dusk Mistgrass"
    jsonfile(lang_path,lang)


if __name__=="__main__":
    main()
