#!/usr/bin/env python3
"""Generate all Fabric mod assets/data for Space Exploration (1.21.8).

Emits item/block models, item model definitions (1.21.4+ items/), blockstates,
lang, loot tables, recipes, and textures (copied from the old resource pack where
they exist, otherwise generated procedurally)."""
import json, os, random
from PIL import Image, ImageDraw

MOD = os.path.join(os.path.dirname(__file__), "src", "main", "resources")
RP_ITEM = os.path.join(os.path.dirname(__file__), "..", "SpaceExploration", "resourcepack", "assets", "space", "textures", "item")
RP_BLOCK = os.path.join(os.path.dirname(__file__), "..", "SpaceExploration", "resourcepack", "assets", "space", "textures", "block")
NS = "spacemod"

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)

def apath(*p): return os.path.join(MOD, "assets", NS, *p)
def dpath(*p): return os.path.join(MOD, "data", NS, *p)

# ---- content lists (MUST match Java) ---------------------------------------
ITEMS = ["raw_xylite","xylite_ingot","raw_titanium","titanium_ingot","oxygen_tank",
         "guidance_circuit","heat_shield_plate","rocket_fuel","launch_pad_core","laser_cutter",
         "space_helmet","space_chestplate","space_leggings","space_boots",
         "thermal_helmet","thermal_chestplate","thermal_leggings","thermal_boots"]
HANDHELD = {"laser_cutter"}
BLOCKS = ["xylite_ore","deepslate_xylite_ore","titanium_ore","deepslate_titanium_ore",
          "moon_stone","mars_stone","europa_ice","venus_rock","fabricator"]
# block -> dropped item id (None = drops itself)
DROPS = {"xylite_ore":"raw_xylite","deepslate_xylite_ore":"raw_xylite",
         "titanium_ore":"raw_titanium","deepslate_titanium_ore":"raw_titanium"}
# item -> resourcepack source texture filename (else generate)
ITEM_SRC = {"raw_xylite":"xylite_raw.png"}

NAMES = {
 "raw_xylite":"Raw Xylite Crystal","xylite_ingot":"Refined Xylite Ingot",
 "raw_titanium":"Raw Titanium","titanium_ingot":"Titanium Ingot","oxygen_tank":"Oxygen Tank",
 "guidance_circuit":"Guidance Circuit","heat_shield_plate":"Heat-Shield Plate","rocket_fuel":"Rocket Fuel",
 "launch_pad_core":"Launch Pad Core","laser_cutter":"Laser Cutter",
 "space_helmet":"Space Helmet","space_chestplate":"Space Chestplate","space_leggings":"Space Leggings","space_boots":"Space Boots",
 "thermal_helmet":"Thermal Space Helmet","thermal_chestplate":"Thermal Space Chestplate","thermal_leggings":"Thermal Space Leggings","thermal_boots":"Thermal Space Boots",
 "xylite_ore":"Xylite Ore","deepslate_xylite_ore":"Deepslate Xylite Ore","titanium_ore":"Titanium Ore","deepslate_titanium_ore":"Deepslate Titanium Ore",
 "moon_stone":"Moon Stone","mars_stone":"Mars Stone","europa_ice":"Europa Ice","venus_rock":"Venus Rock","fabricator":"Fabricator",
}

# ---- textures ---------------------------------------------------------------
def noisy(base, spread=18):
    im = Image.new("RGBA",(16,16)); px=im.load()
    for x in range(16):
        for y in range(16):
            j=random.randint(-spread,spread)
            px[x,y]=(max(0,min(255,base[0]+j)),max(0,min(255,base[1]+j)),max(0,min(255,base[2]+j)),255)
    return im

def ore_tex(base, speck):
    im=noisy(base); d=ImageDraw.Draw(im)
    for _ in range(6):
        x,y=random.randint(1,12),random.randint(1,12); s=random.randint(1,3)
        d.rectangle([x,y,x+s,y+s],fill=speck+(255,))
        im.putpixel((x,y),(min(255,speck[0]+60),min(255,speck[1]+60),min(255,speck[2]+60),255))
    return im

