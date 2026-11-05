// Voiture : pixel. Eclairage "studio" leger : lumiere principale,
// ciel/sol, reflets d'un environnement calcule (pas de texture), vernis.
precision mediump float;
varying vec3 vNor;
varying vec3 vPos;
varying vec2 vUv;
uniform vec3 uCam;
uniform vec4 uColor;
uniform vec3 uEmissive;
uniform float uMetal;
uniform float uRough;
uniform float uClear;
uniform float uUseTex;
uniform float uDarkBack;
uniform float uCutoff;
uniform sampler2D uTex;

vec3 env(vec3 r) {
    float y = r.y;
    vec3 top = vec3(0.64, 0.68, 0.77);
    vec3 hor = vec3(0.30, 0.33, 0.40);
    vec3 gnd = vec3(0.035, 0.04, 0.05);
    vec3 c = mix(hor, top, smoothstep(0.0, 0.9, y));
    c = mix(c, mix(hor * 0.45, gnd, smoothstep(0.0, 0.3, -y)), step(y, 0.0));
    // grandes boites a lumiere au plafond
    c += vec3(0.62) * smoothstep(0.52, 0.60, y) * (1.0 - smoothstep(0.72, 0.80, y));
    // ligne d'horizon nette, typique des photos de voitures
    c += vec3(0.45) * (1.0 - smoothstep(0.0, 0.05, abs(y - 0.04)));
    return c;
}

void main() {
    vec4 base = uColor;
    if (uUseTex > 0.5) base *= texture2D(uTex, vUv);
#ifdef MASK
    if (base.a < uCutoff) discard;
#endif
    vec3 N = normalize(vNor);
    vec3 V = normalize(uCam - vPos);
    if (dot(N, V) < 0.0) N = -N;
    vec3 L = normalize(vec3(0.45, 0.85, 0.35));
    float ndl = max(dot(N, L), 0.0);
    float hemi = 0.5 + 0.5 * N.y;
    vec3 amb = mix(vec3(0.05, 0.055, 0.065), vec3(0.34, 0.36, 0.41), hemi);
    vec3 diff = base.rgb * (amb + vec3(0.95, 0.93, 0.90) * ndl) * (1.0 - 0.8 * uMetal);

    float ndv = max(dot(N, V), 0.0);
    float fres = pow(1.0 - ndv, 5.0);
    vec3 F0 = mix(vec3(0.04), base.rgb, uMetal);
    vec3 F = F0 + (vec3(1.0) - F0) * fres * (1.0 - uRough);
    vec3 e = env(reflect(-V, N));
    vec3 eRough = mix(e, vec3(0.30, 0.32, 0.37), uRough * 0.85);
    vec3 spec = eRough * F;
    // vernis transparent par-dessus la peinture metallisee
    spec += e * (0.03 + 0.5 * fres) * uClear;

    vec3 H = normalize(L + V);
    float sp = pow(max(dot(N, H), 0.0), mix(160.0, 10.0, uRough)) * (1.0 - uRough);
    vec3 col = diff + spec + vec3(sp * 1.2) + uEmissive;
    // tonemapping doux pour eviter les blancs brules
    col = col / (vec3(1.0) + col * 0.28) * 1.18;
    // interieur des panneaux (faces arriere) assombri
    if (uDarkBack > 0.5 && !gl_FrontFacing) col *= 0.2;
    gl_FragColor = vec4(col, base.a);
}
