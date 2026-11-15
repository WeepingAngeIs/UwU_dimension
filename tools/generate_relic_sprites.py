"""Draw original 32-pixel Sakura relics and their two houses' counter weapons."""
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/uwudimension"
ITEMS = ROOT/"textures/item"
# Outline, haft, shaded face, midtone, polished edge, inlay, ferrule.
PETAL = ("#51394e","#795565","#ad6b89","#d695af","#f4cbd8","#ecc78f","#ad856a")
DUSK = ("#352e49","#58465e","#715680","#9982b1","#d7bfde","#c2b5e2","#8b7794")
REEF = ("#374456","#596574","#518887","#76afac","#c4dad3","#e2bb87","#a68167")
FROST = ("#465166","#61667e","#7593aa","#a0b8c9","#dbe3df","#cbb3d4","#8b86a4")
GOLD = ("#524052","#70545b","#b48261","#d8aa7c","#edcfac","#eab3c6","#ab8172")
INK = ("#32304a","#574665","#785383","#a673a6","#d9bedb","#79b5bc","#7c7796")


class Sprite:
    def __init__(self, palette):
        self.img=Image.new("RGBA",(32,32))
        self.draw=ImageDraw.Draw(self.img)
        self.c=palette
    @staticmethod
    def xy(point):
        u,v=point
        return round(2.7+.705*(u+v)),round(29.3+.705*(v-u))
    def line(self, index, points, width=1):
        self.draw.line([self.xy(p) for p in points],self.c[index],width=width,joint="curve")
    def fill(self,index,points):
        self.draw.polygon([self.xy(p) for p in points],self.c[index])
    def point(self,index,u,v):
        self.draw.point(self.xy((u,v)),self.c[index])
    def shaft(self, length):
        self.line(0,[(0,0),(length,0)],4)
        self.line(1,[(0,-.6),(length,-.6)],2)
        self.line(6,[(0,-1.4),(length,-1.4)])
        for u in range(3,min(length,16),3):
            self.line(2,[(u,-1),(u-1,1)])
        self.line(5,[(0,-1),(0,1)],2)
    def jewel(self,u,v,k=1):
        for n,r in [(0,k+1),(5,k)]:
            self.fill(n,[(u-r,v),(u,v-r),(u+r,v),(u,v+r)])
        self.point(4,u,v-.5)
    def blade(self,points,ridge,edge):
        self.fill(0,points)
        self.fill(2,ridge)
        self.line(4,edge)
    def knot(self,u,v):
        self.fill(2,[(u,v),(u-3,v-3),(u-4,v-1),(u-1,v+1),(u-4,v+3),(u-1,v+3)])
        self.point(5,u,v)


