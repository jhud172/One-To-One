"""Build the editable One To One sculpture, web GLB and rendered fallback.

Run with Blender 4.5+: blender --background --python tools/brand/build-logo.py
Version 2.0 artwork is modelled here from the site's existing two-person logo.
"""
import bpy
import bmesh
import math
from pathlib import Path
from mathutils import Vector

APP = Path(__file__).resolve().parents[2]
OUT = APP / 'src/main/resources/static/models/one-to-one'
SOURCE = APP / 'design/brand'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

def material(name, colour, metal=0.0, roughness=0.3, emission=0):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*colour, 1)
    m.use_nodes = True
    p = m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value = (*colour, 1)
    p.inputs['Metallic'].default_value = metal
    p.inputs['Roughness'].default_value = roughness
    p.inputs['Coat Weight'].default_value = 0.55
    p.inputs['Emission Color'].default_value = (*colour, 1)
    p.inputs['Emission Strength'].default_value = emission
    return m

cyan = material('Enamel_Cyan', (.025, .64, .92), .28, .38)
orange = material('Enamel_Orange', (1, .31, .045), .3, .36)
metal = material('Titanium', (.035, .065, .095), .72, .3)
light_cyan = material('Light_Cyan', (.02, .8, 1), .25, .28, 1.2)
light_orange = material('Light_Orange', (1, .4, .025), .25, .28, 1.2)
silver = material('Letter_Silver', (.63, .8, .9), .45, .32)

def empty(name):
    o = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(o)
    return o

left, right = empty('Wing_Left'), empty('Wing_Right')
outline = [(-2.1, 1.05), (-.9, 1.82), (-.9, .99), (-.06, .99),
           (-.06, .65), (-1.16, .65), (-1.33, .48), (-1.33, -.75),
           (-.06, -.35), (-.06, -.76), (-.85, -1.03), (-.85, -1.61),
           (-2.1, -2.05), (-2.1, .35), (-2.52, .21), (-2.52, .75)]

def extrude(name, coords, y, depth, mat, parent, bevel=.06):
    n = len(coords)
    vertices = [(x, y, z + .55) for x, z in coords] + [(x, y + depth, z + .55) for x,z in coords]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    faces += [(i, (i+1)%n, (i+1)%n+n, i+n) for i in range(n)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    bm.to_mesh(mesh)
    bm.free()
    ob = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(ob)
    ob.parent = parent
    ob.data.materials.append(mat)
    mod = ob.modifiers.new('Machined bevel', 'BEVEL'); mod.width = bevel; mod.segments = 4
    ob.modifiers.new('Weighted normals', 'WEIGHTED_NORMAL')
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    for mod in list(ob.modifiers): bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.select_set(False)
    return ob

for sign, parent, enamel, glow in [(1,left,cyan,light_cyan),(-1,right,orange,light_orange)]:
    coords = [(x*sign,z) for x,z in outline]
    side = 'Left' if sign == 1 else 'Right'
    extrude('Shell_'+side, coords, -.02, .5, metal, parent, .095)
    extrude('Circuit_'+side, coords, -.11, .075, glow, parent, .055)
    extrude('Face_'+side, coords, -.32, .2, enamel, parent, .075)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=.38, location=(-.57*sign,-.18,.75))
    head=bpy.context.object; head.name='Core_'+side; head.scale=(1,.64,1); head.parent=parent
    head.data.materials.append(enamel)
    bpy.ops.object.shade_smooth()
    # Precision collar gives each person a legible silhouette and reveals depth
    # when the hinged shell opens. Every piece has volume and shares its wing.
    bpy.ops.mesh.primitive_torus_add(major_segments=48, minor_segments=8,
        major_radius=.46, minor_radius=.035,
        location=(-.57*sign,-.09,.75), rotation=(math.pi/2,0,0))
    collar=bpy.context.object; collar.name='Circuit_Core_'+side; collar.parent=parent
    collar.data.materials.append(glow); bpy.ops.object.shade_smooth()
    # Rear structural ribs are exposed in the opening and exploded views.
    for rib_index in range(3):
        x=-1.7*sign
        z=-.45+rib_index*.42
        rib_coords=[(x-.19,z-.045),(x+.19,z-.045),(x+.19,z+.045),(x-.19,z+.045)]
        extrude('Shell_Rib_'+side+'_'+str(rib_index), rib_coords, .48, .09, metal, parent, .025)
    for index in range(3):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=12,ring_count=6,radius=.032,location=(-1.72*sign,-.3,-.48+index*.18))
        bolt=bpy.context.object; bolt.name='Contact_'+side+'_'+str(index); bolt.parent=parent; bolt.data.materials.append(glow)

bpy.ops.object.text_add(location=(0,-.12,-2.19), rotation=(math.pi/2,0,0))
word=bpy.context.object; word.name='Wordmark'; word.data.body='ONE TO ONE'; word.data.align_x='CENTER'
word.data.size=.5; word.data.extrude=.055; word.data.bevel_depth=.012; word.data.bevel_resolution=3
font=Path('C:/Windows/Fonts/arialbd.ttf')
if font.exists(): word.data.font=bpy.data.fonts.load(str(font))
word.data.materials.append(silver)
bpy.context.view_layer.objects.active=word
bpy.ops.object.convert(target='MESH')

# The GLB contains real, independently transformable meshes, not image planes.
bpy.ops.object.select_all(action='SELECT')
bpy.ops.export_scene.gltf(filepath=str(OUT/'sculpture.glb'), export_format='GLB', use_selection=True,
                          export_apply=True, export_yup=True, export_extras=True)

scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=48
scene.cycles.use_denoising=True
scene.render.resolution_x=1100; scene.render.resolution_y=1100; scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'; scene.render.film_transparent=True
scene.world.color=(.12,.12,.12)

def area(name, position, power, colour, size):
    bpy.ops.object.light_add(type='AREA', location=position)
    light=bpy.context.object; light.name=name; light.data.energy=power; light.data.color=colour; light.data.shape='DISK'; light.data.size=size
    light.rotation_euler=(Vector((0,0,0))-light.location).to_track_quat('-Z','Y').to_euler()

area('Softbox', (1,-7,7), 1300,(.8,.93,1),7)
area('Cyan rim',(-5,1,3),1000,(.05,.6,1),5)
area('Amber rim',(5,0,1),1500,(1,.25,.045),4)
area('Front fill',(0,-6,-2),600,(.5,.8,1),5)
bpy.ops.object.camera_add(location=(3,-12,3.2))
camera=bpy.context.object
camera.rotation_euler=(Vector((0,0,0))-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.type='ORTHO'; camera.data.ortho_scale=7.5; scene.camera=camera
scene.view_settings.view_transform='AgX'
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE/'one-to-one-sculpture-v2.blend'))
scene.render.filepath=str(OUT/'sculpture-poster.png')
bpy.ops.render.render(write_still=True)
print('ONE_TO_ONE_BLENDER_EXPORT_PASS', OUT)
