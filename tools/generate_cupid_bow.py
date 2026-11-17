"""Render the signature bow directly on Minecraft's 64-pixel grid in each draw pose."""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

TEXTURES = Path(__file__).resolve().parents[1] / "src/main/resources/assets/uwudimension/textures"
PALETTE = {
    "outline": "#AF7482", "rose": "#D78AA8", "blush": "#F4AEC8", "silk": "#FFD0DE",
    "milk": "#FFE7ED", "shine": "#FFF9F4", "lace": "#F8DFE0", "pearl": "#E4BCC8",
    "metal": "#BD8C63", "gold": "#EDBA83", "champagne": "#FFE3B2", "gem": "#FFB2D4", "facet": "#E992B9", "pinkmetal": "#F3A1C2",
}


class BowSprite:
    def __init__(self, draw_stage):
        self.image = Image.new("RGBA", (64, 64))
        self.draw = ImageDraw.Draw(self.image)
        self.stage = draw_stage
        nocks = (32.0, 25.0, 18.0, 10.0)
        lower = min(3, int(draw_stage))
        upper = min(3, lower + 1)
        self.nock = nocks[lower] + (nocks[upper] - nocks[lower]) * (draw_stage - lower)

    @staticmethod
    def pixel(p):
        # Rightward in the portrait becomes the firing axis of minecraft:item/bow.
        x, y = p[0] - 32, p[1] - 32
        return (round(31.5 - (x + y) / math.sqrt(2)), round(31.5 + (y - x) / math.sqrt(2)))

    def shape(self, color, points):
        self.draw.polygon([self.pixel(p) for p in points], fill=PALETTE[color])

    def stroke(self, color, points, width=1):
        self.draw.line([self.pixel(p) for p in points], fill=PALETTE[color], width=width, joint="curve")

    def ribbon(self, x, y, scale=1):
        # Each loop folds back over itself. Two cut tails emerge below the pin.
        cx, cy = self.pixel((x,y))
        def poly(color, points):
            self.draw.polygon([(round(cx+dx*scale),round(cy+dy*scale)) for dx,dy in points],
                              fill=PALETTE[color])
        poly("rose",[(-2,1),(-1,2),(-3,7),(-4,5),(-6,6),(-5,2)])
        poly("silk",[(1,1),(3,2),(5,6),(3,5),(2,6),(0,2)])
        for side in (-1,1):
            def loop(color, points):
                poly(color, [(side*dx,dy) for dx,dy in points])
            loop("rose", [(0,0),(3,-3),(6,-4),(7,-3),(7,2),(6,3),(3,2)])
            loop("blush", [(1,0),(4,-2),(6,-3),(6,1),(5,2),(3,1)])
            loop("silk", [(2,0),(4,-2),(6,-3),(5,-1)])
            loop("pinkmetal",[(2,1),(5,0),(6,0),(6,1),(4,2)])
        poly("gold",[(-1,-1),(1,-1),(2,1),(0,2),(-1,1)])

    def jewel(self, x, y, scale=1):
        # A narrow champagne setting lets the pink facets and the gap between both lobes read at UI scale.
        layout = (
            ".ccc...ccc.",
            "cpwwc.cwwpc",
            "cpwgpcpgppc",
            "cgppgggppgc",
            ".cgppgpgfc.",
            "..cgpgffc..",
            "...cgffc...",
            "....cfc....",
            ".....c.....",
        )
        tones = {"c":"gold", "w":"milk", "p":"gem", "g":"blush", "f":"facet"}
        badge = Image.new("RGBA",(11,9))
        paint = ImageDraw.Draw(badge)
        for row, colors in enumerate(layout):
            for col, key in enumerate(colors):
                if key != ".": paint.point((col,row), fill=PALETTE[tones[key]])
        if scale != 1:
            badge = badge.resize((max(1,round(11*scale)),max(1,round(9*scale))),Image.Resampling.NEAREST)
        cx, cy = self.pixel((x,y))
        self.image.alpha_composite(badge,(cx-badge.width//2,cy-badge.height//2))

    def wing(self, x, y, side, height, scale):
        # Stagger individual enclosed feathers, from the longest pinion to the coverts.
        feathers = [
            [(0,1),(-1,-1),(-7,-5),(-7,-3),(-5,-1),(-2,2)],
            [(0,1),(-3,0),(-8,-2),(-7,0),(-5,1),(-1,3)],
            [(0,1),(-2,1),(-7,1),(-6,2),(-3,3),(0,3)],
        ]
        def mapped(points, shift=0):
            return [(x+side*dx*scale,y+height*(dy*scale+shift)) for dx,dy in points]
        for feather in feathers:
            self.shape("pearl",mapped(feather,.8))
            self.shape("lace",mapped(feather))
            self.stroke("shine",mapped(feather[:3]))

    def limbs(self):
        # Shoulders ease in under draw while the heart on the grip stays at the same pixel.
        flex = self.stage * .65
        for end in (-1,1):
            def arm(points):
                return [(x-flex*(d/25)**2,32+end*d) for x,d in points]
            core = arm([(44,4),(43,7),(43,9),(45,13),(46,15),(45,17),(43,20),(39,23),(34,26)])
            self.stroke("metal",core,5)
            self.stroke("gold",core,4)
            self.stroke("pinkmetal",[(x-.5,y) for x,y in core],3)
            self.stroke("silk",[(x-.9,y+.45*end) for x,y in core],1)
            for x, d in [(43,14),(41.6,17),(39,20),(36,22)]:
                edge=arm([(x,d-1),(x-1.6,d-.4),(x-1.5,d+1),(x,d+1.7),(x+.8,d+1)])
                self.stroke("lace",edge,2)
                self.stroke("shine",edge[:3])
            x,y=arm([(34,26)])[0]
            self.wing(x-2,y,1,-end,1.15)
            self.ribbon(x,y,.92)
            self.jewel(x,y,.67)
            x,y=arm([(45,14)])[0]
            self.jewel(x,y,.48)

    def string(self):
        for end in (-1,1):
            endpoint=(34-self.stage*.65,32+end*26)
            start=(self.nock,32)
            self.stroke("rose",[endpoint,start],2)
            self.stroke("silk",[(endpoint[0]-.6,endpoint[1]),(start[0]-.6,start[1])])
            for t in (.24,.41,.59,.76):
                x=start[0]+t*(endpoint[0]-start[0])
                y=start[1]+t*(endpoint[1]-start[1])
                self.stroke("lace",[(x+.2,y-end),(x-1.5,y-end*.6),(x-1.4,y+end),(x+.2,y+end*1.25)])
            if self.stage:
                t=.55
                self.ribbon(start[0]+t*(endpoint[0]-start[0]),start[1]+t*(endpoint[1]-start[1]),.42)

    def arrow(self):
        if not self.stage:
            return
        n=self.nock
        tip = n + 46
        self.stroke("rose",[(n-3,32),(tip-3,32)],3)
        self.stroke("milk",[(n,31.45),(tip-2,31.45)])
        for ysign in (-1,1):
            self.shape("pearl",[(n+1,32),(n-3,32+ysign*3),(n-7,32+ysign*4),(n-5,32+ysign),(n-6,32)])
            self.shape("silk",[(n,32),(n-4,32+ysign*3),(n-7,32+ysign*3),(n-4,32)])
            self.stroke("shine",[(n-6,32+ysign*3),(n-3,32+ysign),(n,32)])
        self.ribbon(n+1,32,.54)
        self.jewel(n+1,32,.40)
        def head(color, pts):
            self.shape(color, [(tip+dx,32+dy) for dx,dy in pts])
        head("metal",[(-9,-3),(-8,-5),(-5,-5),(1,0),(-5,5),(-8,5),(-9,3),(-8,2),(-6,0),(-8,-1)])
        head("champagne",[(-8,-3),(-7,-4),(-5,-4),(0,0),(-5,4),(-7,4),(-8,3),(-5,0)])
        head("gem",[(-7,-3),(-5,-3),(-1,0),(-5,3),(-7,3),(-4,0)])
        head("milk",[(-6,-3),(-5,-3),(-3,-1),(-5,-1)])

    def decorated_body(self):
        self.limbs()
        self.arrow()
        self.stroke("outline",[(44,26),(44,38)],4)
        self.stroke("blush",[(44,26),(44,38)],3)
        for y in (27,30,33,36):
            self.stroke("silk",[(43,y+1),(45,y-1)])
        self.wing(42,32,1,1,.88)
        self.wing(46,32,-1,1,.88)
        self.jewel(44,32,1.12)
        return self.image

    def render(self):
        self.string()
        return self.decorated_body()


def flight_arrow():
    # Vanilla ArrowRenderer uses only a 32x10 side strip and a 10x10 end on a 64x64 atlas.
    image = Image.new("RGBA", (64,64))
    d = ImageDraw.Draw(image)
    def poly(c, pts): d.polygon(pts,fill=PALETTE[c])
    d.rectangle((1,4,27,5),fill=PALETTE["rose"])
    d.line((2,4,26,4),fill=PALETTE["milk"])
    for sign in (-1,1):
        def feathers(c, pts): poly(c,[(x,round(4.5+sign*y)) for x,y in pts])
        feathers("pearl",[(0,4),(4,4),(10,0),(1,1)])
        feathers("silk",[(0,4),(4,3),(8,0),(2,1)])
        feathers("shine",[(0,4),(4,2),(2,2)])
    for x in (9,23):
        d.line((x,3,x,6),fill=PALETTE["gold"])
    poly("metal",[(24,1),(25,0),(27,0),(31,4),(31,5),(27,9),(25,9),(24,8),(24,6),(25,5),(24,3)])
    poly("champagne",[(25,1),(27,1),(30,4),(30,5),(27,8),(25,8),(26,5),(25,3)])
    poly("gem",[(26,2),(27,2),(29,4),(29,5),(27,7),(26,7),(27,5)])
    d.point((26,2),fill=PALETTE["shine"])
    d.ellipse((2,12,7,17),fill=PALETTE["rose"])
    d.ellipse((3,13,6,16),fill=PALETTE["silk"])
    return image


# A generated 1.21.1 item is one model unit thick. These depths are measured on the
# same 16-unit grid, before Minecraft applies its normal hand and GUI transforms.
BODY_DEPTH = 0.5
STRING_DEPTH = 0.125


def layered_sprites(stage):
    complete = BowSprite(stage).render()
    body_alpha = BowSprite(stage).decorated_body().getchannel("A")
    body = Image.new("RGBA", complete.size)
    body.paste(complete, mask=body_alpha)
    cord = complete.copy()
    cord.paste((0, 0, 0, 0), mask=body_alpha)
    return complete, body, cord


def ribbon_mesh(mask, texture, depth):
    """Two textured faces and a narrow side wall along every exposed pixel edge."""
    size = mask.width
    step = 16 / size
    z0, z1 = 8 - depth / 2, 8 + depth / 2
    def face(uv):
        return {"uv": uv, "texture": "#" + texture}
    yield {"from": [0, 0, z0], "to": [16, 16, z1], "faces": {
        "south": face([0, 0, 16, 16]), "north": face([16, 0, 0, 16])}}
    alpha = mask.getchannel("A").load()
    def occupied(x, y):
        return 0 <= x < size and 0 <= y < size and alpha[x, y] != 0
    # Combine neighboring edges into one quad. Sample inside the edge texels
    # to keep a one-pixel strand colored even when seen exactly from the side.
    for side, dx, dy in (("up", 0, -1), ("down", 0, 1), ("west", -1, 0), ("east", 1, 0)):
        for across in range(size):
            begin = None
            for along in range(size + 1):
                x, y = (across, along) if dx else (along, across)
                exposed = occupied(x, y) and not occupied(x + dx, y + dy)
                if exposed and begin is None:
                    begin = along
                if exposed or begin is None:
                    continue
                start, end = begin * step, along * step
                lo, hi = (across + .25) * step, (across + .75) * step
                if dx:
                    edge = (across + int(dx > 0)) * step
                    a, b = [edge, 16-end, z0], [edge, 16-start, z1]
                    uv = [lo, start, hi, end]
                else:
                    edge = 16 - (across + int(dy > 0)) * step
                    a, b = [start, edge, z0], [end, edge, z1]
                    uv = [start, lo, end, hi]
                yield {"from": a, "to": b, "faces": {side: face(uv)}}
                begin = None


def write_pose(stage, name, model_dir):
    complete, body, cord = layered_sprites(stage)
    for suffix, image in (("", complete), ("_body", body), ("_string", cord)):
        image.save(TEXTURES / "item" / (name + suffix + ".png"), optimize=True)
    sprite = "uwudimension:item/" + name
    model = {
        "parent": "uwudimension:item/cupid_bow_handheld",
        "textures": {"particle": "#layer0", "layer0": sprite,
                     "body": sprite + "_body", "string": sprite + "_string"},
        "overrides": [],
    }
    if stage == 0:
        for pose, amount in enumerate((0, .5, .9)):
            predicate = {"uwudimension:drawing": 1}
            if amount:
                predicate["uwudimension:draw"] = amount
            model["overrides"].append({"predicate": predicate, "model": sprite + "_draw" + str(pose)})
    quads = [*ribbon_mesh(body, "body", BODY_DEPTH), *ribbon_mesh(cord, "string", STRING_DEPTH)]
    # Use one line per element so the outline coordinates remain reviewable.
    header = json.dumps(model, indent=2).removesuffix("\n}")
    elements = ",\n".join("    " + json.dumps(quad, separators=(",", ":")) for quad in quads)
    (model_dir / (name + ".json")).write_text(header + ',\n  "elements": [\n' + elements + "\n  ]\n}\n",encoding="utf8")


if __name__ == "__main__":
    for stage, name in enumerate(("cupid_bow", "cupid_bow_draw0", "cupid_bow_draw1", "cupid_bow_draw2")):
        write_pose(stage, name, TEXTURES.parent / "models/item")
    flight_arrow().save(TEXTURES / "entity/cupid_heart_arrow.png", optimize=True)
