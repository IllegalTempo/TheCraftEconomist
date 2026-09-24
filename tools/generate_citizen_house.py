"""Generate the deterministic 9 x 5 x 10 Citizen house, using only Python's stdlib."""
import gzip
import struct
from pathlib import Path


def string(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(value):
    return struct.pack('>i', value)


def sequence(kind, values):
    return bytes([kind]) + integer(len(values)) + b''.join(values)


def compound(fields):
    return b''.join(fields) + b'\0'


palette = []
blocks = {}


def state(name, **properties):
    fields = [tag(8, 'Name', string('minecraft:' + name))]
    if properties:
        fields.append(tag(10, 'Properties', compound([
            tag(8, key, string(value)) for key, value in sorted(properties.items())])))
    palette.append(compound(fields))
    return len(palette) - 1


air = state('air')
stone = state('cobblestone')
water = state('water', level='0')
oak = state('oak_planks')
glass = state('glass_pane')
door_lower = state('oak_door', facing='north', half='lower', hinge='left', open='false', powered='false')
door_upper = state('oak_door', facing='north', half='upper', hinge='left', open='false', powered='false')
bed_foot = state('white_bed', facing='south', part='foot', occupied='false')
bed_head = state('white_bed', facing='south', part='head', occupied='false')
lantern = state('lantern', hanging='true', waterlogged='false')
for x in range(9):
    for z in range(10):
        for y in range(5):
            if z < 7:
                blocks[x, y, z] = stone if y == 0 else oak if y == 4 or x in (0, 8) or z in (0, 6) else air
            else:
                blocks[x, y, z] = stone if y == 0 else air
# Extend the rear foundation into an outdoor, stone-lined water basin.
for x in range(3, 6):
    for z in range(7, 10):
        blocks[x, 0, z] = stone
        if x in (3, 5) or z in (7, 9):
            blocks[x, 1, z] = stone
blocks[4, 0, 8] = water
for x in (0, 8):
    for z in (2, 4):
        blocks[x, 2, z] = glass
blocks[4, 1, 0] = door_lower
blocks[4, 2, 0] = door_upper
for x in (1, 2, 6, 7):
    blocks[x, 1, 4] = bed_foot
    blocks[x, 1, 5] = bed_head
blocks[4, 3, 2] = lantern
blocks[4, 3, 5] = lantern
payload = compound([
    tag(3, 'DataVersion', integer(4900)),
    tag(9, 'size', sequence(3, [integer(v) for v in (9, 5, 10)])),
    tag(9, 'palette', sequence(10, palette)),
    tag(9, 'blocks', sequence(10, [compound([
        tag(9, 'pos', sequence(3, [integer(v) for v in pos])),
        tag(3, 'state', integer(index))]) for pos, index in sorted(blocks.items())])),
    tag(9, 'entities', sequence(10, [])),
])
output = Path(__file__).resolve().parents[1] / 'src/main/resources/data/theeconomist/structure/citizen_house.nbt'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(gzip.compress(tag(10, '', payload), mtime=0))
print(output)
