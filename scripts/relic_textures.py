#!/usr/bin/env python3
"""Generate 16x16 item sprites for Vital Relics.

The old sprites were mostly flat 5-9 colour shapes. This script rebuilds them
from a few parametric templates (ring / bracelet / anklet / amulet / charm /
core / rune / cross) while keeping each relic's original colour identity: the
hue is sampled from the existing texture, then expanded into a proper shading
ramp with an outline, four light steps and a specular pixel.

Usage:
    python3 scripts/relic_textures.py --out build/preview/generated
    python3 scripts/relic_textures.py --out build/preview/generated --only ring_
    python3 scripts/relic_textures.py --out <textures dir>   # real overwrite

Only stdlib is used (zlib/struct/colorsys), so no Pillow needed.
"""

import argparse
import colorsys
import json
import math
import os
import struct
import zlib

N = 16
SRC = "common/src/main/resources/vitalrelics/textures"
RELIC_JSON = "common/src/main/resources/vitalrelics/relics.json"

# How much ornamentation each rarity earns.
DETAIL = {"common": 0, "uncommon": 1, "rare": 2, "epic": 3}

# Colour identity per texture, captured once from the original artwork.
# Regenerating must never re-sample its own output: doing so dragged every
# relic to the same saturation/value and erased the difference between the
# plain and *_saturated variants.

PALETTE = {
    "arcane_reforging_charm.png": (0.987, 0.94, 0.65),
    "astral_halo.png": (0.135, 0.76, 0.76),
    "bleeding_sigil.png": (0.987, 0.85, 0.62),
    "bleeding_sigil_v2.png": (0.960, 0.55, 0.62),
    "blossom_anklet.png": (0.945, 0.72, 0.86),
    "borebolt_bracelet.png": (0.033, 0.66, 0.62),
    "bracelet_aqua.png": (0.500, 0.55, 0.62),
    "bracelet_aqua_saturated.png": (0.500, 0.87, 0.64),
    "bracelet_blue.png": (0.632, 0.55, 0.62),
    "bracelet_gray.png": (0.000, 0.35, 0.49),
    "bracelet_green.png": (0.315, 0.55, 0.62),
    "bracelet_green_saturated.png": (0.291, 0.87, 0.64),
    "bracelet_pink.png": (0.889, 0.55, 0.62),
    "bracelet_pink_saturated.png": (0.872, 0.87, 0.64),
    "bracelet_purple.png": (0.731, 0.55, 0.62),
    "bracelet_red.png": (0.000, 0.55, 0.62),
    "bracelet_red_saturated.png": (0.000, 0.87, 0.64),
    "bracelet_yellow.png": (0.154, 0.55, 0.62),
    "bracelet_yellow_saturated.png": (0.158, 0.87, 0.64),
    "bulwark_ring.png": (0.745, 0.55, 0.62),
    "celestial_aegis.png": (0.149, 0.82, 0.71),
    "celestial_wings.png": (0.592, 0.74, 0.62),
    "cherry_anklet.png": (0.958, 0.70, 0.76),
    "cherry_cross.png": (0.014, 0.73, 0.66),
    "cherry_petal_pendant.png": (0.955, 0.55, 0.62),
    "cherrywood_cross.png": (0.008, 0.59, 0.62),
    "cinderbrand_ring.png": (0.074, 0.82, 0.87),
    "cleansing_bracelet.png": (0.556, 0.55, 0.62),
    "common_relic_core.png": (0.067, 0.55, 0.62),
    "covenant_ring.png": (0.341, 0.55, 0.62),
    "cursed_crimson_rune.png": (0.966, 0.81, 0.62),
    "cursed_crimson_rune_v2.png": (0.973, 0.77, 0.62),
    "discord_bracelet.png": (0.875, 0.63, 0.62),
    "enchanters_bracelet.png": (0.100, 0.58, 0.62),
    "enderkeeper_bracelet.png": (0.753, 0.55, 0.62),
    "epic_relic_core.png": (0.744, 0.59, 0.62),
    "frostbind_ring.png": (0.586, 0.55, 0.77),
    "gilded_circlet.png": (0.117, 0.80, 0.73),
    "golden_apple.png": (0.089, 0.90, 0.69),
    "gothic_spider_eye_pendant.png": (0.943, 0.72, 0.62),
    "gravity_anchor_charm.png": (0.622, 0.55, 0.62),
    "guardian_scale_amulet.png": (0.512, 0.60, 0.62),
    "halo_celestial_crystal.png": (0.925, 0.74, 0.66),
    "healing_bracelet.png": (0.036, 0.55, 0.72),
    "homebound_bracelet.png": (0.041, 0.65, 0.62),
    "icy_feathered_arrow.png": (0.588, 0.63, 0.64),
    "iron_heart.png": (0.058, 0.55, 0.62),
    "jumping_ring.png": (0.128, 0.69, 0.63),
    "luck_ring.png": (0.145, 0.72, 0.64),
    "miners_charm.png": (0.051, 0.57, 0.62),
    "moonmilk_pearl.png": (0.621, 0.55, 0.72),
    "myth_relic.png": (0.056, 0.35, 0.40),
    "petalstep_anklet.png": (0.975, 0.74, 0.74),
    "phantom_step_bracelet.png": (0.561, 0.55, 0.62),
    "power_ring.png": (0.994, 0.72, 0.62),
    "quarry_ring.png": (0.086, 0.90, 0.70),
    "rare_relic_core.png": (0.614, 0.79, 0.62),
    "repulsion_bracelet.png": (0.593, 0.55, 0.62),
    "ring_aqua.png": (0.500, 0.55, 0.62),
    "ring_aqua_saturated.png": (0.500, 1.00, 0.62),
    "ring_bastion_aura.png": (0.425, 0.55, 0.62),
    "ring_blooddrinker.png": (0.000, 1.00, 0.62),
    "ring_blue.png": (0.586, 0.55, 0.62),
    "ring_bulwark_aura.png": (0.196, 0.74, 0.62),
    "ring_fire_resistance.png": (0.047, 0.86, 0.62),
    "ring_gray.png": (0.000, 0.35, 0.27),
    "ring_green.png": (0.289, 0.55, 0.62),
    "ring_green_saturated.png": (0.290, 1.00, 0.62),
    "ring_haste.png": (0.117, 0.91, 0.69),
    "ring_metal_mending.png": (0.542, 0.55, 0.62),
    "ring_night_sight.png": (0.710, 0.59, 0.62),
    "ring_pink.png": (0.846, 0.55, 0.62),
    "ring_purple.png": (0.733, 0.55, 0.62),
    "ring_purple_saturated.png": (0.761, 1.00, 0.62),
    "ring_red.png": (0.000, 0.55, 0.62),
    "ring_red_saturated.png": (0.000, 1.00, 0.62),
    "ring_regeneration.png": (0.954, 0.65, 0.62),
    "ring_swiftness.png": (0.533, 0.72, 0.62),
    "ring_temporal_guard.png": (0.694, 0.93, 0.62),
    "ring_thorned.png": (0.794, 1.00, 0.62),
    "ring_yellow.png": (0.154, 0.55, 0.62),
    "ripple_anklet.png": (0.560, 0.72, 0.80),
    "savage_bloodfang_pendant.png": (0.986, 0.74, 0.62),
    "scholars_convergence_ring.png": (0.561, 0.63, 0.62),
    "shadow_exchange_bracelet.png": (0.745, 0.55, 0.62),
    "skycaller_bracelet.png": (0.608, 0.55, 0.62),
    "stone_ring.png": (0.400, 0.35, 0.44),
    "tidebloom_anklet.png": (0.566, 0.72, 0.78),
    "tidecaller_anklet.png": (0.505, 0.80, 0.88),
    "timberheart_ring.png": (0.098, 0.62, 0.62),
    "totemic_heart_spirit_amulet.png": (0.281, 0.62, 0.62),
    "travelers_eye.png": (0.111, 0.55, 0.62),
    "uncommon_relic_core.png": (0.163, 0.74, 0.62),
    "vitality_ring.png": (0.961, 0.69, 0.65),
    "warcaster_bracelet.png": (0.015, 0.72, 0.62),
    "warding_bracelet.png": (0.517, 0.55, 0.62),
    "windstep_bracelet.png": (0.096, 0.68, 0.62),
    "windward_bracelet.png": (0.430, 0.55, 0.62),
    "withered_heart_pendant.png": (0.763, 0.56, 0.62),
    "wolf_fang.png": (0.038, 0.55, 0.62),
}


