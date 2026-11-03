#version 120

#include "lib/core/vertex.h.glsl"

varying vec4  position;
varying float linearDepth;
varying vec4 screenPosition; // OPENMW_ANDROID_051_WATER_PROJECTIVE_SCREEN_COORDS

#include "shadows_vertex.glsl"
#include "lib/view/depth.glsl"

uniform vec3 nodePosition;
uniform vec3 playerPos;

varying vec3 worldPos;
varying vec2 rippleMapUV;

void main(void)
{
    gl_Position = modelToClip(gl_Vertex);
    screenPosition = gl_Position;

    position = gl_Vertex;

    worldPos = position.xyz + nodePosition.xyz;
    rippleMapUV = (worldPos.xy - playerPos.xy + (@rippleMapSize * @rippleMapWorldScale / 2.0)) / @rippleMapSize / @rippleMapWorldScale;

    vec4 viewPos = modelToView(gl_Vertex);
    linearDepth = getLinearDepth(gl_Position.z, viewPos.z);

    setupShadowCoords(viewPos, normalize((gl_NormalMatrix * gl_Normal).xyz));
}
