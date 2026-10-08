"""Offline geometry preview of the actual GLB, not an AI illustration or Android screenshot.
Usage: python tools/preview_avatar.py [model.glb] [output.png]
Requires numpy and Pillow; renders neutral, speaking, blinking and rear views.
"""
import json
import io
import math
import struct
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parents[1]
path=Path(sys.argv[1]) if len(sys.argv)>1 else root/'app/src/main/assets/models/portrait-avatar.glb'
output=Path(sys.argv[2]) if len(sys.argv)>2 else root/'avatar-source/portrait-3d-preview.png'
data=path.read_bytes()
length=struct.unpack_from('<I',data,12)[0]
doc=json.loads(data[20:20+length]); binary=data[28+length:]

def read(i):
    a=doc['accessors'][i]; v=doc['bufferViews'][a['bufferView']]
    width={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
    return np.frombuffer(binary,dtype={5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']],
                         count=a['count']*width,offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,width).copy()

W,H=460,650
textures=[]
for texture in doc.get('textures',[]):
    im=doc['images'][texture['source']]; bv=doc['bufferViews'][im['bufferView']]
    start=bv.get('byteOffset',0)
    textures.append(np.asarray(Image.open(io.BytesIO(binary[start:start+bv['byteLength']])).convert('RGBA'))/255.)
def render(yaw,mouth,blink):
    color=np.zeros((H,W,3),dtype=np.float32)
    color[:]=[.045,.065,.09]
    depth=np.full((H,W),-np.inf)
    angle=math.radians(yaw)
    rotation=np.array([[math.cos(angle),0,math.sin(angle)],[0,1,0],[-math.sin(angle),0,math.cos(angle)]])
    light=np.array([-.4,.7,1]);light/=np.linalg.norm(light)
    for mesh_index, mesh in enumerate(doc['meshes']):
        for primitive in mesh['primitives']:
            p=read(primitive['attributes']['POSITION'])
            n=read(primitive['attributes']['NORMAL'])
            names=mesh.get('extras',{}).get('targetNames',[])
            for target_index,name in enumerate(names):
                weight=mouth if name=='mouthOpen' else blink if name=='blink' else 0
                target=primitive['targets'][target_index]
                p+=read(target['POSITION'])*weight
                if 'NORMAL' in target:n+=read(target['NORMAL'])*weight
            node=next((node for node in doc['nodes'] if node.get('mesh')==mesh_index), {})
            p+=np.array(node.get('translation',[0,0,0]))
            p=p@rotation.T; n=n@rotation.T
            n/=np.maximum(1e-8,np.linalg.norm(n,axis=1))[:,None]
            screen=np.column_stack([W/2+p[:,0]*205,H/2-(p[:,1]-.1)*205])
            full_material=doc['materials'][primitive['material']]
            material=full_material['pbrMetallicRoughness']
            rgb=np.array(material.get('baseColorFactor',[1,1,1,1])[:3])
            texture= textures[material['baseColorTexture']['index']] if 'baseColorTexture' in material else None
            uv=read(primitive['attributes']['TEXCOORD_0']) if texture is not None else None
            for tri in read(primitive['indices']).reshape(-1,3):
                xy=screen[tri]; z=p[tri,2]
                left=max(0,int(np.floor(xy[:,0].min())));right=min(W-1,int(np.ceil(xy[:,0].max())))
                top=max(0,int(np.floor(xy[:,1].min())));bottom=min(H-1,int(np.ceil(xy[:,1].max())))
                if left>right or top>bottom:continue
                x,y=np.meshgrid(np.arange(left,right+1)+.5,np.arange(top,bottom+1)+.5)
                a,b,c=xy
                denominator=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(denominator)<1e-9:continue
                u=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/denominator
                v=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/denominator
                w=1-u-v
                zz=u*z[0]+v*z[1]+w*z[2]
                region=depth[top:bottom+1,left:right+1]
                mask=(u>=0)&(v>=0)&(w>=0)&(zz>region)
                if not mask.any():continue
                nn=u[:,:,None]*n[tri[0]]+v[:,:,None]*n[tri[1]]+w[:,:,None]*n[tri[2]]
                nn/=np.maximum(1e-8,np.linalg.norm(nn,axis=2))[:,:,None]
                nn=np.where((nn[:,:,2]<0)[:,:,None],-nn,nn)
                diffuse=np.maximum(0,nn@light)
                reflected=2*nn[:,:,2,None]*nn-np.array([0,0,1])
                spec=np.maximum(0,reflected@light)**32*(1-material.get('roughnessFactor',1))*.25
                lit=np.clip(rgb*(.38+.65*diffuse[:,:,None])+spec[:,:,None],0,1)**(1/2.2)
                if texture is not None:
                    texcoord=u[:,:,None]*uv[tri[0]]+v[:,:,None]*uv[tri[1]]+w[:,:,None]*uv[tri[2]]
                    tx=np.clip((texcoord[:,:,0]*texture.shape[1]).astype(int),0,texture.shape[1]-1)
                    ty=np.clip((texcoord[:,:,1]*texture.shape[0]).astype(int),0,texture.shape[0]-1)
                    sample=texture[ty,tx]
                    mask &= sample[:,:,3]>=full_material.get('alphaCutoff',.5)
                    lit=sample[:,:,:3]*rgb
                region[mask]=zz[mask]
                color[top:bottom+1,left:right+1][mask]=lit[mask]
    return Image.fromarray((color*255).astype('uint8'))

sheet=Image.new('RGB',(W*4,H+48),(12,17,23))
draw=ImageDraw.Draw(sheet)
for i,(label,yaw,mouth,blink) in enumerate([
    ('NEUTRAL',0,0,0),('SPEAKING / 25 DEG',25,.85,0),('BLINK',0,0,1),('REAR / FULL GEOMETRY',155,0,0)]):
    sheet.paste(render(yaw,mouth,blink),(W*i,40))
    draw.text((W*i+18,15),label,fill=(218,231,240))
output.parent.mkdir(parents=True,exist_ok=True)
sheet.save(output)
print(output)
