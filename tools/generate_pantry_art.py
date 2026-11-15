"""UwU Dimension pantry pixel art. Requires Python 3.10+ and Pillow.

Check all production sprites against the original pixel artwork:
    python tools/generate_pantry_art.py
Write the 72 food items and 15 fruiting canopies:
    python tools/generate_pantry_art.py --write
Export a labeled contact sheet at a specific location:
    python tools/generate_pantry_art.py --sheet pantry.png

The seven field crop stage atlases and the five leaf-only source textures per orchard are separate assets.
"""
from pathlib import Path
from math import cos, sin, pi
import argparse
import hashlib
import math
import random
import re
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
a = root / "src/main/resources/assets/uwudimension/textures"
java = root / "src/main/java/dev/uwu/dimension"
pantry = (java / "Pantry.java").read_text(encoding="utf-8")
plants = (java / "PantryPlants.java").read_text(encoding="utf-8")
dishes = re.findall(r'food(?:WithRemainder)?\("([^"]+)"', pantry)
ingredients = re.findall(r'"([a-z_]+)"', plants.split("for (String id : List.of(", 1)[1].split(")) {", 1)[0])
seeds = re.findall(r'"([a-z_]+_seeds)"', plants.split("CROPS = Map.of(", 1)[1].split("static {", 1)[0])
ids = dishes + ingredients + seeds
d: ImageDraw.ImageDraw

# Ramps run from the outline and shadow to the lit face and specular reflection.

berry = ['#5f354c', '#a24761', '#df6c86', '#fa9cab', '#ffcfca']
jam = ['#543752', '#913d66', '#c8557d', '#ef84a6', '#ffc0ce']
plum = ['#423646', '#695180', '#9777b2', '#bfa3ce', '#e6cee7']
leaf = ['#374a48', '#507456', '#85a860', '#b5cb83', '#dce4a7']
mint = ['#3f5151', '#648a7b', '#99baa0', '#bed9c0', '#e8ecd3']
toast = ['#68434b', '#a16249', '#d2945b', '#edbc79', '#ffe3a1']
crust = ['#734b47', '#b27554', '#e1a36c', '#f6c997', '#ffe6b3']
cream = ['#826778', '#bc9ca3', '#e8ccd1', '#ffebdf', '#fff8e8']
sugar = ['#806579', '#bf8da7', '#ebbbd0', '#ffdae5', '#fff2ed']
ice = ['#506b82', '#819eaf', '#adcdd4', '#d6ebe5', '#f3fbeb']
silver = ['#485068', '#768298', '#b0bfce', '#d6deea', '#f3f6ee']
bowl = ['#5b5268', '#826f90', '#a4a2b7', '#c9ccd8', '#f1eee5']
greenbowl = ['#484d5a', '#657a77', '#92aca0', '#bbcbba', '#e5e7cd']
bean = ['#513743', '#754157', '#a15c64', '#c7807d', '#efa497']
gold = ['#775341', '#ad7c3b', '#d9a649', '#f6ce6d', '#ffea9a']
meat = ['#633e42', '#995548', '#bc7357', '#e19b70', '#ffcb94']
brown = ['#4f3846', '#785047', '#a37157', '#bf9473', '#e2b696']
white = ['#6c6673', '#a6a4a8', '#d6d6cd', '#f0efdc', '#fff9ec']
teal = ['#375052', '#4f7971', '#73a49b', '#a0c4b7', '#d8e3c7']
fish = ['#685057', '#ab7164', '#e38e7d', '#f7b4a1', '#ffe4c2']
dark = ['#3f3646', '#514252', '#756873', '#93828d', '#c0a3aa']

def polygon(pts, col, edge=None):
    d.polygon(pts, fill=col, outline=edge)

def line(pts, c, w=1):
    d.line(pts, fill=c, width=w)

def ell(box, c, edge=None):
    d.ellipse(box, fill=c, outline=edge)

def rect(box, c, edge=None):
    d.rectangle(box, fill=c, outline=edge)

def pt(x, y, c):
    d.point((x, y), fill=c)

def points(ps, c):
    d.point(ps, fill=c)

def form(pts, p, base=2):
    polygon(pts, p[base], p[0])