def make(shape, palette, key, special="", stage=0):
    a=Sprite(palette)
    bits=hashlib.sha256(key.encode()).digest()
    length=34+bits[0]%3
    tip=length+1
    if shape in ("spear","trident","wand","axe","greatsword","scythe","dagger","scepter"):
        a.shaft(24 if shape not in ("dagger","greatsword") else 11)
    if shape in ("spear","greatsword","dagger"):
        base={"spear":20,"dagger":11,"greatsword":10}[shape]
        width={"spear":3,"dagger":3,"greatsword":5}[shape]
        q=width
        a.blade([(base,-1),(base+2,-q),(base+5,-q),(tip,0),(base+5,q),(base+1,q-1)],
                [(base+2,-q+1),(base+6,-q+1),(tip-2,0),(base+5,1),(base+2,2)],
                [(base+3,-q+1),(base+7,-q+1),(tip-1,0)])
        a.line(3,[(base+2,1),(base+6,0),(tip-4,0)])
        a.line(6,[(base-2,-q-1),(base+1,-q),(base+1,q),(base-2,q+1)],2)
        a.jewel(base,0)
        if special=="lantern":
            a.line(6,[(base,1),(base-1,5)])
            a.jewel(base-1,6,2)
        if special=="blossom":
            a.knot(base+1,-3)
        if special=="rose":
            a.fill(2,[(base,-3),(base-3,-6),(base+1,-5),(base+2,-3)])
    elif shape=="trident":
        a.line(0,[(20,-6),(27,-6),(34,-7)],3)
        a.line(0,[(20,6),(27,6),(34,7)],3)
        for sign in (-1,1):
            a.line(3,[(20,0),(21,sign*5),(27,sign*5),(34,sign*7)],2)
            a.line(4,[(25,sign*5),(31,sign*6)])
        a.line(2,[(20,0),(tip,0)],3);a.line(4,[(23,-1),(tip,-.5)]);a.jewel(21,0)
    elif shape=="axe":
        a.fill(0,[(22,0),(24,-5),(28,-6),(31,-9),(32,-5),(31,-1),(34,2),(31,5),(26,6),(24,4),(26,2),(22,2)])
        a.fill(2,[(23,0),(25,-4),(29,-5),(31,-7),(30,-2),(27,0),(25,1)])
        a.line(4,[(25,-4),(29,-5),(31,-7)])
        a.fill(3,[(27,2),(32,1),(31,4),(27,5),(25,4)])
        a.jewel(25,0)
        if special=="bell":
            a.line(6,[(22,1),(20,5)]);a.jewel(20,6)
    elif shape=="scythe":
        a.line(0,[(19,0),(27,-1),(31,-3),(32,-6)],3)
        a.fill(0,[(25,-1),(31,-2),(31,2),(28,5),(21,8),(12,10),(7,9),(15,7),(22,4),(25,2)])
        a.fill(2,[(26,0),(30,-1),(29,2),(26,4),(19,7),(12,8),(22,4)])
        a.line(4,[(29,2),(26,4),(19,7),(10,9)])
        a.jewel(26,0);a.knot(22,-1)
    elif shape in ("scepter","wand"):
        a.line(6,[(18,-1),(23,-1)],3)
        if shape=="scepter":
            for side in (-1,1):
                a.fill(0,[(28,side*2),(34,side*6),(37,side*6),(36,side*3),(30,side)])
                a.line(3,[(29,side*2),(35,side*5)],2)
            a.fill(3,[(22,0),(25,-4),(29,-3),(31,0),(29,3),(25,4)])
            a.point(0,27,-2);a.point(0,27,2);a.point(5,26,0);a.knot(21,0)
        else:
            a.fill(0,[(21,0),(24,-6),(29,-6),(32,-3),(28,-4),(25,-2),(24,1),(27,3),(32,3),(29,6),(24,5)])
            a.line(5,[(22,0),(25,-5),(29,-5)],2)
            a.line(3,[(22,1),(25,4),(29,5)],2);a.jewel(29,0,2)
    elif shape=="chakram":
        center=(16,16)
        for radius,col in ((13,0),(12,2),(10,3)):
            a.draw.ellipse((16-radius,16-radius,16+radius,16+radius),fill=a.c[col])
        a.draw.ellipse((8,8,24,24),fill=(0,0,0,0))
        a.draw.arc((4,4,28,28),200,290,fill=a.c[4],width=2)
        for j in range(3):
            ang=2*math.pi*j/3 + (bits[0]%16)*.03
            points=[]
            for r,t in ((10,-.26),(14,-.12),(15,.15),(12,.05),(10,.2)):
                points.append((round(16+r*math.cos(ang+t)),round(16+r*math.sin(ang+t))))
            a.draw.polygon(points,fill=a.c[2],outline=a.c[0])
            a.draw.point(points[1],fill=a.c[5])
    elif shape=="whip":
        a.shaft(9)
        curve=[(8,0),(13,-1),(17,-5),(20,-8),(26,-9),(30,-6),(31,-1),(28,3),(24,4),(21,2),(22,-1),(25,-2)]
        a.line(0,curve,4);a.line(2,curve,3);a.line(3,[(u,v-1) for u,v in curve[:-2]])
        a.line(5,[(8,-2),(8,2)],2)
        if special=="twinfin":
            for n in (-1,1):a.line(3,[(25,-2),(28,n*2-2),(29,n*3-2)],2)
        if special=="frost":a.jewel(24,-2)
        if special=="rift":a.line(5,[(23,0),(25,-2),(27,0)])
    elif shape=="bow":
        # Limbs, string and nock all sit on one plane. The Cupid model is authored separately.
        endx=7+stage
        upper=[(17,0),(20,-3),(20,-6),(18,-9),(endx,-14)]
        lower=[(17,0),(20,3),(20,6),(18,9),(endx,14)]
        nock=8-stage*4
        a.line(6,[upper[-1],(nock,0),lower[-1]])
        for line in (upper,lower):
            a.line(0,line,4);a.line(2,line,3);a.line(4,line[:-1])
        a.line(6,[(16,-2),(17,2)],3);a.jewel(18,0)
        if stage:
            a.line(3,[(nock-2,0),(34,0)],2)
            a.fill(4,[(31,-2),(36,0),(31,2)])
    # A pair of enamel pins and an engraved family accent remain readable at inventory scale.
    if key.startswith("counter_") and shape!="chakram":
        u=10 if shape in ("whip","dagger","greatsword") else 16
        for i in range(3):
            if bits[1] & (1<<i):a.point(5,u+i*2,-1)
        if shape not in ("bow","whip"):
            a.line(3,[(6,-1),(7,1),(8,1)])
    return a.img


