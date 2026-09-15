from pathlib import Path
import json, numpy as np
from PIL import Image, ImageDraw
R=Path(__file__).resolve().parents[1]; A=R/'src/main/resources/assets/spacemod'
model=json.loads((A/'geckolib/models/arc_carbine.geo.json').read_text())['minecraft:geometry'][0]
anims=json.loads((A/'geckolib/animations/arc_carbine.animation.json').read_text())['animations']
texture=Image.open(A/'textures/item/arc_carbine_animated.png').convert('RGB')
def trans(v):
    m=np.eye(4); m[:3,3]=v; return m
def rot(v):
    x,y,z=np.radians(v); cx,sx=np.cos(x),np.sin(x); cy,sy=np.cos(y),np.sin(y); cz,sz=np.cos(z),np.sin(z)
    rx=np.array([[1,0,0,0],[0,cx,-sx,0],[0,sx,cx,0],[0,0,0,1]]); ry=np.array([[cy,0,sy,0],[0,1,0,0],[-sy,0,cy,0],[0,0,0,1]]); rz=np.array([[cz,-sz,0,0],[sz,cz,0,0],[0,0,1,0],[0,0,0,1]])
    return rz@ry@rx
def sample(keys,t,default):
    if keys is None: return np.array(default)
    if isinstance(keys,list): return np.array(keys)
    pairs=sorted((float(k),np.array(v)) for k,v in keys.items())
    if t<=pairs[0][0]: return pairs[0][1]
    for (ta,a),(tb,b) in zip(pairs,pairs[1:]):
        if t<=tb: return a+(b-a)*(t-ta)/(tb-ta)
    return pairs[-1][1]
faces=[(0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)]
def draw_model(im,name,t,cx,scale,view):
    draw=ImageDraw.Draw(im); matrices={}; polys=[]; ab=anims[name]['bones']
    for bone in model['bones']:
        p=np.array(bone['pivot']); ch=ab.get(bone['name'],{})
        pos=sample(ch.get('position'),t,[0,0,0]); rt=sample(ch.get('rotation'),t,[0,0,0]); sc=sample(ch.get('scale'),t,[1,1,1]); sm=np.diag([*sc,1])
        m=matrices.get(bone.get('parent'),np.eye(4))@trans(p+pos)@rot(bone.get('rotation',[0,0,0]))@rot(rt)@sm@trans(-p); matrices[bone['name']]=m
        for c in bone.get('cubes',[]):
            o=np.array(c['origin']); s=np.array(c['size']); vs=np.array([np.r_[o+s*np.array([i&1,(i>>1)&1,(i>>2)&1]),1] for i in range(8)])
            vs=(view@m@vs.T).T[:,:3]; uv=c['uv']['north']['uv']; col=np.array(texture.getpixel(tuple(uv)))
            for f in faces:
                v=vs[list(f)]; n=np.cross(v[1]-v[0],v[2]-v[0]); n/=max(np.linalg.norm(n),1e-9)
                light=.5+.5*abs(float(n@np.array([.3,.7,.64]))); color=tuple(np.minimum(255,col*light).astype(int))
                scr=[(cx+vv[0]*scale,330-vv[1]*scale) for vv in v]; polys.append((v[:,2].mean(),scr,color))
    for _,pts,color in sorted(polys,key=lambda x:x[0]): draw.polygon(pts,fill=color,outline=tuple(int(c*.7) for c in color))
im=Image.new('RGB',(960,480),(9,14,26))
for k,(name,cx,lbl,view) in enumerate([('idle',210,'IDLE',[10,-38,0]),('idle',500,'SIDE',[4,-90,0]),('ultimate',790,'ULTIMATE',[12,-40,0])]):
    draw_model(im,name,0.6,cx,10,rot(view))
im.save(R/'docs/references/arc-model-preview.png'); print('preview written')
