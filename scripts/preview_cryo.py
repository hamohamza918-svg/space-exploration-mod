"""Software geometry/keyframe preview, not Minecraft footage. Requires numpy and Pillow."""
from pathlib import Path
import json, math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/spacemod'
model=json.loads((A/'geckolib/models/cryo_lance.geo.json').read_text())['minecraft:geometry'][0]
anims=json.loads((A/'geckolib/animations/cryo_lance.animation.json').read_text())['animations']
texture=Image.open(A/'textures/item/cryo_lance_animated.png').convert('RGB')
fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font=ImageFont.truetype(fontpath,14);title=ImageFont.truetype(fontpath,24)
def trans(v):
 m=np.eye(4);m[:3,3]=v;return m
def rot(v):
 x,y,z=np.radians(v);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 rx=np.array([[1,0,0,0],[0,cx,-sx,0],[0,sx,cx,0],[0,0,0,1]])
 ry=np.array([[cy,0,sy,0],[0,1,0,0],[-sy,0,cy,0],[0,0,0,1]])
 rz=np.array([[cz,-sz,0,0],[sz,cz,0,0],[0,0,1,0],[0,0,0,1]])
 return rz@ry@rx
def sample(keys,t,default):
 if keys is None:return np.array(default)
 if isinstance(keys,list):return np.array(keys)
 pairs=sorted((float(k),np.array(v)) for k,v in keys.items())
 if t<=pairs[0][0]:return pairs[0][1]
 for (ta,a),(tb,b) in zip(pairs,pairs[1:]):
  if t<=tb:return a+(b-a)*(t-ta)/(tb-ta)
 return pairs[-1][1]
faces=[(0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)]
def draw_model(im,name,t,cx):
 draw=ImageDraw.Draw(im);matrices={};polys=[];view=rot([12,-32,-8]);ab=anims[name]['bones']
 for bone in model['bones']:
  p=np.array(bone['pivot']);channels=ab.get(bone['name'],{})
  position=sample(channels.get('position'),t,[0,0,0]);rotation=sample(channels.get('rotation'),t,[0,0,0]);scale=sample(channels.get('scale'),t,[1,1,1]);sm=np.diag([*scale,1])
  m=matrices.get(bone.get('parent'),np.eye(4))@trans(p+position)@rot(bone.get('rotation',[0,0,0]))@rot(rotation)@sm@trans(-p);matrices[bone['name']]=m
  for cube in bone.get('cubes',[]):
   o=np.array(cube['origin']);s=np.array(cube['size']);vs=np.array([np.r_[o+s*np.array([i&1,(i>>1)&1,(i>>2)&1]),1] for i in range(8)])
   vs=(view@m@vs.T).T[:,:3];uv=cube['uv']['north']['uv'];col=np.array(texture.getpixel(tuple(uv)))
   for f in faces:
    verts=vs[list(f)];normal=np.cross(verts[1]-verts[0],verts[2]-verts[0]);normal/=max(np.linalg.norm(normal),1e-9)
    light=.55+.45*abs(float(normal@np.array([.3,.7,.64])));color=tuple(np.minimum(255,col*light).astype(int))
    screen=[(cx+v[0]*7,452-v[1]*7) for v in verts];polys.append((verts[:,2].mean(),screen,color))
 for _,pts,color in sorted(polys,key=lambda x:x[0]):draw.polygon(pts,fill=color,outline=tuple(int(c*.75) for c in color))
frames=[]
for i in range(72):
 t=i/24;im=Image.new('RGB',(960,660),(9,18,30));d=ImageDraw.Draw(im)
 d.text((32,22),'CRYO LANCE / moving geometry',font=title,fill=(217,246,255))
 d.text((32,60),'Authored model + keyframe preview. Not an in-game recording.',font=font,fill=(132,165,181))
 for x in [16,328,640]:d.rounded_rectangle((x,105,x+304,606),radius=14,fill=(15,29,45),outline=(36,67,87))
 for name,cx,label in [('idle',168,'IDLE / rotating core'),('stream',480,'STREAM / prongs open'),('absolute_zero',792,'ULTIMATE / raise and slam')]:
  length=anims[name]['animation_length'];draw_model(im,name,min(t,length) if name!='idle' else t,cx)
  d.text((cx-135,568),label,font=font,fill=(173,233,253))
 d.text((32,625),'Four articulated prongs  •  Separate core and collar  •  Five animation clips',font=font,fill=(132,165,181))
 frames.append(im)
out=R/'docs';out.mkdir(exist_ok=True)
frames[12].save(out/'cryo-model-preview.png')
frames[0].save(out/'cryo-model-preview.gif',save_all=True,append_images=frames[1:],duration=42,loop=0)
print('Wrote geometry preview and animated GIF')
