"""Author the Owl Guardian Girl's editable model and its native mesh from one set of original cuboids."""
import base64
import io
import json
import math
import random
import uuid
from pathlib import Path
from PIL import Image, ImageDraw
from generate_owl_residents import ROOT, RES

ENTITY = RES / 'textures/entity'
DOC = ROOT / 'docs/models'
materials = {
    'skin':'dfb49a f3cfad ffdfc0 ffe8ca',
    'face':'d8a992 e7bd9f f4d2b2 ffe6ca',
    'hair':'91a26d b4bd7c d0d48b e3e49a',
    'hair_light':'a2b17b c6cf8d dfe399 f1edac',
    'hair_shadow':'879c76 a9ba81 c5cc8e dce0a1',
    'bark':'684a44 885a4e a57158 c49673',
    'bark_shadow':'493e3e 654741 835647 aa7960',
    'twig':'685450 865e51 a97b59 c59d76',
    'leaf':'55775b 709263 91ab70 bad18c',
    'leaf_dark':'435f4e 547654 6e9160 94aa77',
    'leaf_light':'74935e 95b175 b1c789 d6dba2',
    'frill':'ce9ba4 e8bdc0 f8dcda fff1e5',
    'petal':'b87a8a df96a5 f1b3bf fbd4cf',
    'pearl':'cfa07e e2b67d f5d699 ffe9b5',
    'boot':'55423c 715447 936d51 b09068',
}
tiles={name:i for i,name in enumerate(materials)}
pivots={'basket':(0,0,0),'head':(0,20.2,0),'left':(4.2,18.8,0),'right':(-4.2,18.8,0),
        'left_root':(2,11.7,0),'right_root':(-2,11.7,0)}
boxes=[]
def box(bone,name,center,size,material,rotation=(0,0,0)):
    boxes.append((bone,name,center,size,material,rotation))

def bloom(bone,name,x,y,z,r=1):
    for i,angle in enumerate([0,72,144,216,288]):
        a=math.radians(angle)
        box(bone,name+'_petal_'+str(i),(x+math.sin(a)*.55*r,y+math.cos(a)*.55*r,z),(.75*r,.94*r,.40*r),'petal',(0,0,-angle))
    box(bone,name+'_pollen',(x,y,z-.19*r),(.53*r,.53*r,.33*r),'pearl')

