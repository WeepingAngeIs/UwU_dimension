"""Author original 32px revival weapons and two rooted Moonbell plants. No existing art is edited."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
import math

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ART = ROOT / 'assets/uwudimension'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

def save_item(name, image):
    p=ART/'textures/item'/(name+'.png');p.parent.mkdir(parents=True,exist_ok=True);image.save(p)

def blossom(cx,cy,frame,petals=5,ready=False):
    img=Image.new('RGBA',(32,32));d=ImageDraw.Draw(img)
    angle=frame*math.pi/4
    for k in range(petals):
        a=angle+k*math.pi*2/5-math.pi/2
        x=cx+round(math.cos(a)*2.5);y=cy+round(math.sin(a)*2.5)
        color='#f4a6c9' if k!=frame%5 else '#df86b2'
        d.rectangle((x-1,y-1,x+1,y+1),fill=color)
        d.point((x-1,y-1),fill='#ffe4ec')
    if petals==0:
        d.polygon([(cx-2,cy),(cx-1,cy-2),(cx+1,cy-2),(cx+2,cy),(cx+1,cy+2),(cx-1,cy+2)],fill='#9b607d')
        d.line((cx-1,cy-1,cx,cy+1),fill='#c587a2')
    else:
        d.point((cx,cy),fill='#ffe9ad' if ready else '#cba16c')
        d.point((cx-1,cy),fill='#ffe9ad' if ready else '#cba16c')
    return img

for name,center in [('returnbloom_dirk',(17,17)),('dawnpetal_axe',(23,10))]:
    image=Image.new('RGBA',(32,32));d=ImageDraw.Draw(image)
    if name.endswith('dirk'):
        d.polygon([(2,1),(3,8),(9,16),(15,22),(21,19),(15,16),(9,10),(5,5)],fill='#8e536e')
        d.polygon([(2,1),(3,8),(9,15),(16,21),(18,20),(11,13),(6,7)],fill='#dcb2bd')
        d.line([(2,1),(4,8),(10,15),(16,20)],fill='#fff1eb',width=1)
        d.polygon([(6,8),(10,12),(14,16),(16,15),(11,10)],fill='#e993b9')
        d.line((17,21,26,29),fill='#392638',width=5)
        d.line((17,21,26,29),fill='#612c46',width=3)
        for x,y in [(19,23),(21,25),(23,27)]:d.line((x-1,y+1,x+1,y-1),fill='#b36382')
        d.line((12,23,21,16),fill='#704753',width=3)
        d.line((12,22,21,15),fill='#efbcaa',width=2)
        d.polygon([(26,27),(29,28),(30,30),(27,31),(25,29)],fill='#b97675')
        d.line((26,28,28,28),fill='#f3c3b6')
    else:
        d.rectangle((22,10,24,29),fill='#482538')
        d.line((22,14,22,28),fill='#a9637c')
        d.rectangle((21,27,25,29),fill='#cb9c90');d.line((21,27,24,27),fill='#f4cfbc')
        d.polygon([(23,6),(15,7),(12,4),(9,1),(8,5),(4,10),(2,14),(4,19),(8,22),(9,18),(13,14),(22,14)],fill='#8f657b')
        d.polygon([(9,1),(8,5),(4,10),(2,14),(4,19),(8,22),(7,17),(8,12),(10,7)],fill='#e6c8ce')
        d.line([(9,1),(8,5),(4,11),(3,14),(5,19),(8,22)],fill='#fff0eb')
        d.polygon([(10,6),(7,12),(6,16),(8,18),(12,13),(19,12),(19,9),(13,9)],fill='#d69ab6')
        d.line((10,8,8,13),fill='#edb7cc')
        d.rectangle((20,5,25,13),fill='#b8a7b4');d.line((20,5,25,5),fill='#f6e4e5')
        d.rectangle((21,15,25,16),fill='#b48b68');d.line((21,15,25,15),fill='#f5d59f')
    save_item(name,image)
    ready=Image.new('RGBA',(32,256))
    for frame in range(8):ready.paste(blossom(*center,frame,ready=True),(0,frame*32))
    save_item(name+'_ready',ready)
    write(ART/'textures/item'/(name+'_ready.png.mcmeta'),{'animation':{'frametime':3,'interpolate':False}})
    for phase in range(1,6):save_item(name+'_phase'+str(phase),blossom(*center,0,petals=phase-1))
    for phase in range(6):
        overlay=name+'_ready' if phase==0 else name+'_phase'+str(phase)
        model={'parent':'minecraft:item/handheld','loader':'neoforge:item_layers','textures':{'layer0':'uwudimension:item/'+name,'layer1':'uwudimension:item/'+overlay},'render_types':{'minecraft:cutout':[0,1]},'neoforge_data':{'layers':{'1':{'block_light':15 if phase==0 else 0,'sky_light':15 if phase==0 else 0,'ambient_occlusion':False}}}}
        if phase==0:model['overrides']=[{'predicate':{'uwudimension:revival_phase':p},'model':f'uwudimension:item/{name}_phase{p}'} for p in range(1,6)]
        write(ART/'models/item'/(name+('' if phase==0 else '_phase'+str(phase))+'.json'),model)

for side in ['petal','dusk']:
    name=side+'_moonbell'
    leaf=['#233e31','#3d6642','#6c864b','#9ca563']
    petal=['#8f536e','#be7697','#edacc6','#ffe0df'] if side=='petal' else ['#625184','#8975ab','#b9a5df','#e0d9f0']
    img=Image.new('RGBA',(32,64));glow=Image.new('RGBA',(32,64));d=ImageDraw.Draw(img);g=ImageDraw.Draw(glow)
    d.line([(16,63),(16,31),(16,19),(18,14),(21,13),(24,15),(25,20)],fill=leaf[0],width=2)
    d.line([(16,43),(10,36),(7,34),(5,35),(5,40)],fill=leaf[1],width=2)
    d.line([(16,49),(23,42),(26,43),(27,47)],fill=leaf[1],width=2)
    d.line([(16,61),(16,30),(17,21),(19,16)],fill=leaf[2])
    for cx,cy,w in [(25,24,5),(5,43,4),(27,50,4)]:
        d.polygon([(cx,cy-7),(cx-2,cy-5),(cx-w+1,cy-1),(cx-w,cy+2),(cx-2,cy+1),(cx,cy+3),(cx+2,cy+1),(cx+w,cy+2),(cx+w-1,cy-1),(cx+2,cy-5)],fill=petal[0])
        d.polygon([(cx,cy-6),(cx-2,cy-3),(cx-w+1,cy+1),(cx,cy),(cx+w-1,cy+1),(cx+2,cy-3)],fill=petal[1])
        d.polygon([(cx-1,cy-4),(cx+1,cy-4),(cx+2,cy),(cx-2,cy)],fill=petal[2])
        d.line((cx-2,cy,cx+2,cy),fill=petal[3])
        d.line((cx-1,cy+1,cx-1,cy+4),fill='#d6b179');d.line((cx+1,cy+1,cx+1,cy+3),fill='#fff0b5')
        g.line((cx-2,cy,cx+2,cy),fill=petal[3]);g.line((cx-1,cy+1,cx-1,cy+4),fill='#ffdf9a');g.line((cx+1,cy+1,cx+1,cy+3),fill='#fff0b5')
    for coords in [[(16,60),(10,55),(3,54),(7,59)],[(16,58),(22,53),(29,54),(23,58)],[(16,49),(11,44),(7,43),(11,48)],[(16,39),(20,34),(24,35),(20,39)]]:
        d.polygon(coords,fill=leaf[1]);d.line((coords[0],coords[2]),fill=leaf[3])
    for half,box in [('upper',(0,0,32,32)),('lower',(0,32,32,64))]:
        p=ART/'textures/block'/(name+'_'+half+'.png');img.crop(box).save(p)
        glow.crop(box).save(ART/'textures/block'/(name+'_'+half+'_glow.png'))
        elements=[]
        for index,texture in enumerate(['#cross','#glow']):
            for angle in [45,-45]:
                element={'from':[0,0,8+index*.01],'to':[16,16,8+index*.01],'rotation':{'origin':[8,8,8],'axis':'y','angle':angle,'rescale':True},'shade':index==0,'faces':{'north':{'uv':[0,0,16,16],'texture':texture},'south':{'uv':[16,0,0,16],'texture':texture}}}
                if index:element['neoforge_data']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
                elements.append(element)
        write(ART/'models/block'/(name+'_'+half+'.json'),{'ambientocclusion':False,'render_type':'minecraft:cutout','textures':{'particle':'uwudimension:block/'+name+'_'+half,'cross':'uwudimension:block/'+name+'_'+half,'glow':'uwudimension:block/'+name+'_'+half+'_glow'},'elements':elements})
    write(ART/'blockstates'/(name+'.json'),{'variants':{f'half={half}':{'model':f'uwudimension:block/{name}_{half}'} for half in ['lower','upper']}})
    write(ART/'models/item'/(name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'uwudimension:block/'+name+'_upper'}})
    write(ROOT/'data/uwudimension/loot_table/blocks'/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'uwudimension:'+name}],'conditions':[{'condition':'minecraft:block_state_property','block':'uwudimension:'+name,'properties':{'half':'lower'}},{'condition':'minecraft:survives_explosion'}]}]})

write(ART/'lang/revival_and_moonbell_en_us.json',{'item.uwudimension.returnbloom_dirk':'Returnbloom Dirk','item.uwudimension.dawnpetal_axe':'Dawnpetal Axe','block.uwudimension.petal_moonbell':'Petal Moonbell','block.uwudimension.dusk_moonbell':'Dusk Moonbell'})
