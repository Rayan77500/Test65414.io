#!/usr/bin/env python3
"""
Generateur du modele 3D PROVISOIRE de la Mercedes Classe C berline W204.

Le resultat est un fichier glTF binaire (.glb) avec des pieces nommees :
  body, door_fl, door_fr, door_rl, door_rr, hood, trunk,
  wheel_fl, wheel_fr, wheel_rl, wheel_rr, interior, engine

Conventions (identiques a la norme glTF) :
  - unite : metre, Y vers le haut, l'avant de la voiture regarde +Z
  - +X = cote GAUCHE de la voiture (cote conducteur en France)

L'application calcule automatiquement les charnieres a partir des boites
englobantes des pieces : il suffit qu'un autre modele .glb nomme ses pieces
de la meme facon (door_fl, hood, trunk...) pour que les ouvrants s'animent.

Usage :  python3 tools/generate_w204_glb.py [sortie.glb]
"""
import json
import math
import struct
import sys

import numpy as np

# ---------------------------------------------------------------- dimensions
HL = 4.581 / 2.0          # demi-longueur
HW0 = 1.770 / 2.0         # demi-largeur
ZF, ZR = 1.47, -1.29      # essieux avant / arriere (empattement 2,76 m)
TRACK = 1.541 / 2.0
TIRE_R = 0.316            # 205/55 R16
ARCH_R, ARCH_C = 0.37, 0.32

WS_BASE, WS_TOP = 0.52, -0.18     # pare-brise (base / haut)
RW_TOP, RW_BASE = -1.03, -1.655   # lunette arriere
HOOD_Z = (0.555, 2.19)
TRUNK_Z0 = -1.66
FD_Z = (-0.565, 0.475)            # porte avant
RD_Z = (-1.355, -0.565)           # porte arriere
GAP = 0.003
SIDE_GLASS = (-1.30, 0.455)
BPILLAR = (-0.60, -0.53)


def clamp(v, a=0.0, b=1.0):
    return a if v < a else (b if v > b else v)


def smoothstep(a, b, x):
    t = clamp((x - a) / (b - a))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


# ---------------------------------------------------------- profils / sections
def hw(z):
    w = HW0
    if z > 1.80:
        t = (z - 1.80) / (HL - 1.80)
        w -= 0.085 * t ** 2 + 0.03 * t ** 6
    if z < -1.90:
        t = (-1.90 - z) / (HL - 1.90)
        w -= 0.06 * t ** 2 + 0.02 * t ** 6
    return w


def floor_y(z):
    y = 0.16
    if z > 1.75:
        y += 0.08 * ((z - 1.75) / (HL - 1.75)) ** 1.5
    if z < -1.60:
        y += 0.13 * ((-1.60 - z) / (HL - 1.60)) ** 1.5
    return y


def belt_y(z):
    if z >= HOOD_Z[0]:
        if z <= HOOD_Z[1]:
            t = (z - HOOD_Z[0]) / (HOOD_Z[1] - HOOD_Z[0])
            return 0.975 - 0.13 * t ** 1.5
        t = (z - HOOD_Z[1]) / (HL - HOOD_Z[1])
        return 0.845 - 0.085 * t ** 2
    if z >= -1.62:
        t = (HOOD_Z[0] - z) / (HOOD_Z[0] + 1.62)
        return 0.975 + 0.06 * t
    if z >= -2.14:
        t = (-1.62 - z) / (2.14 - 1.62)
        return 1.035 - 0.01 * t
    t = (-2.14 - z) / (HL - 2.14)
    return 1.025 - 0.12 * t ** 2


CROWN = 0.04


def hroof(z):
    return 0.40 - 0.035 * ((z + 0.50) / 0.6) ** 2


def hcab(z):
    if z >= WS_BASE:
        return 0.0
    if z > WS_TOP:
        t = (WS_BASE - z) / (WS_BASE - WS_TOP)
        return hroof(WS_TOP) * (1.25 * t - 0.25 * t * t)
    if z >= RW_TOP:
        return hroof(z)
    if z > RW_BASE:
        t = (z - RW_BASE) / (RW_TOP - RW_BASE)
        return hroof(RW_TOP) * (1.3 * t - 0.3 * t * t)
    return 0.0


def arch_edge(z):
    for za in (ZF, ZR):
        dz = z - za
        if abs(dz) < ARCH_R:
            return ARCH_C + math.sqrt(ARCH_R ** 2 - dz ** 2)
    return None


def keypoints(z):
    """14 points cles de la demi-section (x >= 0), du bas-centre au toit-centre."""
    w = hw(z)
    yb = floor_y(z)
    ae = arch_edge(z)
    belt = belt_y(z)
    p = [None] * 14
    p[0] = (0.0, yb)
    p[1] = (0.52 * w, yb)
    if ae is not None:
        p[2] = (0.56 * w, ae)
        p[3] = (0.975 * w, ae)
        p4y = ae + 0.012
    else:
        p[2] = (0.56 * w, yb)
        p[3] = (0.975 * w, yb)
        p4y = yb + 0.10
    p7y = min(0.875, belt - 0.085)
    p5y = max(0.40, p4y + 0.02)
    p6y = max(0.70, p5y + 0.02)
    p6y = min(p6y, p7y - 0.015)
    p5y = min(p5y, p6y - 0.015)
    p4y = min(p4y, p5y - 0.01)
    p[4] = (0.995 * w, p4y)
    p[5] = (1.000 * w, p5y)
    p[6] = (1.006 * w, p6y)
    p[7] = (0.990 * w, p7y)
    p[8] = (0.935 * w, belt)
    h = hcab(z)
    s = smoothstep(0.0, 0.10, h)
    hood_x = [0.905 * w, 0.78 * w, 0.62 * w, 0.36 * w, 0.0]
    x9 = 0.905 * w
    x10 = x9 - 0.30 * 0.80 * h
    cab_x = [x9, x10, x10 - 0.065, 0.40, 0.0]
    f = [0.0, 0.80, 0.93, 0.985, 1.0]
    for i in range(5):
        x = lerp(hood_x[i], cab_x[i], s)
        y = belt + CROWN * (1 - (x / w) ** 2) + h * f[i]
        if i == 0:
            y = belt + 0.006
        p[9 + i] = (x, y)
    return p


