// Voiture : sommet (GLSL ES 1.00 / OpenGL ES 2.0)
attribute vec3 aPos;
attribute vec3 aNor;
attribute vec2 aUv;
uniform mat4 uViewProj;
uniform mat4 uModel;
varying vec3 vNor;
varying vec3 vPos;
varying vec2 vUv;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vPos = wp.xyz;
    vNor = (uModel * vec4(aNor, 0.0)).xyz;
    vUv = aUv;
    gl_Position = uViewProj * wp;
}
