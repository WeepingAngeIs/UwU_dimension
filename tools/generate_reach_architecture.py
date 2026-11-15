"""Generate original 16px joinery/stone/glass art and Java 1.21.1 block resources.
Usage: python tools/generate_reach_architecture.py /path/to/vanilla-1.21.1.jar
Only state orientations, standard model parents and drop rules come from the client."""
import argparse
import json
import random
import re
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ART = ROOT / 'assets/uwudimension'
DATA = ROOT / 'data/uwudimension'
NS = 'uwudimension:'
STONES = ('petalsilk_plaster', 'camellia_glaze', 'shellstone_ashlar', 'vesper_ashlar',
          'lotus_scale_tile', 'fluted_nacre', 'starvein_frieze', 'moonpearl_lamp')
GLASS = {'blush': '#f4b5d0', 'opal': '#e9e2ff', 'dew': '#a2dee6', 'orchid': '#d79acb', 'iris': '#b7a9ed'}
NAMES = []


def dump(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=True)+'\n', encoding='utf-8')


def sprite(name, image, group='block'):
    path = ART / 'textures' / group / (name+'.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def canvas(colors, seed, noise=4):
    base = tuple(bytes.fromhex(colors[1][1:]))
    rng = random.Random(seed)
    im = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            jitter = rng.choice((-noise, 0, 0, noise))
            im.putpixel((x, y), tuple(max(0, min(255, c+jitter)) for c in base))
    return im, ImageDraw.Draw(im)


def wood_art(prefix, colors):
    shade, ground, lit, shine, metal = colors
    im, d = canvas(colors, prefix)
    for y in (0, 5, 11):
        d.line((0,y,15,y), shade)
        d.line((0,y+1,15,y+1), lit)
    for x,y,w in ((2,3,4),(8,3,5),(1,8,7),(11,9,3),(2,14,5),(10,14,4)):
        d.line((x,y,x+w,y), lit)
        d.point((x+w+1,y-1), lit)
    d.line((8,6,8,10),shade)
    d.line((2,12,2,15),shade)
    sprite(prefix+'_planks', im)
    im, d = canvas(colors, prefix+'beam')
    for x in (1,5,11):
        d.line([(x,0),(x,4),(x+1,5),(x+1,10),(x,11),(x,15)],lit)
    d.line([(9,3),(7,5),(7,8),(9,10),(10,8),(10,6),(9,5)],shade)
    d.point((9,7),shine)
    sprite(prefix+'_beam', im)
    im, d = canvas(colors, prefix+'end')
    for a in (0,3,6):
        d.rounded_rectangle((a,a,15-a,15-a), radius=3, outline=shade)
        d.line((a+3,a+1,11-a,a+1), lit)
    sprite(prefix+'_beam_top',im)
    # An upright trellis with a chevron inlay. The upper panel has real open lights.
    upper=Image.new('RGBA',(16,16),shade); du=ImageDraw.Draw(upper)
    du.rectangle((2,1,13,13),fill=(0,0,0,0))
    for path in ([(1,7),(7,1),(14,8)],[(1,9),(7,15),(14,8)],[(1,1),(14,14)],[(1,14),(14,1)]):
        du.line(path,fill=lit,width=2)
    du.line((0,0,0,15),fill=shine);du.line((14,2,14,15),fill=lit)
    lower,d=canvas(colors,prefix+'door')
    d.rectangle((0,0,15,15),outline=shade); d.rectangle((2,2,13,13),outline=lit)
    d.line([(4,10),(7,6),(11,10)],fill=shine)
    d.rectangle((11,3,12,5),fill=metal)
    sprite(prefix+'_door_top',upper);sprite(prefix+'_door_bottom',lower)
    item=Image.new('RGBA',(16,16))
    item.paste(upper.resize((8,8),Image.Resampling.NEAREST),(4,0))
    item.paste(lower.resize((8,8),Image.Resampling.NEAREST),(4,8));sprite(prefix+'_door',item,'item')
    shutter=upper.copy(); dd=ImageDraw.Draw(shutter)
    for x in (4,10):
        dd.line((x,0,x,15),fill=lit)
        dd.line((x+1,0,x+1,15),fill=shade)
    sprite(prefix+'_shutter',shutter)


def masonry():
    warm=('#b098b7','#e8d2df','#f1e4ec','#fbf3ee','#c8b4c9')
    blush=('#985e88','#c989a9','#e3b0c6','#f7d9dd','#ae7797')
    pearl=('#977f9d','#cdb5c8','#e4cbda','#f3e1e6','#bba8bd')
    plum=('#42334e','#68536e','#87708b','#b291b1','#57445e')
    for name, colors in zip(STONES,(warm,blush,pearl,plum,blush,pearl,plum,pearl)):
        sh,b,m,hi,lo=colors
        im,d=canvas(colors,name)
        if name=='petalsilk_plaster':
            for x,y in ((1,4),(10,7),(6,13),(14,1)):
                d.line((x,y,x+1,y),m);d.point((x+1,y-1),m)
        elif name=='camellia_glaze':
            d.rectangle((0,0,15,15),outline=lo)
            d.line((1,1,14,1),m);d.line((1,2,1,12),m)
            d.line([(3,12),(6,12),(8,10),(11,10)],m)
        elif name.endswith('ashlar'):
            for y in (0,8):
                d.line((0,y,15,y),sh);d.line((0,y+1,15,y+1),m)
                x=4 if y else 12
                d.line((x,y+1,x,y+7),sh);d.line((x+1,y+2,x+1,y+6),lo)
            d.line((5,4,9,4),m);d.line((9,12,12,12),lo)
        elif name=='lotus_scale_tile':
            for y in (0,8,16):
                for x in range(-8,25,8):
                    mid=x+(4 if y==8 else 0)
                    d.line([(mid-4,y-4),(mid-2,y-1),(mid,y+1),(mid+2,y-1),(mid+4,y-4)],sh)
                    d.line([(mid-3,y-2),(mid,y),(mid+3,y-2)],m)
                    d.point((mid,y-3),hi)
        elif name=='fluted_nacre':
            for x in (0,5,10,15):
                d.line((x,0,x,15),lo);d.line((x+1,0,x+1,15),m)
            d.line((7,1,7,5),hi);d.line((12,9,12,14),hi)
        elif name=='starvein_frieze':
            for y in (0,14): d.line((0,y,15,y),lo);d.line((0,y+1,15,y+1),m)
            d.line([(0,8),(4,8),(7,4),(10,8),(15,8)],'#b88ea9')
            d.line([(4,8),(7,11),(10,8)],'#e9c9d4');d.point((7,7),'#fff3da')
        else:
            d.rectangle((0,0,15,15),fill=sh);d.rectangle((1,1,14,14),outline=lo)
            d.polygon([(2,7),(7,2),(9,2),(14,7),(14,9),(9,14),(7,14),(2,9)],fill='#efd3d9')
            d.polygon([(4,8),(8,4),(12,8),(8,12)],fill='#fff3eb')
            d.point((8,8),'#fffef9')
        sprite(name,im)
    im,d=canvas(plum,'nightbriar_shingles')
    for y in range(0,16,4):
        shift=(y//4%2)*4
        for x in range(-8+shift,17,8):
            d.line([(x,y),(x+3,y+2),(x+5,y+2),(x+8,y)],'#302637')
            d.line([(x+1,y),(x+3,y+1),(x+4,y+1)],'#8b7596')
            d.point((x+5,y+3),'#a995b1')
    sprite('nightbriar_shingles',im)
    for name,color in GLASS.items():
        rgb=tuple(bytes.fromhex(color[1:]))
        im=Image.new('RGBA',(16,16),(*rgb,56)); d=ImageDraw.Draw(im)
        d.line([(0,8),(7,1),(8,1),(15,8),(8,15),(7,15),(0,8)],fill=(*rgb,168))
        d.line([(1,8),(7,2)],fill=(255,238,246,189))
        d.point((8,0),fill=(228,215,231,205)); d.line((3,3,4,2),fill=(255,255,255,125))
        sprite(name+'_leaded_glass',im)


def tags(group, entries, registry='block'):
    # IDs in a namespaced subtag keep the vanilla tag merge small and pack-friendly.
    path=ROOT/'data/minecraft/tags'/registry/(group+'.json')
    own='architecture/'+group.replace('/','_')
    dump(DATA/'tags'/registry/(own+'.json'),{'replace':False,'values':[NS+i for i in entries]})
    current=json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {'replace':False,'values':[]}
    if '#'+NS+own not in current['values']:
        current['values'].append('#'+NS+own)
        dump(path,current)


def resources(vanilla):
    def read(path): return json.loads(vanilla.read(path))
    def simple(name, glass=False):
        model={'parent':'minecraft:block/cube_all','textures':{'all':NS+'block/'+name}}
        if glass: model['render_type']='minecraft:translucent'
        dump(ART/'blockstates'/(name+'.json'),{'variants':{'':{'model':NS+'block/'+name}}})
        dump(ART/'models/block'/(name+'.json'),model)
        dump(ART/'models/item'/(name+'.json'),{'parent':NS+'block/'+name})
        drop(name,'calcite')
        NAMES.append(name)
    def drop(name,ref):
        src=json.dumps(read('data/minecraft/loot_table/blocks/'+ref+'.json'))
        src=src.replace('minecraft:'+ref+'"',NS+name+'"').replace('minecraft:blocks/'+ref+'"',NS+'blocks/'+name+'"')
        dump(DATA/'loot_table/blocks'/(name+'.json'),json.loads(src))
    def shaped(src,new,mappings,cutout=False):
        def translate(data): 
            raw=json.dumps(data)
            for a,b in sorted(mappings.items(),key=lambda kv:-len(kv[0])):
                raw=raw.replace('minecraft:block/'+a, NS+'block/'+b).replace('minecraft:item/'+a,NS+'item/'+b)
            return json.loads(raw)
        states=read('assets/minecraft/blockstates/'+src+'.json')
        item=read('assets/minecraft/models/item/'+src+'.json')
        refs=set(re.findall('"minecraft:block/([^"]+)"',json.dumps([states,item])))
        for ref in refs:
            target=translate('minecraft:block/'+ref)
            if not target.startswith(NS): raise ValueError((ref,target))
            model=translate(read('assets/minecraft/models/block/'+ref+'.json'))
            if cutout: model['render_type']='minecraft:cutout'
            dump(ART/'models/block'/(target.split('/')[-1]+'.json'),model)
        dump(ART/'blockstates'/(new+'.json'),translate(states))
        dump(ART/'models/item'/(new+'.json'),translate(item))
        drop(new,src)
        NAMES.append(new)
    woods=[]
    for w in ('silkwood','velvetwood'):
        kinds={'cherry_planks':w+'_planks','stripped_cherry_log':w+'_beam','cherry_stairs':w+'_stairs',
               'cherry_slab':w+'_slab','cherry_fence':w+'_fence','cherry_trapdoor':w+'_shutter','cherry_door':w+'_door'}
        for src,name in kinds.items(): shaped(src,name,kinds,src.endswith(('trapdoor','door')))
        woods.extend(kinds.values())
    for n in (*STONES,'nightbriar_shingles'):simple(n)
    roofing={'stone_brick_stairs':'nightbriar_shingle_stairs','stone_brick_slab':'nightbriar_shingle_slab','stone_bricks':'nightbriar_shingles'}
    for ref in ('stone_brick_slab','stone_brick_stairs'):shaped(ref,roofing[ref],roofing)
    for n in GLASS:simple(n+'_leaded_glass',True)
    for name,base in (('shellstone_steps','shellstone_ashlar'),('vesper_steps','vesper_ashlar')):
        shaped('stone_brick_stairs',name,{'stone_brick_stairs':name,'stone_bricks':base})
    tags('mineable/axe',woods)
    tags('mineable/pickaxe',list(STONES)+['shellstone_steps','vesper_steps','nightbriar_shingles','nightbriar_shingle_stairs','nightbriar_shingle_slab'])
    for registry in ('block','item'):
        for tag,suffix in [('planks','planks'),('wooden_stairs','stairs'),('wooden_slabs','slab'),('wooden_doors','door'),
                           ('wooden_trapdoors','shutter'),('wooden_fences','fence')]:
            tags(tag,[w+'_'+suffix for w in ('silkwood','velvetwood')],registry)
        tags('stairs',['shellstone_steps','vesper_steps','nightbriar_shingle_stairs'],registry)
        tags('slabs',['nightbriar_shingle_slab'],registry)
    lang=ART/'lang/en_us.json'
    names=json.loads(lang.read_text(encoding='utf-8-sig'))
    for name in NAMES:names['block.uwudimension.'+name]=name.replace('_',' ').title()
    dump(lang,names)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('client',type=Path)
    args=parser.parse_args()
    wood_art('silkwood',('#956279','#cc99a7','#e0b8bc','#f7d4cf','#87788f'))
    wood_art('velvetwood',('#492f49','#78546c','#99728b','#c49bb2','#c7bbc9'))
    masonry()
    with zipfile.ZipFile(args.client) as archive: resources(archive)