def load_rarity(path=RELIC_JSON):
    """Map texture file name -> rarity, so sprites can scale with rarity."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError):
        return {}
    return {r["texture"]: r.get("rarity", "common")
            for r in data.get("relics", []) if r.get("texture")}


def infer_rarity(name):
    """Materials are named after their rarity (e.g. epic_relic_core.png)."""
    for rank in ("common", "uncommon", "rare", "epic"):
        if name.startswith(rank + "_"):
            return rank
    return "rare"


def name_hash(name):
    """FNV-1a.  A plain sum collides too often (both epic anklets hit 0)."""
    h = 2166136261
    for ch in name:
        h = ((h ^ ord(ch)) * 16777619) & 0xFFFFFFFF
    return h

# --------------------------------------------------------------------------
# PNG read / write (RGBA only), stdlib
# --------------------------------------------------------------------------

def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def read_rgba(path):
    """Return (w, h, [[(r,g,b,a), ...], ...]) for 8/4-bit palette or RGBA PNG."""
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a png: " + path)
    pos, idat, plte, trns = 8, b"", None, None
    w = h = bd = ct = None
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            w, h, bd, ct, _comp, _filt, inter = struct.unpack(">IIBBBBB", body)
            if inter:
                raise ValueError("interlaced png unsupported: " + path)
        elif ctype == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif ctype == b"tRNS":
            trns = body
        elif ctype == b"IDAT":
            idat += body
        elif ctype == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    bpp = max(1, (channels * bd) // 8)
    stride = (w * channels * bd + 7) // 8
    raw = zlib.decompress(idat)
    rows, prev, p = [], bytearray(stride), 0
    for _y in range(h):
        ft = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + _paeth(a, b, c)) & 255
        elif ft != 0:
            raise ValueError("bad filter %d" % ft)
        rows.append(line)
        prev = line

    out = []
    for y in range(h):
        line = rows[y]
        row = []
        for x in range(w):
            if ct == 6 and bd == 8:
                i = x * 4
                row.append((line[i], line[i + 1], line[i + 2], line[i + 3]))
            elif ct == 3 and bd == 8:
                i = line[x]
                r, g, b = plte[i]
                a = trns[i] if trns is not None and i < len(trns) else 255
                row.append((r, g, b, a))
            elif ct == 3 and bd == 4:
                byte = line[x // 2]
                i = (byte >> 4) if x % 2 == 0 else (byte & 0x0F)
                r, g, b = plte[i]
                a = trns[i] if trns is not None and i < len(trns) else 255
                row.append((r, g, b, a))
            elif ct == 2 and bd == 8:
                i = x * 3
                row.append((line[i], line[i + 1], line[i + 2], 255))
            elif ct == 0 and bd == 8:
                v = line[x]
                row.append((v, v, v, 255))
            else:
                raise ValueError("unsupported png %s bd=%s ct=%s" % (path, bd, ct))
        out.append(row)
    return w, h, out


def write_rgba(path, canvas):
    raw = bytearray()
    for y in range(N):
        raw.append(0)
        for x in range(N):
            r, g, b, a = canvas.px[y][x]
            raw.extend((r & 255, g & 255, b & 255, a & 255))

    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


# --------------------------------------------------------------------------
# Colour ramps
# --------------------------------------------------------------------------

def _hsv_to_rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, min(1, max(0, s)), min(1, max(0, v)))
    return (round(r * 255), round(g * 255), round(b * 255), 255)


def tones(h, s, v):
    """Five-step ramp: outline, dark, mid, light, spec."""
    return {
        "outline": _hsv_to_rgb(h, min(1, s * 1.05), max(0.06, v * 0.18)),
        "dark": _hsv_to_rgb(h, min(1, s * 1.05), max(0.14, v * 0.46)),
        "mid": _hsv_to_rgb(h, s, max(0.28, v * 0.76)),
        "light": _hsv_to_rgb(h, s * 0.78, min(1, v * 1.02 + 0.10)),
        "spec": _hsv_to_rgb(h, s * 0.30, min(1, v * 0.55 + 0.48)),
    }


def gem_tones(h, s):
    """A gem ramp that stays saturated; the metal ramp washes stones out."""
    s = min(1.0, max(0.72, s))
    return {
        "outline": _hsv_to_rgb(h, s, 0.16),
        "dark": _hsv_to_rgb(h, s, 0.44),
        "mid": _hsv_to_rgb(h, s, 0.72),
        "light": _hsv_to_rgb(h, min(1.0, s * 0.92), 1.0),
        "spec": _hsv_to_rgb(h, s * 0.30, 1.0),
    }


def analyze(path):
    """Sample the dominant saturated hue from an existing sprite."""
    try:
        _w, _h, px = read_rgba(path)
    except Exception:
        return 0.12, 0.72, 0.85
    zx = zy = 0.0
    sat_sum = val_sum = n = 0
    grey_sum = [0, 0, 0]
    grey_n = 0
    for row in px:
        for r, g, b, a in row:
            if a < 128:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= 0.22 and v >= 0.18:
                ang = 2 * math.pi * h
                weight = s * v
                zx += math.cos(ang) * weight
                zy += math.sin(ang) * weight
                sat_sum += s
                val_sum += v
                n += 1
            elif v < 0.9:
                grey_sum[0] += r
                grey_sum[1] += g
                grey_sum[2] += b
                grey_n += 1
    if n:
        hue = (math.atan2(zy, zx) / (2 * math.pi)) % 1.0
        return hue, max(0.55, sat_sum / n), min(1.0, max(0.62, val_sum / n))
    if grey_n:
        r, g, b = (c // grey_n for c in grey_sum)
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        return h, 0.35, v
    return 0.12, 0.72, 0.85


# --------------------------------------------------------------------------
# Canvas + primitives
# --------------------------------------------------------------------------

class Canvas:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * N for _ in range(N)]

    def set(self, x, y, colour):
        if 0 <= x < N and 0 <= y < N:
            self.px[y][x] = colour


def _tone_for(brightness):
    if brightness > 0.60:
        return "spec"
    if brightness > 0.15:
        return "light"
    if brightness > -0.35:
        return "mid"
    return "dark"


def _edge_tone(b):
    """Pick the edge tone: subtle on the lit side, near-black in shadow.

    *b* is the local light dot (roughly -1..1).  Vanilla items keep an edge
    all the way round, but only the shadowed part is near-black; the lit part
    is a quiet step down from the surface next to it.
    """
    if b > 0.50:
        return "mid"
    if b > 0.05:
        return "dark"
    return "outline"


def _inner_edge_tone(b):
    """A softer edge for the silhouettes *inside* a shape.

    An inner line shares the same directional rule as the outside one, but the
    band it sits on is only a couple of pixels wide, so it must not spend most
    of the lit half on near-black.  Thresholds are therefore pushed towards the
    shadow: quiet mid across the upper left, dark around the right, near-black
    only at the very bottom right.
    """
    if b > 0.20:
        return "mid"
    if b > -0.30:
        return "dark"
    return "outline"


def draw_ring(c, cx, cy, radius, thickness, ramp, gem=None, gem_offset=(0.0, -0.72),
              gem_r=1.65, beads=None):
    inner = 1.0 - thickness

    def norm(x, y):
        return math.hypot((x + 0.5 - cx) / radius, (y + 0.5 - cy) / radius)

    for y in range(N):
        for x in range(N):
            d = norm(x, y)
            if not (inner <= d <= 1.0):
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            dd = math.hypot(dx, dy) or 1.0
            tone = _tone_for(-(dx / dd + dy / dd) * 0.7071)
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = norm(x + ox, y + oy)
                if q > 1.0:
                    tone = "outline"
                    break
                if q < inner:
                    tone = "dark" if tone != "spec" else "light"
            c.set(x, y, ramp[tone])

    if beads:
        for bx, by in beads:
            draw_oval(c, cx + bx * radius, cy + by * radius, 1.1, 1.1, ramp)

    if gem is not None:
        gx = cx + gem_offset[0] * radius
        gy = cy + gem_offset[1] * radius
        draw_oval(c, gx, gy, gem_r * 1.12, gem_r, gem)


def draw_oval(c, cx, cy, rx, ry, ramp, specular=True):
    def inside(x, y):
        return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0

    for y in range(N):
        for x in range(N):
            if not inside(x, y):
                continue
            nx = (x + 0.5 - cx) / rx
            ny = (y + 0.5 - cy) / ry
            b = -(nx + ny) * 0.7071
            tone = _tone_for(b)
            if any(not inside(x + ox, y + oy)
                   for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                tone = _edge_tone(b)
            c.set(x, y, ramp[tone])
    if specular:
        c.set(int(math.floor(cx)) - 1, int(math.floor(cy)) - 1, ramp["spec"])


def draw_line(c, x0, y0, x1, y1, colour):
    x0, y0, x1, y1 = (int(round(v)) for v in (x0, y0, x1, y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        c.set(x0, y0, colour)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def draw_rect(c, x0, y0, x1, y1, ramp, corner=0):
    def inside(px, py):
        if not (x0 <= px <= x1 and y0 <= py <= y1):
            return False
        if corner > 0:
            qx = min(max(px, x0 + corner), x1 - corner)
            qy = min(max(py, y0 + corner), y1 - corner)
            if (px - qx) ** 2 + (py - qy) ** 2 > corner * corner + 0.01:
                return False
        return True

    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    hw = max(0.5, (x1 - x0) / 2.0)
    hh = max(0.5, (y1 - y0) / 2.0)

    for y in range(N):
        for x in range(N):
            if not inside(x, y):
                continue
            tone = "mid"
            if any(not inside(x + ox, y + oy)
                   for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                b = -(((x + 0.5 - cx) / hw) + ((y + 0.5 - cy) / hh)) * 0.7071
                tone = _edge_tone(b)
            elif not inside(x, y - 2):
                tone = "light"
            elif not inside(x, y + 2):
                tone = "dark"
            c.set(x, y, ramp[tone])


GLYPHS = [
    ("..#..", ".#.#.", "#...#", ".#.#.", "..#.."),
    ("..#..", "..#..", "#####", "..#..", "..#.."),
    ("#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
    ("..#..", ".###.", "#.#.#", ".###.", "..#.."),
    ("##.##", "#.#.#", "..#..", "#.#.#", "##.##"),
    ("..##.", ".##..", "##...", ".##..", "..##."),
]


def draw_glyph(c, cx, cy, glyph, colour):
    top = cy - 2
    left = cx - 2
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch == "#":
                c.set(left + gx, top + gy, colour)


# --------------------------------------------------------------------------
# Templates
# --------------------------------------------------------------------------

_HOOP_N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def _hoop(c, cx, cy, orx, ory, irx, iry, metal):
    """A thin, face-on hoop with directionally lit outer *and* inner edges.

    Light comes from the upper left, so the upper-left rim is brighter and the
    lower-right rim is shadowed.  Both silhouettes (outside and the hole) take
    an edge that follows the same rule: a quiet mid line on the lit side and a
    near-black line on the shadowed side.
    """
    def outer(x, y):
        return ((x + 0.5 - cx) / orx) ** 2 + ((y + 0.5 - cy) / ory) ** 2

    def inner(x, y):
        return ((x + 0.5 - cx) / irx) ** 2 + ((y + 0.5 - cy) / iry) ** 2

    for y in range(N):
        for x in range(N):
            if outer(x, y) > 1.0 or inner(x, y) < 1.0:
                continue
            nx = (x + 0.5 - cx) / orx
            ny = (y + 0.5 - cy) / ory
            b = -(nx + ny) * 0.7071
            if b > 0.80:
                tone = "spec"
            elif b > 0.30:
                tone = "light"
            elif b < -0.55:
                tone = "dark"
            else:
                tone = "mid"
            if (any(outer(x + ox, y + oy) > 1.0 for ox, oy in _HOOP_N4)):
                tone = _edge_tone(b)
            elif any(inner(x + ox, y + oy) < 1.0 for ox, oy in _HOOP_N4):
                tone = _inner_edge_tone(b)
            c.set(x, y, metal[tone])

    return outer, inner


def make_ring(accent_h, accent_s, accent_v, detail=2, variant=0):
    """A small round band with a stone set in the crown.

    The whole sprite stays inside a 10x10 footprint: the hoop spans x/y 3..12
    and the setting is tucked onto the crown rather than above it.
    """
    metal = tones(accent_h, min(0.80, accent_s * 0.45), min(1.0, accent_v * 0.95 + 0.05))
    gem = gem_tones(accent_h, accent_s)
    c = Canvas()
    cx, cy = 8.0, 8.0
    orx = ory = 4.65
    irx = iry = 2.2
    _hoop(c, cx, cy, orx, ory, irx, iry, metal)

    gem_r = (1.15, 1.30, 1.45, 1.60)[detail]
    gy = cy - (ory + iry) * 0.5
    draw_diamond(c, cx, gy + 0.20, gem_r + 0.30, metal)
    _gem_shape(c, cx, gy, gem_r, gem, variant)

    mid = (orx + irx) * 0.5

    def bead(deg, r):
        a = math.radians(deg)
        draw_bead(c, cx + mid * math.cos(a), cy + mid * math.sin(a), gem, r)

    if detail >= 1:
        bead(180, 0.75)
        bead(0, 0.75)
    if detail >= 2:
        bead(90, 0.75)
    if detail >= 3:
        bead(135, 0.70)
        bead(45, 0.70)
        c.set(5, 6, metal["spec"])
    return c


def draw_diamond(c, cx, cy, r, ramp):
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if abs(dx) + abs(dy) > r:
                continue
            b = -(dx + dy)
            if b > 0.6:
                tone = "spec"
            elif b > 0.0:
                tone = "light"
            elif b > -0.9:
                tone = "mid"
            else:
                tone = "dark"
            c.set(x, y, ramp[tone])


def _gem_shape(c, cx, cy, r, ramp, style):
    """Three cuts, so pieces of equal rarity do not read as clones."""
    style %= 3
    if style == 0:
        draw_diamond(c, cx, cy, r, ramp)
    elif style == 1:
        draw_bead(c, cx, cy, ramp, r * 0.94)
    else:
        x0 = int(round(cx)) - 1
        y0 = int(round(cy)) - 1
        for dy in range(3):
            for dx in range(3):
                b = -(dx - 1) - (dy - 1)
                tone = "spec" if b >= 2 else "light" if b >= 1 else "mid" if b >= -1 else "dark"
                c.set(x0 + dx, y0 + dy, ramp[tone])


def make_bracelet(accent_h, accent_s, accent_v, detail=2, variant=0):
    """A slim round bangle seen face-on, lit from the upper left.

    The hoop is deliberately round (not a squashed ellipse) so it reads the
    same way a ring does.  The band is thin (~2.7 px) and both silhouettes
    follow the light: a quiet mid line on the upper-left, near-black lower-right.
    Rarity adds studs; *variant* changes the gem cut.
    """
    metal = tones(accent_h, min(0.80, accent_s * 0.45), min(1.0, accent_v * 0.95 + 0.05))
    gem = gem_tones(accent_h, accent_s)
    c = Canvas()
    cx, cy = 8.0, 8.4
    orx, ory = 6.6, 6.0
    irx, iry = 3.9, 3.3
    _hoop(c, cx, cy, orx, ory, irx, iry, metal)

    gem_r = (1.55, 1.75, 1.95, 2.15)[detail]
    gy = cy - (ory + iry) * 0.5
    draw_diamond(c, cx, gy + 0.25, gem_r + 0.35, metal)
    _gem_shape(c, cx, gy, gem_r, gem, variant)

    # --- studs ride the band's centreline so they always land on metal -----
    mx, my = (orx + irx) * 0.5, (ory + iry) * 0.5

    def stud(deg, r=0.80):
        a = math.radians(deg)
        draw_bead(c, cx + mx * math.cos(a), cy + my * math.sin(a), gem, r)

    if detail >= 1:
        stud(90)
    if detail >= 2:
        stud(45)
        stud(135)
    if detail >= 3:
        stud(20, 0.7)
        stud(160, 0.7)
        # glint on the lit top-left of the band (never inside the hole)
        a = math.radians(248.0)
        c.set(int(cx + mx * math.cos(a)), int(cy + my * math.sin(a)), metal["spec"])
    return c


def draw_bead(c, cx, cy, ramp, r=1.1):
    for y in range(N):
        for x in range(N):
            nx = (x + 0.5 - cx) / r
            ny = (y + 0.5 - cy) / r
            if nx * nx + ny * ny > 1.0:
                continue
            b = -(nx + ny) * 0.7071
            if b > 0.55:
                tone = "spec"
            elif b > 0.05:
                tone = "light"
            elif b > -0.62:
                tone = "mid"
            else:
                tone = "dark"
            c.set(x, y, ramp[tone])


def make_anklet(accent_h, accent_s, accent_v, detail=2, variant=0):
    """A slim beaded hoop with a charm hanging off the front.

    Shares the bracelet's face-on hoop so the two families read as the same
    set; the ankle version differs by threading small stones along the front
    arc and hanging a charm under the hoop.  *variant* changes the charm.
    """
    metal = tones(accent_h, min(0.85, accent_s * 0.45), min(1.0, accent_v * 0.95 + 0.05))
    gem = gem_tones(accent_h, accent_s)
    c = Canvas()
    cx, cy = 8.0, 6.8
    orx, ory = 6.6, 5.2
    irx, iry = 3.9, 2.5
    _hoop(c, cx, cy, orx, ory, irx, iry, metal)

    mx, my = (orx + irx) * 0.5, (ory + iry) * 0.5
    style = variant % 3

    # --- stones threaded along the front (lower) arc ---------------------
    arcs = (
        (70.0, 110.0, 3),
        (45.0, 135.0, 5),
        (30.0, 150.0, 7),
        (25.0, 155.0, 9),
    )
    lo, hi, count = arcs[detail]
    for i in range(count):
        a = math.radians(lo + (hi - lo) * i / (count - 1))
        px = cx + mx * math.cos(a)
        py = cy + my * math.sin(a)
        if abs(px - cx) < 1.6:
            continue  # leave room for the charm's bail
        draw_bead(c, px, py, gem, 0.80)

    # --- the charm hangs below the front of the hoop ---------------------
    base = cy + ory  # bottom of the hoop
    drop_r = (1.00, 1.15, 1.30, 1.45)[detail]
    if style == 0:
        draw_diamond(c, cx, base + 0.90, drop_r + 0.30, metal)
        draw_diamond(c, cx, base + 0.80, drop_r, gem)
    elif style == 1:
        for sx in (-1.0, 1.0):
            draw_bead(c, cx + sx * 1.10, base + 0.70, gem, drop_r * 0.80)
    else:
        draw_bead(c, cx, base + 0.60, gem, drop_r * 0.85)
        draw_bead(c, cx, base + 1.30, gem, drop_r * 0.50)

    if detail >= 3:
        # epic: studs on the crown as well, so the whole hoop is jewelled
        for deg in (215.0, 325.0):
            a = math.radians(deg)
            draw_bead(c, cx + mx * math.cos(a), cy + my * math.sin(a), gem, 0.72)
        # ...and small drops flanking the main charm, so the front reads as a
        # cluster rather than a single stone.  Twin-drop charms sit wider.
        span = 2.55 if style == 1 else 1.80
        for sx in (-1.0, 1.0):
            draw_bead(c, cx + sx * span, base + 0.85, gem, 0.62)
        # a glint on the lit top-left of the band
        a = math.radians(248.0)
        c.set(int(cx + mx * math.cos(a)), int(cy + my * math.sin(a)), metal["spec"])
    return c


def _cord(c, accent_h):
    """A clean two-strand necklace cord meeting at a bail between (7,4) and (8,4)."""
    cord = tones(accent_h, 0.30, 0.34)
    left = ((5, 1), (6, 2), (6, 3), (7, 4))
    for x, y in left:
        c.set(x, y, cord["mid"])
        c.set(15 - x, y, cord["mid"])
    c.set(7, 4, cord["outline"])
    c.set(8, 4, cord["outline"])
    return cord


def _disc(c, cx, cy, rx, ry, rim, gem, core=0.60):
    """One disc with a metal rim around a gem core, lit from the upper left."""
    core2 = core * core

    def inside(x, y):
        nx = (x + 0.5 - cx) / rx
        ny = (y + 0.5 - cy) / ry
        return nx * nx + ny * ny

    for y in range(N):
        for x in range(N):
            d2 = inside(x, y)
            if d2 > 1.0:
                continue
            nx = (x + 0.5 - cx) / rx
            ny = (y + 0.5 - cy) / ry
            b = -(nx + ny) * 0.7071
            in_core = d2 <= core2
            ramp = gem if in_core else rim
            if in_core:
                # A gem glows: it never goes fully dark.
                tone = "spec" if b > 0.55 else "light" if b > 0.0 else "mid"
            elif b > 0.10:
                tone = "light"
            elif b > -0.40:
                tone = "mid"
            else:
                tone = "dark"
            if any(inside(x + ox, y + oy) > 1.0
                   for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                tone = _edge_tone(b)
            elif not in_core and any(inside(x + ox, y + oy) <= core2
                                     for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                # inner silhouette: the rim drops a step where it meets the gem
                tone = _inner_edge_tone(b)
            c.set(x, y, ramp[tone])


def make_amulet(accent_h, accent_s, accent_v, detail=2):
    """A round medallion on a cord, with a gem set in the middle."""
    c = Canvas()
    _cord(c, accent_h)
    rim = tones(accent_h, min(0.90, accent_s * 0.45), min(1.0, accent_v * 0.95 + 0.05))
    gem = tones(accent_h, min(1.0, max(0.70, accent_s)), 0.92)
    _disc(c, 8.0, 9.8, 4.3, 4.3, rim, gem, core=0.50 + 0.04 * detail)
    c.set(7, 5, rim["dark"])
    c.set(8, 5, rim["dark"])
    c.set(6, 8, gem["spec"])
    if detail >= 1:
        draw_bead(c, 4.2, 8.0, rim, 0.8)
        draw_bead(c, 11.8, 8.0, rim, 0.8)
    if detail >= 2:
        draw_bead(c, 5.2, 13.2, rim, 0.8)
        draw_bead(c, 10.8, 13.2, rim, 0.8)
    if detail >= 3:
        draw_bead(c, 8.0, 13.9, gem, 0.9)
        c.set(6, 9, gem["spec"])
    return c


def make_pendant(accent_h, accent_s, accent_v, detail=2):
    """A teardrop gem hanging from a bail."""
    c = Canvas()
    _cord(c, accent_h)
    setting = tones(accent_h, min(0.90, accent_s * 0.40), min(1.0, accent_v * 0.95 + 0.05))
    gem = tones(accent_h, min(1.0, max(0.70, accent_s)), 0.92)
    _disc(c, 8.0, 9.7, 2.9, 4.0, setting, gem, core=0.60 + 0.03 * detail)
    c.set(7, 5, setting["dark"])
    c.set(8, 5, setting["dark"])
    c.set(6, 8, gem["spec"])
    if detail >= 1:
        c.set(7, 6, setting["light"])
        c.set(8, 6, setting["light"])
    if detail >= 2:
        c.set(9, 10, gem["light"])
    if detail >= 3:
        draw_bead(c, 5.1, 10.2, gem, 0.85)
        draw_bead(c, 10.9, 10.2, gem, 0.85)
    return c


def make_charm(accent_h, accent_s, accent_v, glyph_index, detail=2):
    plate = tones(accent_h, min(0.75, accent_s * 0.40), accent_v * 0.72)
    glow = gem_tones(accent_h, max(0.55, accent_s))
    ring = tones(accent_h, 0.35, accent_v * 0.80)
    c = Canvas()
    draw_oval(c, 8.0, 2.3, 1.6, 1.6, ring)
    if detail >= 3:
        # epic: a raised frame around an inset face, larger than the plain plate
        draw_rect(c, 3, 3, 12, 14, ring, corner=3)
        draw_rect(c, 5, 5, 10, 12, plate, corner=1)
        for rx, ry in ((4, 4), (11, 4), (4, 13), (11, 13)):
            c.set(rx, ry, ring["spec"])
    else:
        draw_rect(c, 4, 4, 11, 13, plate, corner=2)
        if detail >= 1:
            for rx, ry in ((5, 5), (10, 5), (5, 12), (10, 12)):
                c.set(rx, ry, plate["light"])
    if detail >= 2:
        draw_glyph(c, 8, 9, GLYPHS[glyph_index % len(GLYPHS)], glow["spec"])
    else:
        draw_glyph(c, 8, 9, GLYPHS[glyph_index % len(GLYPHS)], glow["light"])
    if detail >= 3:
        # epic: a stone beneath the sigil and a flash of light above it
        draw_bead(c, 8.0, 12.3, glow, 0.80)
        c.set(8, 4, glow["spec"])
        if glyph_index % 2:
            c.set(6, 6, glow["spec"])
            c.set(10, 6, glow["spec"])
    return c


def make_core(accent_h, accent_s, accent_v, detail=2):
    """A stone shell around a glowing core; cracks spread with rarity."""
    shell = tones(accent_h, min(0.85, accent_s * 0.30), min(1.0, accent_v * 0.95 + 0.05))
    glow = tones(accent_h, min(1.0, max(0.75, accent_s)), 0.95)
    c = Canvas()
    _disc(c, 8.0, 8.4, 5.6, 5.6, shell, glow, core=0.40 + 0.04 * detail)
    c.set(6, 6, glow["spec"])
    cracks = (((-3.6, -3.2), (4.0, -1.2), (-1.4, 4.0)),
              ((4.2, 2.2), (-4.0, 1.0), (1.0, -4.2)))
    for i in range(detail - 1):
        for dx, dy in cracks[i]:
            draw_line(c, 8.0 + dx, 8.4 + dy, 8.0 + dx * 0.42, 8.4 + dy * 0.42, shell["dark"])
    return c


def make_rune(accent_h, accent_s, accent_v, glyph_index, detail=2, bloody=False):
    plate = tones(accent_h, min(0.65, accent_s * 0.30), min(1.0, accent_v * 0.75))
    glow = gem_tones(accent_h, max(0.55, accent_s))
    c = Canvas()
    draw_rect(c, 2, 2, 13, 13, plate, corner=3)
    if detail >= 1:
        for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
            c.set(cx, cy, plate["light"])
    if detail >= 3:
        # epic: an engraved square frame around the sigil
        for x in range(4, 12):
            c.set(x, 4, plate["light"])
            c.set(x, 11, plate["light"])
        for y in range(4, 12):
            c.set(4, y, plate["light"])
            c.set(11, y, plate["light"])
        # notch the mid-edges so the frame is not a plain box
        for nx, ny in ((7, 4), (8, 4), (7, 11), (8, 11),
                       (4, 7), (4, 8), (11, 7), (11, 8)):
            c.set(nx, ny, plate["dark"])
    draw_glyph(c, 8, 8, GLYPHS[glyph_index % len(GLYPHS)], glow["spec"])
    if detail >= 3:
        if glyph_index % 2:
            for cx, cy in ((6, 5), (10, 5), (6, 10), (10, 10)):
                c.set(cx, cy, glow["spec"])
        else:
            for cx, cy in ((4, 8), (11, 8), (6, 6), (10, 6)):
                c.set(cx, cy, glow["spec"])
    if bloody:
        # blood wells under the sigil and runs down the plate
        for bx, by in ((5, 12), (7, 12), (9, 12), (11, 12)):
            c.set(bx, by, glow["dark"])
        c.set(6, 11, glow["dark"])
        c.set(10, 11, glow["dark"])
        c.set(8, 13, glow["mid"])
    c.set(7, 7, glow["spec"])
    return c


def make_cross(accent_h, accent_s, accent_v, detail=2):
    """A three-pixel cross lit from the upper left; rarity adds stones."""
    bar = tones(accent_h, min(0.90, accent_s * 0.45), min(1.0, accent_v * 0.95 + 0.05))
    gem = tones(accent_h, min(1.0, max(0.70, accent_s)), 0.92)
    c = Canvas()
    steps = ("light", "mid", "dark")

    def inside(x, y):
        return (6 <= x <= 8 and 1 <= y <= 14) or (2 <= x <= 13 and 5 <= y <= 7)

    for y in range(N):
        for x in range(N):
            if not inside(x, y):
                continue
            if 6 <= x <= 8:
                off = (x + 0.5) - 7.5
            else:
                off = (y + 0.5) - 7.5
            c.set(x, y, bar[steps[min(2, max(0, int(off + 1.5)))]])

    c.set(6, 2, bar["spec"])
    if detail >= 1:
        draw_bead(c, 7.5, 6.5, gem, 1.05)
    if detail >= 2:
        for bx, by in ((7.5, 2.0), (2.5, 6.5), (12.5, 6.5)):
            draw_bead(c, bx, by, gem, 0.8)
    if detail >= 3:
        draw_bead(c, 7.5, 13.0, gem, 0.8)
        c.set(7, 3, gem["spec"])
    return c


# --------------------------------------------------------------------------
# Badges: a mythic crest per empowered mob
# --------------------------------------------------------------------------
#
# Every badge shares one plaque silhouette so the set reads as a family; the
# monster is carried by the emblem and by the accent colour.  Emblem masks use
# '.' skip, '#' solid, 'x' recessed hole and 'o' highlight.  Solid pixels are
# shaded automatically from the upper left, like every other relic.

MOTIFS = {
    "skull": (
        "..####..",
        ".######.",
        ".#x##x#.",
        ".#x##x#.",
        ".######.",
        "..#xx#..",
        "...##...",
    ),
    "bones": (
        "##....##",
        ".##..##.",
        "..####..",
        "...##...",
        "..####..",
        ".##..##.",
        "##....##",
    ),
    "spider": (
        ".#....#.",
        "..#..#..",
        ".######.",
        "##.##.##",
        ".######.",
        "..#..#..",
        ".#....#.",
    ),
    "blob": (
        "........",
        "..####..",
        ".######.",
        "#x####x#",
        "########",
        ".######.",
        "..####..",
    ),
    "axe": (
        "...###..",
        "..#####.",
        "..######",
        "...##...",
        "....#...",
        "....#...",
        "....#...",
    ),
    "totem": (
        "..####..",
        ".######.",
        ".#o##o#.",
        ".######.",
        ".##xx##.",
        ".######.",
        "..####..",
    ),
    "cauldron": (
        ".#....#.",
        ".#....#.",
        ".######.",
        ".######.",
        ".#xxxx#.",
        "..####..",
        "........",
    ),
    "snout": (
        "........",
        ".######.",
        ".#x##x#.",
        ".#x##x#.",
        ".######.",
        "..####..",
        "........",
    ),
    "tusks": (
        ".######.",
        ".#x##x#.",
        ".######.",
        ".######.",
        "##....##",
        "#.####.#",
        "#......#",
    ),
    "cracked_skull": (
        "..####..",
        ".##.##..",
        ".#x##x#.",
        ".###.##.",
        ".#x##x#.",
        "..####..",
        "...##...",
    ),
    "flame": (
        "...##...",
        "..####..",
        ".##.##..",
        ".#oo##..",
        ".#oo##..",
        ".######.",
        "..####..",
    ),
    "ghost": (
        "########",
        "##x##x##",
        "########",
        "#xxxxxx#",
        "########",
        ".#.##.#.",
        "..#..#..",
    ),
    "ender_eye": (
        "...##...",
        "..####..",
        ".##xx##.",
        ".#xxxx#.",
        ".##xx##.",
        "..####..",
        "...##...",
    ),
    "eye": (
        "#..##..#",
        ".######.",
        "########",
        "#xxxxxx#",
        "########",
        ".######.",
        "#..##..#",
    ),
    "eye_ring": (
        "#..##..#",
        "#.####.#",
        ".######.",
        ".#xxxx#.",
        ".######.",
        "#.####.#",
        "#..##..#",
    ),
    "creeper": (
        "########",
        "#xx##xx#",
        "#xx##xx#",
        "########",
        "#xxxxxx#",
        "#xx##xx#",
        "#xx##xx#",
    ),
    "wings": (
        "........",
        "#......#",
        "##....##",
        ".######.",
        "..####..",
        "...##...",
        "...##...",
    ),
    "wind": (
        "..###...",
        ".#...#..",
        "..###...",
        "........",
        "....###.",
        "...#....",
        ".###....",
    ),
    "branches": (
        "#..##..#",
        ".#.##.#.",
        "..####..",
        ".#x##x#.",
        "..####..",
        "...##...",
        "...##...",
    ),
    "horned_beast": (
        "#......#",
        "##....##",
        ".######.",
        ".#x##x#.",
        ".######.",
        ".##xx##.",
        "..####..",
    ),
    "tendrils": (
        "#......#",
        ".#....#.",
        "..####..",
        ".#oooo#.",
        ".#oooo#.",
        "..####..",
        ".#....#.",
    ),
    "wither_skull": (
        "#..#..#.",
        ".#..#.#.",
        "..####..",
        ".#x##x#.",
        ".######.",
        ".#xxxx#.",
        "..####..",
    ),
    "dragon": (
        "##....##",
        "###..###",
        ".######.",
        ".#o##o#.",
        ".##xx##.",
        ".#xxxx#.",
        "..####..",
    ),
}

# texture -> (motif, hue, saturation, value)
BADGES = {
    "undead_badge.png": ("skull", 0.230, 0.28, 0.86),
    "skeletal_badge.png": ("bones", 0.130, 0.22, 0.90),
    "arthropod_badge.png": ("spider", 0.780, 0.34, 0.58),
    "ooze_badge.png": ("blob", 0.290, 0.72, 0.72),
    "raider_badge.png": ("axe", 0.600, 0.16, 0.54),
    "evoker_badge.png": ("totem", 0.630, 0.22, 0.50),
    "witch_badge.png": ("cauldron", 0.800, 0.46, 0.56),
    "piglin_badge.png": ("snout", 0.110, 0.74, 0.86),
    "nether_beast_badge.png": ("tusks", 0.020, 0.64, 0.56),
    "withered_badge.png": ("cracked_skull", 0.090, 0.16, 0.38),
    "blaze_badge.png": ("flame", 0.080, 0.90, 0.96),
    "ghast_badge.png": ("ghost", 0.080, 0.10, 0.92),
    "end_badge.png": ("ender_eye", 0.750, 0.56, 0.62),
    "guardian_badge.png": ("eye", 0.480, 0.60, 0.72),
    "creeper_badge.png": ("creeper", 0.330, 0.66, 0.66),
    "phantom_badge.png": ("wings", 0.600, 0.36, 0.46),
    "breeze_badge.png": ("wind", 0.550, 0.36, 0.86),
    "creaking_badge.png": ("branches", 0.090, 0.46, 0.46),
    "ravager_badge.png": ("horned_beast", 0.060, 0.30, 0.50),
    "elder_guardian_badge.png": ("eye_ring", 0.520, 0.66, 0.56),
    "warden_badge.png": ("tendrils", 0.550, 0.56, 0.36),
    "wither_badge.png": ("wither_skull", 0.600, 0.26, 0.30),
    "dragon_badge.png": ("dragon", 0.900, 0.56, 0.46),
}


def _draw_emblem(c, rows, x0, y0, ink):
    """Stamp a motif mask, shading only its rims so the silhouette stays legible.

    A gradient across the whole emblem turns every badge into the same
    "bright upper left, dark lower right" blob, so instead solid pixels keep a
    flat mid tone and only their edges step up (upper left) or down (lower
    right).  That is the same directional outline rule the rest of the relic
    set uses, and it keeps each monster's shape readable at 16 px.
    """
    h = len(rows)
    w = max(len(r) for r in rows)
    solid = {(rx, ry)
             for ry, row in enumerate(rows)
             for rx, ch in enumerate(row)
             if ch in "#o"}

    def is_solid(rx, ry):
        return (rx, ry) in solid

    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch == ".":
                continue
            x, y = x0 + rx, y0 + ry
            if ch == "o":
                c.set(x, y, ink["spec"])
                continue
            if ch == "x":
                c.set(x, y, ink["outline"])
                continue
            lit = not is_solid(rx, ry - 1) or not is_solid(rx - 1, ry)
            shade = not is_solid(rx, ry + 1) or not is_solid(rx + 1, ry)
            # The emblem is raised enamel on a dark plate, so it never goes
            # darker than the recessed face.  Direction still reads: near-white
            # along the upper-left silhouette, a deeper tone on the lower right.
            if lit and not shade:
                tone = "spec"
            elif shade and not lit:
                tone = "mid"
            else:
                tone = "light"
            c.set(x, y, ink[tone])


def make_badge(accent_h, accent_s, accent_v, motif, detail=2):
    """A crest plaque: metal rim, recessed face and a mob emblem.

    The plaque is a 12x12 rounded square so every badge in the set lines up.
    The rim takes the usual directional edge (quiet mid on the upper left,
    near-black lower right) and the face is recessed behind an inner edge.
    """
    rim = tones(accent_h, 0.26, 0.74)
    face = tones(accent_h, 0.22, 0.34)
    ink = gem_tones(accent_h, max(0.62, accent_s))
    c = Canvas()
    x0, y0, x1, y1 = 2, 2, 13, 13
    cx, cy = 8.0, 8.0

    def inside(x, y):
        if x < x0 or x > x1 or y < y0 or y > y1:
            return False
        qx = min(max(x, x0 + 2), x1 - 2)
        qy = min(max(y, y0 + 2), y1 - 2)
        return (x - qx) ** 2 + (y - qy) ** 2 <= 4.01

    cells = {(x, y) for y in range(N) for x in range(N) if inside(x, y)}
    border = {p for p in cells
              if any((p[0] + ox, p[1] + oy) not in cells for ox, oy in _HOOP_N4)}

    for x, y in sorted(cells):
        b = -(((x + 0.5 - cx) / 6.0) + ((y + 0.5 - cy) / 6.0)) * 0.7071
        if (x, y) in border:
            c.set(x, y, rim[_edge_tone(b)])
        elif any((x + ox, y + oy) in border for ox, oy in _HOOP_N4):
            c.set(x, y, face[_inner_edge_tone(b)])
        else:
            # a deep, flat panel so the emblem is what reads, not the plate
            c.set(x, y, face["mid"])

    # studs at the corners of the recess, lit from the usual top left
    studs = ((4, 4, "light"), (11, 4, "mid"), (4, 11, "mid"), (11, 11, "dark"))
    for rx, ry, tone in studs:
        if (rx, ry) in cells:
            c.set(rx, ry, rim[tone])

    _draw_emblem(c, motif, 4, 4, ink)

    if detail >= 3:
        draw_bead(c, 8.0, 12.0, ink, 0.7)
    return c


# --------------------------------------------------------------------------
# File -> template mapping
# --------------------------------------------------------------------------

UNIQUE_LEFT_ALONE = {
    "golden_apple.png",
    "icy_feathered_arrow.png",
    "iron_heart.png",
    "moonmilk_pearl.png",
    "travelers_eye.png",
    "wolf_fang.png",
    # Hand-painted mod icon: never regenerate.
    "cherry_cross.png",
    # Doctor's own art; leave alone.
    "arcane_reforging_charm.png",
    # Head/back relics with deliberate, distinct silhouettes.
    "astral_halo.png",
    "celestial_wings.png",
    "celestial_aegis.png",
    "gilded_circlet.png",
    "halo_celestial_crystal.png",
    # Crafting cores keep their deliberate per-rarity crystal designs.
    "common_relic_core.png",
    "uncommon_relic_core.png",
    "rare_relic_core.png",
    "epic_relic_core.png",
    "myth_relic.png",
}


def category(name):
    if name in UNIQUE_LEFT_ALONE:
        return None
    if name in BADGES or name.endswith("_badge.png"):
        return "badge"
    if "bracelet" in name:
        return "bracelet"
    if "anklet" in name:
        return "anklet"
    if "ring" in name:
        return "ring"
    if "pendant" in name:
        return "pendant"
    if "amulet" in name:
        return "amulet"
    if "core" in name or name == "myth_relic.png":
        return "core"
    if "rune" in name or "sigil" in name:
        return "rune"
    if "cross" in name:
        return "cross"
    if ("charm" in name):
        return "charm"
    return None


def tint_override(name, hue, sat, val):
    """A few relics carry a fixed, more vivid colour scheme."""
    if "crimson" in name:
        # cursed: deep, saturated, and darker than everything else
        return 0.0, 0.96, 0.52 if "_v2" in name else 0.60
    if "sigil" in name or "bleeding" in name:
        return 0.0, 0.94, 0.72 if "_v2" in name else 0.80
    return hue, sat, val


def palette_for(name, path):
    """The colour identity of an item: pinned if known, else sampled on disk."""
    if name in BADGES:
        return BADGES[name][1:]
    if name in PALETTE:
        return PALETTE[name]
    return analyze(path)


def build(name, hue, sat, val, detail=2):
    kind = category(name)
    glyph = name_hash(name)
    hue, sat, val = tint_override(name, hue, sat, val)
    if kind == "ring":
        return make_ring(hue, sat, val, detail, glyph)
    if kind == "bracelet":
        return make_bracelet(hue, sat, val, detail, glyph)
    if kind == "anklet":
        return make_anklet(hue, sat, val, detail, glyph)
    if kind == "amulet":
        return make_amulet(hue, sat, val, detail)
    if kind == "pendant":
        return make_pendant(hue, sat, val, detail)
    if kind == "core":
        return make_core(hue, sat, val, detail)
    if kind == "rune":
        bloody = "sigil" in name or "crimson" in name
        return make_rune(hue, sat, val, glyph, detail, bloody)
    if kind == "cross":
        return make_cross(hue, sat, val, detail)
    if kind == "charm":
        return make_charm(hue, sat, val, glyph, detail)
    if kind == "badge":
        motif = MOTIFS[BADGES[name][0]]
        return make_badge(hue, sat, val, motif, detail)
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, help="directory to write sprites into")
    parser.add_argument("--src", default=SRC, help="directory of existing sprites")
    parser.add_argument("--only", default="", help="only files whose name contains this")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    disk = {f for f in os.listdir(args.src) if f.endswith(".png")}
    names = sorted(disk | set(BADGES))
    if args.only:
        names = [f for f in names if args.only in f]
    rarity = load_rarity()
    done, skipped = 0, []
    for name in names:
        kind = category(name)
        if kind is None:
            skipped.append(name)
            continue
        rank = rarity.get(name) or infer_rarity(name)
        detail = DETAIL.get(rank, 2)
        hue, sat, val = palette_for(name, os.path.join(args.src, name))
        canvas = build(name, hue, sat, val, detail)
        if canvas is None:
            skipped.append(name)
            continue
        if not args.dry_run:
            write_rgba(os.path.join(args.out, name), canvas)
        done += 1
        print("%-34s %-9s %-9s hue=%.2f sat=%.2f val=%.2f"
              % (name, kind, rank, hue, sat, val))

    print("\n%d generated, %d left alone" % (done, len(skipped)))
    if skipped:
        print("left alone: " + ", ".join(skipped))


if __name__ == "__main__":
    main()