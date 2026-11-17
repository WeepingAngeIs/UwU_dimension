"""Author view-specific mounts and original animated accent pixels on existing item art.

Run from this checkout. Base PNGs remain untouched, except the two new Rippleglass draw
poses which are assembled from its existing limb pixels and native string palette.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import hashlib
import json
import math
import generate_cupid_bow as cupid


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/uwudimension'
MODELS = ASSETS / 'models/item'
TEXTURES = ASSETS / 'textures'
OVERLAYS = TEXTURES / 'item/animated'
OVERLAYS.mkdir(parents=True, exist_ok=True)
BASE = {
    'cupid_bow': 'bow', 'ripple_bow': 'bow', 'ribbonhare_scepter': 'wand',
    'blossom_greatsword': 'axe', 'bellheart_axe': 'axe', 'lockkeeper_axe': 'axe',
    'vesper_scythe': 'scythe', 'axolotl_whip': 'whip', 'frost_whip': 'whip',
    'rift_whip': 'whip', 'rose_dagger': 'dagger', 'satin_whip': 'whip',
    'lantern_spear': 'spear', 'tidelace_trident': 'trident', 'blue_hour_wand': 'wand',
    'heart_launcher': 'launcher', 'bell_hammer': 'hammer',
}
ROSTER = json.loads((ASSETS / 'counter_roster.json').read_text(encoding='utf-8'))
SHAPES = BASE | {r['id']: r['shape'] for r in ROSTER}
THEMES = {r['id']: r['theme'] for r in ROSTER}
MOTIFS = json.loads((ROOT / 'tools/weapon_pixel_motifs.json').read_text(encoding='utf-8'))


def write(path, data):
    if isinstance(data, dict) and 'elements' in data:
        header = dict(data)
        elements = header.pop('elements')
        text = json.dumps(header, indent=2, ensure_ascii=False).removesuffix('\n}')
        text += ',\n  "elements": [\n'
        text += ',\n'.join('    ' + json.dumps(element, separators=(',', ':')) for element in elements)
        text += '\n  ]\n}\n'
    else:
        text = json.dumps(data, indent=2, ensure_ascii=False) + '\n'
    path.write_text(text, encoding='utf-8')


def model(name):
    return json.loads((MODELS / (name + '.json')).read_text(encoding='utf-8'))


def source_texture(data):
    textures = data.get('textures', {})
    if 'layer0' not in textures:
        parent = data['parent'].split(':')[1].removeprefix('item/')
        inherited = model(parent)
        source = source_texture(inherited)
        data.setdefault('textures', {})['layer0'] = 'uwudimension:' + source.relative_to(TEXTURES).with_suffix('').as_posix()
        return source
    source = textures['layer0'].split(':')[1]
    return TEXTURES / (source + '.png')


def transform(rotation, translation, scale):
    return {'rotation': rotation, 'translation': translation, 'scale': scale}


def mounts(key, shape, image):
    # Match source span, not texture resolution. Each override uses its actual silhouette.
    left, top, right, bottom = image.getchannel('A').getbbox()
    fraction = (bottom - top) / image.height
    target = {'scythe': (1.28, 1.64, 1.0), 'spear': (.98, 1.28, .78),
              'axe': (1.0, 1.28, .80), 'hammer': (.94, 1.22, .80),
              'wand': (.85, 1.05, .67), 'whip': (.75, .92, .58),
              'chakram': (.74, .94, .63)}.get(shape)
    result = {}
    if target:
        fp, tp, floor = [round(x / fraction, 4) for x in target]
        if shape == 'scythe':
            # The blade clears the outer shoulder; the wrapped lower staff sits at the palm.
            result = {
                'firstperson_righthand': transform([0, -90, 25], [1.13, 3.8, .85], [fp, fp, .64]),
                'firstperson_lefthand': transform([0, 90, -25], [1.13, 3.8, .85], [fp, fp, .64]),
                'thirdperson_righthand': transform([0, -90, 55], [0, 7.6, .5], [tp, tp, .82]),
                'thirdperson_lefthand': transform([0, 90, -55], [0, 7.6, .5], [tp, tp, .82]),
            }
        elif shape == 'chakram':
            twin = key.endswith(('twinblade', 'rapier')) and ('iron_' in key or 'runic_' in key)
            # Linked rings are supported at their bridge, single rings at the near inner rim.
            shift = 1.2 if twin else .15
            result = {
                'firstperson_righthand': transform([0, -90, 18], [.75 + shift, 2.3, .8], [fp, fp, .57]),
                'firstperson_lefthand': transform([0, 90, -18], [.75 + shift, 2.3, .8], [fp, fp, .57]),
                'thirdperson_righthand': transform([0, -90, 35], [shift, 2.8, .7], [tp, tp, .65]),
                'thirdperson_lefthand': transform([0, 90, -35], [shift, 2.8, .7], [tp, tp, .65]),
            }
        else:
            roll = {'spear': 25, 'axe': 25, 'hammer': 25, 'wand': 20, 'whip': 12}[shape]
            lift = {'spear': 5.2, 'axe': 4.5, 'hammer': 4.2, 'wand': 3.8, 'whip': 2.9}[shape]
            result = {
                'firstperson_righthand': transform([0, -90, roll], [1.13, 3.2, 1.13], [fp, fp, .65]),
                'firstperson_lefthand': transform([0, 90, -roll], [1.13, 3.2, 1.13], [fp, fp, .65]),
                'thirdperson_righthand': transform([0, -90, 55], [0, lift, .5], [tp, tp, .75]),
                'thirdperson_lefthand': transform([0, 90, -55], [0, lift, .5], [tp, tp, .75]),
            }
        result['ground'] = transform([0, 0, 0], [0, 4 if shape == 'scythe' else 3, 0], [floor, floor, .5])
    # Preserve accepted dagger/trident mounts and the existing left-leading scythe/axe art.
    # A GUI reflection is confined to the specific right-leading points and foci.
    if shape in ('spear', 'wand') or key == 'ripple_bow' or shape == 'bow' and key.startswith('counter_'):
        result['gui'] = transform([0, 180, 0], [0, 0, 0], [1, 1, 1])
    return result


def tone(key, element):
    if element == 1 or key in ('frost_whip', 'ripple_bow', 'tidelace_trident', 'lockkeeper_axe'):
        return (169, 239, 247, 255)
    if element == 2 or THEMES.get(key) == 'dusk' or key == 'rift_whip':
        return (209, 170, 255, 255)
    if element == 3 or key.startswith('counter_gold_') or key == 'lantern_spear':
        return (255, 224, 157, 255)
    return (255, 184, 215, 255)


def accent_frames(key, shape, source, element):
    size = source.width
    alpha = source.getchannel('A')
    pixels = [(x, y) for y in range(size) for x in range(size) if alpha.getpixel((x, y)) > 128]
    boundary = [(x, y) for x, y in pixels if any(not(0 <= x+dx < size and 0 <= y+dy < size) or alpha.getpixel((x+dx, y+dy)) == 0 for dx, dy in [(-1,0),(1,0),(0,-1),(0,1)])]
    center_x = sum(x for x,y in pixels)/len(pixels)
    center_y = sum(y for x,y in pixels)/len(pixels)
    seed = int.from_bytes(hashlib.sha256(key.encode()).digest()[:4], 'big')
    shade = tone(key, element)
    motif = MOTIFS[key]['pattern']
    sheet = Image.new('RGBA', (size, size * 32))
    if shape == 'chakram' or motif == 'orbit':
        path = sorted(boundary, key=lambda p: math.atan2(p[1]-center_y, p[0]-center_x))
    elif shape in ('spear', 'dagger', 'trident', 'wand'):
        path = sorted(pixels, key=lambda p: p[0]-p[1])
    elif shape == 'whip':
        path = sorted(boundary, key=lambda p: (math.atan2(p[1]-center_y, p[0]-center_x),p[1]))
    else:
        path = sorted(boundary, key=lambda p: p[1]*.75+p[0]*.25)
    for frame in range(32):
        if frame >= 26:
            continue # a short visual rest keeps the silhouette quiet between pulses
        cursor = (frame / 25 + (seed % 7) / 16) % 1
        point_index = int(cursor*(len(path)-1))
        for tail in range(4 if motif in ('needle','ribbon') else 2):
            x, y = path[(point_index-tail*max(1,len(path)//32)) % len(path)]
            color = (255, 247, 232, 255) if tail == 0 else shade[:3]+(210-tail*35,)
            sheet.putpixel((x,y+frame*size), color)
        if 7 <= frame <= 20:
            # This signature follows the reviewed item motif, independently of its school.
            # Sample the existing material so the opening of a ring is always transparent.
            marks = 3 if motif == 'triad' else 2 if motif in ('paired','echo') else 1
            for mark in range(marks):
                index=(point_index+mark*len(path)//marks)%len(path)
                x,y=path[index]
                sheet.putpixel((x,y+frame*size),shade)
                if motif in ('star','heart','blossom','drop','flare'):
                    offsets={'star':[(-1,0),(1,0),(0,-1),(0,1)],
                             'heart':[(-1,-1),(1,-1),(0,1)],
                             'blossom':[(-1,0),(1,0),(0,-1),(0,1)],
                             'drop':[(0,-1),(0,-2),(1,0)],
                             'flare':[(0,-1),(-1,-2),(1,0)]}[motif]
                    for dx,dy in offsets:
                        if 0<=x+dx<size and 0<=y+dy<size and alpha.getpixel((x+dx,y+dy))>128:
                            sheet.putpixel((x+dx,y+dy+frame*size),shade[:3]+(185,))
        # A second blossom accent is tied to this item's own phase and its head material.
        if shape in ('scythe', 'axe', 'wand') and 9 <= frame <= 16:
            head = min(pixels, key=lambda p: (p[0]-size*.7)**2+(p[1]-size*.22)**2)
            for k in range(5):
                ang = k*math.tau/5 + (seed%5)*.19
                x = head[0]+round(math.cos(ang)*1.8)
                y = head[1]+round(math.sin(ang)*1.8)
                if 0<=x<size and 0<=y<size and alpha.getpixel((x,y))>128:
                    sheet.putpixel((x,y+frame*size),shade)
    return sheet


def overlay(key, name, data, element=0):
    image = Image.open(source_texture(data)).convert('RGBA')
    asset = name + '_glint'
    accent_frames(key, SHAPES[key], image, element).save(OVERLAYS/(asset+'.png'), optimize=True)
    write(OVERLAYS/(asset+'.png.mcmeta'), {'animation': {'frametime': 2, 'interpolate': False}})
    glow = 'uwudimension:item/animated/' + asset
    if data.get('elements'):
        data['elements'] = [e for e in data['elements'] if not any(f.get('texture') == '#glint' for f in e.get('faces', {}).values())]
        data['textures']['glint'] = glow
        for face,z,uv in [('south',8.28,[0,0,16,16]),('north',7.72,[16,0,0,16])]:
            data['elements'].append({
                'from': [0, 0, z], 'to': [16, 16, z],
                'neoforge_data': {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False},
                'faces': {face: {'uv':uv,'texture':'#glint'}}})
    else:
        data['loader'] = 'neoforge:item_layers'
        # Each source was a single-layer generated item. The real blade stays on layer zero.
        data['textures']['layer1'] = glow
        data['render_types'] = {'minecraft:cutout': [0, 1]}
        data['neoforge_data'] = {'layers': {'1': {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}}}
    return asset


def cupid_deep():
    cupid.write_pose(2.5, 'cupid_bow_deep', MODELS)


def ripple_stages():
    original = Image.open(TEXTURES/'item/ripple_bow.png').convert('RGBA')
    # The source has six solid palette entries. Separate the cord by its exact brown
    # color and its known diagonal endpoints, retaining the warm handle and fittings.
    body = original.copy()
    for y in range(16,32):
        x=y-16
        if body.getpixel((x,y)) == (166,129,103,255):
            body.putpixel((x,y),(0,0,0,0))
    for name,nock in [('ripple_bow_nock',(8,24)),('ripple_bow_half',(5,27)),('ripple_bow_deep',(3,29)),('ripple_bow_full',(1,31))]:
        pic=Image.new('RGBA',(32,32));d=ImageDraw.Draw(pic)
        d.line([(0,16),nock,(15,31)],fill=(166,129,103,255),width=1)
        tip=(nock[0]+20,nock[1]-20)
        d.line([nock,tip],fill=(55,68,86,255),width=3)
        d.line([nock,tip],fill=(196,218,211,255),width=1)
        d.polygon([tip,(tip[0]-5,tip[1]+1),(tip[0]-1,tip[1]+5)],fill=(196,218,211,255))
        pic.alpha_composite(body)
        pic.save(TEXTURES/('item/'+name+'.png'),optimize=True)
        write(MODELS/(name+'.json'), {'parent':'minecraft:item/bow','textures':{'layer0':'uwudimension:item/'+name},'overrides':[]})


def bow_mounts(kind):
    # Vanilla's nocked arrow and Cupid's point upper-left in sprite coordinates.
    # Rippleglass points upper-right and needs the extra quarter turn in F5 as in 1P.
    roll = 90 if kind == 'ripple_bow' else 0
    thickness = .28 if kind == 'cupid_bow' else .68
    return {
        'firstperson_righthand': transform([0,-90,25+roll],[1.13,3.2,1.13],[.68,.68,thickness]),
        'firstperson_lefthand': transform([0,90,-25-roll],[1.13,3.2,1.13],[.68,.68,thickness]),
        'thirdperson_righthand': transform([-80,260,-40+roll],[-1,-2,2.5],[.9,.9,.9]),
        'thirdperson_lefthand': transform([-80,-280,40-roll],[-1,-2,2.5],[.9,.9,.9]),
    }


def draw_overrides(kind, glitch=False):
    prefix='uwudimension:glitch_'+('cupid' if kind=='cupid_bow' else 'ripple') if glitch else 'uwudimension:'
    drawing=prefix+'_drawing' if glitch else prefix+'drawing'
    draw=prefix+'_draw' if glitch else prefix+'draw'
    names=[kind+'_draw0',kind+'_draw1',kind+'_deep',kind+'_draw2'] if kind=='cupid_bow' else [kind+'_nock',kind+'_half',kind+'_deep',kind+'_full']
    return [{'predicate':{drawing:1,draw:amount},'model':'uwudimension:item/'+name} for name,amount in zip(names,[0,.4,.75,1])]


def main():
    cupid_deep();ripple_stages()
    inventory=[]
    for key,shape in SHAPES.items():
        names={key}
        if shape=='scythe':
            names.update(key+'_'+str(n) for n in range(4))
        if key=='cupid_bow':names.update(['cupid_bow_draw0','cupid_bow_draw1','cupid_bow_deep','cupid_bow_draw2'])
        if key=='ripple_bow':names.update(['ripple_bow_nock','ripple_bow_half','ripple_bow_deep','ripple_bow_full','ripple_bow_draw1','ripple_bow_draw2'])
        for name in sorted(names):
            if not (MODELS/(name+'.json')).is_file():continue
            data=model(name)
            image=Image.open(source_texture(data)).convert('RGBA')
            display=mounts(key,shape,image)
            if key in ('cupid_bow','ripple_bow'):display.update(bow_mounts(key))
            data.setdefault('display',{}).update(display)
            if key in ('cupid_bow','ripple_bow') and name==key:
                data['overrides']=draw_overrides(key)
            element=int(name[-1]) if shape=='scythe' and name[-1:].isdigit() else 0
            glow=overlay(key,name,data,element)
            write(MODELS/(name+'.json'),data)
            inventory.append({'item':key,'shape':shape,'model':name,'display':data.get('display',{}),'overlay':glow,'source':data['textures']['layer0'],'motif':MOTIFS[key]})
    bud=Image.new('RGBA',(32,32));bd=ImageDraw.Draw(bud)
    # A spent/available follow-up has a distinct little two-leaf silhouette at the hinge.
    bd.polygon([(22,9),(20,7),(20,9),(22,11),(24,10),(25,7),(23,8)],fill=(141,235,185,255))
    bd.point((22,8),fill=(255,249,209,255))
    bud.save(TEXTURES/'item/flower_credit_bud.png',optimize=True)
    vesper=model('vesper_scythe')
    vesper['overrides']=[r for r in vesper['overrides'] if 'uwudimension:flower_credit' not in r['predicate']]
    for element in range(4):
        credit=model('vesper_scythe_'+str(element))
        credit['textures']['layer2']='uwudimension:item/flower_credit_bud'
        credit['render_types']={'minecraft:cutout':[0,1,2]}
        credit['neoforge_data']['layers']['2']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
        credit['overrides']=[]
        write(MODELS/('vesper_scythe_credit_'+str(element)+'.json'),credit)
        vesper['overrides'].append({'predicate':{'uwudimension:element':element,'uwudimension:flower_credit':1},'model':'uwudimension:item/vesper_scythe_credit_'+str(element)})
    write(MODELS/'vesper_scythe.json',vesper)
    glitch=model('glitch_bow')
    glitch['overrides']=[r for r in glitch['overrides'] if not any('glitch_cupid_' in p or 'glitch_ripple_' in p or 'glitch_flower_credit' in p for p in r['predicate'])]
    # Predicate priority: saved forms, elemental views, drawing, then transition static.
    static=[r for r in glitch['overrides'] if 'uwudimension:static' in r['predicate']]
    glitch['overrides']=[r for r in glitch['overrides'] if r not in static]
    glitch['overrides']+=draw_overrides('cupid_bow',True)+draw_overrides('ripple_bow',True)
    for element in range(4):
        glitch['overrides'].append({'predicate':{'uwudimension:glitch_flower_credit':1,'uwudimension:glitch_scythe':28+element},'model':'uwudimension:item/vesper_scythe_credit_'+str(element)})
    glitch['overrides']+=static
    write(MODELS/'glitch_bow.json',glitch)
    # The physical strike is narrow and the nose points toward UV v=0.
    crest=Image.new('RGBA',(32,32));d=ImageDraw.Draw(crest)
    d.polygon([(16,2),(29,20),(24,17),(21,15),(16,9),(11,15),(8,17),(3,20)],fill=(255,255,255,235))
    d.line([(4,23),(10,19),(16,13),(22,19),(28,23)],fill=(255,255,255,100),width=1)
    d.point((16,4),fill=(255,255,255,255))
    crest.save(TEXTURES/'entity/petal_crest.png',optimize=True)
    write(ROOT/'ci/fixtures/weapon-presentation.json',inventory)


if __name__=='__main__':main()
