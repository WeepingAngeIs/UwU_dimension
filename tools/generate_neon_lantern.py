"""Build the pearl-silver bezel and the open heart-shaped glass channel on a 16-pixel grid."""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/uwudimension"
# Two lobes and a notch are present in both outlines. A dot inside stays open all
# the way from the front to the back. No transparent enclosing cube is required.
OUTLINE = (
    "................","...###....###...","..#####..#####..",".##############.",
    ".##############.",".##############.","..############..","..############..",
    "...##########...","....########....",".....######.....","......####......",
    ".......##.......","................","................","................")
APERTURE = (
    "................","................","................","................",
    "....##....##....","...####..####...","....########....",".....######.....",
    "......####......",".......##.......","................","................",
    "................","................","................","................")
SILVER = ("#8292A4", "#AAB6C4", "#D0D8DE", "#E0E5E9")
PINK = ("#C888A6", "#E2A5C1", "#F1BFD4", "#F9D4E1", "#FFE1EA")
SIDES = {"west":(-1,0), "east":(1,0), "up":(0,-1), "down":(0,1)}


def pixels(rows):
    return {(x,y) for y,row in enumerate(rows) for x,c in enumerate(row) if c == "#"}


def square(a, b, tex, glowing=False):
    element = {"from":a, "to":b, "faces":{d:{"texture":"#"+tex} for d in (*SIDES, "north","south")}}
    if glowing:
        element["neoforge_data"] = {"block_light":13,"ambient_occlusion":False}
    return element


def build(dusk=False):
    base = "dusk_heart" if dusk else "heart_neon"
    metal_colors = tuple(map(rgba, ("#282936","#343342","#555064","#776F83"))) if dusk else SILVER_RGB
    glass_colors = tuple(map(rgba, ("#372E45","#42344F","#544164","#795C88","#AC89B6"))) if dusk else PINK_RGB
    outline, hole = pixels(OUTLINE), pixels(APERTURE)
    ring = outline - hole
    bezel = {p for p in ring if any((p[0]+dx,p[1]+dy) not in outline for dx,dy in SIDES.values())}
    face = Image.new("RGBA",(16,16))
    for x,y in ring:
        if (x,y) in bezel:
            index = 3 if (x-1,y) not in outline or (x,y-1) not in outline else 1
            face.putpixel((x,y),metal_colors[index])
        else:
            distance = min(abs(x-hx)+abs(y-hy) for hx,hy in hole)
            color = 4 if distance==1 else 3 if distance==2 else 1
            if (x+y)%5==0 and color>1: color-=1
            face.putpixel((x,y),glass_colors[color])
    face.save(ASSETS/f"textures/block/{base}_heart.png")
    for name,colors in (("metal",metal_colors),("rim",glass_colors)):
        surface=Image.new("RGBA",(16,16))
        for y in range(16):
            for x in range(16):
                idx = (1,2,1,1,2,3,2,1)[(y+x//6)%8] if name=="metal" else (3,3,4,3,2,3,3,4)[(x+y//4)%8]
                surface.putpixel((x,y), colors[idx])
        surface.save(ASSETS/f"textures/block/{base}_{name}.png")
    # Group each continuous run of an identical material and edge exposure. UVs for
    # the two broad faces map back to the hand-authored paint, including the hole.
    for hanging in (False,True):
        elements=[]
        offset=0 if hanging else -1
        for y in range(16):
            start=0
            def section(x):
                if (x,y) not in ring: return None
                metal=(x,y) in bezel
                boundary=tuple(d for d,(dx,dy) in SIDES.items()
                               if (x+dx,y+dy) not in ring or (metal and (x+dx,y+dy) not in bezel))
                return metal,boundary
            while start<16:
                info=section(start)
                end=start+1
                while end<16 and section(end)==info: end+=1
                if info is not None:
                    metal,boundary=info
                    z0,z1=(6.25,9.75) if metal else (6.5,9.5)
                    block=square([start,14-y+offset,z0],[end,15-y+offset,z1],"metal" if metal else "rim",not metal)
                    block["faces"]={side:block["faces"][side] for side in boundary}
                    block["faces"].update(south={"texture":"#heart","uv":[start,y,end,y+1]},
                                          north={"texture":"#heart","uv":[end,y,start,y+1]})
                    elements.append(block)
                start=end
        if hanging:
            # A small open bail and V-shaped shoulders attach the two lobes to a chain.
            for lo,hi in [([7,14,7.5],[7.55,16,8.5]),([8.45,14,7.5],[9,16,8.5]),
                          ([7,15.5,7.5],[9,16,8.5]),([6,13,7.5],[10,14,8.5]),
                          ([5,12.5,7.25],[6.5,13.5,8.75]),([9.5,12.5,7.25],[11,13.5,8.75])]:
                elements.append(square(lo,hi,"metal"))
        else:
            elements.append(square([6.75,0,6.6],[9.25,1.5,9.4],"metal"))
            elements.append(square([5.5,0,6],[10.5,.55,10],"metal"))
        model={"parent":"minecraft:block/block","render_type":"minecraft:cutout",
               "textures":{k:"uwudimension:block/"+base+"_"+v for k,v in
                           (("particle","heart"),("heart","heart"),("rim","rim"),("metal","metal"))},
               "elements":elements}
        name=base+"_lantern"+("_hanging" if hanging else "")
        (ASSETS/f"models/block/{name}.json").write_text(json.dumps(model,indent=2)+"\n",encoding="utf8")
    return face


def rgba(color):
    return tuple(bytes.fromhex(color[1:]))+(255,)


SILVER_RGB = tuple(map(rgba,SILVER))
PINK_RGB = tuple(map(rgba,PINK))
def front_preview():
    """Paint an orthographic front view of each actual model, including its hook or base."""
    board = Image.new("RGB", (600, 218), "#2c2832")
    captions = ImageDraw.Draw(board)
    for i, (base, hanging) in enumerate((("heart_neon", False), ("heart_neon", True),
                                         ("dusk_heart", False), ("dusk_heart", True))):
        name = base + "_lantern" + ("_hanging" if hanging else "")
        model = json.loads((ASSETS / f"models/block/{name}.json").read_text())
        textures = {key: Image.open(ASSETS / ("textures/" + value.split(":",1)[1] + ".png")).convert("RGBA")
                    for key,value in model["textures"].items()}
        for element in sorted(model["elements"], key=lambda e:e["to"][2]):
            front = element["faces"].get("south")
            if front is None:
                continue
            x0,y0,_ = element["from"]
            x1,y1,_ = element["to"]
            uv = front.get("uv", [x0,16-y1,x1,16-y0])
            tex = textures[front["texture"][1:]]
            box = (int(uv[0]),int(uv[1]),max(int(uv[2]),int(uv[0])+1),max(int(uv[3]),int(uv[1])+1))
            x = i*150 + 11 + round(x0*8)
            y = 24 + round((16-y1)*8)
            tile = tex.crop(box).resize((max(1,round((x1-x0)*8)),max(1,round((y1-y0)*8))),Image.Resampling.NEAREST)
            board.paste(tile,(x,y),tile)
        label = ("Petal" if base=="heart_neon" else "Dusk") + (" / hanging" if hanging else " / standing")
        captions.text((i*150+22,174),label,fill="#f2dde6")
    board.save(ASSETS.parents[4] / "docs/art/heart-neon-lantern-preview.png")


if __name__=="__main__":
    build()
    build(dusk=True)
    front_preview()