def oval(x, y, w, h, p):
    ell((x, y, x + w, y + h), p[2], p[0])
    ell((x + 1, y + 1, x + w - 2, y + h - 2), p[3])
    line([(x + 2, y + max(2, h // 3)), (x + min(w - 2, 4), y + 1)], p[4])

def berrylet(x, y, p=berry):
    ell((x, y, x + 5, y + 5), p[1], p[0])
    line([(x + 1, y + 3), (x + 1, y + 1), (x + 3, y + 1)], p[3])
    pt(x + 2, y + 1, p[4])
    pt(x + 4, y + 3, p[2])

def strawberry(x, y):
    pts = [(x, y + 2), (x + 2, y), (x + 6, y + 1), (x + 8, y + 3), (x + 7, y + 6), (x + 4, y + 9), (x + 2, y + 8), (x, y + 5)]
    form(pts, berry)
    polygon([(x + 1, y + 2), (x + 4, y + 1), (x + 6, y + 2), (x + 2, y + 6)], berry[3])
    points([(x + 1, y + 3), (x + 4, y + 4), (x + 3, y + 6), (x + 6, y + 3)], cream[3])
    line([(x + 1, y), (x + 4, y + 1), (x + 7, y)], leaf[1])
    pt(x + 3, y, leaf[3])

def sprig(x, y):
    line([(x - 3, y + 5), (x + 2, y)], leaf[0])
    polygon([(x - 1, y + 2), (x - 5, y + 1), (x - 6, y - 3), (x - 2, y - 2), (x, y)], leaf[2], leaf[0])
    polygon([(x, y + 1), (x + 1, y - 3), (x + 7, y - 4), (x + 5, y), (x + 2, y + 2)], leaf[2], leaf[0])
    line([(x + 1, y), (x + 5, y - 2)], leaf[3])

def flower(x, y, p=sugar):
    for dx, dy in [(-2, 0), (1, -2), (3, 1), (1, 3), (-1, 3)]:
        ell((x + dx - 1, y + dy - 1, x + dx + 1, y + dy + 1), p[3], p[2])
    rect((x, y, x + 1, y + 1), gold[3])
    pt(x, y, gold[4])

def star(x, y, r, p=gold):
    pts = [(int(round(x + cos(-pi / 2 + i * pi / 5) * (r if i % 2 == 0 else r * 0.47))), int(round(y + sin(-pi / 2 + i * pi / 5) * (r if i % 2 == 0 else r * 0.47)))) for i in range(10)]
    form(pts, p, 3)
    line([pts[9], pts[0], (x, y), pts[6]], p[4])
    line([pts[2], (x, y), pts[4]], p[2])
    pt(x, y, p[1])

def heart(x, y, w, p):
    pts = [(x, y + 2), (x + 2, y), (x + w // 2, y + 2), (x + w - 2, y), (x + w, y + 2), (x + w - 1, y + 5), (x + w // 2, y + w - 1), (x + 1, y + 5)]
    form(pts, p, 2)
    line([(x + 1, y + 3), (x + 2, y + 1), (x + 3, y + 1)], p[4])

def dish(p=bowl):
    form([(1, 15), (7, 10), (21, 8), (29, 11), (30, 16), (26, 22), (17, 26), (8, 25), (3, 21)], p)
    polygon([(2, 16), (8, 20), (17, 22), (25, 19), (28, 16), (26, 21), (17, 25), (9, 24), (4, 20)], p[1])
    ell((3, 9, 29, 21), p[3], p[0])
    ell((6, 11, 26, 19), p[2])
    line([(4, 16), (7, 19), (13, 20)], p[4])

def bowlbody(p=bowl, soup=toast):
    form([(2, 12), (6, 9), (22, 8), (29, 11), (30, 15), (27, 22), (21, 26), (12, 28), (6, 25), (3, 19)], p)
    polygon([(5, 17), (11, 20), (21, 20), (28, 17), (25, 23), (20, 25), (12, 26), (7, 23)], p[1])
    polygon([(7, 18), (12, 20), (15, 20), (12, 25), (9, 23)], p[2])
    line([(17, 26), (21, 25), (24, 23)], p[3])
    ell((2, 8, 30, 20), p[3], p[0])
    ell((4, 10, 28, 18), soup[2], soup[0])
    line([(5, 12), (10, 10), (17, 10)], soup[3])
    line([(4, 16), (9, 18), (16, 19)], p[4])

def scoop(x, y, w, h, p, flavor=None):
    pts = [(x, y + h - 2), (x + 1, y + 3), (x + 4, y + 1), (x + w // 2, y), (x + w - 3, y + 1), (x + w, y + 5), (x + w, y + h - 1), (x + w - 3, y + h + 1), (x + w - 5, y + h), (x + w // 2, y + h + 2), (x + 3, y + h + 1)]
    form(pts, p)
    polygon([(x + 2, y + 4), (x + 5, y + 2), (x + w // 2 + 1, y + 1), (x + w - 2, y + 3), (x + w - 5, y + 4), (x + w // 2, y + 4), (x + 3, y + 7)], p[3])
    line([(x + 3, y + 4), (x + 5, y + 2), (x + w // 2, y + 2)], p[4])
    if flavor is not None:
        for ox, oy in [(3, 6), (w - 4, 4), (w - 6, h - 2)]:
            line([(x + ox, y + oy), (x + ox + 2, y + oy)], flavor)

def bottle(p, top=berry):
    form([(11, 2), (20, 2), (20, 7), (19, 8), (20, 10), (24, 13), (25, 16), (24, 26), (21, 28), (9, 28), (7, 25), (7, 15), (10, 12), (13, 10), (13, 7), (11, 6)], silver, 3)
    form([(9, 15), (15, 13), (23, 14), (23, 25), (20, 27), (10, 26), (9, 24)], p)
    polygon([(11, 16), (19, 15), (22, 15), (21, 17), (11, 18)], p[3])
    line([(10, 17), (10, 23)], cream[4])
    line([(22, 19), (21, 24), (18, 25)], p[1])
    rect((12, 3, 20, 6), top[2], top[0])
    line([(13, 3), (18, 3)], top[4])
    line([(14, 8), (14, 10)], white[4])

def noodles(x, y, p=gold):
    for a, b in [(0, 0), (5, -1), (9, 1)]:
        line([(x + a, y + b), (x + a + 2, y + b - 2), (x + a + 4, y + b - 1), (x + a + 2, y + b + 1), (x + a + 3, y + b + 2), (x + a + 6, y + b)], p[4], 1)

def dumpling(x, y, p=cream):
    form([(x, y + 6), (x + 2, y + 3), (x + 4, y + 1), (x + 7, y), (x + 10, y + 3), (x + 12, y + 7), (x + 9, y + 9), (x + 3, y + 9)], p, 3)
    for v in (3, 6, 9):
        line([(x + v, y + 3), (x + v + 1, y + 5)], p[1])
        pt(x + 2, y + 6, p[4])

def pie(p, top='fruit'):
    form([(2, 17), (20, 7), (25, 7), (29, 11), (28, 21), (6, 27), (2, 25)], crust)
    polygon([(3, 19), (24, 12), (27, 13), (26, 19), (5, 25), (3, 24)], p[2], p[0])
    line([(5, 22), (10, 20), (14, 20), (19, 17), (23, 17)], p[3], 2)
    polygon([(2, 17), (20, 6), (25, 6), (29, 10), (26, 13), (5, 20)], p[3], p[0])
    line([(4, 17), (21, 8), (25, 8)], p[4])
    line([(5, 26), (14, 24), (26, 20)], crust[3])
    if top == 'lattice':
        for x, y in [(9, 14), (14, 12), (19, 10)]:
            line([(x, y), (x + 4, y + 3)], crust[3], 2)
        line([(10, 17), (24, 10)], crust[2], 2)
        line([(14, 13), (23, 8)], crust[4])

def steak(x, y, w=18, h=12, p=meat):
    oval(x, y, w, h, p)
    for a, b in [(4, 2), (9, 1), (13, 3)]:
        line([(x + a, y + b), (x + a + 2, y + b + 3)], p[1])
        pt(x + a, y + b, p[3])

def bun(x, y, w, h, p=toast):
    form([(x, y + h - 2), (x + 2, y + 3), (x + 5, y + 1), (x + w - 6, y), (x + w - 2, y + 2), (x + w, y + 5), (x + w, y + h - 1), (x + w - 3, y + h + 1), (x + 3, y + h + 1)], p)
    polygon([(x + 2, y + 4), (x + 6, y + 2), (x + w - 6, y + 1), (x + w - 3, y + 3), (x + w - 7, y + 5), (x + 4, y + 6)], p[3])
    line([(x + 3, y + 4), (x + 6, y + 2), (x + 10, y + 2)], p[4])

def sprinkle(points_, p):
    for x, y in points_:
        line([(x, y), (x + 1, y - 1)], p)

def candy_stick(x, y):
    polygon([(x, y), (x + 3, y + 1), (x - 3, y + 10), (x - 5, y + 10), (x - 6, y + 8)], crust[2], crust[0])
    line([(x - 1, y + 4), (x - 4, y + 8)], crust[3])

def cookie(p, spots):
    oval(3, 5, 25, 21, p)
    line([(6, 21), (11, 23), (21, 22), (25, 19)], p[1], 2)
    for x, y in spots:
        ell((x, y, x + 2, y + 2), jam[1])
        pt(x, y, jam[3])

def tart(x, y, w=21, p=toast):
    form([(x, y + 4), (x + 2, y + 2), (x + 6, y), (x + w - 5, y), (x + w, y + 4), (x + w - 2, y + 12), (x + w - 6, y + 15), (x + 5, y + 15), (x + 1, y + 12)], p)
    ell((x, y, x + w, y + 9), p[4], p[0])
    ell((x + 3, y + 2, x + w - 3, y + 8), p[1])
    line([(x + 5, y + 3), (x + w - 6, y + 2)], p[3])
    for j in range(3, w - 2, 4):
        line([(x + j, y + 10), (x + j + 1, y + 13)], p[1])

def plate_food(name):
    if name.endswith('_slice'):
        if 'cheesecake' in name:
            pie(cream)
            line([(5, 20), (13, 18), (16, 18), (23, 15)], jam[2], 2)
            strawberry(17, 3)
            sprig(17, 5)
        elif name.startswith('apple'):
            pie(toast, 'lattice')
            polygon([(17, 6), (22, 2), (26, 3), (26, 6), (21, 9)], berry[2], berry[0])
            line([(19, 6), (23, 4), (25, 4)], cream[4])
        elif name.startswith('chorus'):
            pie(plum)
            berrylet(18, 4, plum)
            berrylet(21, 6, plum)
            flower(12, 12, sugar)
        elif name.startswith('rose'):
            pie(jam)
            flower(21, 7, sugar)
            sprig(15, 8)
            line([(7, 17), (11, 14), (14, 15)], cream[4], 2)
        else:
            pie(plum, 'lattice')
            berrylet(19, 4, plum)
            berrylet(23, 6, plum)
    elif name == 'uwu_pancake_stack':
        for x, y in [(2, 17), (4, 13), (3, 9)]:
            oval(x, y, 24, 10, toast)
        line([(6, 15), (9, 16), (12, 15), (15, 16), (19, 15), (20, 19)], brown[1], 2)
        rect((12, 9, 17, 11), gold[4], gold[1])
        points([(9, 19), (17, 18)], brown[0])
        line([(12, 20), (13, 21), (15, 19)], brown[0])
        flower(23, 8)
    elif name == 'pink_melon_popsicle':
        candy_stick(13, 18)
        form([(6, 13), (12, 5), (18, 3), (24, 6), (26, 10), (19, 19), (14, 23), (8, 21), (5, 17)], berry)
        polygon([(7, 13), (14, 5), (19, 5), (23, 7), (21, 10), (13, 20), (8, 18)], berry[3])
        line([(8, 13), (15, 6), (18, 6)], berry[4], 2)
        line([(8, 19), (14, 22), (24, 11)], leaf[1], 2)
        points([(14, 12), (16, 9), (16, 15), (20, 10)], bean[1])
    elif name == 'pink_fruit_salad':
        bowlbody(greenbowl, sugar)
        berrylet(6, 12)
        star(15, 12, 4)
        berrylet(21, 11, plum)
        sprig(18, 8)
    elif name == 'sweet_berry_cookie':
        cookie(crust, [(8, 10), (16, 8), (21, 14), (12, 17), (6, 16), (18, 21)])
        pt(13, 12, cream[4])
    elif name == 'blush_cabbage_roll':
        form([(3, 14), (8, 9), (19, 5), (24, 6), (28, 10), (28, 15), (21, 21), (10, 26), (5, 24), (2, 20)], leaf)
        polygon([(4, 15), (9, 10), (21, 6), (24, 8), (21, 12), (10, 18)], mint[2])
        line([(7, 11), (9, 17), (16, 21)], leaf[1], 2)
        line([(15, 8), (18, 13), (23, 17)], leaf[3], 2)
        oval(3, 15, 11, 10, sugar)
        line([(5, 20), (8, 17), (12, 18), (10, 21), (7, 22)], berry[2], 2)
    elif name in ('strawberry_milk', 'dragon_breath_soda', 'chorus_blossom_tea'):
        bottle({'strawberry_milk': sugar, 'dragon_breath_soda': plum, 'chorus_blossom_tea': toast}[name], plum if 'dragon' in name else berry)
        if name == 'strawberry_milk':
            heart(13, 18, 6, berry)
        elif 'dragon' in name:
            for x, y in [(14, 18), (18, 22), (21, 17), (17, 10)]:
                ell((x, y, x + 2, y + 2), plum[4])
        else:
            flower(16, 19)
            line([(21, 8), (24, 11), (24, 16)], brown[0])
            rect((23, 15, 26, 18), mint[3], leaf[0])
    elif name == 'shulker_soup':
        bowlbody(plum, cream)
        polygon([(7, 12), (9, 7), (16, 5), (19, 7), (17, 12), (10, 14)], plum[2], plum[0])
        line([(9, 9), (15, 7), (18, 8)], plum[4])
        berrylet(20, 13, plum)
        sprinkle([(13, 14), (18, 14)], leaf[2])
    elif name == 'strider_moss_stew':
        bowlbody(brown, meat)
        steak(7, 10, 8, 5)
        sprig(20, 11)
        sprinkle([(13, 15), (21, 14), (24, 15)], gold[3])
    elif name == 'nether_fungus_skewer':
        line([(2, 28), (27, 3)], brown[0], 3)
        line([(2, 27), (27, 3)], toast[3])
        for x, y, p in [(6, 17, meat), (13, 12, plum), (18, 6, berry)]:
            line([(x + 3, y + 3), (x + 5, y + 6)], cream[3], 3)
            form([(x, y + 3), (x + 1, y), (x + 4, y - 2), (x + 7, y), (x + 8, y + 3), (x + 4, y + 5)], p)
            points([(x + 2, y + 1), (x + 4, y)], p[4])
    elif name in ('stuffed_hoglin_roast', 'flower_stuffed_chicken', 'honey_glazed_ham'):
        dish(greenbowl if 'chicken' in name else bowl)
        p = toast if 'chicken' in name else meat
        steak(6, 7, 21, 13, p)
        oval(4, 13, 10, 8, p)
        if 'hoglin' in name:
            polygon([(4, 16), (1, 14), (1, 11), (4, 14), (7, 15)], cream[3], brown[0])
            line([(16, 10), (19, 11), (22, 10)], p[4], 2)
            sprig(23, 7)
        elif 'chicken' in name:
            line([(22, 11), (26, 6)], crust[2], 4)
            ell((24, 3, 28, 7), cream[3], cream[0])
            flower(11, 13)
            sprig(19, 9)
        else:
            for x in (9, 14, 19):
                line([(x, 9), (x + 5, 14), (x + 8, 13)], gold[2])
                line([(x + 6, 9), (x + 2, 15)], gold[3])
                oval(13, 5, 6, 3, gold)
    elif name == 'magma_gelatin':
        dish()
        form([(6, 18), (9, 9), (12, 5), (21, 5), (25, 10), (27, 17), (24, 20), (12, 22), (7, 20)], meat)
        polygon([(9, 17), (11, 9), (14, 7), (20, 7), (19, 10), (14, 13), (13, 18)], gold[2])
        line([(11, 11), (13, 8), (17, 7)], gold[4], 2)
        line([(21, 10), (23, 16), (20, 18)], berry[1], 2)
    elif name == 'blossom_ice_cream_bucket':
        form([(4, 13), (7, 9), (25, 9), (28, 13), (25, 25), (21, 28), (11, 28), (6, 25)], silver)
        polygon([(6, 16), (15, 18), (22, 17), (25, 15), (23, 24), (19, 26), (10, 25)], silver[2])
        line([(7, 17), (8, 24), (12, 26)], silver[4], 2)
        ell((3, 9, 29, 18), silver[3], silver[0])
        scoop(7, 5, 12, 8, cream)
        scoop(14, 4, 11, 9, sugar, jam[2])
        scoop(5, 9, 11, 6, sugar)
        flower(20, 5)
        heart(13, 18, 8, sugar)
        line([(4, 13), (2, 18), (4, 21), (7, 21)], silver[1])
    elif name == 'strawberry_ice_cream_cake':
        form([(3, 12), (6, 8), (13, 6), (22, 6), (28, 10), (29, 14), (28, 22), (23, 26), (12, 28), (5, 25), (2, 21)], sugar)
        polygon([(4, 16), (11, 19), (21, 18), (27, 14), (27, 17), (21, 21), (11, 22), (4, 19)], cream[3])
        polygon([(4, 21), (12, 24), (22, 22), (27, 19), (26, 22), (21, 25), (12, 26), (5, 24)], berry[2])
        ell((2, 6, 29, 18), cream[3], cream[0])
        line([(4, 14), (7, 16), (9, 16), (10, 19), (12, 17), (16, 18), (18, 20), (19, 17), (24, 16), (27, 13)], cream[4], 2)
        strawberry(8, 6)
        strawberry(17, 3)
        line([(20, 2), (23, 1), (25, 2)], leaf[1])
        points([(18, 13), (24, 12), (6, 13)], berry[3])
    elif name == 'heartberry_burger':
        bun(3, 17, 25, 7)
        line([(4, 20), (8, 22), (13, 23), (23, 21), (27, 18)], brown[0], 3)
        line([(4, 18), (8, 17), (12, 19), (17, 18), (21, 18), (26, 16)], leaf[2], 3)
        polygon([(5, 16), (23, 13), (27, 15), (22, 17), (18, 21), (14, 18), (8, 19)], gold[3], gold[1])
        line([(6, 16), (12, 17), (22, 14), (26, 15)], berry[1], 3)
        bun(3, 6, 24, 10)
        sprinkle([(9, 9), (18, 8), (22, 10), (14, 12), (7, 12)], cream[4])
    elif name in ('blossom_hot_pocket', 'tourtiere_hand_pie'):
        form([(2, 19), (5, 13), (14, 7), (20, 5), (25, 7), (29, 13), (28, 18), (21, 22), (11, 26), (5, 25)], crust)
        polygon([(4, 19), (7, 14), (19, 7), (24, 8), (26, 11), (19, 16), (10, 22)], crust[3])
        line([(6, 23), (12, 23), (22, 18), (26, 15)], crust[1], 2)
        for x, y in [(10, 14), (15, 11), (20, 9)]:
            line([(x, y), (x + 3, y + 1)], meat[1], 2)
            pt(x, y - 1, crust[4])
        if 'blossom' in name:
            flower(20, 14)
        else:
            line([(4, 21), (10, 24), (19, 20), (26, 16)], meat[0])
            sprinkle([(9, 24), (15, 22), (22, 19)], leaf[2])
    elif name in ('uwu_ramen_bowl', 'cozy_udon_hotpot'):
        if 'udon' in name:
            ell((0, 13, 6, 19), None, brown[0])
            ell((26, 10, 31, 16), None, brown[0])
        bowlbody(greenbowl if 'udon' in name else sugar, toast)
        noodles(7, 14)
        ell((16, 7, 23, 15), cream[4], cream[0])
        ell((18, 10, 22, 14), gold[2])
        pt(19, 11, gold[4])
        line([(22, 11), (28, 2)], brown[1], 2)
        line([(24, 11), (30, 3)], toast[3])
        if 'ramen' in name:
            line([(10, 21), (11, 22), (12, 21)], berry[0])
            line([(18, 21), (19, 22), (20, 21)], berry[0])
            line([(14, 23), (15, 24), (16, 23)], berry[0])
        else:
            polygon([(6, 12), (6, 7), (12, 6), (14, 11)], leaf[0])
            line([(8, 10), (10, 8)], leaf[2])
    elif name in ('cherry_picnic_bento', 'cherry_onigiri_bento', 'sakura_katsu_curry', 'maple_glazed_salmon_bowl'):
        if name.endswith('bento'):
            form([(2, 13), (19, 5), (29, 10), (29, 20), (13, 28), (2, 23)], berry)
            polygon([(3, 14), (19, 7), (27, 11), (13, 20)], brown[1], berry[0])
            line([(13, 20), (13, 26), (27, 20)], berry[1], 2)
            polygon([(5, 13), (10, 9), (14, 9), (16, 13), (10, 16)], cream[3], cream[0])
            polygon([(9, 14), (12, 12), (14, 14), (11, 16)], dark[0])
            if 'onigiri' in name:
                polygon([(13, 11), (18, 6), (21, 5), (24, 10), (19, 13)], cream[4], cream[0])
                polygon([(17, 11), (20, 9), (22, 11), (19, 13)], dark[1])
                flower(22, 13)
            else:
                strawberry(18, 9)
                sprig(11, 17)
        else:
            dish(greenbowl)
            oval(5, 10, 16, 10, white)
            if name.endswith('curry'):
                ell((13, 12, 26, 21), toast[1], meat[0])
                steak(11, 7, 15, 8, toast)
                for xx in (13, 17, 21):
                    line([(xx, 9), (xx + 1, 13)], brown[0])
                    pt(xx + 2, 10, crust[4])
            else:
                polygon([(8, 12), (20, 6), (26, 8), (27, 12), (15, 18), (7, 16)], fish[2], fish[0])
                for x in (11, 15, 19):
                    line([(x, 12), (x + 2, 11), (x + 4, 12)], cream[4])
                    sprig(22, 15)
    elif name == 'berry_cinnamon_roll':
        oval(2, 6, 26, 19, crust)
        line([(5, 16), (7, 10), (13, 8), (20, 8), (24, 11), (24, 15), (19, 18), (12, 18), (10, 15), (11, 12), (16, 11), (19, 13), (17, 15), (14, 14)], brown[1], 2)
        line([(6, 18), (10, 20), (20, 19), (24, 16)], sugar[4], 2)
        berrylet(23, 7)
    elif name == 'starberry_waffle_stack':
        for y in (13, 9):
            form([(2, y + 4), (18, y - 3), (28, y + 1), (28, y + 6), (12, y + 13), (2, y + 9)], toast)
            polygon([(3, y + 4), (18, y - 2), (27, y + 1), (12, y + 9)], gold[3], toast[1])
            for i in range(3):
                line([(6 + i * 4, y + 4 - i * 2), (15 + i * 4, y + 7 - i * 2)], toast[2])
                line([(8 + i * 3, y + 6 + i), (21 + i * 2, y + i)], toast[1])
        star(18, 8, 5)
        berrylet(22, 8)
    elif name == 'creamy_mushroom_stew':
        bowlbody(brown, cream)
        for x, y in [(7, 10), (18, 9)]:
            form([(x, y + 4), (x + 1, y + 1), (x + 4, y), (x + 7, y + 2), (x + 8, y + 4)], toast)
            rect((x + 3, y + 4, x + 5, y + 6), cream[3])
            pt(x + 3, y + 1, crust[4])
        sprinkle([(12, 15), (19, 14), (23, 15)], leaf[1])
    elif name == 'loaded_heart_potato':
        dish()
        oval(4, 8, 23, 14, brown)
        polygon([(6, 15), (12, 11), (21, 9), (25, 11), (18, 16), (10, 19)], crust[3], brown[1])
        line([(8, 16), (14, 14), (15, 15), (22, 11)], cream[3], 3)
        heart(15, 9, 6, berry)
        sprinkle([(9, 16), (18, 16), (21, 13)], leaf[2])
    elif name == 'pastel_sakura_dumplings':
        dish(greenbowl)
        dumpling(5, 9, sugar)
        dumpling(15, 7, cream)
        dumpling(11, 14, mint)
        flower(24, 15)
    elif name == 'moonberry_pudding':
        dish()
        form([(7, 18), (10, 7), (15, 5), (21, 5), (25, 8), (27, 18), (22, 21), (14, 23), (8, 21)], plum)
        ell((10, 5, 24, 12), jam[1], plum[0])
        line([(10, 12), (10, 17), (13, 20)], plum[3], 2)
        line([(12, 9), (15, 7), (21, 7)], jam[3])
        berrylet(19, 3, plum)
        sprig(17, 5)
    elif name == 'chicken_flower_pot_pie':
        tart(3, 9, 25, crust)
        ell((3, 6, 28, 17), crust[3], crust[0])
        for x in (9, 14, 19):
            line([(x, 10), (x + 2, 12)], meat[1])
            flower(21, 9)
            sprig(9, 9)
    elif name == 'heart_omurice':
        dish(greenbowl)
        bun(5, 8, 21, 10, gold)
        line([(9, 14), (10, 11), (13, 10), (15, 12), (18, 9), (21, 10), (21, 13), (15, 17), (9, 14)], berry[1], 2)
        sprig(22, 16)
    elif name == 'golden_taiyaki':
        polygon([(2, 13), (4, 7), (10, 12), (15, 7), (21, 6), (26, 9), (29, 14), (27, 18), (21, 22), (14, 22), (9, 18), (3, 23), (4, 17)], toast[2], toast[0])
        polygon([(10, 13), (15, 9), (21, 8), (25, 10), (22, 12), (17, 13), (13, 18)], toast[3])
        line([(9, 12), (9, 18)], toast[0])
        ell((22, 11, 24, 13), brown[0])
        pt(22, 11, cream[4])
        for x, y in [(12, 13), (16, 12), (14, 16), (18, 15)]:
            line([(x, y), (x + 2, y + 2), (x + 4, y)], toast[1])
    elif name == 'sakura_melonpan':
        bun(2, 6, 27, 17)
        line([(4, 20), (10, 23), (17, 23), (26, 20)], crust[1], 2)
        for x in (7, 13, 19):
            line([(x, 9), (x + 6, 18)], toast[1])
            line([(x, 19), (x + 7, 9)], crust[4])
            flower(22, 10)
    elif name == 'strawberry_shokupan':
        form([(3, 13), (3, 7), (6, 5), (13, 4), (18, 6), (22, 6), (27, 8), (29, 12), (26, 15), (24, 24), (12, 28), (4, 25), (5, 15)], crust)
        polygon([(5, 8), (10, 6), (16, 7), (20, 9), (20, 13), (17, 15), (16, 24), (11, 25), (6, 23), (7, 13)], cream[3], crust[1])
        heart(8, 12, 8, berry)
        line([(21, 16), (21, 23), (17, 25)], crust[3], 2)
    elif name == 'sweet_bean_dorayaki':
        oval(3, 13, 25, 13, toast)
        ell((3, 12, 27, 21), bean[1], bean[0])
        oval(2, 6, 25, 13, toast)
        line([(7, 14), (12, 16), (19, 15)], toast[2])
        flower(21, 8)
    elif name == 'sakura_mochi_ice_cream':
        dish(greenbowl)
        scoop(5, 9, 14, 10, sugar)
        scoop(15, 11, 12, 9, cream)
        polygon([(16, 15), (19, 12), (23, 12), (26, 15), (25, 18), (20, 21), (17, 19)], sugar[2], sugar[1])
        ell((19, 14, 24, 18), cream[4])
        flower(12, 9)
    elif name == 'maple_ice_cream_sundae':
        form([(4, 14), (27, 13), (24, 18), (18, 22), (17, 26), (22, 27), (23, 29), (9, 29), (8, 28), (13, 25), (13, 22), (7, 19)], ice, 3)
        scoop(6, 9, 13, 8, cream)
        scoop(15, 8, 10, 8, toast)
        scoop(10, 3, 12, 8, sugar)
        line([(8, 13), (12, 12), (14, 14), (18, 12), (20, 13), (22, 11)], toast[1], 2)
        berrylet(17, 1)
        line([(23, 8), (27, 2)], brown[1], 2)
    elif name == 'maple_poutine':
        form([(2, 13), (19, 8), (29, 12), (26, 22), (10, 27), (4, 23)], berry)
        rng = random.Random(8)
        for i in range(14):
            x = rng.randrange(4, 24)
            y = rng.randrange(7, 14)
            line([(x, y), (x + 2, y + 8)], toast[0], 4)
            line([(x, y), (x + 2, y + 7)], gold[3], 2)
        line([(6, 15), (11, 14), (14, 16), (21, 13), (25, 14)], brown[1], 3)
        for x, y in [(7, 13), (15, 14), (21, 10)]:
            rect((x, y, x + 3, y + 2), cream[3], crust[1])
    elif name == 'nanaimo_heart_bar':
        form([(3, 12), (12, 6), (19, 5), (27, 9), (28, 19), (16, 26), (4, 22)], brown)
        polygon([(4, 15), (16, 20), (27, 13), (27, 16), (16, 23), (4, 19)], gold[3])
        polygon([(3, 12), (17, 5), (27, 9), (27, 12), (16, 19), (4, 15)], brown[1], brown[0])
        heart(12, 8, 8, sugar)
        line([(7, 20), (11, 21)], crust[2])
    elif name == 'maple_butter_tart':
        tart(3, 8, 25)
        polygon([(10, 12), (13, 11), (12, 8), (16, 10), (19, 7), (19, 11), (23, 10), (21, 14), (18, 16), (13, 17)], gold[3], toast[1])
        line([(14, 16), (18, 12)], gold[4])
        pt(7, 13, cream[3])
    else:
        raise ValueError(name)

def seed(x, y, w, h, p):
    form([(x, y + h - 2), (x + 1, y + 2), (x + w - 2, y), (x + w, y + 1), (x + w - 1, y + h - 3), (x + 2, y + h)], p)
    line([(x + 1, y + h - 3), (x + 2, y + 3), (x + w - 2, y + 1)], p[3])
    pt(x + 2, y + 2, p[4])

def grain(x, y, p):
    line([(x, y + 3), (x + 3, y), (x + 6, y)], p[0], 3)
    line([(x, y + 2), (x + 3, y), (x + 5, y)], p[3], 2)
    pt(x + 3, y, p[4])

def pod(x, y, p):
    form([(x, y + 13), (x + 2, y + 8), (x + 8, y + 3), (x + 15, y), (x + 19, y), (x + 20, y + 2), (x + 17, y + 7), (x + 9, y + 12), (x + 3, y + 15)], p)
    line([(x + 2, y + 12), (x + 8, y + 7), (x + 15, y + 3), (x + 18, y + 2)], p[3], 2)
    line([(x + 7, y + 12), (x + 12, y + 9), (x + 18, y + 5)], p[1])
    pt(x + 6, y + 11, p[4])

def crystals(x, y, p):
    for dx, dy, w, h in [(1, 4, 9, 8), (8, 0, 7, 12), (15, 5, 7, 7), (6, 8, 10, 6)]:
        form([(x + dx, y + dy + 2), (x + dx + 3, y + dy), (x + dx + w - 1, y + dy + 1), (x + dx + w, y + dy + h - 3), (x + dx + w - 3, y + dy + h), (x + dx + 1, y + dy + h - 1)], p)
        polygon([(x + dx + 1, y + dy + 2), (x + dx + 3, y + dy + 1), (x + dx + w - 2, y + dy + 2), (x + dx + 4, y + dy + 4)], p[4])

def product(n):
    if n == 'heartberry':
        line([(12, 13), (17, 6), (20, 3)], leaf[0], 2)
        sprig(17, 6)
        heart(3, 12, 15, berry)
        heart(14, 10, 12, jam)
        line([(7, 19), (11, 23), (14, 20)], berry[3])
        sprinkle([(9, 17), (7, 19), (14, 16), (18, 15), (22, 13)], gold[4])
    elif n == 'starfruit':
        form([(3, 17), (6, 12), (14, 8), (22, 5), (26, 5), (28, 8), (26, 12), (18, 18), (9, 22), (4, 22)], gold)
        polygon([(5, 17), (11, 12), (22, 7), (25, 6), (22, 10), (12, 17), (6, 20)], gold[3])
        line([(5, 20), (14, 14), (23, 10), (27, 7)], gold[1])
        line([(9, 17), (15, 12), (22, 9)], gold[4], 2)
        polygon([(22, 6), (24, 4), (26, 2), (29, 2), (27, 5), (25, 6)], leaf[2], leaf[0])
        star(21, 20, 9, gold)
        line([(19, 18), (21, 20), (22, 16)], cream[4])
        pt(21, 20, toast[1])
    elif n == 'moonberry':
        sprig(15, 6)
        line([(16, 8), (11, 17), (19, 15), (21, 9)], leaf[1], 2)
        for x, y in [(5, 11), (14, 8), (12, 17), (20, 14)]:
            oval(x, y, 8, 8, plum)
            line([(x + 4, y + 5), (x + 5, y + 4), (x + 6, y + 5), (x + 5, y + 6), (x + 4, y + 5)], plum[0])
            pt(x + 2, y + 2, ice[3])
    elif n == 'sakura_rice':
        line([(3, 23), (8, 20), (13, 16), (19, 9), (23, 3)], toast[1], 2)
        line([(10, 21), (11, 15), (11, 10)], leaf[2])
        for x, y in [(9, 16), (10, 11), (13, 8), (16, 5), (16, 12), (13, 17), (6, 19)]:
            grain(x, y, crust)
        for x, y in [(11, 24), (16, 21), (20, 25), (23, 18)]:
            grain(x, y, cream)
    elif n == 'frostpetal':
        line([(7, 27), (13, 20), (16, 13)], teal[1], 2)
        sprig(11, 20)
        for pts in [[(14, 14), (8, 12), (6, 7), (10, 6), (15, 10)], [(14, 13), (12, 6), (15, 2), (18, 4), (18, 10)], [(16, 12), (21, 5), (25, 5), (25, 8), (20, 13)], [(18, 14), (24, 11), (29, 13), (26, 17), (20, 17)], [(16, 15), (20, 19), (18, 23), (15, 21), (14, 17)], [(14, 15), (10, 19), (6, 18), (6, 15), (10, 13)]]:
            form(pts, ice, 3)
            line(pts[:2], ice[4])
        ell((12, 10, 19, 16), gold[2], teal[0])
        line([(14, 12), (17, 11)], cream[4])
    elif n == 'sweetbean_pods':
        pod(3, 6, leaf)
        pod(8, 13, mint)
        line([(25, 14), (28, 11), (29, 8)], leaf[0], 2)
        for x, y in [(10, 18), (15, 15), (20, 13)]:
            ell((x, y, x + 3, y + 3), leaf[3], leaf[1])
            pt(x + 1, y + 1, leaf[4])
    elif n == 'glowginger':
        for pts in [[(4, 18), (6, 14), (13, 14), (16, 8), (19, 5), (24, 5), (25, 8), (22, 13), (19, 17), (24, 17), (26, 20), (23, 23), (16, 22), (12, 26), (7, 26), (4, 23)]]:
            form(pts, toast)
        oval(4, 17, 9, 8, gold)
        oval(15, 7, 8, 7, crust)
        line([(12, 16), (16, 17), (18, 19)], toast[1], 2)
        line([(17, 15), (20, 16), (22, 15)], gold[3])
        for x, y in [(21, 4), (24, 13), (11, 21)]:
            pt(x, y, gold[4])
            sprig(21, 5)
    elif n == 'vanilla_bean':
        for x, y in [(2, 2), (6, -1)]:
            line([(x + 3, y + 23), (x + 8, y + 22), (x + 16, y + 15), (x + 21, y + 6), (x + 22, y + 3)], bean[0], 3)
            line([(x + 4, y + 22), (x + 8, y + 21), (x + 16, y + 13), (x + 20, y + 7)], brown[2])
            pt(x + 12, y + 17, crust[3])
        flower(21, 7, cream)
        sprig(21, 5)
    elif n == 'sakura_flour':
        form([(6, 9), (8, 6), (13, 7), (15, 5), (21, 7), (25, 6), (23, 12), (25, 16), (27, 25), (23, 28), (9, 28), (5, 25), (6, 15), (9, 12)], crust, 3)
        line([(8, 10), (14, 12), (21, 10)], bean[1], 2)
        ell((8, 6, 22, 11), cream[4], crust[0])
        form([(10, 20), (12, 16), (18, 15), (22, 18), (21, 23), (13, 25)], sugar, 3)
        flower(15, 19)
    elif n in ('heartberry_jam', 'sweetbean_paste'):
        if n.endswith('jam'):
            form([(7, 7), (10, 5), (22, 5), (25, 8), (24, 11), (26, 14), (25, 25), (22, 28), (9, 28), (6, 25), (6, 15), (8, 12)], silver, 3)
            form([(8, 14), (13, 13), (23, 13), (24, 24), (21, 26), (10, 26), (8, 24)], jam)
            rect((7, 6, 25, 10), sugar[2], jam[0])
            line([(8, 7), (23, 7)], sugar[4])
            line([(9, 16), (9, 22)], cream[4])
            heart(13, 16, 8, sugar)
            line([(24, 11), (27, 14)], berry[1], 2)
        else:
            dish(greenbowl)
            form([(5, 17), (8, 12), (13, 10), (17, 7), (17, 4), (20, 7), (18, 10), (23, 11), (25, 15), (25, 18), (18, 21), (11, 21)], bean)
            for x, y in [(9, 15), (13, 11), (12, 18)]:
                line([(x, y), (x + 5, y + 1), (x + 9, y - 1)], bean[3])
    elif n in ('shaved_ice', 'pink_shaved_ice'):
        p = ice if n == 'shaved_ice' else sugar
        bowlbody(greenbowl if n == 'shaved_ice' else bowl, ice)
        polygon([(3, 14), (7, 10), (9, 10), (10, 7), (13, 7), (14, 4), (16, 3), (18, 6), (21, 7), (24, 11), (28, 13), (27, 16), (19, 18), (9, 18)], p[3], p[0])
        for x, y in [(6, 13), (10, 12), (14, 8), (16, 14), (22, 11), (19, 13)]:
            line([(x, y), (x + 3, y - 2), (x + 4, y - 2)], p[4], 2)
        if n.startswith('pink'):
            line([(11, 10), (13, 9), (14, 11), (17, 10), (20, 13), (23, 13)], berry[2], 2)
            berrylet(18, 5)
    elif n in ('chocolate_syrup', 'maple_syrup', 'vanilla_extract'):
        p = {'chocolate_syrup': brown, 'maple_syrup': toast, 'vanilla_extract': bean}[n]
        bottle(p, brown)
        if n == 'chocolate_syrup':
            oval(12, 17, 8, 6, brown)
            line([(13, 21), (18, 18)], crust[3])
        elif n == 'maple_syrup':
            polygon([(13, 19), (15, 19), (14, 16), (17, 17), (19, 14), (19, 18), (22, 18), (21, 20), (17, 23), (15, 22), (12, 21)], gold[3], toast[1])
        else:
            flower(16, 19, cream)
    elif n == 'petal_sugar':
        crystals(3, 9, sugar)
        flower(19, 8)
        line([(7, 22), (9, 23), (14, 24)], plum[3])
        pt(25, 22, sugar[4])
        pt(27, 19, sugar[2])
    elif n.endswith('_seeds'):
        typ = n[:-6]
        if typ == 'sakura_rice':
            for x, y in [(5, 13), (9, 19), (14, 8), (17, 16), (19, 23)]:
                grain(x, y, crust)
            line([(5, 24), (10, 16)], leaf[1])
            pt(10, 14, leaf[3])
        elif typ == 'heartberry':
            for x, y in [(4, 14), (11, 8), (15, 17), (20, 11)]:
                seed(x, y, 6, 7, bean)
            sprig(9, 11)
        elif typ == 'starfruit':
            for x, y in [(4, 12), (12, 18), (16, 8)]:
                seed(x, y, 8, 8, toast)
            line([(18, 10), (20, 8), (24, 7)], gold[3], 2)
            pt(23, 6, gold[4])
        elif typ == 'frostpetal':
            for x, y in [(6, 13), (13, 18), (17, 10)]:
                seed(x, y, 6, 8, ice)
            line([(8, 12), (9, 7), (12, 5)], ice[2])
            line([(10, 8), (6, 7)], ice[3])
        elif typ == 'sweetbean':
            for x, y in [(4, 11), (17, 8), (13, 18)]:
                oval(x, y, 9, 7, bean)
                line([(x + 3, y + 5), (x + 6, y + 3)], cream[2])
        elif typ == 'glowginger':
            for x, y in [(4, 14), (14, 16), (17, 8)]:
                seed(x, y, 8, 7, gold)
                line([(x + 5, y + 1), (x + 8, y - 2)], mint[2], 2)
        else:
            for x, y in [(5, 16), (10, 11), (17, 8), (16, 17), (21, 18), (11, 22)]:
                seed(x, y, 4, 5, brown)
            line([(19, 8), (23, 5), (25, 5)], leaf[1])
    else:
        raise ValueError(n)

def orchard():
    """Paint ripe fruit on all canopy variants while preserving each source canopy's silhouette."""
    for family in ('heartbloom', 'moonberry', 'starpetal'):
        for i, key in enumerate(['', '_v0', '_v1', '_v2', '_v3']):
            f = a / 'block' / f'{family}_leaves{key}.png'
            canvas = Image.open(f).convert('RGBA').resize((32, 32), Image.Resampling.NEAREST)
            global d
            d = ImageDraw.Draw(canvas)
            x, y = [(7, 8), (9, 7), (6, 14), (12, 9), (7, 9)][i]
            xx, yy = [(23, 20), (22, 23), (24, 12), (5, 21), (20, 22)][i]
            for px, py in [(x, y), (xx, yy)]:
                line([(px - 1, py - 5), (px + 1, py - 3), (px, py)], leaf[0], 2)
                pt(px - 2, py - 5, leaf[3])
                if family == 'heartbloom':
                    heart(px - 4, py - 1, 8, berry)
                elif family == 'starpetal':
                    star(px, py + 2, 5, gold)
                else:
                    berrylet(px - 3, py, plum)
                    berrylet(px + 1, py + 2, plum)
                    pt(px + 4, py + 5, plum[0])
            yield (a / 'block' / f'{family}_leaves_fruiting{key}.png', canvas)

def sprites():
    global d
    for name in ids:
        canvas = Image.new("RGBA", (32, 32))
        d = ImageDraw.Draw(canvas)
        if name in dishes:
            plate_food(name)
        else:
            product(name)
        yield a / "item" / f"{name}.png", canvas
    yield from orchard()


def contact_sheet(path, items):
    columns, cell_width, cell_height = 8, 132, 92
    sheet = Image.new("RGB", (columns * cell_width, math.ceil(len(items) / columns) * cell_height), "#e6e0dc")
    labels = ImageDraw.Draw(sheet)
    for i, (name, item) in enumerate(items):
        x = i % columns * cell_width
        y = i // columns * cell_height
        preview = item.resize((64, 64), Image.Resampling.NEAREST)
        sheet.paste(preview, (x + (cell_width - 64) // 2, y), preview)
        words = name.split("_")
        split = (len(words) + 1) // 2
        for j, row in enumerate((" ".join(words[:split]), " ".join(words[split:]))):
            labels.text((x + 4, y + 62 + j * 12), row, fill="#413849")
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Write the rendered pantry sprites into the project")
    parser.add_argument("--sheet", type=Path, help="Save an enlarged contact sheet at this file")
    args = parser.parse_args()
    items, hashes, stale = [], set(), []
    for target, image in sprites():
        if args.write:
            image.save(target, optimize=True)
        if not target.exists():
            stale.append(str(target.relative_to(root)))
            continue
        with Image.open(target) as saved:
            if image.tobytes() != saved.convert("RGBA").tobytes() or saved.size != image.size:
                stale.append(str(target.relative_to(root)))
        if target.parent.name == "item":
            items.append((target.stem, image))
            opacity = {alpha for alpha, count in enumerate(image.getchannel("A").histogram()) if count}
            assert opacity == {0, 255}, f"{target.stem} has non-pixel transparency"
            colors = [pixel for pixel in image.getcolors(1024) if pixel[1][3]]
            luma = [0.2126 * red + 0.7152 * green + 0.0722 * blue for _, (red, green, blue, _) in colors]
            assert max(luma) - min(luma) > 100, f"{target.stem} has insufficient highlight/shadow contrast"
            hashes.add(hashlib.sha256(image.tobytes()).digest())
    if stale:
        raise SystemExit("Pantry sprite mismatch: " + ", ".join(stale))
    if len(hashes) != len(ids):
        raise SystemExit("Two registered pantry items share their artwork")
    if args.sheet:
        contact_sheet(args.sheet, items)
    print(f"Pantry art verified: {len(dishes)} prepared dishes, {len(ingredients)} ingredients, {len(seeds)} seeds, 15 ripe canopies.")

if __name__ == "__main__":
    main()
