"""Bake AvatarSample_A VRM 0 into a textured GLB with mouthOpen/blink.

Requires numpy. Preserves the source separately; no network or Blender needed.
Arm pose is baked, so this export supports facial morphs, not skeletal animation.
"""
import copy
import hashlib
import json
import math
import struct
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'avatar-source/AvatarSample_A.vrm'
OUTPUT = ROOT / 'app/src/main/assets/models/avatar-sample-a.glb'


def convert():
    raw = SOURCE.read_bytes()
    size = struct.unpack_from('<I', raw, 12)[0]
    src = json.loads(raw[20:20 + size])
    binary = raw[28 + size:]
    assert src['extensions']['VRM']['meta']['title'] == 'AvatarSample_A'
    out = {'asset': {'version': '2.0', 'generator': 'convert_vroid_avatar.py',
                     'copyright': 'VRoid / pixiv; see avatar-source/AvatarSample_A-LICENSE.md'},
           'scene': 0, 'scenes': [{'nodes': []}], 'nodes': [], 'meshes': [],
           'accessors': [], 'bufferViews': [], 'buffers': [],
           'materials': copy.deepcopy(src['materials']),
           'textures': copy.deepcopy(src['textures']),
           'samplers': copy.deepcopy(src.get('samplers', [])), 'images': [],
           'extensionsUsed': ['KHR_materials_unlit']}
    # Use the VRM's standard unlit fallback, not its unsupported MToon shader.
    # Lighting textures have no effect in unlit and would request tangent generation.
    for material in out['materials']:
        for key in ('normalTexture', 'occlusionTexture', 'emissiveTexture', 'emissiveFactor'):
            material.pop(key, None)
        material['pbrMetallicRoughness'].pop('metallicRoughnessTexture', None)
    used_textures = sorted({m['pbrMetallicRoughness']['baseColorTexture']['index']
                            for m in out['materials'] if 'baseColorTexture' in m['pbrMetallicRoughness']})
    out['textures'] = [copy.deepcopy(src['textures'][i]) for i in used_textures]
    used_images = sorted({t['source'] for t in out['textures']})
    for t in out['textures']:
        t['source'] = used_images.index(t['source'])
    for m in out['materials']:
        tex = m['pbrMetallicRoughness'].get('baseColorTexture')
        if tex:
            tex['index'] = used_textures.index(tex['index'])
    data = bytearray()

    def read(idx):
        a = src['accessors'][idx]
        assert 'sparse' not in a
        v = src['bufferViews'][a['bufferView']]
        dtype = np.dtype({5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']])
        width = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}[a['type']]
        arr = np.ndarray((a['count'], width), dtype=dtype, buffer=binary,
                         offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                         strides=(v.get('byteStride', dtype.itemsize * width), dtype.itemsize)).copy()
        if a.get('normalized'):
            arr = arr.astype(float) / np.iinfo(dtype).max
        return arr

    def blob(b):
        data.extend(b'\0' * (-len(data) % 4))
        index = len(out['bufferViews'])
        out['bufferViews'].append({'buffer': 0, 'byteOffset': len(data), 'byteLength': len(b)})
        data.extend(b)
        return index

    accessor_cache = {}

    def accessor(arr, kind, component=5126, bounds=False):
        arr = np.asarray(arr, dtype='<f4' if component == 5126 else '<u4')
        assert np.isfinite(arr).all()
        payload = arr.tobytes()
        key = (kind, component, bounds, hashlib.sha256(payload).digest())
        if key in accessor_cache:
            return accessor_cache[key]
        a = {'bufferView': blob(payload), 'componentType': component,
             'count': len(arr), 'type': kind}
        out['bufferViews'][a['bufferView']]['target'] = 34963 if kind == 'SCALAR' else 34962
        if bounds:
            a.update(min=arr.min(axis=0).tolist(), max=arr.max(axis=0).tolist())
        out['accessors'].append(a)
        accessor_cache[key] = len(out['accessors']) - 1
        return accessor_cache[key]

    # Explicit TRS support; glTF quaternion order is x,y,z,w.
    def local(n):
        if 'matrix' in n:
            return np.array(n['matrix']).reshape(4, 4).T
        x, y, z, w = n.get('rotation', [0, 0, 0, 1])
        m = np.eye(4)
        m[:3, :3] = [[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
                      [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
                      [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]]
        m[:3, :3] *= n.get('scale', [1, 1, 1])
        m[:3, 3] = n.get('translation', [0, 0, 0])
        return m

    nodes = copy.deepcopy(src['nodes'])
    bones = {b['bone']: b['node'] for b in src['extensions']['VRM']['humanoid']['humanBones']}
    for name, degrees in [('leftUpperArm', 65), ('rightUpperArm', -65)]:
        angle = math.radians(degrees) / 2
        assert nodes[bones[name]].get('rotation', [0, 0, 0, 1]) == [0, 0, 0, 1]
        nodes[bones[name]]['rotation'] = [0, 0, math.sin(angle), math.cos(angle)]
    world = {}

    def visit(i, parent):
        world[i] = parent @ local(nodes[i])
        for c in nodes[i].get('children', []):
            visit(c, world[i])
    for i in src['scenes'][src.get('scene', 0)]['nodes']:
        visit(i, np.eye(4))

    groups = src['extensions']['VRM']['blendShapeMaster']['blendShapeGroups']
    expressions = {}
    for preset, name in [('a', 'mouthOpen'), ('blink', 'blink')]:
        expressions[name] = next(g['binds'] for g in groups if g['presetName'] == preset)
    prepared = []
    for ni, node in enumerate(nodes):
        if 'mesh' not in node:
            continue
        mi = node['mesh']
        mesh = src['meshes'][mi]
        primitives = []
        for p in mesh['primitives']:
            attrs = p['attributes']
            positions = read(attrs['POSITION'])
            if 'skin' in node:
                skin = src['skins'][node['skin']]
                inverse = read(skin['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
                joint_matrices = np.array([world[j] @ inv for j, inv in zip(skin['joints'], inverse)])
                joints, weights = read(attrs['JOINTS_0']), read(attrs['WEIGHTS_0'])
                weights = weights / weights.sum(axis=1, keepdims=True)
                matrices = (joint_matrices[joints] * weights[:, :, None, None]).sum(axis=1)
            else:
                matrices = np.broadcast_to(world[ni], (len(positions), 4, 4))
            linear = matrices[:, :3, :3]
            def direction(v):
                return np.einsum('nij,nj->ni', linear, v)
            pos = direction(positions) + matrices[:, :3, 3]
            normal = direction(read(attrs['NORMAL']))
            normal /= np.maximum(np.linalg.norm(normal, axis=1, keepdims=True), 1e-8)
            targets = []
            if any(b['mesh'] == mi for binds in expressions.values() for b in binds):
                for binds in expressions.values():
                    delta = np.zeros_like(positions)
                    for b in binds:
                        if b['mesh'] == mi:
                            delta += read(p['targets'][b['index']]['POSITION']) * b['weight'] / 100
                    targets.append(direction(delta))
            primitives.append((p, pos, normal, targets))
        prepared.append((node['name'], primitives))

    all_pos = np.concatenate([p[1] for _, ps in prepared for p in ps])
    lo, hi = all_pos.min(axis=0), all_pos.max(axis=0)
    scale = 2.7 / (hi[1] - lo[1])
    center = (lo + hi) / 2
    # VRM 0 faces -Z. Rotate 180 degrees about Y to match the camera.
    flip = np.array([-1, 1, -1])
    for name, ps in prepared:
        mesh = {'name': name, 'primitives': []}
        for p, pos, normal, targets in ps:
            pos = (pos - center) * scale * flip + [0, 0.2, 0]
            new = {'attributes': {'POSITION': accessor(pos, 'VEC3', bounds=True),
                                   'NORMAL': accessor(normal * flip, 'VEC3'),
                                   'TEXCOORD_0': accessor(read(p['attributes']['TEXCOORD_0']), 'VEC2')},
                   'indices': accessor(read(p['indices']), 'SCALAR', 5125), 'material': p['material']}
            if targets:
                new['targets'] = [{'POSITION': accessor(t * scale * flip, 'VEC3', bounds=True)} for t in targets]
                mesh['extras'] = {'targetNames': list(expressions)}
                mesh['weights'] = [0, 0]
            mesh['primitives'].append(new)
        out['scenes'][0]['nodes'].append(len(out['nodes']))
        out['nodes'].append({'name': name, 'mesh': len(out['meshes'])})
        out['meshes'].append(mesh)
    for image_index in used_images:
        im = src['images'][image_index]
        v = src['bufferViews'][im['bufferView']]
        start = v.get('byteOffset', 0)
        out['images'].append({'mimeType': im['mimeType'], 'bufferView': blob(binary[start:start+v['byteLength']])})
    out['buffers'] = [{'byteLength': len(data)}]
    encoded = json.dumps(out, separators=(',', ':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    data.extend(b'\0' * (-len(data) % 4))
    result = struct.pack('<III', 0x46546C67, 2, 28+len(encoded)+len(data))
    result += struct.pack('<II', len(encoded), 0x4E4F534A) + encoded
    result += struct.pack('<II', len(data), 0x004E4942) + data
    OUTPUT.write_bytes(result)
    print(json.dumps({'output': str(OUTPUT), 'bytes': len(result),
                      'source_sha256': hashlib.sha256(raw).hexdigest(),
                      'output_sha256': hashlib.sha256(result).hexdigest()}, indent=2))


if __name__ == '__main__':
    convert()
