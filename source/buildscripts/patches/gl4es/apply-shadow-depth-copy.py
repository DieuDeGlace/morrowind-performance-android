#!/usr/bin/env python3
"""Expose accurate depth-copy support from the pinned GL4ES backend."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply-shadow-depth-copy.py <gl4es-source-dir>')

path = Path(sys.argv[1]) / 'src/glx/hardext.c'
text = path.read_text(encoding='utf-8')
marker = 'OPENMW_ANDROID_SHADOW_DEPTH_COPY'
if marker not in text:
    anchor = 'static int tested = 0;'
    assert text.count(anchor) == 1, 'GL4ES hardware initialization anchor changed'
    text = text.replace(anchor, '''static int tested = 0;

// OPENMW_ANDROID_SHADOW_DEPTH_COPY
// Cached during hardware detection; querying it needs no current GL context.
static int shadow_depth_copy = 0;

EXPORT int gl4es_getDepthCopySupport(void) {
    return shadow_depth_copy;
}''')
    anchor = '        S("GL_EXT_frag_depth ", fragdepth, 1);'
    assert text.count(anchor) == 1, 'GL4ES fragment-depth anchor changed'
    text = text.replace(anchor, anchor + '''
        // Depth copies must preserve the stored value even when ordinary shaders
        // use the user's medium-precision preference. Query hardware directly.
        if(hardext.fragdepth && hardext.depthtex) {
            LOAD_GLES2(glGetShaderPrecisionFormat);
            if(gles_glGetShaderPrecisionFormat) {
                GLint range[2] = {0};
                GLint precision = 0;
                gles_glGetShaderPrecisionFormat(GL_FRAGMENT_SHADER, GL_HIGH_FLOAT, range, &precision);
                shadow_depth_copy = precision >= 23;
            }
        }''')
    path.write_bytes(text.encode('utf-8'))
    print('Applied GL4ES accurate shadow depth-copy capability query.')
else:
    print('GL4ES shadow depth-copy capability query is already applied.')

assert text.count('int gl4es_getDepthCopySupport(void)') == 1
assert 'shadow_depth_copy = precision >= 23;' in text