RELICS={
 "blossom_greatsword":("greatsword",PETAL,"blossom"),"ribbonhare_scepter":("scepter",PETAL,"hare"),
 "bellheart_axe":("axe",PETAL,"bell"),"lockkeeper_axe":("axe",REEF,"lock"),"vesper_scythe":("scythe",DUSK,"vesper"),
 "axolotl_whip":("whip",PETAL,"twinfin"),"frost_whip":("whip",FROST,"frost"),"rift_whip":("whip",DUSK,"rift"),
 "satin_whip":("whip",PETAL,"satin"),"rose_dagger":("dagger",PETAL,"rose"),"lantern_spear":("spear",GOLD,"lantern"),
 "tidelace_trident":("trident",REEF,"tidelace"),"blue_hour_wand":("wand",REEF,"hour"),
 "ripple_bow":("bow",REEF,"ripple"),"glitch_bow":("bow",INK,"glitch")}


def main():
    for name,(shape,palette,motif) in RELICS.items():
        stages=range(3) if name=="ripple_bow" else range(1)
        for stage in stages:
            suffix=f"_draw{stage}" if stage else ""
            make(shape,palette,name,motif,stage).save(ITEMS/(name+suffix+".png"),optimize=True)
    for e in json.loads((ROOT/"counter_roster.json").read_text()):
        p=list(PETAL if e["theme"]=="petal" else DUSK)
        p[5]={"QUENCH":"#9dc8cb","CLEANSE":"#cad8ac","REAP":"#b99ac9","ANCHOR":"#9bb0bd",
              "FEINT":"#e3b7c6","GUARD":"#cfbda9","SHATTER":"#bdcddb","SILENCE":"#b2a6c9",
              "SEVER":"#d9a1a5","BANISH":"#ceb9d8","SPACE":"#c7a8d7","STAGGER":"#dab99b"}[e["school"]]
        # Vary the metal finish and the narrow wrapped haft among individual relics
        # while keeping their house's textile family and spell school's enamel.
        salt=hashlib.sha256(e["id"].encode()).digest()
        for i in (1,2,3):
            rgb=bytes.fromhex(p[i][1:])
            p[i]="#"+bytes(max(0,min(255,c+(salt[(i+j)%32]%23)-11)) for j,c in enumerate(rgb)).hex()
        make(e["shape"],p,e["id"]).save(ITEMS/(e["id"]+".png"),optimize=True)


if __name__=="__main__":
    main()

