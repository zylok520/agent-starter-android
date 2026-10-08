"""Generate the original, texture-free prototype GLB. Python standard library only.

Run from anywhere: python tools/generate_avatar.py
All geometry is authored procedurally here; no third-party character assets.
"""
import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/src/main/assets/models/prototype-avatar.glb'
blob = bytearray()
doc = dict(asset=dict(version='2.0', generator='agent-starter-android prototype'),
           scene=0, scenes=[dict(nodes=[0])], nodes=[dict(name='Avatar', children=[])],
           meshes=[], materials=[], accessors=[], bufferViews=[], buffers=[])


def accessor(values, kind, component=5126):
    width = 3 if kind == 'VEC3' else 1
    while len(blob) % 4:
        blob.append(0)
    start = len(blob)
    flat = [v for row in values for v in row] if width == 3 else values
    blob.extend(struct.pack('<' + ('f' if component == 5126 else 'H') * len(flat), *flat))
    view = len(doc['bufferViews'])
    doc['bufferViews'].append(dict(buffer=0, byteOffset=start, byteLength=len(blob)-start,
                                   target=34963 if component == 5123 else 34962))
    result = dict(bufferView=view, componentType=component, count=len(values), type=kind)
    if width == 3:
        result.update(min=[min(row[i] for row in values) for i in range(3)],
                      max=[max(row[i] for row in values) for i in range(3)])
    doc['accessors'].append(result)
    return len(doc['accessors']) - 1


def material(name, rgb):
    doc['materials'].append(dict(name=name, pbrMetallicRoughness=dict(
        baseColorFactor=[*rgb, 1], metallicFactor=0, roughnessFactor=0.65)))
    return len(doc['materials']) - 1


skin = material('Warm porcelain', [0.82, 0.49, 0.33])
hair = material('Midnight hair', [0.035, 0.055, 0.09])
coat = material('Ocean jacket', [0.055, 0.24, 0.34])
white = material('Ivory', [0.9, 0.94, 0.95])
iris = material('Teal eyes', [0.035, 0.30, 0.32])
mouth = material('Mouth interior', [0.07, 0.009, 0.022])


def ellipsoid(name, center, radii, mat, morph=None):
    rows, cols = 20, 32
    positions, normals, deltas, normal_deltas, indices = [], [], [], [], []
    for row in range(rows + 1):
        theta = math.pi * row / rows
        for col in range(cols + 1):
            phi = 2 * math.pi * col / cols
            unit = (math.sin(theta)*math.cos(phi), math.cos(theta), math.sin(theta)*math.sin(phi))
            positions.append([unit[i]*radii[i] for i in range(3)])
            n = [unit[i]/radii[i] for i in range(3)]
            length = math.sqrt(sum(v*v for v in n))
            n = [v/length for v in n]
            normals.append(n)
            if morph:
                deltas.append([unit[i]*(morph[i]-radii[i]) for i in range(3)])
                m = [unit[i]/morph[i] for i in range(3)]
                length = math.sqrt(sum(v*v for v in m))
                normal_deltas.append([m[i]/length - n[i] for i in range(3)])
    for row in range(rows):
        for col in range(cols):
            a = row*(cols+1)+col
            b = a+cols+1
            if row > 0:
                indices.extend([a, a+1, b])
            if row < rows-1:
                indices.extend([a+1, b+1, b])
    primitive = dict(attributes=dict(POSITION=accessor(positions, 'VEC3'), NORMAL=accessor(normals, 'VEC3')),
                     indices=accessor(indices, 'SCALAR', 5123), material=mat)
    mesh = dict(name=name, primitives=[primitive])
    if morph:
        primitive['targets'] = [dict(POSITION=accessor(deltas, 'VEC3'), NORMAL=accessor(normal_deltas, 'VEC3'))]
        mesh.update(weights=[0], extras=dict(targetNames=['mouthOpen' if name == 'Mouth' else 'blink']))
    doc['meshes'].append(mesh)
    doc['nodes'][0]['children'].append(len(doc['nodes']))
    doc['nodes'].append(dict(name=name, mesh=len(doc['meshes'])-1, translation=center))


ellipsoid('Shoulders', [0, -0.55, -0.08], [0.70, 0.48, 0.31], coat)
ellipsoid('Shirt', [0, -0.39, 0.22], [0.23, 0.33, 0.06], white)
ellipsoid('Neck', [0, -0.06, 0], [0.18, 0.32, 0.20], skin)
ellipsoid('HairBack', [0, 0.70, -0.12], [0.49, 0.63, 0.39], hair)
ellipsoid('Head', [0, 0.66, 0], [0.43, 0.56, 0.40], skin)
ellipsoid('HairTop', [0, 1.13, -0.02], [0.46, 0.24, 0.38], hair)
ellipsoid('Fringe', [-0.18, 1.07, 0.25], [0.26, 0.17, 0.18], hair)
for x in [-1, 1]:
    ellipsoid(f'Ear{x}', [x*0.43, 0.63, 0], [0.085, 0.15, 0.08], skin)
    ellipsoid(f'Brow{x}', [x*0.18, 0.91, 0.35], [0.11, 0.022, 0.025], hair)
    # Each component closes into a thin line using the same morph weight.
    for suffix, z, r, m in [('White', 0.358, [0.105, 0.062, 0.045], white),
                             ('Iris', 0.396, [0.040, 0.046, 0.014], iris),
                             ('Pupil', 0.408, [0.019, 0.028, 0.009], hair)]:
        ellipsoid(f'Eye{x}{suffix}', [x*0.18, 0.79, z], r, m, [r[0], 0.004, r[2]])
ellipsoid('Nose', [0, 0.62, 0.39], [0.065, 0.105, 0.08], skin)
ellipsoid('Mouth', [0, 0.41, 0.36], [0.105, 0.010, 0.026], mouth, [0.12, 0.092, 0.040])

doc['buffers'] = [dict(byteLength=len(blob))]
metadata = json.dumps(doc, separators=(',', ':')).encode()
metadata += b' ' * (-len(metadata) % 4)
blob.extend(b'\0' * (-len(blob) % 4))
glb = (struct.pack('<III', 0x46546C67, 2, 12+8+len(metadata)+8+len(blob))
       + struct.pack('<II', len(metadata), 0x4E4F534A) + metadata
       + struct.pack('<II', len(blob), 0x004E4942) + blob)
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_bytes(glb)
print(f'{OUTPUT}: {len(glb):,} bytes, {len(doc["meshes"])} meshes')