def guardian():
    box('head','warm_face',(0,23.75,-.12),(7.35,6.75,5.1),'face')
    box('head','leaf_hair_cap',(0,26.5,.2),(7.85,1.8,5.72),'hair_light')
    box('head','hair_rear',(0,22.3,2),(7.70,7.8,2.1),'hair')
    for i in range(5):
        x=(i-2)*1.4
        y=25.17-abs(i-2)*.19
        box('head','fringe_'+str(i),(x,y+.58,-2.73),(1.63,1.15+.34*(i%2),.82),
            'hair_light' if i%2==0 else 'hair',(0,0,(i-1)*6))
        box('head','fringe_tip_'+str(i),(x+.14,y-.06,-2.83),(1.06,.78,.65),'hair_light',(3,0,(i-2)*10))
        box('head','back_vine_'+str(i),(x,18.8-.7*(i%2),2.24+.15*(i%2)),(1.67,8.0+.65*(i%3),1.5),'hair_shadow' if i%2 else 'hair',(8,0,(i-2)*3))
        box('head','tapered_back_lock_'+str(i),(x+(i-2)*.18,14.7-.7*(i%2),1.8),(1.25,2.1,.95),'hair',(11,0,(i-2)*4))
    for j,(x,y,z,ang) in enumerate([(-2.75,20.0,3.2,-9),(-1.35,18.7,3.44,6),(.15,19.6,3.52,-7),(1.6,19.0,3.40,9),(2.83,20.0,3.15,15)]):
        box('head','overlapping_back_leaf_'+str(j),(x,y,z),(1.80,5.10,1.15),'hair_light' if j%2 else 'hair',(14,0,ang))
        box('head','back_leaf_point_'+str(j),(x+ang*.034,y-2.75,z+.32),(1.0,1.77,.72),'hair',(14,0,ang))
    for s in (-1,1):
        n='left' if s==1 else 'right'
        box('head',n+'_pointed_ear',(s*3.98,23.8,-.06),(1.6,.92,1.32),'skin',(0,0,s*29))
        box('head',n+'_temple_leaf',(s*3.6,24.3,.02),(1.65,2.17,1.25),'hair',(18,0,-s*31))
        box('head',n+'_temple_tip',(s*4.08,23.46,.0),(.73,1.02,.81),'hair_light',(19,0,-s*33))
        box('head',n+'_rear_leaf',(s*3.79,20.67,1.47),(1.59,4.25,1.58),'hair',(3,0,-s*14))
        box('head',n+'_rear_tip',(s*4.29,18.43,1.63),(1.0,1.93,1.1),'hair_light',(5,0,-s*17))
        box('head',n+'_leaf_hair',(s*3.35,22.6,-1.53),(1.45,6.4,1.8),'hair_light',(0,0,s*5))
        box('head',n+'_loose_vine',(s*4.0,16.2,1.42),(1.08,4.4,1.04),'hair',(7,0,s*16))
        box('head',n+'_tapered_vine',(s*4.57,13.50,1.22),(.77,1.55,.77),'hair_light',(8,0,s*8))
        if s<0: bloom('head',n+'_vine_bud',s*4.21,14.9,.86,.61)
        box('head',n+'_vine_lock',(s*3.68,18.02,-.99),(1.22,4.45,1.47),'hair_shadow',(0,0,-s*8))
        box('head',n+'_golden_tip',(s*3.44,16.26,-1.04),(.93,1.73,1.13),'hair_light',(4,0,-s*17))
        box('head',n+'_curl',(s*3.54,19.2,-1.38),(1.31,2,1.44),'hair',(0,0,-s*12))
        # Antlers branch in two directions; each fine tip carries an offset fresh leaf.
        box('head',n+'_twig_base',(s*2.05,27.8,.42),(.76,2.8,.72),'twig',(0,0,-s*23))
        box('head',n+'_twig_fork',(s*3.0,29.7,.42),(.58,2.28,.57),'twig',(8,0,-s*34))
        box('head',n+'_low_branch',(s*3.60,29.03,.25),(.44,1.79,.49),'twig',(0,0,-s*73))
        box('head',n+'_low_branch_bud',(s*4.46,29.39,.23),(.71,1.07,.49),'leaf_light',(0,0,-s*47))
        box('head',n+'_twig_outer',(s*3.76,30.52,.46),(.51,1.52,.48),'twig',(4,0,-s*55))
        box('head',n+'_twig_tip',(s*2.65,30.56,.53),(.43,1.52,.45),'twig',(-10,0,s*18))
        box('head',n+'_antler_leaf',(s*3.95,30.93,.42),(.71,1.35,.42),'leaf_light',(0,0,-s*56))
        box('head',n+'_fork_bud',(s*2.48,31.39,.48),(.56,.71,.55),'leaf')
    # A continuous ring of sprouting lily leaves and four individual flowers.
    for i in range(12):
        a=math.tau*i/12
        x,z=3.68*math.sin(a),-.17-2.68*math.cos(a)
        box('head','crown_lily_'+str(i),(x,26.4+.16*(i%2),z),(1.12,.84,.84),'leaf' if i%2 else 'leaf_light',(0,-30*i,(-1)**i*22))
    for i,(x,y,z,r) in enumerate([(-2.88,26.84,-3.26,1.69),(2.80,26.57,-3.09,.71),(-.30,26.93,-3.19,.64),(-3.78,26.74,.95,.93),(2.1,26.7,2.93,.80),(-3.65,25.26,-2.70,.59),(-3.87,24.34,-2.40,.47)]):
        bloom('head','lily_flower_'+str(i),x,y,z,r)
    for x,y,z in [(-3.82,26.30,-2.05),(-4.03,25.74,-1.55),(3.71,26.23,-1.84)]:
        box('head','temple_leaflet_'+str(abs(int(y*100))),(x,y,z),(.86,1.12,.76),'leaf_light',(12,0,35 if x<0 else -35))
    # Wider segmented bracts curl away from the sash on the front, with an asymmetric blossom.
    for j,(x,y,z,r) in enumerate([(-2.52,12.85,-1.89,.86),(2.72,11.35,-1.92,.61)]):
        bloom('basket','skirt_blossom_'+str(j),x,y,z-.58,r)
    # Chest is clothed in two overlapping outward-turned bark bodice panels.
    box('basket','neck',(0,20.07,0),(2.55,1.72,2.2),'skin')
    box('basket','collar',(0,19.12,0),(6.50,1.35,3.37),'skin')
    box('basket','underbodice',(0,17.52,0),(6.22,3.45,3.55),'bark')
    for s in (-1,1):
        box('basket','bodice_panel_'+str(s),(s*1.48,17.98,-1.15),(3.0,2.05,2.18),'bark',(9,0,-s*8))
        box('basket','collar_leaf_'+str(s),(s*1.80,18.93,-1.65),(2.56,.78,.68),'leaf_light',(0,0,s*22))
    box('basket','waist',(0,15.0,0),(4.46,2.7,3.22),'bark_shadow')
    for i in range(3):
        box('basket','bodice_lacing_'+str(i),(.1,15.02+i*.49,-1.68),(1.02,.16,.19),'pearl',(0,0,16 if i%2 else -16))
    box('basket','belt',(0,13.78,0),(5.45,.76,3.75),'twig')
    bloom('basket','waist_flower',.78,13.72,-2.02,.60)
    for i in range(5):
        box('basket','vine_sash_'+str(i),(-2.35+i*.94,15.65+i*.43,-1.91),(1.28,.25,.28),'leaf_light',(0,0,22))
    bloom('basket','collar_blossom',.15,18.62,-2.10,.57)
    box('basket','hips',(0,12.65,0),(6.82,2.27,4.15),'leaf_dark')
    box('basket','underskirt',(0,11.22,.02),(7.52,2.24,5.17),'frill')
    for i in range(10):
        a=math.tau*i/10
        x,z=3.43*math.sin(a),2.18*math.cos(a)
        box('basket','skirt_frill_'+str(i),(x*1.09,10.15+.32*(i%2),z*1.2),(2.28,1.52,1.86),'frill',(-10*math.cos(a),i*36,9*math.sin(a)))
        box('basket','flared_leaf_'+str(i),(x*1.09,10.94+.44*(i%2),z*1.17),(1.95,3.09,1.19),'leaf' if i%2 else 'leaf_light',(18*math.cos(a),i*36,-13*math.sin(a)))
        box('basket','pointed_hem_'+str(i),(x*1.14,9.60+.44*(i%2),z*1.23),(.94,1.00,.90),'leaf_light',(18*math.cos(a),i*36,-16*math.sin(a)))
        box('basket','skirt_leaf_'+str(i),(x,12.17+.13*(i%3),z),(2.15,3.76,1.28),
            ['leaf','leaf_light','leaf_dark'][i%3],(14*math.cos(a),i*36,-11*math.sin(a)))
    for s,b in [(1,'left'),(-1,'right')]:
        box(b,'shoulder',(s*4.0,18.48,0),(2.3,2.04,2.47),'leaf',(0,0,s*9))
        box(b,'upper_arm',(s*4.32,16.65,.05),(1.75,2.91,1.86),'skin',(0,0,s*5))
        box(b,'vine_cuff',(s*4.5,15.2,.02),(1.93,.72,2.02),'leaf')
        box(b,'forearm',(s*4.51,13.76,-.18),(1.72,2.61,1.78),'skin',(-5,0,0))
        box(b,'bracer',(s*4.50,13.30,-.05),(1.88,1.74,1.82),'bark')
        box(b,'hand',(s*4.55,12.08,-.24),(1.76,1.14,1.71),'skin')
        box(b,'blossom_sleeve',(s*4.55,14.67,.09),(2.28,1.08,2.22),'frill',(0,0,s*9))
        box(b,'petal_sleeve_tip',(s*4.88,14.04,.34),(.96,1.02,1.31),'frill',(0,0,s*23))
        bloom(b,'shoulder_blossom',s*4.24,18.40,-1.24,.55)
        leg=b+'_root'
        box(leg,'thigh',(s*1.96,9.12,0),(2.22,5.0,2.4),'skin')
        box(leg,'knee_frill',(s*1.96,6.69,0),(2.53,.85,2.65),'frill')
        box(leg,'boot_leg',(s*1.99,4.02,.15),(2.48,4.69,2.65),'boot')
        box(leg,'boot_opening',(s*1.99,6.13,.10),(2.76,.72,2.90),'bark')
        box(leg,'leaf_boot_trim',(s*2.22,5.56,-1.22),(1.05,1.46,.48),'leaf_light',(0,0,-s*24))
        box(leg,'boot_foot',(s*1.99,1.16,-.37),(2.73,2.31,3.76),'boot')
        for i in range(3):
            box(leg,'boot_wrap_'+str(i),(s*1.98,2.48+i*1.20,-1.21),(2.55,.27,.36),'leaf_light',(0,0,s*(16 if i%2 else -14)))
        box(leg,'mushroom_stalk',(s*3.28,3.17,-.52),(.40,1.18,.48),'frill',(0,0,-s*11))
        box(leg,'mushroom_brim',(s*3.44,3.78,-.52),(1.42,.42,1.15),'petal',(0,0,-s*9))
        box(leg,'mushroom_cap',(s*3.44,4.06,-.52),(1.13,.44,1.11),'petal',(0,0,-s*9))
        box(leg,'mushroom_spot',(s*3.29,4.23,-.79),(.34,.21,.36),'frill')
        bloom(leg,'boot_blossom',s*1.72,2.18,-1.47,.47)
        box(leg,'sole',(s*1.99,.36,-.4),(2.88,.62,3.89),'bark_shadow')

