// Sol : ombre douce et halo sous la voiture
attribute vec2 aXZ;
uniform mat4 uViewProj;
uniform vec2 uHalf;
uniform vec2 uCenter;
varying vec2 vQ;
void main() {
    vQ = aXZ;
    vec2 p = uCenter + aXZ * uHalf;
    gl_Position = uViewProj * vec4(p.x, 0.003, p.y, 1.0);
}
