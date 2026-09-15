"""Generate Arc Carbine GeckoLib geometry, animations, item JSONs and palette texture. Requires Pillow."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/spacemod'

def write(name, obj):
    p = ASSETS / name; p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=2) + '\n')

# electric palette: gunmetal, steel, light steel, electric-yellow, bright
palette = [(28, 30, 38), (70, 74, 86), (150, 156, 170), (255, 214, 90), (255, 246, 200)]
im = Image.new('RGBA', (80, 16)); d = ImageDraw.Draw(im)
for i, col in enumerate(palette):
    d.rectangle((i * 16, 0, i * 16 + 15, 15), fill=col + (255,))
    d.rectangle((i * 16 + 1, 1, i * 16 + 14, 2), fill=tuple(min(255, c + 25) for c in col) + (255,))
    d.line((i * 16 + 2, 14, i * 16 + 14, 14), fill=tuple(max(0, c - 20) for c in col) + (255,), width=1)
p = ASSETS / 'textures/item/arc_carbine_animated.png'; p.parent.mkdir(parents=True, exist_ok=True); im.save(p)

def cube(origin, size, material=0, **extra):
    return dict(origin=origin, size=size,
                uv={face: {'uv': [material * 16 + 1, 3], 'uv_size': [13, 10]} for face in ['north', 'south', 'east', 'west', 'up', 'down']}, **extra)

# carbine built along +Z (barrel forward), Y up
bones = [
    {'name': 'root', 'pivot': [0, 2, 0]},
    {'name': 'body', 'parent': 'root', 'pivot': [0, 2, 0], 'cubes': [
        cube([-1.5, 0, -5], [3, 3.2, 9], 1),
        cube([-1.6, 3.0, -5.2], [3.2, 0.6, 9.4], 2)]},
    {'name': 'barrel', 'parent': 'root', 'pivot': [0, 2.6, 4], 'cubes': [
        cube([-0.7, 1.9, 4], [1.4, 1.4, 7], 0),
        cube([-0.5, 2.1, 10.6], [1.0, 1.0, 1.4], 3)]},
    {'name': 'coils', 'parent': 'root', 'pivot': [0, 2.6, 6.7], 'cubes': [
        cube([-1.15, 1.4, 4.5], [2.3, 2.3, 0.5], 3), cube([-1.15, 1.4, 6.0], [2.3, 2.3, 0.5], 3),
        cube([-1.15, 1.4, 7.5], [2.3, 2.3, 0.5], 3), cube([-1.15, 1.4, 9.0], [2.3, 2.3, 0.5], 4)]},
    {'name': 'stock', 'parent': 'root', 'pivot': [0, 1, -5], 'cubes': [
        cube([-1.1, -1.4, -9], [2.2, 3, 4.2], 1), cube([-1.1, -1.4, -5.4], [2.2, 3.8, 1], 2)]},
    {'name': 'grip', 'parent': 'root', 'pivot': [0, 0, -1], 'cubes': [
        cube([-1, -4.2, -2.6], [2, 4.2, 2.4], 1)]},
    {'name': 'sight', 'parent': 'root', 'pivot': [0, 3.6, 0], 'cubes': [
        cube([-0.4, 3.6, -2], [0.8, 1.2, 1], 2), cube([-0.4, 3.6, 8.5], [0.8, 1.4, 1], 2)]},
    {'name': 'mag', 'parent': 'root', 'pivot': [0, 0, 1], 'cubes': [
        cube([-1, -4.6, 0.4], [2, 3.6, 2.2], 0), cube([-0.8, -4.8, 0.6], [1.6, 0.6, 1.8], 3)]},
]
write('geckolib/models/arc_carbine.geo.json', {'format_version': '1.12.0', 'minecraft:geometry': [
    {'description': {'identifier': 'geometry.arc_carbine', 'texture_width': 80, 'texture_height': 16,
                     'visible_bounds_width': 6, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1, 0]}, 'bones': bones}]})

def keys(*pairs):
    return {str(t): v for t, v in pairs}

anims = {
    'idle': {'loop': True, 'animation_length': 2, 'bones': {
        'coils': {'rotation': keys((0, [0, 0, 0]), (2, [0, 0, 360]))}}},
    'fire': {'animation_length': 0.4, 'bones': {
        'root': {'position': keys((0, [0, 0, 0]), (0.05, [0, 0, -1.6]), (0.4, [0, 0, 0]))},
        'coils': {'scale': keys((0, [1, 1, 1]), (0.05, [1.35, 1.35, 1]), (0.4, [1, 1, 1]))}}},
    'ultimate': {'animation_length': 1.2, 'bones': {
        'root': {'rotation': keys((0, [0, 0, 0]), (0.3, [-35, 0, 0]), (1.0, [-35, 0, 0]), (1.2, [0, 0, 0]))},
        'coils': {'scale': keys((0, [1, 1, 1]), (0.3, [1.5, 1.5, 1.3]), (1.2, [1, 1, 1])),
                  'rotation': keys((0, [0, 0, 0]), (1.2, [0, 0, 720]))}}},
}
write('geckolib/animations/arc_carbine.animation.json', {'format_version': '1.8.0', 'animations': anims})
write('items/arc_carbine.json', {'model': {'type': 'minecraft:special', 'base': 'spacemod:item/arc_carbine', 'model': {'type': 'geckolib:geckolib'}}})
write('models/item/arc_carbine.json', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'spacemod:item/arc_carbine'}, 'display': {
    'thirdperson_righthand': {'rotation': [0, 100, 0], 'translation': [0, 3, 1], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_lefthand': {'rotation': [0, -80, 0], 'translation': [0, 3, 1], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_righthand': {'rotation': [0, 90, 0], 'translation': [1, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_lefthand': {'rotation': [0, -90, 0], 'translation': [1, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'gui': {'rotation': [8, -130, -8], 'translation': [0, 0, 0], 'scale': [0.42, 0.42, 0.42]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 4, 0], 'scale': [0.35, 0.35, 0.35]},
    'fixed': {'rotation': [0, 90, 0], 'scale': [0.4, 0.4, 0.4]}}})
print('Generated arc carbine: %d bones, 3 animations.' % len(bones))