def copy_or_gen_item(name):
    dst = apath("textures","item",name+".png")
    src_name = ITEM_SRC.get(name, name+".png")
    src = os.path.join(RP_ITEM, src_name)
    if os.path.exists(src):
        Image.open(src).save(dst); return
    os.makedirs(os.path.dirname(dst),exist_ok=True)
    noisy((150,150,160)).save(dst)  # fallback placeholder

def block_tex(name):
    dst=apath("textures","block",name+".png"); os.makedirs(os.path.dirname(dst),exist_ok=True)
    src=os.path.join(RP_BLOCK,name+".png")
    if os.path.exists(src): Image.open(src).save(dst); return
    gen={
     "deepslate_xylite_ore":lambda:ore_tex((70,70,78),(155,92,255)),
     "titanium_ore":lambda:ore_tex((128,128,132),(200,205,215)),
     "deepslate_titanium_ore":lambda:ore_tex((70,70,78),(200,205,215)),
     "moon_stone":lambda:noisy((150,150,156)),
     "mars_stone":lambda:noisy((150,80,55)),
     "europa_ice":lambda:noisy((150,205,230),10),
     "venus_rock":lambda:noisy((60,50,44)),
    }.get(name, lambda:noisy((130,130,140)))
    gen().save(dst)

random.seed(42)
for it in ITEMS: copy_or_gen_item(it)
for b in BLOCKS: block_tex(b)

# ---- item models + item definitions ----------------------------------------
for it in ITEMS:
    parent = "minecraft:item/handheld" if it in HANDHELD else "minecraft:item/generated"
    w(apath("models","item",it+".json"), {"parent":parent,"textures":{"layer0":f"{NS}:item/{it}"}})
    w(apath("items",it+".json"), {"model":{"type":"minecraft:model","model":f"{NS}:item/{it}"}})

# ---- block models, blockstates, block-item definitions ---------------------
for b in BLOCKS:
    w(apath("models","block",b+".json"), {"parent":"minecraft:block/cube_all","textures":{"all":f"{NS}:block/{b}"}})
    w(apath("blockstates",b+".json"), {"variants":{"":{"model":f"{NS}:block/{b}"}}})
    w(apath("items",b+".json"), {"model":{"type":"minecraft:model","model":f"{NS}:block/{b}"}})

# ---- lang ------------------------------------------------------------------
lang={"itemgroup.spacemod.space":"Space Exploration","entity.spacemod.void_walker":"Void Walker"}
for k,v in NAMES.items():
    prefix = "block" if k in BLOCKS else "item"
    lang[f"{prefix}.{NS}.{k}"]=v
w(apath("lang","en_us.json"), lang)

# ---- loot tables (blocks) --------------------------------------------------
for b in BLOCKS:
    drop = DROPS.get(b, b)
    w(dpath("loot_table","blocks",b+".json"), {
        "type":"minecraft:block",
        "pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":f"{NS}:{drop}"}],
                  "conditions":[{"condition":"minecraft:survives_explosion"}]}]
    })

# ---- recipes ---------------------------------------------------------------
def rid(x): return x if ":" in x else f"{NS}:{x}"
def smelt(inp,out,name,exp=0.7):
    w(dpath("recipe",name+".json"), {"type":"minecraft:smelting","category":"misc",
        "ingredient":rid(inp),"result":{"id":rid(out)},"experience":exp,"cookingtime":200})
def shapeless(out,count,ings,name):
    w(dpath("recipe",name+".json"), {"type":"minecraft:crafting_shapeless","category":"misc",
        "ingredients":[rid(i) for i in ings],"result":{"id":rid(out),"count":count}})
