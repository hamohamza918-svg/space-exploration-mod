"""Rebuild authored Cryo Lance geometry, keyframes and UV atlas. Requires Pillow."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/spacemod'
def write(name,obj):
 p=ASSETS/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,indent=2)+'\n')
# Each palette tile is a separate material. Explicit face UVs avoid atlas bleeding.
palette=[(18,32,52),(44,77,101),(132,188,205),(62,194,241),(220,252,255)]
im=Image.new('RGBA',(80,16));d=ImageDraw.Draw(im)
for i,col in enumerate(palette):
 d.rectangle((i*16,0,i*16+15,15),fill=col+(255,))
 d.rectangle((i*16+1,1,i*16+14,2),fill=tuple(min(255,c+25) for c in col)+(255,))
 d.line((i*16+2,14,i*16+14,14),fill=tuple(max(0,c-20) for c in col)+(255,),width=1)
p=ASSETS/'textures/item/cryo_lance_animated.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
def cube(origin,size,material=0,**extra):
 return dict(origin=origin,size=size,uv={face:{'uv':[material*16+1,3],'uv_size':[13,10]} for face in ['north','south','east','west','up','down']},**extra)
bones=[{'name':'root','pivot':[0,9,0]},
 {'name':'shaft','parent':'root','pivot':[0,0,0],'cubes':[
 cube([-1,-14,-1],[2,31,2],1),cube([-1.5,-9,-1.5],[3,9,3],0),
 *[cube([-1.7,y,-1.7],[3.4,.6,3.4],2) for y in [-9,-6,-3,0]],
 cube([-1.5,-14.5,-1.5],[3,2,3],2),cube([-.4,1,-1.1],[.8,14,.3],3)]},
 {'name':'housing','parent':'root','pivot':[0,17,0],'cubes':[
 cube([-3,15,-3],[6,4,6],0),cube([-3.3,15,-3.3],[6.6,.8,6.6],2),
 cube([-3.3,18.2,-3.3],[6.6,.8,6.6],2)]},
 {'name':'core','parent':'root','pivot':[0,22,0],'cubes':[
 cube([-1.3,19,-1.3],[2.6,7,2.6],3),cube([-.6,20,-1.5],[1.2,5,3],4)]},
 {'name':'collar','parent':'root','pivot':[0,20,0],'cubes':[
 cube([-3.8,19.5,-3.8],[7.6,1,1],2),cube([-3.8,19.5,2.8],[7.6,1,1],2),
 cube([-3.8,19.5,-2.8],[1,1,5.6],2),cube([2.8,19.5,-2.8],[1,1,5.6],2)]}]
for i in range(4):
 # Radial split prongs with stepped ice blades.
 bones.append({'name':f'prong_{i}','parent':'root','pivot':[0,17,0],'rotation':[0,i*90,0], 'cubes':[
 cube([2,18,-1.3],[1.8,8,2.6],1),cube([2.1,25,-1],[1.5,6,2],2),
 cube([2.3,30,-.7],[1,4,1.4],3),cube([2.55,33,-.35],[.5,2,.7],4),
 cube([3.7,19,-.5],[.3,8,1],3)]})
write('geckolib/models/cryo_lance.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.cryo_lance','texture_width':80,'texture_height':16,'visible_bounds_width':5,'visible_bounds_height':6,'visible_bounds_offset':[0,1,0]},'bones':bones}]})
def keys(*pairs):return {str(t):v for t,v in pairs}
def animation(length,spread,root=None,spin=180):
 b={'core':{'rotation':keys((0,[0,0,0]),(length,[0,spin,0])), 'scale':keys((0,[1,1,1]),(.2,[1.3,1.12,1.3]),(length,[1,1,1]))},
 'collar':{'rotation':keys((0,[0,0,0]),(length,[0,-spin,0]))}}
 for i in range(4):
  b[f'prong_{i}']={'rotation':keys((0,[0,0,0]),(.2,[0,0,-spread]),(max(.25,length-.15),[0,0,-spread]),(length,[0,0,0]))}
 if root:b['root']=root
 return {'animation_length':length,'bones':b}
anims={'idle':{'loop':True,'animation_length':4,'bones':{'core':{'rotation':keys((0,[0,0,0]),(4,[0,360,0]))},'collar':{'rotation':keys((0,[0,0,0]),(4,[0,-180,0]))}}},
 'stream':animation(2,20,{'rotation':keys((0,[0,0,0]),(.15,[-65,0,0]),(1.8,[-65,0,0]),(2,[0,0,0]))},720),
 'nova':animation(1.2,35,{'position':keys((0,[0,0,0]),(.08,[0,-4,0]),(.3,[0,-2,0]),(1.2,[0,0,0]))},360),
 'absolute_zero':animation(2.3,48,{'position':keys((0,[0,0,0]),(.45,[0,9,0]),(.6,[0,9,0]),(.65,[0,-7,0]),(.85,[0,-6,0]),(2.3,[0,0,0])), 'rotation':keys((0,[0,0,0]),(.45,[0,0,-15]),(.6,[0,0,-15]),(.65,[0,0,0]),(2.3,[0,0,0]))},1080),
 'shatter':animation(1,60,{'rotation':keys((0,[0,0,0]),(.15,[0,0,30]),(.25,[0,0,-20]),(1,[0,0,0]))},540)}
write('geckolib/animations/cryo_lance.animation.json',{'format_version':'1.8.0','animations':anims})
write('items/cryo_lance.json',{'model':{'type':'minecraft:special','base':'spacemod:item/cryo_lance','model':{'type':'geckolib:geckolib'}}})
write('models/item/cryo_lance.json',{'parent':'minecraft:item/handheld','textures':{'layer0':'spacemod:item/cryo_lance'},'display':{
 'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,1,0],'scale':[.55,.55,.55]},
 'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,1,0],'scale':[.55,.55,.55]},
 'firstperson_righthand':{'rotation':[0,0,-15],'translation':[2,0,-3],'scale':[.55,.55,.55]},
 'firstperson_lefthand':{'rotation':[0,0,15],'translation':[-2,0,-3],'scale':[.55,.55,.55]},
 'gui':{'rotation':[0,0,-35],'translation':[0,-3,0],'scale':[.29,.29,.29]},
 'ground':{'translation':[0,4,0],'scale':[.3,.3,.3]},'fixed':{'scale':[.35,.35,.35]}}})
# Transparent, authored sigil: two circles, hexagram, six distinct geometric glyphs.
im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im);white=(225,253,255,255)
for radius,width in [(243,3),(231,2),(172,3),(162,1),(56,2)]:
 d.ellipse((256-radius,256-radius,256+radius,256+radius),outline=white,width=width)
def pt(a,r):return (256+math.cos(a)*r,256+math.sin(a)*r)
for offset in [0,math.pi]:
 points=[pt(offset+i*math.tau/3,167) for i in range(3)];d.line(points+[points[0]],fill=white,width=3)
for i in range(12):
 a=i*math.tau/12
 d.line([pt(a,232),pt(a,243)],fill=white,width=3)
 a+=math.tau/24
 d.line([pt(a-.04,183),pt(a-.04,216),pt(a+.04,200),pt(a-.04,183)],fill=white,width=3)
 d.line([pt(a+.04,185),pt(a+.04,213)],fill=white,width=2)
p=ASSETS/'textures/effect/cryo_rune.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
print('Generated lance: 9 bones, 36 cubes, 5 animations; rune atlas.')
