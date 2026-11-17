"""Original small pixel shapes for grass, crystalline ribs, wind petals and water beads."""
from pathlib import Path
from PIL import Image, ImageDraw
import json


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/uwudimension'
TEXTURES = ASSETS / 'textures/particle'


def save(name, image):
    image.save(TEXTURES / (name + '.png'), optimize=True)
    (ASSETS / 'particles' / (name + '.json')).write_text(
        json.dumps({'textures': ['uwudimension:' + name]}, indent=2) + '\n', encoding='utf-8')


grass = Image.new('RGBA', (16, 16))
d = ImageDraw.Draw(grass)
# Three distinct blades have a dark neck, a cool green face and a dew-lit edge.
d.polygon([(5,15),(4,12),(1,9),(0,6),(4,9),(7,14)], fill='#386A4C')
d.polygon([(7,15),(6,8),(4,3),(4,1),(7,5),(9,12),(10,15)], fill='#4C9D62')
d.polygon([(9,15),(10,9),(12,6),(15,5),(13,8),(12,12),(11,15)], fill='#75BC76')
d.line([(7,14),(7,8),(5,3)], fill='#A5D995')
d.line([(10,14),(11,10),(13,7)], fill='#C8E6AB')
d.line([(6,14),(4,11),(2,9)], fill='#81BF79')
d.point((5,2), fill='#ECF4C3')
save('rising_grass', grass)

ice = Image.new('RGBA', (16, 16))
d = ImageDraw.Draw(ice)
d.polygon([(7,0),(11,5),(9,14),(6,15),(4,6)], fill='#62AEBB')
d.polygon([(7,0),(8,6),(6,14),(4,6)], fill='#CBF6F7')
d.polygon([(8,5),(11,5),(9,14),(7,15)], fill='#8DD7E7')
d.line([(7,2),(6,7),(6,12)], fill='#F4FFFF')
d.polygon([(12,7),(14,9),(13,14),(10,15)], fill='#81C4DD')
d.line([(12,8),(12,12)], fill='#E3FCFF')
d.polygon([(2,9),(4,11),(4,15),(1,14)], fill='#9DDFE8')
d.point((3,11), fill='#E3FCFF')
save('ice_shard', ice)

wind = Image.new('RGBA', (16, 16))
d = ImageDraw.Draw(wind)
# A bent, tapering leaf shows the handed flow of each helical wind ribbon.
d.polygon([(1,11),(2,7),(5,4),(10,3),(14,4),(11,5),(7,6),(5,8),(5,10),(7,11),(9,10),(10,8),(11,9),(10,12),(7,14),(4,13)], fill='#8CD5C6')
d.line([(2,10),(3,7),(6,5),(10,4),(13,4)], fill='#EFF9DF')
d.line([(5,10),(7,12),(9,11)], fill='#CFF3DF')
d.point((13,7), fill='#FCFFE9')
save('wind_petal', wind)

water = Image.new('RGBA', (16, 16))
d = ImageDraw.Draw(water)
d.polygon([(8,1),(10,5),(13,9),(13,12),(11,14),(5,14),(3,12),(3,9),(6,5)], fill='#409BAF')
d.polygon([(8,2),(9,6),(12,10),(11,13),(6,13),(4,11),(5,8)], fill='#90E3E8')
d.polygon([(7,6),(8,5),(8,8),(6,11),(5,10)], fill='#EEFCFA')
d.line([(8,13),(11,12)], fill='#B5F4EF')
save('water_bead', water)