def owl_face(eyelid):
    # Two stepped, upturned lashes leave room for individual iris highlights at this resolution.
    im=Image.new('RGBA',(32,32),'#f7d5bb');d=ImageDraw.Draw(im)
    d.polygon([(0,0),(3,0),(3,18),(2,23),(5,28),(10,31),(0,31)],fill='#e9bca5')
    d.polygon([(31,0),(29,0),(29,21),(27,27),(22,31),(31,31)],fill='#f1c7ac')
    d.polygon([(0,0),(31,0),(31,9),(29,8),(27,7),(25,5),(23,7),(21,6),(18,8),(17,6),(15,9),(13,6),(9,7),(7,6),(4,9),(0,10)],fill='#bdc38b')
    for side in (False,True):
        def pts(c):return [(31-x if side else x,y) for x,y in c]
        def line(c,color):d.line(pts(c),fill=color)
        def poly(c,color):d.polygon(pts(c),fill=color)
        line([(4,11),(6,10),(9,9),(12,9)],'#9a986d')
        if eyelid==2:
            line([(2,17),(4,18),(6,19),(10,19),(12,18),(13,17)],'#593a43')
            line([(4,17),(6,18),(10,18)],'#754c4d')
        else:
            top=18 if eyelid else 14
            poly([(3,top+1),(5,top),(10,top),(13,top+1),(13,20),(11,22),(7,22),(4,20)],'#fff0df')
            poly([(5,top),(10,top),(12,top+1),(12,20),(10,22),(7,22),(5,20)],'#8ea358')
            poly([(6,top),(10,top),(11,top+1),(11,20),(9,21),(7,20)],'#485b3c')
            line([(6,20),(7,21),(10,21),(11,20)],'#b5c96a')
            line([(8,20),(9,20)],'#8a9f5f');line([(7,21),(8,21)],'#d0d792')
            if eyelid==0:
                line([(6,15),(7,15),(7,16)],'#fffbed')
                line([(6,16),(7,16)],'#faffe2')
                line([(10,19),(11,19)],'#dee9a8')
            line([(1,top-2),(3,top),(5,top-1),(9,top-2),(11,top-1),(13,top+1)],'#583e43')
            line([(3,top+2),(4,top),(6,top-1),(9,top-1),(12,top)],'#413637')
            line([(3,top),(2,top-2)],'#47383c');line([(5,top-1),(4,top-3)],'#47383c')
        line([(2,23),(4,23),(5,24)],'#e8a6a1')
        line([(4,22),(5,23)],'#f2b5aa');line([(6,24),(7,24)],'#f1afa6')
    d.point((16,22),fill='#e5ae9b');d.point((15,21),fill='#ffe1c4')
    d.polygon([(13,25),(15,26),(17,26),(19,25),(18,27),(16,28),(15,28)],fill='#b66d71')
    d.line([(15,27),(17,27)],fill='#f3a7a4');d.point((14,25),fill='#fff1db')
    return im

