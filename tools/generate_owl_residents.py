"""Original native pixel art for the Guardians and the two keeper families."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/assets/uwudimension'

def item_model(name, model):
    (RES / 'models/item' / (name + '.json')).write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')

def eggs():
    # Egg outline and two independently shaded shell regions. Badges are painted last.
    variants = {
        'owl_guardian_girl_egg': ('835774 f39cbe ffd1e2 fff1e7', '466659 75aa80 b6d6a1', 'heart', 'aa5384 fff3f0'),
        'owl_guardian_male_egg': ('734e60 c27f8e ecaab6 ffdae1', '4e5547 799566 adc587', 'sprig', '424d44 f6dca5'),
        'petal_keeper_spawn_egg': ('945a78 e791ad f9b8cd ffe6e8', '814757 ba687f e997a7', 'flower', 'b05d85 fff1ce'),
        'dusk_keeper_spawn_egg': ('453950 6a5280 9273a4 c4a4cd', '302b3c 47394f 685877', 'moon', '58495a eed5ff'),
        'petal_zombie_villager_spawn_egg': ('4d665e 81a894 acc5a5 dae0bb', '74545e a76b78 d18e94', 'scar', '594552 f5c4c6'),
        'dusk_zombie_villager_spawn_egg': ('373d48 56666a 829389 b2b4a3', '3d3548 5e4c68 887492', 'scar', '3f354b cab4e0')}
    neutral = 'uwu_guardian_spawn_egg'
    pattern = [(6,9),(5,10),(4,11),(4,11),(3,12),(3,12),(2,13),(2,13),(2,13),(2,13),(2,13),(3,12),(4,11),(5,10)]
    for index,(name,(upper,lower,mark,badge)) in enumerate(variants.items()):
        top, bottom = [[tuple(bytes.fromhex(v)) for v in s.split()] for s in (upper,lower)]
        dark, pale = ['#' + v for v in badge.split()]
        img = Image.new('RGBA',(16,16))
        dr = ImageDraw.Draw(img)
        for j,(left,right) in enumerate(pattern):
            y = j + 1
            for x in range(left, right + 1):
                shoulder = x - (9-y) * .23
                if y >= 10 + (x//2)%2:
                    colors=bottom
                else:
                    colors=top
                rank = 0 if x in (left,right) or j in (0,13) else (1 if shoulder > 9 or j >= 12 else len(colors)-1 if shoulder < 6 else len(colors)-2)
                dr.point((x,y),fill=colors[rank])
        if mark=='heart':
            dr.polygon([(5,6),(6,5),(7,6),(8,5),(9,6),(9,7),(7,9),(5,7)],fill=dark)
            dr.point([(6,6),(8,6),(7,7)],fill=pale)
        if mark=='sprig':
            dr.line((5,9,9,5),fill=pale)
            dr.line((6,8,5,6),fill=dark)
            dr.line((8,6,10,6),fill=dark)
        if mark=='flower':
            dr.line((5,7,9,7),fill=pale,width=2)
            dr.line((7,5,7,9),fill=pale,width=2)
            dr.point((7,7),fill=dark)
        if mark=='moon':
            dr.arc((5,4,10,9),70,270,fill=pale,width=2)
            dr.point((10,5),fill=pale)
        if mark=='scar':
            dr.line((4,8,10,6),fill=dark)
            dr.line((6,6,7,9),fill=pale)
            dr.line((8,5,9,8),fill=pale)
        dr.point((5,4), fill='#fff1dc' if index<3 else '#d8cadb')
        img.save(RES/'textures/item'/(name+'.png'))
        item_model(name, {'parent':'minecraft:item/generated','textures':{'layer0':'uwudimension:item/'+name}})
    item_model(neutral, {'parent':'minecraft:item/generated','textures':{'layer0':'uwudimension:item/'+neutral},'overrides':[
        {'predicate':{'custom_model_data':value},'model':'uwudimension:item/owl_guardian_'+sex+'_egg'}
        for value,sex in ((1,'male'),(2,'girl'))]})

def painted_face(kind, undead=False, eyelid=0):
    """Paint square front UV; all coordinates originate on this 16-pixel face."""
    if kind == 'owl':
        skin,shadow,skinlight,hair,fringe,lash,iris,low,blush = ('#f5d5b7','#d5a58f','#ffe6c7','#c5cd90','#e6e6a8','#483d43','#66985e','#344f39','#e7a7a0')
    elif kind == 'petal':
        skin,shadow,skinlight,hair,fringe,lash,iris,low,blush = ('#ffdec9','#e4ad98','#ffeada','#b86779','#df91a2','#674454','#579a88','#416366','#e99d9d')
    else:
        skin,shadow,skinlight,hair,fringe,lash,iris,low,blush = ('#f8e2e4','#cfaaba','#ffebea','#302b4a','#594765','#362c47','#b477ae','#684a78','#cc99aa')
    if undead:
        skin,shadow,skinlight,iris,low,blush = ('#a9b49a','#728876','#c3c4aa','#dba8a1','#735767','#918673') if kind=='petal' else ('#939992','#637678','#b1b5ac','#c6a0bc','#62556d','#827d87')
    image=Image.new('RGBA',(16,16),skin)
    d=ImageDraw.Draw(image)
    d.line((0,0,0,15),fill=shadow);d.line((1,15,14,15),fill=shadow)
    d.line((3,14,11,14),fill=skinlight)
    d.polygon([(0,0),(15,0),(15,4),(13,3),(11,3),(12,2),(9,2),(8,4),(7,2),(5,4),(3,3),(1,5),(0,5)],fill=hair)
    d.line((3,1,6,1),fill=fringe);d.line((9,1,12,1),fill=fringe)
    for right in (False,True):
        def pts(coords):return [(15-x if right else x,y) for x,y in coords]
        def line(coords,col):d.line(pts(coords),fill=col)
        line([(2,5),(4,4),(5,4)],shadow)
        if eyelid==2:
            line([(1,8),(2,9),(4,9),(6,8)],lash)
        else:
            top=8 if eyelid else 6
            d.polygon(pts([(2,top),(6,top),(6,10),(3,10),(2,9)]), fill='#fff5e7')
            line([(3,top+1),(3,9),(4,10)],low)
            line([(4,top+1),(4,9),(5,9)],iris)
            if not eyelid:
                line([(3,7),(4,7)],'#fff6d8');line([(5,9),(5,10)],'#a7cda0' if kind!='dusk' else '#dcb2d9')
            line([(1,top-1),(2,top),(5,top),(6,top+1)],lash)
        line([(1,11),(3,11)],blush)
    d.point((8,10),fill=shadow)
    d.line((7,12,8,12),fill='#a36b68' if not undead else '#776561')
    if undead:
        d.line((11,12,13,14),fill=shadow);d.point((13,13),fill='#d2bcaa')
    return image

def keepers():
    base=RES/'textures/entity'
    for kind in ('petal','dusk'):
        family=kind+'_keeper'
        for file in [base/(family+'.png'),*sorted((base/family).glob('*.png'))]:
            normal=Image.open(file).convert('RGBA')
            normal.paste(painted_face(kind),(112,47))
            normal.save(file)
            # Make a weathered version of each original dress, including its profession swatches.
            skin=normal.copy()
            lut={}
            for color in set(normal.getdata()):
                r,g,b,a=color
                luminance=.30*r+.56*g+.14*b
                lut[color]=(min(255,int(.68*r+.21*luminance+5)),min(255,int(.70*g+.23*luminance+4)),min(255,int(.68*b+.20*luminance+7)),a)
            skin.putdata([lut[v] for v in normal.getdata()])
            skin.paste(painted_face(kind,True),(112,47))
            d=ImageDraw.Draw(skin)
            # Three mended seams in jacket, apron and cuff keep the profession print readable.
            for x,y in [(8,28),(62,31),(39,44)]:
                d.line((x-1,y-1,x+3,y+1),fill='#58555b')
                d.point([(x,y),(x+2,y)],fill='#c3aaa7')
            target=base/'undead'/file.relative_to(base)
            target.parent.mkdir(parents=True,exist_ok=True)
            skin.save(target)

if __name__ == '__main__':
    eggs()
    keepers()
