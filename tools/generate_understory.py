"""Rebuild original 16px flora, their models, recipes and drops. No input artwork."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ART = ROOT / "assets/uwudimension"
DATA = ROOT / "data/uwudimension"
SPECIES = ("short_grass","tall_grass","lace_fern","star_clover","rain_bell","wood_aster","frost_sorrel")
PREFIX = "uwudimension:"

PALETTE = {
    "petal": ("#285866","#3b7580","#579497","#80afb0","#b6cdaa"),
    "dusk": ("#43394f","#594c6c","#73638e","#9585a0","#b9a6ad")
}
BLOOMS = {
    "petal": (("#a75988","#f399ba","#ffcfda"),("#6966a5","#99a7e0","#d4e0f6"),
              ("#8e7080","#ece4ba","#fff4d6"),("#6b628c","#ad95ca","#e4c5df")),
    "dusk": (("#70426f","#b571a5","#ebb5cf"),("#644586","#a27acf","#dbb8eb"),
             ("#9c587b","#d487a7","#ffbbbc"),("#626b97","#9fa9c9","#dee0ed"))
}


def emit(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+"\n", encoding="utf-8")


def draw(side, kind):
    h=32 if kind=="tall_grass" else 16
    im=Image.new("RGBA",(16,h))
    pen=ImageDraw.Draw(im)
    g=PALETTE[side]
    def line(points, color):
        pen.line(points, fill=color)
    def dot(x,y,color):
        pen.point((x,y),fill=color)
    def blade(x,tip,height,shade):
        base=h-1
        middle=(round((x+tip)/2),base-height//2)
        line([(x,base),middle,(tip,base-height)],g[shade])
        line([(x+1,base),(middle[0]+1,middle[1]+1)],g[min(shade+1,4)])
    if kind in ("short_grass","tall_grass"):
        high=kind=="tall_grass"
        blades=[(3,1,5,1),(5,2,7,2),(6,6,5,1),(7,5,10,2),(8,9,8,2),(9,13,9,1),(11,12,5,3),(12,15,4,2)]
        for i,(x,tip,n,shade) in enumerate(blades):
            blade(x,tip,n*(2 if high else 1)+(i%3 if high else -2),shade)
        if high:
            # Two separately drooping seed panicles, joined to the lower leaves.
            for offset in (0,6):
                line([(5+offset,26),(5+offset,16),(4+offset,10),(3+offset,5),(2+offset,4)],g[2])
                for dx,dy in [(2,4),(1,5),(0,7),(3,6),(2,8),(4,9)]:
                    dot(dx+offset,dy,g[4 if dy%2==0 else 3])
    elif kind=="lace_fern":
        for ex,ey,bx in [(1,6,7),(6,2,8),(11,3,8),(15,8,9)]:
            line([(bx,15),(ex,ey)],g[1])
            for k in (.25,.48,.71,.89):
                x=round(bx+(ex-bx)*k);y=round(15+(ey-15)*k)
                arm=2 if k<.8 else 1
                line([(x,y),(x-arm,y-1),(x-arm-1,y-2)],g[2])
                line([(x,y),(x+arm,y-1),(x+arm,y-2)],g[3])
    elif kind=="star_clover":
        bloom=BLOOMS[side][0]
        for x,y in [(3,12),(8,9),(12,12)]:
            line([(8,15),(x,y)],g[1])
            for dx,dy in [(-2,0),(0,-2),(2,0)]:
                pen.rectangle((x+dx-1,y+dy-1,x+dx,y+dy), fill=g[2])
                dot(x+dx,y+dy-1,g[3])
        for x,y in [(4,9),(11,10)]:
            line([(x-1,y),(x+1,y)],bloom[1]);line([(x,y-1),(x,y+1)],bloom[1]);dot(x,y,bloom[2])
    else:
        flower=BLOOMS[side][{"rain_bell":1,"wood_aster":2,"frost_sorrel":3}[kind]]
        if kind=="rain_bell":
            for x,y,foot in [(4,6,6),(10,3,9),(13,9,10)]:
                line([(foot,15),(foot,10),(x-1,y-1),(x,y-1)],g[2])
                line([(x-1,y),(x+1,y)],flower[0]);pen.rectangle((x-1,y+1,x+1,y+2),fill=flower[1])
                dot(x-2,y+3,flower[1]);dot(x,y+2,flower[2]);dot(x+1,y+3,flower[2])
        elif kind=="wood_aster":
            for x,y,foot in [(5,7,7),(10,4,9)]:
                line([(foot,15),(x,y+1)],g[2])
                for dx,dy in [(-2,0),(2,0),(0,-2),(0,2),(-1,-1),(1,-1),(1,1),(-1,1)]:
                    dot(x+dx,y+dy,flower[1 if dx+dy>0 else 2])
                dot(x,y,"#edbe76" if side=="petal" else "#ebac83")
        else:
            for x,y in [(3,10),(7,7),(12,9)]:
                line([(7,15),(x,y+1)],g[2])
                for dx,dy in [(-1,0),(1,0),(0,1),(0,-1)]:
                    dot(x+dx,y+dy,flower[2 if dy<1 else 1])
                dot(x,y,flower[0])
        line([(8,15),(5,12),(2,12)],g[2]);line([(9,14),(11,11),(13,10)],g[3])
    return im


def resources(side,kind):
    key=side+"_"+kind
    ref=PREFIX+key
    tall=kind=="tall_grass"
    picture=draw(side,kind)
    variants={}
    for half in (("lower","upper") if tall else ("",)):
        name=key+"_"+half if half else key
        tex=PREFIX+"block/"+name
        part=picture.crop((0,0 if half=="upper" else 16,16,16 if half=="upper" else 32)) if tall else picture
        directory=ART/"textures/block"
        part.save(directory/(name+".png"),optimize=True)
        emit(ART/"models/block"/(name+".json"),{"parent":"minecraft:block/cross","render_type":"minecraft:cutout","textures":{"cross":tex}})
        variants["half="+half if half else ""]={"model":PREFIX+"block/"+name}
    emit(ART/"blockstates"/(key+".json"),{"variants":variants})
    if tall:
        # Keep the entire silhouette on the inventory item. Original manual short blades retain full pixels.
        sprite=Image.new("RGBA",(16,16));sprite.paste(picture.resize((8,16),Image.Resampling.NEAREST),(4,0))
        sprite.save(ART/"textures/item"/(key+".png"));texture=PREFIX+"item/"+key
    else:texture=PREFIX+"block/"+key
    emit(ART/"models/item"/(key+".json"),{"parent":"minecraft:item/generated","textures":{"layer0":texture}})
    cut=kind in ("short_grass","tall_grass","lace_fern")
    entry={"type":"minecraft:item","name":ref}
    pools=[]
    for half in (("lower","upper") if tall else ("",)):
        conditions=[{"condition":"minecraft:match_tool","predicate":{"items":"minecraft:shears"}}] if cut else [{"condition":"minecraft:survives_explosion"}]
        if half:
            other="upper" if half=="lower" else "lower"
            conditions.extend([{"condition":"minecraft:block_state_property","block":ref,"properties":{"half":half}},
                            {"condition":"minecraft:location_check","offsetY":1 if half=="lower" else -1,
                             "predicate":{"block":{"blocks":ref,"state":{"half":other}}}}])
        pools.append({"rolls":1,"conditions":conditions,"entries":[entry]})
    emit(DATA/"loot_table/blocks"/(key+".json"),{"type":"minecraft:block","pools":pools})
    if not cut:
        dyes={"petal":("pink","light_blue","yellow","magenta"),"dusk":("magenta","purple","pink","light_gray")}
        emit(DATA/"recipe"/(key+"_dye.json"),{"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[{"item":ref}],
             "result":{"id":"minecraft:"+dyes[side][SPECIES.index(kind)-3]+"_dye","count":1}})


def tag(path,values):
    emit(path,{"replace":False,"values":values})


def main():
    loc=ART/"lang/en_us.json"; names=json.loads(loc.read_text(encoding="utf-8"))
    for side in PALETTE:
        for kind in SPECIES:
            resources(side,kind)
            key=side+"_"+kind
            names["block.uwudimension."+key]=key.replace("_"," ").title()
        tag(DATA/"tags/block"/(side+"_understory.json"),[PREFIX+side+"_"+k for k in SPECIES])
        for season,flower in (("spring","rain_bell"),("summer","wood_aster"),("frost","frost_sorrel")):
            tag(DATA/"tags/block"/(side+"_"+season+"_blooms.json"),[PREFIX+side+"_"+flower])
    emit(loc,names)
    tag(DATA/"tags/block/understory_soil.json",["#uwudimension:grove_turf","minecraft:grass_block","minecraft:dirt","minecraft:coarse_dirt","minecraft:podzol","minecraft:rooted_dirt","minecraft:moss_block"])
    tag(DATA/"tags/worldgen/biome/dusk_understory_land.json",["#uwudimension:dusk_turf"])
    tag(DATA/"tags/worldgen/biome/petal_understory_land.json",["minecraft:cherry_grove","uwudimension:ribbon_bluffs","uwudimension:lantern_fens"])
    minecraft=ROOT/"data/minecraft/tags"
    flowers=[PREFIX+side+"_"+k for side in PALETTE for k in SPECIES[3:]]
    for registry in ("block","item"):
        tag(minecraft/registry/"small_flowers.json",flowers)

if __name__=="__main__":
    main()