def patch(material,side,eyelid=0):
    palette=['#'+v for v in materials[material].split()]
    image=Image.new('RGBA',(16,16),palette[1])
    d=ImageDraw.Draw(image)
    rng=random.Random(material+'_'+side)
    if material=='face' and side=='north':
        return owl_face(eyelid)
    if material.startswith('hair') or material in ('twig','bark','bark_shadow','boot'):
        for x in (1,5,9,13):
            y=rng.randrange(4)
            d.line([(x,y),(x,y+4),(x+1,y+5),(x+1,y+10)],fill=palette[rng.choice([0,2])])
            if rng.random()<.8:d.line((x+1,y+1,x+1,y+3),fill=palette[2])
        d.line((3,0,6,0),fill=palette[3])
        if material in ('bark','boot'):
            d.line([(4,9),(6,7),(8,7),(9,8),(8,10),(7,10)],fill=palette[0])
    elif material.startswith('leaf'):
        d.polygon([(0,3),(4,1),(10,1),(14,4),(15,14),(6,14),(1,10)],fill=palette[2])
        d.line([(2,1),(6,5),(9,10),(13,14)],fill=palette[0])
        for x,y in [(5,4),(8,8),(10,11)]:
            d.line((x,y,x+3,y-2),fill=palette[1]);d.line((x,y,x-4,y),fill=palette[1])
        d.line((2,3,4,3),fill=palette[3])
    elif material in ('petal','pearl','frill'):
        d.polygon([(0,1),(12,1),(15,5),(13,10),(9,13),(2,12)],fill=palette[2])
        d.line([(2,4),(5,2),(8,2)],fill=palette[3])
        d.line([(3,13),(7,14),(10,13)],fill=palette[0])
    else:
        d.polygon([(1,0),(14,0),(15,12),(12,14),(1,14)],fill=palette[2])
    return image.resize((32,32),Image.Resampling.NEAREST)

