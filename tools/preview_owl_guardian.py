"""Render an orthographic preview directly from the packaged guardian mesh and atlas."""
from pathlib import Path
import json
import math
import numpy as np
from PIL import Image
from generate_owl_residents import ROOT, RES

FACE = {
    'north': ([[-1,1,-1],[1,1,-1],[-1,-1,-1]], (1,1)),
    'south': ([[1,1,1],[-1,1,1],[1,-1,1]], (3,1)),
    'east': ([[1,1,-1],[1,1,1],[1,-1,-1]], (2,1)),
    'west': ([[-1,1,1],[-1,1,-1],[-1,-1,1]], (0,1)),
    'up': ([[-1,1,1],[1,1,1],[-1,1,-1]], (1,0)),
    'down': ([[-1,-1,-1],[1,-1,-1],[-1,-1,1]], (2,0)),
}

def render(angle, tilt, zoom, focus, width, height, frame=''):
    yaw,up=math.radians(angle),math.radians(tilt)
    right=np.array([math.cos(yaw),0,math.sin(yaw)])
    camera=np.array([math.sin(yaw)*math.cos(up),math.sin(up),-math.cos(yaw)*math.cos(up)])
    vertical=np.cross(right,camera)
    canvas=np.zeros((height,width,4),dtype=np.uint8)
    depth=np.full((height,width),-np.inf)
    atlas=np.asarray(Image.open(RES/'textures/entity'/('female_uwu_guardian'+frame+'.png')).convert('RGBA'))
    for bone in json.loads((RES/'geometry/guardian_bones.json').read_text())['bones']:
        pivot=np.array(bone['pivot'])
        for part in bone['cubes']:
            center=(pivot+part['position'])*[1,-1,1]+[0,24,0]
            size=np.array(part['size'])/2
            ax,ay,az=np.array(part['rotation'])*[-1,1,-1]
            sx,cx=math.sin(ax),math.cos(ax);sy,cy=math.sin(ay),math.cos(ay);sz,cz=math.sin(az),math.cos(az)
            rotate=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
            for coords,(u,v) in FACE.values():
                points=(np.array(coords)*size)@rotate.T+center
                normal=np.cross(points[1]-points[0],points[2]-points[0]);normal/=np.linalg.norm(normal)
                if normal@camera<=.002:continue
                a=np.array([[(p-focus)@right*zoom+width/2, -(p-focus)@vertical*zoom+height/2] for p in points])
                positions=np.vstack([a,a[1]+a[2]-a[0]])
                x1,y1=np.maximum(np.floor(positions.min(axis=0)).astype(int),[0,0])
                x2,y2=np.minimum(np.ceil(positions.max(axis=0)).astype(int)+1,[width,height])
                if x1>=x2 or y1>=y2:continue
                yy,xx=np.mgrid[y1:y2,x1:x2]
                inv=np.linalg.inv(np.column_stack((a[1]-a[0],a[2]-a[0])))
                dx=xx+.5-a[0,0];dy=yy+.5-a[0,1]
                tx=inv[0,0]*dx+inv[0,1]*dy;ty=inv[1,0]*dx+inv[1,1]*dy
                zs=points@camera
                z=zs[0]+(zs[1]-zs[0])*tx+(zs[2]-zs[0])*ty
                region=depth[y1:y2,x1:x2]
                hit=(tx>=0)&(tx<=1)&(ty>=0)&(ty<=1)&(z>region)
                index=part['tile']
                tex=atlas[(index//4)*64+32*v+np.clip((ty*32).astype(int),0,31),(index%4)*128+32*u+np.clip((tx*32).astype(int),0,31)].copy()
                hit &= tex[:,:,3]>0
                factor=.79+.21*max(0,normal@np.array([-.28,.59,-.75]))
                tex[:,:,:3]=(tex[:,:,:3]*factor).astype(np.uint8)
                canvas[y1:y2,x1:x2][hit]=tex[hit];region[hit]=z[hit]
    return Image.fromarray(canvas)

if __name__=='__main__':
    out=Image.new('RGB',(988,640),'#302e37')
    def add(im,x,y):out.paste(im,(x,y),im)
    add(render(-16,5,18.2,[0,15.5,0],300,640),0,0)
    add(render(146,6,18.2,[0,15.5,0],280,640),305,0)
    for y,f in enumerate(['','_half','_blink']):
        add(render(3,0,35,[0,24,0],365,201,f),615,10+207*y)
    out.save(ROOT/'docs/models/owl_guardian_girl-preview.png')
