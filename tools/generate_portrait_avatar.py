"""Original stylized 3D bust inspired by the supplied portrait, not a face reconstruction.
Requires numpy. Geometry, morphs and materials are authored here, without photo billboards.
"""
import json
import math
import struct
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/src/main/assets/models/portrait-avatar.glb'
doc = dict(asset=dict(version='2.0', generator='Portrait bust v1'), scene=0,
           scenes=[dict(nodes=[0])], nodes=[dict(name='PortraitRoot', children=[])],
           meshes=[], materials=[], accessors=[], bufferViews=[], buffers=[])
binary = bytearray()


def material(name, rgb, roughness=0.65):
    doc['materials'].append(dict(name=name, doubleSided=True, pbrMetallicRoughness=dict(
        baseColorFactor=[*rgb, 1], metallicFactor=0, roughnessFactor=roughness)))
    return len(doc['materials']) - 1


SKIN = material('Peach porcelain', [.86, .58, .44])
HAIR = material('Soft black hair', [.019, .016, .023], .4)
STRAND = material('Subtle hair sheen', [.033, .027, .037], .42)
LIPS = material('Rose lips', [.48, .16, .17], .5)
MOUTH = material('Mouth interior', [.055, .008, .017])
WHITE = material('Eyes ivory', [.93, .91, .87], .28)
IRIS = material('Warm brown iris', [.08, .033, .017], .3)
PUPIL = material('Pupil and eyelashes', [.007, .004, .009], .35)
SHIRT = material('White linen', [.88, .9, .87], .9)
SEAM = material('Linen seams', [.68, .71, .68], .9)
BUTTON = material('Coconut button', [.20, .13, .07])


def accessor(array, kind, index=False):
    a = np.asarray(array, dtype='<u2' if index else '<f4')
    while len(binary) % 4: binary.append(0)
    offset = len(binary)
    binary.extend(a.tobytes())
    doc['bufferViews'].append(dict(buffer=0, byteOffset=offset, byteLength=a.nbytes,
                                   target=34963 if index else 34962))
    entry = dict(bufferView=len(doc['bufferViews'])-1, componentType=5123 if index else 5126,
                 count=len(a), type=kind)
    if kind == 'VEC3': entry.update(min=a.min(axis=0).tolist(), max=a.max(axis=0).tolist())
    doc['accessors'].append(entry)
    return len(doc['accessors'])-1


def normals(p, triangles):
    p = np.asarray(p)
    t = np.asarray(triangles).reshape(-1, 3)
    n = np.zeros_like(p)
    face = np.cross(p[t[:, 1]]-p[t[:, 0]], p[t[:, 2]]-p[t[:, 0]])
    for i in range(3): np.add.at(n, t[:, i], face)
    length = np.linalg.norm(n, axis=1)
    n[length < 1e-12] = [0, 1, 0]
    length[length < 1e-12] = 1
    return n / length[:, None]


def mesh(name, p, triangles, mat, target=None, target_name=None):
    p = np.asarray(p, dtype=float)
    n = normals(p, triangles)
    primitive = dict(attributes=dict(POSITION=accessor(p, 'VEC3'), NORMAL=accessor(n, 'VEC3')),
                     indices=accessor(np.asarray(triangles).flatten(), 'SCALAR', True), material=mat)
    m = dict(name=name, primitives=[primitive])
    if target is not None:
        target = np.asarray(target)
        primitive['targets'] = [dict(POSITION=accessor(target-p, 'VEC3'),
                                    NORMAL=accessor(normals(target, triangles)-n, 'VEC3'))]
        m.update(weights=[0], extras=dict(targetNames=[target_name]))
    doc['meshes'].append(m)
    doc['nodes'][0]['children'].append(len(doc['nodes']))
    doc['nodes'].append(dict(name=name, mesh=len(doc['meshes'])-1))


def grid(name, fn, mat, rows=32, cols=48, target=None, target_name=None):
    p, q, tri = [], [], []
    for r in range(rows+1):
        for c in range(cols+1):
            p.append(fn(r/rows, c/cols))
            if target: q.append(target(r/rows, c/cols))
    for r in range(rows):
        for c in range(cols):
            a = r*(cols+1)+c; b = a+cols+1
            tri.extend([[a, a+1, b], [a+1, b+1, b]])
    mesh(name, p, tri, mat, q if target else None, target_name)