# Subdivision de chaque segment cle (parametres t). Le premier petit pas de
# certains segments sert de "joint" (ligne sombre entre panneaux).
SEG_T = [
    [0, .35, .7, 1],          # 0  p0-p1 plancher
    [0, 1],                   # 1  p1-p2 paroi passage de roue
    [0, .33, .66, 1],         # 2  p2-p3 plafond passage de roue
    [0, .5, 1],               # 3  p3-p4 bas de caisse (arrondi)
    [0, .5, 1],               # 4  p4-p5 bas de porte / seuil
    [0, .08, .4, .7, 1],      # 5  p5-p6
    [0, .35, .7, 1],          # 6  p6-p7
    [0, .5, 1],               # 7  p7-p8 epaule
    [0, .15, .6, 1],          # 8  p8-p9 haut d'epaule / joint capot
    [0, .2, .4, .6, .8, 1],   # 9  p9-p10 vitre laterale
    [0, .5, 1],               # 10 p10-p11 montant de toit
    [0, .25, .5, .75, 1],     # 11 p11-p12 toit / pare-brise
    [0, .25, .5, .75, 1],     # 12 p12-p13
]
CHAIN_OF_SEG = [0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2]


def catmull(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t +
                        (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 +
                        (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3) for i in range(2))


def section(z):
    """Retourne la liste des points echantillonnes + (segment, sous-index)."""
    kp = keypoints(z)
    pts = [kp[0]]
    meta = []
    for k in range(13):
        a, b = kp[k], kp[k + 1]
        ch = CHAIN_OF_SEG[k]
        for si in range(1, len(SEG_T[k])):
            t = SEG_T[k][si]
            if ch == 0:
                q = (lerp(a[0], b[0], t), lerp(a[1], b[1], t))
            else:
                # voisins pour la spline, en restant dans la meme chaine
                if k >= 1 and CHAIN_OF_SEG[k - 1] == ch:
                    pa = kp[k - 1]
                else:
                    pa = (2 * a[0] - b[0], 2 * a[1] - b[1])
                if k == 12:  # symetrie au centre du toit
                    pb = (-kp[12][0], kp[12][1])
                elif CHAIN_OF_SEG[k + 1] == ch:
                    pb = kp[k + 2]
                else:
                    pb = (2 * b[0] - a[0], 2 * b[1] - a[1])
                q = catmull(pa, a, b, pb, t)
            pts.append(q)
            meta.append((k, si - 1))
    return pts, meta


# ---------------------------------------------------------------- echantillons z
def z_samples():
    zs = set()
    n = int(round(2 * HL / 0.025))
    for i in range(n + 1):
        zs.add(round(-HL + i * 2 * HL / n, 5))
    bounds = [FD_Z[0], FD_Z[1], RD_Z[0], HOOD_Z[0], HOOD_Z[1], TRUNK_Z0]
    for b in bounds:
        zs.add(round(b - GAP, 5))
        zs.add(round(b + GAP, 5))
    for v in (WS_BASE, WS_TOP, RW_TOP, RW_BASE, SIDE_GLASS[0], SIDE_GLASS[1],
              BPILLAR[0], BPILLAR[1], 2.10, 2.12, -2.12, -2.15):
        zs.add(round(v, 5))
    for za in (ZF, ZR):
        for i in range(0, 21):
            th = math.pi * i / 20
            zs.add(round(za + (ARCH_R - 0.0004) * math.cos(th), 5))
        zs.add(round(za + ARCH_R + 0.0004, 5))
        zs.add(round(za - ARCH_R - 0.0004, 5))
    out = []
    for z in sorted(zs):
        if -HL <= z <= HL and (not out or z - out[-1] > 0.0006):
            out.append(z)
    return out


# ------------------------------------------------------------------ mesh store
class Store:
    def __init__(self):
        self.parts = {}   # part -> mat -> (verts dict, list verts, list idx)

    def _bucket(self, part, mat):
        pm = self.parts.setdefault(part, {})
        if mat not in pm:
            pm[mat] = ({}, [], [])
        return pm[mat]

    def tri(self, part, mat, ps, ns):
        """ps, ns : 3 positions / 3 normales (sortantes). Corrige l'ordre CCW."""
        if part not in ('interior',) and not part.startswith('wheel'):
            ns = [warp_normal(p, n) for p, n in zip(ps, ns)]
            ps = [warp(p) for p in ps]
        a, b, c = (np.array(p, dtype=float) for p in ps)
        g = np.cross(b - a, c - a)
        area = np.linalg.norm(g)
        if area < 1e-10:
            return
        nav = np.array(ns[0]) + np.array(ns[1]) + np.array(ns[2])
        order = [0, 1, 2]
        if np.dot(g, nav) < 0:
            order = [0, 2, 1]
        d, verts, idx = self._bucket(part, mat)
        for o in order:
            p = tuple(round(float(v), 5) for v in ps[o])
            n = np.array(ns[o], dtype=float)
            ln = np.linalg.norm(n)
            n = n / ln if ln > 1e-12 else np.array([0, 1.0, 0])
            nk = tuple(round(float(v), 3) for v in n)
            key = p + nk
            if key not in d:
                d[key] = len(verts)
                verts.append((p, tuple(float(v) for v in n)))
            idx.append(d[key])

    def quad(self, part, mat, ps, ns):
        self.tri(part, mat, [ps[0], ps[1], ps[2]], [ns[0], ns[1], ns[2]])
        self.tri(part, mat, [ps[0], ps[2], ps[3]], [ns[0], ns[2], ns[3]])

    def flat_poly(self, part, mat, ps, n):
        for i in range(1, len(ps) - 1):
            self.tri(part, mat, [ps[0], ps[i], ps[i + 1]], [n, n, n])


S = Store()


def warp(p):
    """Deformation douce des extremites : face avant inclinee, arriere arrondi."""
    x, y, z = p
    if z > HL - 0.32:
        g = smoothstep(HL - 0.32, HL, z)
        z = z - g * 0.11 * smoothstep(0.40, 0.86, y)
    elif z < -HL + 0.30:
        g = smoothstep(-HL + 0.30, -HL, z)
        z = z + g * 0.075 * smoothstep(0.72, 0.96, y)
    return np.array([x, y, z])


def warp_normal(p, n):
    eps = 1e-4
    p = np.array(p, dtype=float)
    J = np.zeros((3, 3))
    for i in range(3):
        d = np.zeros(3)
        d[i] = eps
        J[:, i] = (warp(p + d) - warp(p - d)) / (2 * eps)
    m = np.linalg.inv(J).T @ np.array(n, dtype=float)
    ln = np.linalg.norm(m)
    return m / ln if ln > 1e-12 else np.array(n, dtype=float)


# ------------------------------------------------------------ classification
def in_range(z, r):
    return r[0] < z < r[1]


def near(z, b):
    return abs(z - b) <= GAP + 1e-6


def classify(zc, k, sub, yc, xc, side):
    """Retourne (piece, materiau) pour un quad de la carrosserie."""
    L = side  # 'l' ou 'r'
    arch = arch_edge(zc) is not None
    if k <= 2:
        return 'body', ('well' if (arch and k >= 1) else 'under')
    if k == 3:
        return 'body', ('well' if arch else 'paint')
    # capot / coffre (dessus)
    if k >= 8:
        if near(zc, HOOD_Z[0]) or near(zc, HOOD_Z[1]) or near(zc, TRUNK_Z0):
            return 'body', 'gap'
        if in_range(zc, HOOD_Z):
            return 'hood', ('gap' if (k == 8 and sub == 0) else 'paint')
        if zc < TRUNK_Z0:
            if k == 8 and sub == 0:
                return 'body', 'gap'
            return 'trunk', 'paint'
        if zc > HOOD_Z[1]:
            if abs(xc) > 0.36 and 0.64 < yc < 0.86:
                return 'body', 'headlight'
            return 'body', 'paint'
    # cotes
    if 4 <= k <= 9:
        door = None
        if in_range(zc, (FD_Z[0] + GAP, FD_Z[1] - GAP)):
            door = 'door_f' + L
        elif in_range(zc, (RD_Z[0] + GAP, RD_Z[1] - GAP)):
            door = 'door_r' + L
        if k == 4:
            return 'body', 'paint'
        if door is None:
            if (near(zc, FD_Z[0]) or near(zc, FD_Z[1]) or near(zc, RD_Z[0])):
                return 'body', 'gap'
            if k == 9:
                return 'body', 'paint'
            if zc > 2.14 and 0.64 < yc < 0.86:
                return 'body', 'headlight'
            if zc < -2.12 and 0.76 < yc < 0.96:
                return 'body', 'taillight'
            return 'body', 'paint'
        if k == 5 and sub == 0:
            return door, 'gap'
        if k == 9:
            if in_range(zc, SIDE_GLASS) and not in_range(zc, BPILLAR):
                return door, 'glass'
            if BPILLAR[0] <= zc <= BPILLAR[1]:
                return door, 'trim'
            return door, 'paint'
        if k == 8:
            if sub >= 1 and in_range(zc, SIDE_GLASS):
                return door, 'chrome'
            return door, 'paint'
        return door, 'paint'
    # k >= 10 dans l'habitacle
    if k == 10:
        return 'body', 'paint'
    if WS_BASE - 0.035 < zc < WS_BASE + 0.04:
        return 'body', 'trim'          # grille d'auvent (essuie-glaces)
    if WS_TOP < zc <= WS_BASE - 0.035:
        return 'body', 'glass'
    if RW_BASE < zc < RW_TOP:
        return 'body', 'glass'
    return 'body', 'paint'


# ------------------------------------------------------------- carrosserie
def build_body():
    zs = z_samples()
    secs = [section(z) for z in zs]
    meta = secs[0][1]
    nj = len(secs[0][0])
    for side, sx in (('l', 1.0), ('r', -1.0)):
        V = np.zeros((len(zs), nj, 3))
        for i, z in enumerate(zs):
            for j, (x, y) in enumerate(secs[i][0]):
                V[i, j] = (sx * x, y, z)
        ni, nq = len(zs) - 1, nj - 1
        FN = np.zeros((ni, nq, 3))
        for i in range(ni):
            for j in range(nq):
                d1 = V[i + 1, j + 1] - V[i, j]
                d2 = V[i, j + 1] - V[i + 1, j]
                FN[i, j] = np.cross(d1, d2)
        # orientation : les quads de flanc (segment 6) doivent pointer vers l'exterieur
        side_js = [j for j in range(nq) if meta[j][0] == 6]
        sgn = np.sign(np.sum(FN[:, side_js, 0])) * sx
        FN *= sgn
        # groupes de lissage : chaine + "paroi" (normale surtout selon z)
        grp = np.zeros((ni, nq), dtype=int)
        for i in range(ni):
            for j in range(nq):
                n = FN[i, j]
                ln = np.linalg.norm(n)
                wall = ln > 1e-12 and abs(n[2]) / ln > 0.75
                grp[i, j] = CHAIN_OF_SEG[meta[j][0]] * 2 + (1 if wall else 0)

        def vnormal(i, j, g):
            acc = np.zeros(3)
            for di in (-1, 0):
                for dj in (-1, 0):
                    a, b = i + di, j + dj
                    if 0 <= a < ni and 0 <= b < nq and grp[a, b] == g:
                        acc += FN[a, b]
            if j == 0 or j == nj - 1:
                acc[0] = 0.0
            ln = np.linalg.norm(acc)
            return acc / ln if ln > 1e-12 else None

        for i in range(ni):
            zc = 0.5 * (zs[i] + zs[i + 1])
            for j in range(nq):
                k, sub = meta[j]
                quad = [V[i, j], V[i + 1, j], V[i + 1, j + 1], V[i, j + 1]]
                cen = sum(quad) / 4.0
                part, mat = classify(zc, k, sub, cen[1], cen[0], side)
                g = grp[i, j]
                fnn = FN[i, j] / (np.linalg.norm(FN[i, j]) + 1e-15)
                ns = []
                for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    n = vnormal(a, b, g)
                    ns.append(n if n is not None else fnn)
                S.quad(part, mat, quad, ns)
    return zs, secs


def clip_slab(poly, x0, x1):
    def clip(poly, keep):
        out = []
        n = len(poly)
        for i in range(n):
            a, b = poly[i], poly[(i + 1) % n]
            ka, kb = keep(a), keep(b)
            if ka:
                out.append(a)
            if ka != kb:
                fa = a[0] - (x0 if keep is k0 else x1)
                fb = b[0] - (x0 if keep is k0 else x1)
                t = fa / (fa - fb)
                out.append((lerp(a[0], b[0], t), lerp(a[1], b[1], t)))
        return out
    k0 = lambda p: p[0] >= x0 - 1e-9
    k1 = lambda p: p[0] <= x1 + 1e-9
    p = clip(poly, k0)
    if len(p) >= 3:
        p = clip(p, k1)
    return p if len(p) >= 3 else []


def build_cap(z, front):
    pts, meta = section(z)
    j3 = 1 + sum(len(SEG_T[k]) - 1 for k in range(3))   # index de p3
    half = pts[j3:]
    ycuts_f = [0.26, 0.37, 0.40, 0.50, 0.505, 0.52, 0.64, 0.76, 0.775]
    ycuts_r = [0.28, 0.37, 0.70, 0.705, 0.72, 0.735, 0.845]
    ycuts = ycuts_f if front else ycuts_r
    # insere les coupes horizontales dans le demi-contour
    hp = [half[0]]
    for a, b in zip(half, half[1:]):
        for c in ycuts:
            if a[1] < c < b[1]:
                t = (c - a[1]) / (b[1] - a[1])
                hp.append((lerp(a[0], b[0], t), c))
        hp.append(b)
    xc_f = [0.235, 0.26, 0.30, 0.32, 0.36, 0.62]
    xc_r = [0.235, 0.26, 0.47, 0.48, 0.70]
    xc = xc_f if front else xc_r
    cuts = sorted(set([-c for c in xc] + xc))
    slabs = [-9.0] + cuts + [9.0]
    nz = (0.0, 0.0, 1.0 if front else -1.0)
    for a, b in zip(hp, hp[1:]):
        if b[1] - a[1] < 1e-7:
            continue
        poly = [(-a[0], a[1]), (a[0], a[1]), (b[0], b[1]), (-b[0], b[1])]
        for s0, s1 in zip(slabs, slabs[1:]):
            pc = clip_slab(poly, s0, s1)
            if not pc:
                continue
            cx = sum(p[0] for p in pc) / len(pc)
            cy = sum(p[1] for p in pc) / len(pc)
            ax = abs(cx)
            part, mat = 'body', 'paint'
            if front:
                if ax < 0.30 and 0.52 < cy < 0.76:
                    mat = 'grille'
                elif ax < 0.32 and 0.505 < cy < 0.775:
                    mat = 'chrome'
                elif ax > 0.36 and cy > 0.64:
                    mat = 'headlight'
                elif ax < 0.26 and 0.40 < cy < 0.50:
                    mat = 'plate_blue' if cx < -0.235 else 'plate'
                elif ax < 0.62 and 0.26 < cy < 0.37:
                    mat = 'trim'
            else:
                if cy > 0.705 and ax < 0.47:
                    part = 'trunk'
                    if ax < 0.26 and 0.735 < cy < 0.845:
                        mat = 'plate_blue' if cx > 0.235 else 'plate'
                elif 0.70 < cy < 0.705 and ax < 0.47:
                    mat = 'gap'
                elif cy > 0.72 and ax > 0.48:
                    mat = 'taillight'
                elif cy > 0.70 and 0.47 <= ax <= 0.48:
                    mat = 'gap'
                elif cy < 0.37 and ax < 0.70:
                    mat = 'trim'
            S.flat_poly(part, mat, [(p[0], p[1], z) for p in pc], nz)


# ------------------------------------------------------------- primitives 3D
def box(part, mat, c, size, rot=None):
    """Boite centree en c, taille size, rotation optionnelle (matrice 3x3)."""
    hx, hy, hz = size[0] / 2, size[1] / 2, size[2] / 2
    R = np.eye(3) if rot is None else np.array(rot)
    c = np.array(c)
    faces = [((1, 0, 0), [(hx, -hy, -hz), (hx, hy, -hz), (hx, hy, hz), (hx, -hy, hz)]),
             ((-1, 0, 0), [(-hx, -hy, -hz), (-hx, -hy, hz), (-hx, hy, hz), (-hx, hy, -hz)]),
             ((0, 1, 0), [(-hx, hy, -hz), (-hx, hy, hz), (hx, hy, hz), (hx, hy, -hz)]),
             ((0, -1, 0), [(-hx, -hy, -hz), (hx, -hy, -hz), (hx, -hy, hz), (-hx, -hy, hz)]),
             ((0, 0, 1), [(-hx, -hy, hz), (hx, -hy, hz), (hx, hy, hz), (-hx, hy, hz)]),
             ((0, 0, -1), [(-hx, -hy, -hz), (-hx, hy, -hz), (hx, hy, -hz), (hx, -hy, -hz)])]
    for n, vs in faces:
        nn = R @ np.array(n, dtype=float)
        ps = [c + R @ np.array(v) for v in vs]
        S.quad(part, mat, ps, [nn] * 4)


def rounded_box(part, mat, c, size, r=0.03, seg=3):
    """Boite aux aretes arrondies (superellipsoide simplifiee)."""
    c = np.array(c, dtype=float)
    h = np.array(size, dtype=float) / 2 - r
    nu, nv = 16, 10
    P = []
    for iv in range(nv + 1):
        v = -math.pi / 2 + math.pi * iv / nv
        row = []
        for iu in range(nu + 1):
            u = 2 * math.pi * iu / nu
            d = np.array([math.cos(v) * math.cos(u), math.sin(v), math.cos(v) * math.sin(u)])
            core = np.sign(d) * h
            core[np.abs(d) < 1e-9] = 0
            row.append((c + core + r * d, d))
        P.append(row)
    for iv in range(nv):
        for iu in range(nu):
            q = [P[iv][iu], P[iv + 1][iu], P[iv + 1][iu + 1], P[iv][iu + 1]]
            S.quad(part, mat, [x[0] for x in q], [x[1] for x in q])


def ellipsoid(part, mat, c, rad, nu=14, nv=8):
    c = np.array(c, dtype=float)
    rad = np.array(rad, dtype=float)
    P = []
    for iv in range(nv + 1):
        v = -math.pi / 2 + math.pi * iv / nv
        row = []
        for iu in range(nu + 1):
            u = 2 * math.pi * iu / nu
            d = np.array([math.cos(v) * math.cos(u), math.sin(v), math.cos(v) * math.sin(u)])
            p = c + d * rad
            n = d / rad
            row.append((p, n / np.linalg.norm(n)))
        P.append(row)
    for iv in range(nv):
        for iu in range(nu):
            q = [P[iv][iu], P[iv + 1][iu], P[iv + 1][iu + 1], P[iv][iu + 1]]
            S.quad(part, mat, [x[0] for x in q], [x[1] for x in q])


def disc(part, mat, c, normal, r_in, r_out, seg=32, a0=0.0, a1=2 * math.pi, up=(0, 1, 0)):
    n = np.array(normal, dtype=float)
    n /= np.linalg.norm(n)
    u = np.cross(np.array(up, dtype=float), n)
    if np.linalg.norm(u) < 1e-6:
        u = np.cross(np.array((1.0, 0, 0)), n)
    u /= np.linalg.norm(u)
    v = np.cross(n, u)
    c = np.array(c, dtype=float)
    for i in range(seg):
        t0 = a0 + (a1 - a0) * i / seg
        t1 = a0 + (a1 - a0) * (i + 1) / seg
        d0 = math.cos(t0) * u + math.sin(t0) * v
        d1 = math.cos(t1) * u + math.sin(t1) * v
        if r_in < 1e-6:
            S.tri(part, mat, [c, c + r_out * d0, c + r_out * d1], [n, n, n])
        else:
            S.quad(part, mat, [c + r_in * d0, c + r_out * d0, c + r_out * d1, c + r_in * d1], [n] * 4)


def torus(part, mat, c, axis, R, r, nu=32, nv=8):
    a = np.array(axis, dtype=float)
    a /= np.linalg.norm(a)
    u = np.cross(a, np.array((0, 1.0, 0)))
    if np.linalg.norm(u) < 1e-6:
        u = np.cross(a, np.array((1.0, 0, 0)))
    u /= np.linalg.norm(u)
    v = np.cross(a, u)
    c = np.array(c, dtype=float)
    P = []
    for i in range(nu + 1):
        th = 2 * math.pi * i / nu
        d = math.cos(th) * u + math.sin(th) * v
        row = []
        for j in range(nv + 1):
            ph = 2 * math.pi * j / nv
            n = math.cos(ph) * d + math.sin(ph) * a
            row.append((c + R * d + r * n, n))
        P.append(row)
    for i in range(nu):
        for j in range(nv):
            q = [P[i][j], P[i + 1][j], P[i + 1][j + 1], P[i][j + 1]]
            S.quad(part, mat, [x[0] for x in q], [x[1] for x in q])


# ------------------------------------------------------------------- roues
def wheel(name, cx, cz, outward):
    """Roue 205/55 R16 a 5 branches. outward = +1 (gauche, +X) ou -1."""
    seg = 40
    c = np.array([cx, TIRE_R, cz])
    ax = np.array([outward, 0.0, 0.0])
    # profil du pneu (rayon, decalage axial vers l'exterieur)
    prof = [(0.203, -0.092), (0.245, -0.101), (0.290, -0.099), (0.309, -0.090),
            (0.316, -0.070), (0.316, 0.070), (0.309, 0.090), (0.290, 0.099),
            (0.245, 0.101), (0.203, 0.092)]

    def ring_pt(r, a, th):
        return c + ax * a + r * np.array([0.0, math.cos(th), math.sin(th)])

    for i in range(seg):
        t0 = 2 * math.pi * i / seg
        t1 = 2 * math.pi * (i + 1) / seg
        for (r0, a0), (r1, a1) in zip(prof, prof[1:]):
            # normale du profil : perpendiculaire a la tangente, vers l'exterieur du tore
            tr, ta = r1 - r0, a1 - a0
            nr, na = ta, -tr
            if nr * (0.5 * (r0 + r1) - 0.26) + na * (0.5 * (a0 + a1)) < 0:
                nr, na = -nr, -na
            ps = [ring_pt(r0, a0, t0), ring_pt(r1, a1, t0), ring_pt(r1, a1, t1), ring_pt(r0, a0, t1)]

            def nn(th):
                v = nr * np.array([0.0, math.cos(th), math.sin(th)]) + na * ax
                return v / (np.linalg.norm(v) + 1e-12)
            S.quad(name, 'tire', ps, [nn(t0), nn(t0), nn(t1), nn(t1)])
    # jante : face exterieure
    face_a = 0.078
    spokes = 5
    rings = [(0.0, 0.032, 'chrome'), (0.032, 0.075, 'rim'), (0.075, 0.185, None), (0.185, 0.203, 'rim')]
    nseg = 60
    for (ri, ro, m) in rings:
        for i in range(nseg):
            t0 = 2 * math.pi * i / nseg
            t1 = 2 * math.pi * (i + 1) / nseg
            mm = m
            if mm is None:
                tm = (t0 + t1) / 2
                ang = (tm % (2 * math.pi / spokes)) / (2 * math.pi / spokes)
                mm = 'rim' if (ang < 0.13 or ang > 0.87 or 0.40 < ang < 0.60) else 'rim_dark'
            dish = lambda r: face_a - 0.012 * (r / 0.203)
            p = [ring_pt(ri, dish(ri), t0), ring_pt(ro, dish(ro), t0), ring_pt(ro, dish(ro), t1), ring_pt(ri, dish(ri), t1)]
            S.quad(name, mm, p, [ax] * 4)
    # tambour de jante (interieur)
    for i in range(seg):
        t0 = 2 * math.pi * i / seg
        t1 = 2 * math.pi * (i + 1) / seg
        p = [ring_pt(0.203, -0.09, t0), ring_pt(0.203, 0.066, t0), ring_pt(0.203, 0.066, t1), ring_pt(0.203, -0.09, t1)]
        nm = lambda th: -np.array([0.0, math.cos(th), math.sin(th)])
        S.quad(name, 'rim_dark', p, [nm(t0), nm(t0), nm(t1), nm(t1)])


# ----------------------------------------------------------------- details
def side_x(z, y):
    pts, meta = section(z)
    j4 = 1 + sum(len(SEG_T[k]) - 1 for k in range(4))
    j9 = 1 + sum(len(SEG_T[k]) - 1 for k in range(9))
    chain = pts[j4:j9 + 1]
    for a, b in zip(chain, chain[1:]):
        if a[1] <= y <= b[1] and b[1] > a[1]:
            t = (y - a[1]) / (b[1] - a[1])
            return lerp(a[0], b[0], t)
    return hw(z)


def details():
    for side, sx in (('l', 1.0), ('r', -1.0)):
        # retroviseur (sur la porte avant)
        z = 0.36
        yb = belt_y(z)
        xs = side_x(z, yb - 0.01)
        part = 'door_f' + side
        ellipsoid(part, 'paint', (sx * (xs + 0.115), yb + 0.085, z), (0.105, 0.062, 0.05))
        disc(part, 'mirror', (sx * (xs + 0.118), yb + 0.085, z - 0.051), (0, 0, -1), 0.0, 0.052, seg=20,
             up=(0, 1, 0))
        box(part, 'trim', (sx * (xs + 0.03), yb + 0.045, z + 0.01), (0.07, 0.035, 0.06))
        # poignees
        for (zz, yy, pp) in ((-0.40, 0.872, 'door_f' + side), (-1.17, 0.882, 'door_r' + side)):
            x = side_x(zz, yy)
            box(pp, 'chrome', (sx * (x + 0.006), yy, zz), (0.016, 0.026, 0.13))
        # baguette de bas de caisse
        box('body', 'chrome', (sx * (side_x(0.09, 0.31) + 0.002), 0.31, 0.09), (0.01, 0.012, 1.94))
    # calandre : barres chromees + etoile
    zf = HL + 0.006
    for yy in (0.60, 0.68):
        box('body', 'chrome', (0.0, yy, zf), (0.56, 0.012, 0.012))
    torus('body', 'chrome', (0.0, 0.64, HL + 0.022), (0, 0, 1), 0.072, 0.008, nu=36, nv=6)
    disc('body', 'grille', (0.0, 0.64, HL + 0.016), (0, 0, 1), 0.0, 0.072, seg=36)
    for k in range(3):
        a = math.pi / 2 + k * 2 * math.pi / 3
        d = np.array([math.cos(a), math.sin(a), 0.0])
        pz = HL + 0.024
        ctr = np.array([0.0, 0.64, pz])
        perp = np.array([-d[1], d[0], 0.0])
        tip = ctr + d * 0.068
        p = [ctr + perp * 0.012, tip, ctr - perp * 0.012]
        S.tri('body', 'chrome', p, [(0, 0, 1)] * 3)
    # antibrouillards
    for sx in (1.0, -1.0):
        disc('body', 'headlight', (sx * 0.56, 0.315, HL + 0.004), (0, 0, 1), 0.0, 0.034, seg=20)
        for hx in (0.47, 0.64):
            disc('body', 'chrome', (sx * hx, 0.705, HL + 0.004), (0, 0, 1), 0.0, 0.036, seg=20)
            disc('body', 'lens', (sx * hx, 0.705, HL + 0.006), (0, 0, 1), 0.0, 0.022, seg=16)
    # 3e feu stop & etoile du coffre
    disc('trunk', 'chrome', (0.0, 0.88, -HL - 0.004), (0, 0, -1), 0.0, 0.035, seg=24)


def interior():
    P = 'interior'
    tilt = lambda a: [[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]]
    for sx in (1.0, -1.0):
        x = sx * 0.37
        rounded_box(P, 'seat', (x, 0.44, -0.32), (0.50, 0.13, 0.50), r=0.04)
        box(P, 'seat', (x, 0.78, -0.62), (0.48, 0.60, 0.12), rot=tilt(-0.25))
        rounded_box(P, 'seat', (x, 1.13, -0.71), (0.26, 0.16, 0.09), r=0.035)
    rounded_box(P, 'seat', (0.0, 0.44, -1.10), (1.30, 0.14, 0.48), r=0.04)
    box(P, 'seat', (0.0, 0.76, -1.37), (1.30, 0.56, 0.12), rot=tilt(-0.30))
    box(P, 'dash', (0.0, 0.84, 0.40), (1.56, 0.26, 0.42))
    box(P, 'dash', (0.0, 0.47, -0.05), (0.24, 0.28, 0.80))
    box(P, 'screen', (0.0, 0.99, 0.34), (0.30, 0.13, 0.02), rot=tilt(-0.35))
    torus(P, 'dash', (0.37, 0.88, 0.12), (0, 0.38, -0.93), 0.185, 0.019, nu=28, nv=6)
    disc(P, 'dash', (0.37, 0.88, 0.13), (0, 0.38, -0.93), 0.0, 0.06, seg=16)
    box(P, 'dash', (0.37, 0.92, 0.25), (0.05, 0.05, 0.22), rot=tilt(0.45))
    box(P, 'seat', (0.0, 0.985, -1.50), (1.40, 0.02, 0.30))           # plage arriere
    box(P, 'seat', (0.0, 0.44, -1.93), (1.30, 0.04, 0.50))            # plancher coffre
    box(P, 'under', (0.0, 0.62, HOOD_Z[0] + 0.01), (1.56, 0.66, 0.01))  # tablier
    E = 'engine'
    rounded_box(E, 'engine', (0.0, 0.62, 1.32), (0.62, 0.30, 0.70), r=0.05)
    box(E, 'engine_cover', (0.0, 0.79, 1.30), (0.52, 0.04, 0.56))
    box(E, 'under', (0.0, 0.50, 2.10), (1.40, 0.34, 0.04))           # radiateur
    box(E, 'engine', (-0.55, 0.70, 1.15), (0.18, 0.20, 0.26))        # batterie / boite a air
    box(E, 'well', (0.0, 0.40, 1.25), (1.30, 0.02, 1.70))            # fond de compartiment


# -------------------------------------------------------------- materiaux
MATS = {
    'paint':        dict(c=(0.545, 0.565, 0.580, 1), m=0.75, r=0.32),
    'glass':        dict(c=(0.05, 0.065, 0.08, 0.62), m=0.0, r=0.32, blend=True),
    'trim':         dict(c=(0.03, 0.03, 0.035, 1), m=0.0, r=0.35),
    'gap':          dict(c=(0.01, 0.01, 0.012, 1), m=0.0, r=0.9),
    'chrome':       dict(c=(0.85, 0.86, 0.88, 1), m=1.0, r=0.08),
    'grille':       dict(c=(0.02, 0.02, 0.025, 1), m=0.3, r=0.25),
    'headlight':    dict(c=(0.42, 0.45, 0.48, 1), m=0.8, r=0.08, e=(0.03, 0.03, 0.035)),
    'lens':         dict(c=(0.90, 0.93, 1.0, 1), m=0.2, r=0.05, e=(0.35, 0.37, 0.40)),
    'taillight':    dict(c=(0.55, 0.03, 0.04, 1), m=0.2, r=0.15, e=(0.10, 0.0, 0.0)),
    'plate':        dict(c=(0.92, 0.92, 0.90, 1), m=0.0, r=0.6),
    'plate_blue':   dict(c=(0.05, 0.15, 0.55, 1), m=0.0, r=0.6),
    'under':        dict(c=(0.06, 0.06, 0.065, 1), m=0.0, r=0.9),
    'well':         dict(c=(0.035, 0.035, 0.04, 1), m=0.0, r=0.9),
    'tire':         dict(c=(0.045, 0.045, 0.05, 1), m=0.0, r=0.85),
    'rim':          dict(c=(0.72, 0.73, 0.75, 1), m=0.9, r=0.22),
    'rim_dark':     dict(c=(0.10, 0.10, 0.11, 1), m=0.3, r=0.5),
    'mirror':       dict(c=(0.25, 0.27, 0.30, 1), m=1.0, r=0.05),
    'seat':         dict(c=(0.09, 0.09, 0.10, 1), m=0.0, r=0.6),
    'dash':         dict(c=(0.07, 0.07, 0.075, 1), m=0.0, r=0.5),
    'screen':       dict(c=(0.02, 0.03, 0.05, 1), m=0.2, r=0.1, e=(0.02, 0.05, 0.09)),
    'engine':       dict(c=(0.12, 0.12, 0.13, 1), m=0.4, r=0.5),
    'engine_cover': dict(c=(0.05, 0.05, 0.055, 1), m=0.1, r=0.4),
}


# -------------------------------------------------------------- ecriture glb
def write_glb(path):
    part_order = ['body', 'hood', 'trunk', 'door_fl', 'door_fr', 'door_rl', 'door_rr',
                  'wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr', 'interior', 'engine']
    mat_names = list(MATS.keys())
    gl = {"asset": {"version": "2.0", "generator": "W204 Dash - modele provisoire"},
          "scene": 0, "scenes": [{"name": "W204", "nodes": [0]}],
          "nodes": [{"name": "W204_C180_CDI", "children": []}],
          "meshes": [], "materials": [], "accessors": [], "bufferViews": [], "buffers": []}
    for name in mat_names:
        d = MATS[name]
        m = {"name": name, "pbrMetallicRoughness": {
            "baseColorFactor": [round(v, 4) for v in d['c']],
            "metallicFactor": d['m'], "roughnessFactor": d['r']}, "doubleSided": True}
        if d.get('blend'):
            m["alphaMode"] = "BLEND"
        if d.get('e'):
            m["emissiveFactor"] = list(d['e'])
        gl["materials"].append(m)
    blob = bytearray()

    def add_view(data, target):
        while len(blob) % 4:
            blob.append(0)
        off = len(blob)
        blob.extend(data)
        gl["bufferViews"].append({"buffer": 0, "byteOffset": off, "byteLength": len(data), "target": target})
        return len(gl["bufferViews"]) - 1

    tri_total = 0
    for part in part_order:
        if part not in S.parts:
            continue
        prims = []
        for mat, (_, verts, idx) in S.parts[part].items():
            if not idx:
                continue
            assert len(verts) < 65536, (part, mat, len(verts))
            pos = np.array([v[0] for v in verts], dtype=np.float32)
            nor = np.array([v[1] for v in verts], dtype=np.float32)
            ind = np.array(idx, dtype=np.uint16)
            tri_total += len(idx) // 3
            vp = add_view(pos.tobytes(), 34962)
            vn = add_view(nor.tobytes(), 34962)
            vi = add_view(ind.tobytes(), 34963)
            ap = len(gl["accessors"])
            gl["accessors"].append({"bufferView": vp, "componentType": 5126, "count": len(verts), "type": "VEC3",
                                    "min": [float(x) for x in pos.min(axis=0)],
                                    "max": [float(x) for x in pos.max(axis=0)]})
            gl["accessors"].append({"bufferView": vn, "componentType": 5126, "count": len(verts), "type": "VEC3"})
            gl["accessors"].append({"bufferView": vi, "componentType": 5123, "count": len(idx), "type": "SCALAR"})
            prims.append({"attributes": {"POSITION": ap, "NORMAL": ap + 1}, "indices": ap + 2,
                          "material": mat_names.index(mat)})
        gl["meshes"].append({"name": part, "primitives": prims})
        gl["nodes"].append({"name": part, "mesh": len(gl["meshes"]) - 1})
        gl["nodes"][0]["children"].append(len(gl["nodes"]) - 1)
    while len(blob) % 4:
        blob.append(0)
    gl["buffers"].append({"byteLength": len(blob)})
    js = json.dumps(gl, separators=(',', ':')).encode('utf-8')
    while len(js) % 4:
        js += b' '
    total = 12 + 8 + len(js) + 8 + len(blob)
    with open(path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, total))
        f.write(struct.pack('<II', len(js), 0x4E4F534A))
        f.write(js)
        f.write(struct.pack('<II', len(blob), 0x004E4942))
        f.write(bytes(blob))
    return tri_total, total


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/models/car.glb'
    build_body()
    build_cap(HL, True)
    build_cap(-HL, False)
    details()
    for (nm, x, z, o) in (('wheel_fl', TRACK, ZF, 1), ('wheel_fr', -TRACK, ZF, -1),
                          ('wheel_rl', TRACK, ZR, 1), ('wheel_rr', -TRACK, ZR, -1)):
        wheel(nm, x, z, o)
    interior()
    tris, size = write_glb(out)
    print("ecrit %s : %d triangles, %.0f Ko" % (out, tris, size / 1024.0))


if __name__ == '__main__':
    main()