faces={'west':(0,1),'north':(1,1),'east':(2,1),'south':(3,1),'up':(1,0),'down':(2,0)}
def bake():
    guardian()
    geometry=[]
    outliner=[]
    elements=[]
    def ident(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'uwudimension/owl_guardian_girl/'+name))
    for bone,pivot in pivots.items():
        cubes=[]
        children=[]
        for b,name,center,size,material,rotation in boxes:
            if b!=bone:continue
            xyz=[center[0]-pivot[0],pivot[1]-center[1],center[2]-pivot[2]]
            tile=tiles[material]
            cubes.append({'name':name,'position':[round(x,4) for x in xyz],'size':size,'tile':tile,
                          'rotation':[round(math.radians(x),5) for x in (-rotation[0],rotation[1],-rotation[2])]})
            uv={}
            for side,(u,v) in faces.items():
                x=(tile%4)*128+32*u;y=(tile//4)*64+32*v
                uv[side]={'uv':[x,y,x+32,y+32],'texture':0}
            uid=ident(bone+'/'+name);children.append(uid)
            elements.append({'name':name,'type':'cube','uuid':uid,'origin':center,'rotation':rotation,
                             'from':[round(c-s/2,4) for c,s in zip(center,size)],'to':[round(c+s/2,4) for c,s in zip(center,size)],
                             'box_uv':False,'faces':uv})
        geometry.append({'name':bone,'pivot':[pivot[0],24-pivot[1],pivot[2]],'cubes':cubes})
        outliner.append({'name':bone,'uuid':ident(bone),'origin':pivot,'rotation':[0,0,0],'children':children,'isOpen':False})
    h=64*math.ceil(len(tiles)/4)
    (RES/'geometry/guardian_bones.json').write_text(json.dumps({'width':512,'height':h,'bones':geometry},indent=2)+'\n',encoding='utf-8')
    for frame in range(3):
        sheet=Image.new('RGBA',(512,h))
        for material,tile in tiles.items():
            for side,(u,v) in faces.items():
                sheet.paste(patch(material,side,frame),((tile%4)*128+u*32,(tile//4)*64+v*32))
        suffix=['','_half','_blink'][frame]
        sheet.save(ENTITY/('female_uwu_guardian'+suffix+'.png'))
        if frame==0:
            raw=io.BytesIO();sheet.save(raw,format='PNG')
            model={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'Owl Guardian Girl',
                   'resolution':{'width':512,'height':h},'elements':elements,'outliner':outliner,
                   'textures':[{'name':'Owl Guardian Girl','id':'0','mode':'bitmap','uuid':ident('skin'),'width':512,'height':h,
                                'source':'data:image/png;base64,'+base64.b64encode(raw.getvalue()).decode()}]}
            (DOC/'owl_guardian_girl.bbmodel').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    bake()
