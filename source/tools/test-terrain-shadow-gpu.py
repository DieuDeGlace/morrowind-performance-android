"""Render the actual Android shadow receiver; synthetic planar depth fixtures."""
from pathlib import Path
import re
import sys
import unittest
import moderngl
import numpy as np

ROOT=Path(__file__).resolve().parent
OLD=Path(sys.argv[1]).read_text()
NEW=Path(sys.argv[2]).read_text()
sys.argv=sys.argv[:1]
CTX=moderngl.create_standalone_context(require=330)
SINK=CTX.simple_framebuffer((1,1))
print(CTX.info['GL_RENDERER'])

VERT='''#version 120
attribute vec2 position;
varying vec4 shadowSpaceCoords0;
varying vec2 shadowReceiverPlaneSlope0;
uniform vec2 slope;
uniform vec2 stable;
void main() {
    gl_Position=vec4(position,0.0,1.0);
    vec2 uv=vec2(.5)+position*.025;
    shadowSpaceCoords0=vec4(uv,.5+dot(slope,uv-vec2(.5)),1.0);
    shadowReceiverPlaneSlope0=stable;
}
'''

def expand(source,level,res,terrain,coverage,es):
    source=re.sub(r'@foreach shadow_texture_unit_index @shadow_texture_unit_list\s*(.*?)@endforeach',lambda m:m[1].replace('@shadow_texture_unit_index','0'),source,flags=re.S)
    for key,value in {'shadows_enabled':1,'perspectiveShadowMaps':0,'limitShadowMapDistance':0,'useShadowDebugOverlay':0}.items():
        source=source.replace('@'+key,str(value))
    source=re.sub(r'(#define OPENMW_ANDROID_SHADOW_PCF_LEVEL) \d+',rf'\g<1> {level}',source)
    source=re.sub(r'(#define OPENMW_ANDROID_SHADOW_MAP_RESOLUTION) [\d.]+',rf'\g<1> {res}.0',source)
    source=re.sub(r'(#define OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE) [\d.]+',rf'\g<1> {coverage}',source)
    assert '@' not in source
    header='#version 120\n'
    if es: header='#version 100\n#extension GL_OES_standard_derivatives : enable\nprecision highp float;\nprecision highp int;\n'
    if terrain: header+='#define OPENMW_ANDROID_TERRAIN_SHADOW_RECEIVER\n'
    return header+source+'\nvoid main(){gl_FragColor=vec4(vec3(unshadowedLightRatio(100.0)),1.0);}\n'

def render(source,level=2,res=1024,terrain=True,coverage='.55',blocker=False,error=True,es=False):
    size=128
    uv=(np.arange(res,dtype='f4')+.5)/res
    depth=.5+4*(uv[None,:]-.5)+2*(uv[:,None]-.5)
    if error: depth-=.00035*(.5+.5*np.sin((uv[None,:]-.475)*1000))
    if blocker: depth[:,uv>.5]-=.02
    depth=np.clip(depth,0,1).astype('f4')
    texture=CTX.texture((res,res),1,depth.tobytes(),dtype='f4')
    texture.filter=(moderngl.NEAREST,moderngl.NEAREST)
    texture.repeat_x=texture.repeat_y=False
    output=CTX.texture((size,size),4,dtype='f4')
    fbo=CTX.framebuffer([output])
    vertex=VERT.replace('#version 120','#version 100\nprecision highp float;') if es else VERT
    program=CTX.program(vertex_shader=vertex,fragment_shader=expand(source,level,res,terrain,coverage,es))
    vbo=CTX.buffer(np.array([-1,-1,3,-1,-1,3],dtype='f4').tobytes())
    vao=CTX.vertex_array(program,[(vbo,'2f','position')])
    program['slope'].value=(4,2)
    program['stable'].value=(.2,.1)
    program['shadowTexture0'].value=0
    texture.use(0)
    fbo.use(); CTX.viewport=(0,0,size,size)
    vao.render(moderngl.TRIANGLES)
    data=np.frombuffer(fbo.read(components=1,dtype='f4'),dtype='f4').reshape(size,size).copy()
    assert CTX.error=='GL_NO_ERROR'
    SINK.use()
    for obj in (vao,vbo,program,fbo,output,texture):obj.release()
    return data

class ShadowTests(unittest.TestCase):
    def test_terrain_self_shadow_bands_are_rejected(self):
        for level in range(1,5):
            with self.subTest(level=level):
                old=render(OLD,level)
                new=render(NEW,level)
                self.assertLess(float(old.mean()),.98)
                self.assertGreater(float(old.std()),.01)
                self.assertGreater(float(new.mean()),.999)

    def test_correction_works_at_default_4096_map_resolution(self):
        old=render(OLD,res=4096)
        new=render(NEW,res=4096)
        self.assertLess(float(old.mean()),.98)
        self.assertGreater(float(new.mean()),.999)

    def test_real_cast_shadow_is_retained(self):
        for level in range(1,5):
            data=render(NEW,level,blocker=True)
            self.assertGreater(float(data[:,:40].mean()),.999)
            self.assertLess(float(data[:,88:].mean()),.001)

    def test_actor_and_object_receivers_are_unchanged(self):
        for level in range(5):
            np.testing.assert_array_equal(render(OLD,level,terrain=False,blocker=True),render(NEW,level,terrain=False,blocker=True))

    def test_legacy_option_is_exact_old_output(self):
        for level in range(5):
            np.testing.assert_array_equal(render(OLD,level,blocker=True),render(NEW,level,coverage='0.0',blocker=True))

    def test_es100_all_quality_levels_compile_and_render(self):
        for level in range(5):
            data=render(NEW,level,es=True,blocker=True)
            self.assertTrue(np.isfinite(data).all())

if __name__=='__main__':unittest.main(verbosity=2)
