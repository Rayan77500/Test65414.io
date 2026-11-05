precision mediump float;
varying vec2 vQ;
uniform vec4 uColor;
uniform float uInner;
void main() {
    float d = length(vQ);
    float a = 1.0 - smoothstep(uInner, 1.0, d);
    gl_FragColor = vec4(uColor.rgb, uColor.a * a);
}
