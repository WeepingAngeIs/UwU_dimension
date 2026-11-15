"""Author the Owl Guardian Girl's editable model and its native mesh from one set of original cuboids."""
import base64
import io
import json
import math
import random
import uuid
from pathlib import Path
from PIL import Image, ImageDraw
from generate_owl_residents import ROOT, RES, painted_face

ENTITY = RES / 'textures/entity'
DOC = ROOT / 'docs/models'
materials = {
    'skin':'dfb49a f3cfad ffdfc0 ffe8ca',
    'face':'d8a992 e7bd9f f4d2b2 ffe6ca',
    'hair':'98ae7d b9c788 d9da99 ece7ab',
    'hair_light':'a5b786 c9d299 e5e6a7 f8efba',
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
        box('head','back_vine_'+str(i),(x,18.8-.7*(i%2),2.24+.15*(i%2)),(1.67,8.0+.65*(i%3),1.5),'hair_shadow' if i%2 else 'hair',(8,0,(i-2)*3))
        box('head','tapered_back_lock_'+str(i),(x+(i-2)*.18,14.7-.7*(i%2),1.8),(1.25,2.1,.95),'hair',(11,0,(i-2)*4))
    for s in (-1,1):
        n='left' if s==1 else 'right'
        box('head',n+'_pointed_ear',(s*3.98,23.8,-.06),(1.6,.92,1.32),'skin',(0,0,s*29))
        box('head',n+'_leaf_hair',(s*3.35,22.6,-1.53),(1.45,6.4,1.8),'hair_light',(0,0,s*5))
        box('head',n+'_vine_lock',(s*3.68,18.02,-.99),(1.22,4.45,1.47),'hair_shadow',(0,0,-s*8))
        box('head',n+'_golden_tip',(s*3.44,16.26,-1.04),(.93,1.73,1.13),'hair_light',(4,0,-s*17))
        box('head',n+'_curl',(s*3.54,19.2,-1.38),(1.31,2,1.44),'hair',(0,0,-s*12))
        # Antlers branch in two directions; each fine tip carries an offset fresh leaf.
        box('head',n+'_twig_base',(s*2.05,27.8,.42),(.76,2.8,.72),'twig',(0,0,-s*23))
        box('head',n+'_twig_fork',(s*3.0,29.7,.42),(.58,2.28,.57),'twig',(8,0,-s*34))
        box('head',n+'_twig_outer',(s*3.76,30.52,.46),(.51,1.52,.48),'twig',(4,0,-s*55))
        box('head',n+'_twig_tip',(s*2.65,30.56,.53),(.43,1.52,.45),'twig',(-10,0,s*18))
        box('head',n+'_antler_leaf',(s*3.95,30.93,.42),(.71,1.35,.42),'leaf_light',(0,0,-s*56))
        box('head',n+'_fork_bud',(s*2.48,31.39,.48),(.56,.71,.55),'leaf')
    # A continuous ring of sprouting lily leaves and four individual flowers.
    for i in range(12):
        a=math.tau*i/12
        x,z=3.68*math.sin(a),-.17-2.68*math.cos(a)
        box('head','crown_lily_'+str(i),(x,26.4+.16*(i%2),z),(1.12,.84,.84),'leaf' if i%2 else 'leaf_light',(0,-30*i,(-1)**i*22))
    for i,(x,y,z,r) in enumerate([(-2.70,26.74,-3.00,1.25),(2.80,26.57,-3.09,1.16),(-.47,26.93,-3.19,.78),(-3.78,26.74,.95,.93),(2.1,26.7,2.93,1)]):
        bloom('head','lily_flower_'+str(i),x,y,z,r)
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
    box('basket','hips',(0,12.65,0),(6.82,2.27,4.15),'leaf_dark')
    box('basket','underskirt',(0,11.22,.02),(7.15,2.04,4.67),'frill')
    for i in range(10):
        a=math.tau*i/10
        x,z=3.17*math.sin(a),2.0*math.cos(a)
        box('basket','skirt_frill_'+str(i),(x*1.09,10.15+.32*(i%2),z*1.2),(2.16,1.18,1.45),'frill',(-10*math.cos(a),i*36,9*math.sin(a)))
        box('basket','skirt_leaf_'+str(i),(x,12.17+.13*(i%3),z),(2.15,3.76,1.28),
            ['leaf','leaf_light','leaf_dark'][i%3],(14*math.cos(a),i*36,-11*math.sin(a)))
    for s,b in [(1,'left'),(-1,'right')]:
        box(b,'shoulder',(s*4.0,18.48,0),(2.3,2.04,2.47),'leaf',(0,0,s*9))
        box(b,'upper_arm',(s*4.32,16.65,.05),(1.75,2.91,1.86),'skin',(0,0,s*5))
        box(b,'vine_cuff',(s*4.5,15.2,.02),(1.93,.72,2.02),'leaf')
        box(b,'forearm',(s*4.51,13.76,-.18),(1.72,2.61,1.78),'skin',(-5,0,0))
        box(b,'bracer',(s*4.50,13.30,-.05),(1.88,1.74,1.82),'bark')
        box(b,'hand',(s*4.55,12.08,-.24),(1.76,1.14,1.71),'skin')
        bloom(b,'shoulder_blossom',s*4.24,18.40,-1.24,.55)
        leg=b+'_root'
        box(leg,'thigh',(s*1.96,9.12,0),(2.22,5.0,2.4),'skin')
        box(leg,'knee_frill',(s*1.96,6.69,0),(2.53,.85,2.65),'frill')
        box(leg,'boot_leg',(s*1.99,4.02,.15),(2.48,4.69,2.65),'boot')
        box(leg,'boot_opening',(s*1.99,6.13,.10),(2.76,.72,2.90),'bark')
        box(leg,'leaf_boot_trim',(s*2.22,5.56,-1.22),(1.05,1.46,.48),'leaf_light',(0,0,-s*24))
        box(leg,'boot_foot',(s*1.99,1.16,-.37),(2.73,2.31,3.76),'boot')
        box(leg,'sole',(s*1.99,.36,-.4),(2.88,.62,3.89),'bark_shadow')

def patch(material,side,eyelid=0):
    palette=['#'+v for v in materials[material].split()]
    image=Image.new('RGBA',(16,16),palette[1])
    d=ImageDraw.Draw(image)
    rng=random.Random(material+'_'+side)
    if material=='face' and side=='north':
        return painted_face('owl',eyelid=eyelid)
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
    return image

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
                x=(tile%4)*64+16*u;y=(tile//4)*32+16*v
                uv[side]={'uv':[x,y,x+16,y+16],'texture':0}
            uid=ident(bone+'/'+name);children.append(uid)
            elements.append({'name':name,'type':'cube','uuid':uid,'origin':center,'rotation':rotation,
                             'from':[round(c-s/2,4) for c,s in zip(center,size)],'to':[round(c+s/2,4) for c,s in zip(center,size)],
                             'box_uv':False,'faces':uv})
        geometry.append({'name':bone,'pivot':[pivot[0],24-pivot[1],pivot[2]],'cubes':cubes})
        outliner.append({'name':bone,'uuid':ident(bone),'origin':pivot,'rotation':[0,0,0],'children':children,'isOpen':False})
    h=32*math.ceil(len(tiles)/4)
    (RES/'geometry/guardian_bones.json').write_text(json.dumps({'width':256,'height':h,'bones':geometry},indent=2)+'\n',encoding='utf-8')
    for frame in range(3):
        sheet=Image.new('RGBA',(256,h))
        for material,tile in tiles.items():
            for side,(u,v) in faces.items():
                sheet.paste(patch(material,side,frame),((tile%4)*64+u*16,(tile//4)*32+v*16))
        suffix=['','_half','_blink'][frame]
        sheet.save(ENTITY/('female_uwu_guardian'+suffix+'.png'))
        if frame==0:
            raw=io.BytesIO();sheet.save(raw,format='PNG')
            model={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'Owl Guardian Girl',
                   'resolution':{'width':256,'height':h},'elements':elements,'outliner':outliner,
                   'textures':[{'name':'Owl Guardian Girl','id':'0','mode':'bitmap','uuid':ident('skin'),'width':256,'height':h,
                                'source':'data:image/png;base64,'+base64.b64encode(raw.getvalue()).decode()}]}
            (DOC/'owl_guardian_girl.bbmodel').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    bake()