def ellipsoid(name, center, radius, mat, target_radius=None, target_center=None, target_name=None):
    def surface(r, c, rad=radius, ctr=center):
        t, f = math.pi*r, math.tau*c
        unit = np.array([math.sin(t)*math.cos(f), math.cos(t), math.sin(t)*math.sin(f)])
        return np.array(ctr)+unit*rad
    target = None
    if target_radius is not None:
        target = lambda r, c: surface(r, c, target_radius, target_center if target_center is not None else center)
    grid(name, surface, mat, 24, 40, target, target_name)


def tube(name, path, radius, mat, target_path=None, target_radius=None, target_name=None, steps=40):
    def surface(r, c, fun=path, rad=radius):
        center = np.array(fun(r))
        tangent = np.array(fun(min(1, r+.001)))-np.array(fun(max(0, r-.001)))
        tangent /= max(1e-10, np.linalg.norm(tangent))
        side = np.cross(tangent, [0, 0, 1.])
        if np.linalg.norm(side) < .01: side = np.cross(tangent, [0, 1., 0])
        side /= np.linalg.norm(side)
        other = np.cross(tangent, side)
        width = rad(r) if callable(rad) else rad
        return center+width*(math.cos(math.tau*c)*side+math.sin(math.tau*c)*other)
    target = None
    if target_path:
        target = lambda r, c: surface(r, c, target_path, target_radius if target_radius is not None else radius)
    grid(name, surface, mat, steps, 10, target, target_name)


# Smooth shaped face: narrower jaw, integrated nose bridge and cheek volume.
def head(r, c):
    t, f = math.pi*r, math.tau*c
    vertical = math.cos(t)
    taper = 1 - .24 * max(0, -vertical)
    x = .425*math.sin(t)*math.cos(f)*taper
    y = .73 + .59*vertical
    z = .355*math.sin(t)*math.sin(f)
    front = max(0, math.sin(f))**12
    nose = .09*math.exp(-(x/.065)**2-((y-.64)/.085)**2)
    bridge = .035*math.exp(-(x/.048)**2-((y-.76)/.19)**2)
    cheeks = .025*math.exp(-((abs(x)-.22)/.13)**2-((y-.57)/.12)**2)
    z += front*(nose+bridge+cheeks)
    return [x, y, z]


grid('SculptedFace', head, SKIN, 64, 96)
ellipsoid('Neck', [0, -.005, -.025], [.145, .34, .16], SKIN)
ellipsoid('HairBack', [0, .39, -.205], [.465, .90, .25], HAIR)

# Cap covers the back and crown; the front boundary traces a parted hairline.
def cap(r, c):
    phi = math.tau*c
    front = max(0, math.sin(phi))
    end = 1.95 - 1.0*front**3 + .14*front**20
    theta = r*end
    return [.457*math.sin(theta)*math.cos(phi), .74+.635*math.cos(theta),
            -.025+.40*math.sin(theta)*math.sin(phi)]
grid('PartedHairCap', cap, HAIR, 36, 80)

for side in [-1, 1]:
    ellipsoid(f'Ear{side}', [side*.405, .66, -.015], [.068, .122, .055], SKIN)
    # Layered sculpted locks fall over the shoulders, with slender tonal ridges.
    for strand in range(7):
        angle = .12+strand*.19
        def lock(t, side=side, strand=strand, angle=angle):
            x = side*(.07 + .36*math.sin(min(1, t*2.7)*math.pi/2)
                      + .032*math.sin(t*7+strand*.5) + strand*.013)
            y = 1.30 - t*(1.68+strand*.024)
            z = .14+ .17*math.sin(t*math.pi) - strand*.058
            return [x, y, z]
        tube(f'HairLock{side}_{strand}', lock,
             lambda t: .035*(.55+math.sin(math.pi*t)**.5)*(1-.67*t**8), HAIR, steps=55)
        tube(f'HairRidge{side}_{strand}', lambda t, lock=lock: np.array(lock(t))+[0, 0, .026],
             lambda t: .0035*math.sin(math.pi*t)**.5+.001, STRAND, steps=55)

    # Almond eye surface, eyelashes and iris all collapse to the eyelid seam.
    cx, cy = side*.171, .795
    def eye(r, c, cx=cx, blink=False):
        x = (c*2-1)*.111
        arc = max(0, 1-(x/.111)**2)**.65
        y = cy + ((1-r)*.047-r*.038)*arc*(.035 if blink else 1)
        z = .330 + .025*arc + .006*math.sin(math.pi*r)
        return [cx+x, y, z]
    grid(f'EyeWhite{side}', eye, WHITE, 16, 40,
         lambda r,c,eye=eye: eye(r,c,blink=True), 'blink')
    for name, rad, z, mat in [('Iris',[.037,.040,.009],.365,IRIS),
                              ('Pupil',[.018,.025,.005],.374,PUPIL),
                              ('Catchlight',[.009,.009,.004],.380,WHITE)]:
        ctr=[cx-(.011 if name=='Catchlight' else 0), cy+(.014 if name=='Catchlight' else 0),z]
        end=ctr.copy(); end[1]=cy
        ellipsoid(f'{name}{side}',ctr,rad,mat,[rad[0],.0015,rad[2]],end,'blink')
    def lash(t,cx=cx):
        x=(t*2-1)*.112
        return [cx+x,cy+.048*max(0,1-(x/.112)**2)**.65,.357]
    tube(f'UpperLash{side}',lash,lambda t:.004+.003*math.sin(math.pi*t),PUPIL,
         lambda t,lash=lash:[lash(t)[0],cy,.357], target_name='blink')
    tube(f'Brow{side}',lambda t,cx=cx,side=side:[cx+(t*2-1)*.106,
         .932+.022*math.sin(math.pi*t)+side*(t-.5)*-.017,.323],
         lambda t:.006+.010*math.sin(math.pi*t)**.6,HAIR)