def shaped(out,count,pattern,key,name):
    w(dpath("recipe",name+".json"), {"type":"minecraft:crafting_shaped","category":"misc",
        "pattern":pattern,"key":{k:rid(v) for k,v in key.items()},"result":{"id":rid(out),"count":count}})

smelt("raw_xylite","xylite_ingot","xylite_ingot")
smelt("raw_titanium","titanium_ingot","titanium_ingot")
shaped("titanium_ingot",2,["III","III"],{"I":"minecraft:iron_ingot"},"titanium_from_iron")  # fallback source on Earth
shaped("oxygen_tank",1,["I I","IGI","III"],{"I":"minecraft:iron_ingot","G":"minecraft:glass"},"oxygen_tank")
shapeless("guidance_circuit",1,["minecraft:redstone","minecraft:gold_ingot","minecraft:quartz"],"guidance_circuit")
shaped("heat_shield_plate",1,["TMT"],{"T":"titanium_ingot","M":"minecraft:magma_cream"},"heat_shield_plate")
shapeless("rocket_fuel",4,["minecraft:copper_ingot","minecraft:copper_ingot","minecraft:copper_ingot","minecraft:copper_ingot","minecraft:blaze_powder"],"rocket_fuel")
shaped("launch_pad_core",1,["III","IQI","III"],{"I":"minecraft:iron_ingot","Q":"minecraft:quartz"},"launch_pad_core")
shaped("laser_cutter",1,["XCX","XTX"," T "],{"X":"xylite_ingot","C":"guidance_circuit","T":"titanium_ingot"},"laser_cutter")
shaped("fabricator",1,["III","ICI","III"],{"I":"minecraft:iron_block","C":"guidance_circuit"},"fabricator")
# basic suit
shaped("space_helmet",1,["TTT","TOT"],{"T":"titanium_ingot","O":"oxygen_tank"},"space_helmet")
shaped("space_chestplate",1,["T T","TOT","TTT"],{"T":"titanium_ingot","O":"oxygen_tank"},"space_chestplate")
shaped("space_leggings",1,["TTT","T T","T T"],{"T":"titanium_ingot"},"space_leggings")
shaped("space_boots",1,["T T","T T"],{"T":"titanium_ingot"},"space_boots")
# thermal suit
for slot in ["helmet","chestplate","leggings","boots"]:
    shaped(f"thermal_{slot}",1,["HXH"," S "],{"H":"heat_shield_plate","X":"xylite_ingot","S":f"space_{slot}"},f"thermal_{slot}")

# ---- dimensions (best-effort; validated at runtime, not compile) -----------
# planet -> (surface block, ambient_light, fixed_time, effects)
PLANETS = {
 "moon":   ("moon_stone",  0.05, 18000, "minecraft:the_end"),
 "mars":   ("mars_stone",  0.12, 18000, "minecraft:overworld"),
 "europa": ("europa_ice",  0.10, 16000, "minecraft:the_end"),
 "venus":  ("venus_rock",  0.20, 18000, "minecraft:the_nether"),
}
for p,(block,ambient,time,effects) in PLANETS.items():
    w(dpath("dimension_type",p+".json"), {
        "ultrawarm": False, "natural": False, "piglin_safe": False,
        "respawn_anchor_works": False, "bed_works": False, "has_raids": False,
        "has_skylight": True, "has_ceiling": False,
        "coordinate_scale": 1.0, "ambient_light": ambient, "fixed_time": time,
        "logical_height": 384, "effects": effects,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "min_y": -64, "height": 384,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0
    })
    w(dpath("dimension",p+".json"), {
        "type": f"{NS}:{p}",
        "generator": {
            "type": "minecraft:flat",
            "settings": {
                "biome": "minecraft:the_end",
                "lakes": False, "features": False,
                "layers": [
                    {"block": "minecraft:bedrock", "height": 1},
                    {"block": f"{NS}:{block}", "height": 62}
                ],
                "structure_overrides": []
            }
        }
    })

print("mod assets generated")