# Dimensional mouth cavity and separate lip meshes, sharing mouthOpen morph.
ellipsoid('MouthCavity',[0,.448,.314],[.092,.004,.015],MOUTH,[.098,.047,.020],[0,.423,.314],'mouthOpen')
def upper(t):
    x=(2*t-1)*.101
    y=.45+.012*math.sin(math.pi*t)+.005*math.sin(3*math.pi*t)
    return [x,y,.327+.014*math.sin(math.pi*t)]
def lower(t):
    return [(2*t-1)*.101,.448-.018*math.sin(math.pi*t),.327+.014*math.sin(math.pi*t)]
tube('UpperLip',upper,lambda t:.003+.008*math.sin(math.pi*t),LIPS,
     lambda t:np.array(upper(t))+[0,.005*math.sin(math.pi*t),.004*math.sin(math.pi*t)],target_name='mouthOpen')
tube('LowerLip',lower,lambda t:.003+.012*math.sin(math.pi*t),LIPS,
     lambda t:np.array(lower(t))+[0,-.059*math.sin(math.pi*t),.008*math.sin(math.pi*t)],target_name='mouthOpen')

# Tailored white sleeveless blouse, neckline and dimensional folded collar.
def torso(r,c):
    y=-.22-r*.92
    rx=.51+.15*math.sin(math.pi*min(1,r*2))-.04*r
    phi=math.tau*c
    return [rx*math.cos(phi),y,.25*math.sin(phi)-.015]
grid('LinenBlouse',torso,SHIRT,28,64)
mesh('Neckline', [[-.15,-.08,.18],[.15,-.08,.18],
                 [.23,-.24,.245],[.07,-.47,.255],[-.07,-.47,.255],[-.23,-.24,.245]],
     [[0,1,2],[0,2,3],[0,3,4],[0,4,5]], SKIN)
for side in [-1,1]:
    p=np.array([[side*.13,-.09,.245],[side*.40,-.25,.252],
                [side*.23,-.53,.30],[side*.04,-.39,.289],
                [side*.22,-.26,.33]])
    mesh(f'FoldedCollar{side}',p,[[0,1,4],[1,2,4],[2,3,4],[3,0,4]],SHIRT)
    tube(f'CollarStitch{side}',lambda t,side=side:[side*(.4-.17*t),-.25-.28*t,.263+.047*t],.003,SEAM)
    ellipsoid(f'UpperArm{side}',[side*.56,-.77,-.005],[.145,.40,.185],SKIN)
tube('Placket',lambda t:[0,-.47-.63*t,.248],.013,SHIRT)
for y in [-.67,-.94]:
    ellipsoid(f'Button{y}',[0,y,.270],[.023,.023,.008],BUTTON)
    for x in [-.006,.006]:
        ellipsoid(f'ButtonHole{y}{x}',[x,y,.278],[.003,.005,.002],PUPIL)

doc['buffers']=[dict(byteLength=len(binary))]
meta=json.dumps(doc,separators=(',',':')).encode()
meta+=b' '*(-len(meta)%4)
binary.extend(b'\0'*(-len(binary)%4))
data=struct.pack('<III',0x46546c67,2,28+len(meta)+len(binary))
data+=struct.pack('<II',len(meta),0x4e4f534a)+meta
data+=struct.pack('<II',len(binary),0x004e4942)+binary
OUTPUT.parent.mkdir(parents=True,exist_ok=True)
OUTPUT.write_bytes(data)
print(f'{OUTPUT}: {len(data):,} bytes; {len(doc["meshes"])} meshes')
