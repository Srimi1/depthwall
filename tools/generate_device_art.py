"""Original "Best for your phone" illustrations, tuned for the OnePlus 7T Pro panel.

Run with Python 3, Pillow and numpy. Output is 2160 x 4680 (the panel's 6:13
ratio at 1.5x), plus 360 x 780 thumbnails, device_catalog.json and the ordered
device_picks.json shown in Explore. Everything is drawn here from geometry and
seeded noise; there are no photographs, textures, logos or marque shapes.

Panel rules applied to every design:
- luma at or below 3 becomes true black, so unused AMOLED pixels stay off;
- the status bar band (top 3%) is black and the clock zone (top ~32%) stays dark;
- subjects sit in the 40-80% height band, which survives the 0.68 card crop;
- fine detail stays out of the curved outer 5% of each side (Edge Glow uses it on purpose);
- woven patterns repeat no finer than 36 px, so they do not shimmer under parallax.
"""
from pathlib import Path
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_extra_art import BLUE, PAPAYA, H, S, W, ellipse, line  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/wallpapers"
SOURCE = "https://github.com/Srimi1/depthwall/blob/main/tools/generate_device_art.py"
EMBER = (150, 38, 6)
IVORY = (255, 236, 214)
DEEP_BLUE = (24, 74, 160)
TOP = H * 3 // 100
UNITS_H = H / S  # design-space height; width is 1000 units

_x = np.linspace(0, 1000, W, dtype=np.float32)[None, :]
_y = np.linspace(0, UNITS_H, H, dtype=np.float32)[:, None]


def canvas():
    return np.zeros((H, W, 3), dtype=np.float32)


def add(rgb, color, weight):
    for i, c in enumerate(color):
        rgb[:, :, i] += weight * c


def smoothstep(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def to_image(rgb, seed):
    """Fade the darkest tail smoothly to black, dither gradients above luma 10 and quantise."""
    rng = np.random.default_rng(seed)
    noise = (rng.random((H, W), dtype=np.float32) - rng.random((H, W), dtype=np.float32))
    luma = rgb @ np.array([.299, .587, .114], dtype=np.float32)
    # Without this knee, the true-black clamp in finish() would leave a visible ring.
    rgb = rgb * smoothstep(1.5, 8, luma)[:, :, None]
    rgb = rgb + (noise * (luma > 10))[:, :, None]
    return Image.fromarray(np.clip(np.rint(rgb), 0, 255).astype(np.uint8))


def finish(image):
    """Final AMOLED pass shared by every design."""
    a = np.asarray(image).copy()
    luma = a @ np.array([.299, .587, .114])
    a[luma <= 3] = 0
    a[:TOP] = 0
    return Image.fromarray(a)


def stars(draw, seed, count, top, bottom, avoid=None):
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x, y = rng.uniform(70, 930), rng.uniform(top, bottom)
        if avoid and avoid(x, y):
            continue
        v = int(rng.integers(40, 150))
        size = float(rng.choice([.45, .7, 1.0]))
        ellipse(draw, (x, y, x + size, y + size), fill=(v, v * 9 // 10, v * 4 // 5))


def weave(tint, highlight):
    """Authored 2x2 twill; one cell is 36 px at full size, rendered in float."""
    pitch = 36
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = np.floor(xx / pitch), np.floor(yy / pitch)
    fx, fy = xx / pitch - cx, yy / pitch - cy
    warp = ((cx + cy) % 4) < 2
    # Each tow is brighter along its centre and darker where it dives under the next one.
    across = np.where(warp, fx, fy)
    along = np.where(warp, fy, fx)
    tow = np.cos(np.pi * (across - .5)) ** 2 * (.72 + .28 * np.sin(np.pi * along))
    shade = np.where(warp, .78, 1.0) * tow
    # A broad diagonal sheen like light across a curved carbon panel.
    diagonal = (_x * .62 + _y * .78)
    sheen = np.exp(-((diagonal - 1450) / 210) ** 2)
    rgb = canvas()
    for i in range(3):
        rgb[:, :, i] = shade * (tint[i] + sheen * (highlight[i] - tint[i]))
    return rgb


def hairline(rgb, y, color, core=1.6, glow=10, strength=1.0, x0=0, x1=1000):
    span = smoothstep(x0, x0 + 60, _x) * (1 - smoothstep(x1 - 60, x1, _x))
    d = np.abs(_y - y)
    add(rgb, color, (np.exp(-(d / core) ** 2) + .35 * np.exp(-d / glow)) * span * strength)


def papaya_horizon():
    rgb = canvas()
    radius, top = 2600.0, 1345.0
    d = np.sqrt((_x - 500) ** 2 + (_y - (top + radius)) ** 2) - radius  # > 0 above the limb
    above = d > 0
    atmosphere = np.where(above, np.exp(-np.abs(d) / 46), np.exp(-np.abs(d) / 9))
    add(rgb, PAPAYA, atmosphere * .85)
    add(rgb, IVORY, np.exp(-(d / 1.7) ** 2) * .95)
    # The night side keeps a faint ember terminator before falling to black.
    inside = np.where(above, 0, np.exp(-np.abs(d) / 140)) * smoothstep(1500, 1360, _y)
    add(rgb, EMBER, inside * .22)
    image = to_image(rgb, 101)
    stars(ImageDraw.Draw(image), 102, 46, 930, 1290)
    return image


def carbon_weave():
    rgb = weave((19, 20, 24), (58, 60, 66)) * smoothstep(745, 790, _y)[:, :, None]
    rgb *= (1 - .45 * (1 - smoothstep(0, 60, _x)) - .45 * smoothstep(940, 1000, _x))[:, :, None]
    hairline(rgb, 728, PAPAYA, strength=.95)
    return to_image(rgb, 201)


def midnight_weave():
    rgb = weave((12, 17, 30), (40, 62, 104)) * smoothstep(745, 790, _y)[:, :, None]
    rgb *= (1 - .45 * (1 - smoothstep(0, 60, _x)) - .45 * smoothstep(940, 1000, _x))[:, :, None]
    hairline(rgb, 728, (89, 167, 255), strength=.95)
    return to_image(rgb, 1101)


def edge_glow():
    rgb = canvas()
    edge = np.minimum(_x, 1000 - _x)
    vertical = smoothstep(820, 1450, _y) * (1 - .55 * smoothstep(1850, 2167, _y))
    add(rgb, PAPAYA, np.exp(-edge / 34) * vertical * .95)
    add(rgb, EMBER, np.exp(-edge / 95) * vertical * .45)
    add(rgb, IVORY, np.exp(-((edge - 9) / 2.2) ** 2) * vertical * .55)
    return to_image(rgb, 301)


def rise():
    rgb = canvas()
    node = 975.0
    dx = np.abs(_x - 500)
    column = smoothstep(node - 10, node + 30, _y)
    # The pillar brightens towards its base and narrows towards the node.
    strength = (.35 + .65 * smoothstep(node, 2167, _y)) * column
    add(rgb, PAPAYA, (np.exp(-(dx / 2.6) ** 2) * .9 + np.exp(-dx / 22) * .45) * strength)
    r = np.sqrt((_x - 500) ** 2 + (_y - node) ** 2)
    add(rgb, PAPAYA, np.exp(-r / 34) * 1.1)
    add(rgb, IVORY, np.exp(-(r / 6) ** 2))
    floor = np.exp(-((_y - 2035) / 14) ** 2) * np.exp(-(dx / 150) ** 2)
    add(rgb, IVORY, floor * .4)
    add(rgb, EMBER, np.exp(-((_y - 2100) / 80) ** 2) * np.exp(-(dx / 260) ** 2) * .5)
    return to_image(rgb, 401)


def papaya_eclipse():
    rgb = canvas()
    cy, radius = 1257.0, 150.0
    dx, dy = _x - 500, _y - cy
    r = np.sqrt(dx ** 2 + dy ** 2)
    theta = np.arctan2(dy, dx)
    outside = r > radius
    streamers = 1 + .32 * np.sin(6 * theta + .7) + .18 * np.sin(11 * theta + 2.1)
    halo = np.where(outside, np.exp(-(r - radius) / (38 * streamers)), 0)
    add(rgb, PAPAYA, halo * .9)
    add(rgb, EMBER, np.where(outside, np.exp(-(r - radius) / 120), 0) * .35)
    add(rgb, IVORY, np.exp(-((r - radius) / 2.1) ** 2) * .9)
    image = to_image(rgb, 501)
    stars(ImageDraw.Draw(image), 502, 70, 1110, 1950,
          avoid=lambda x, y: (x - 500) ** 2 + (y - cy) ** 2 < 330 ** 2)
    return image


def ninety():
    rgb = canvas()
    angle = math.radians(-18)
    along = (_x - 500) * math.cos(angle) + (_y - 1450) * math.sin(angle)
    across = -(_x - 500) * math.sin(angle) + (_y - 1450) * math.cos(angle)
    palette = [PAPAYA, (238, 104, 16), (214, 84, 12), (186, 66, 10), EMBER,
               (118, 30, 6), (90, 22, 5), (64, 16, 4), (44, 11, 3)]
    for i, color in enumerate(palette):
        offset = -330 + i * 82
        width = 11 + i * 2.2
        head = 260 - i * 34
        # Soft streaks with a bright leading edge and a long fading tail.
        tail = smoothstep(-640, head - 90, along) * (1 - smoothstep(head - 40, head + 40, along))
        add(rgb, color, np.exp(-((across - offset) / width) ** 2) * tail * (1.0 - i * .05))
    rgb *= smoothstep(860, 1000, _y)[:, :, None]
    return to_image(rgb, 601)


def carbon_chamfer():
    rgb = weave((18, 19, 23), (52, 54, 60))
    panel = Image.new("L", (W, H))
    shape = [(600, 1150), (800, 950), (1000, 950), (1000, 2167), (600, 2167)]
    ImageDraw.Draw(panel).polygon([(round(x * S), round(y * S)) for x, y in shape], fill=255)
    mask = np.asarray(panel, dtype=np.float32)[:, :, None] / 255
    rgb *= mask * (1 - .5 * smoothstep(950, 1000, _x))[:, :, None]
    # 45 degree chamfer, then an ivory pinstripe running parallel to it.
    edge = (_x + _y - 1750) / math.sqrt(2)
    on_chamfer = smoothstep(560, 600, _x) * (1 - smoothstep(800, 840, _x))
    add(rgb, PAPAYA, (np.exp(-((edge + 7) / 2.2) ** 2) + .4 * np.exp(-np.abs(edge + 7) / 12)) * on_chamfer)
    add(rgb, IVORY, np.exp(-((edge + 19) / .9) ** 2) * on_chamfer * .7)
    vertical = np.abs(_x - 593)
    down = smoothstep(1150, 1190, _y) * (1 - smoothstep(2050, 2167, _y))
    add(rgb, PAPAYA, (np.exp(-(vertical / 2.2) ** 2) + .4 * np.exp(-vertical / 12)) * down * .8)
    return to_image(rgb, 701)


def papaya_orbit():
    rgb = canvas()
    cx, cy, radius = 690.0, 1520.0, 118.0
    dx, dy = (_x - cx) / radius, (_y - cy) / radius
    rr = dx ** 2 + dy ** 2
    nz = np.sqrt(np.clip(1 - rr, 0, 1))
    light = np.clip(-.82 * dx - .35 * dy + .2 * nz, 0, 1) * (rr < 1)
    add(rgb, PAPAYA, light ** 1.4 * .95)
    add(rgb, IVORY, light ** 6 * .45)
    limb = np.sqrt(rr) - 1
    add(rgb, EMBER, np.where(limb > 0, np.exp(-limb * radius / 16), 0) * .55 * smoothstep(-.1, -.8, dx))
    image = to_image(rgb, 801)
    d = ImageDraw.Draw(image)
    stars(d, 802, 60, 900, 2000, avoid=lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 < 180 ** 2)
    a = math.radians(-24)
    for i, (rx, ry, color, width) in enumerate([(290, 62, PAPAYA, 1.6), (360, 96, (36, 74, 128), .9),
                                                (420, 126, (24, 50, 92), .8)]):
        points = []
        for t in np.linspace(0, math.tau, 720):
            x, y = rx * math.cos(t), ry * math.sin(t)
            px, py = cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)
            # The orbit passes behind the planet's disc.
            behind = math.sin(t) < 0 and (px - cx) ** 2 + (py - cy) ** 2 < radius ** 2
            points.append(None if behind or px < 55 or px > 945 else (px, py))
        run = []
        for p in points + [None]:
            if p is None:
                if len(run) > 1:
                    line(d, run, color, width)
                run = []
            else:
                run.append(p)
    return image


def fbm(seed, w, h, octaves=6, cells=5):
    """Seeded fractal noise in [0, 1] from upsampled random grids."""
    rng = np.random.default_rng(seed)
    total = np.zeros((h, w), dtype=np.float32)
    amplitude = 1.0
    for _ in range(octaves):
        grid = rng.random((cells * h // w + 2, cells + 2)).astype(np.float32)
        total += np.asarray(Image.fromarray(grid).resize((w, h), Image.Resampling.BICUBIC)) * amplitude
        amplitude *= .55
        cells *= 2
    total -= total.min()
    return total / total.max()


def upscale(small):
    return np.stack([np.asarray(Image.fromarray(np.ascontiguousarray(small[:, :, i])).resize(
        (W, H), Image.Resampling.BICUBIC)) for i in range(3)], axis=2)


def ember_nebula():
    # Built at quarter resolution and upscaled, so the gas stays soft and compresses well.
    w, h = W // 4, H // 4
    cloud = fbm(901, w, h)
    yy, xx = np.mgrid[0:h, 0:w]
    # Domain warping turns round noise blobs into drifting filaments.
    sx = np.clip(xx + (fbm(902, w, h, 4) - .5) * w * .45, 0, w - 1).astype(int)
    sy = np.clip(yy + (fbm(903, w, h, 4) - .5) * h * .22, 0, h - 1).astype(int)
    cloud = cloud[sy, sx]
    ux = np.linspace(0, 1000, w, dtype=np.float32)[None, :]
    uy = np.linspace(0, UNITS_H, h, dtype=np.float32)[:, None]
    ellipse_distance = ((ux - 500) / 430) ** 2 + ((uy - 1440) / 440) ** 2
    envelope = (1 - smoothstep(.3, 1.0, ellipse_distance)) * smoothstep(930, 1120, uy)
    density = smoothstep(.4, .86, cloud) * envelope
    small = np.zeros((h, w, 3), dtype=np.float32)
    for i in range(3):
        small[:, :, i] = density * EMBER[i] * .9 + density ** 2.2 * PAPAYA[i] * .85 + density ** 5 * IVORY[i] * .5
    image = to_image(upscale(small), 904)
    d = ImageDraw.Draw(image)
    stars(d, 905, 40, 960, 1940)
    for x, y in [(352, 1310), (640, 1486), (488, 1702)]:
        ellipse(d, (x - 2.4, y - 2.4, x + 2.4, y + 2.4), fill=IVORY)
    return image


def aero_contours():
    rgb = canvas()
    xp, yp = _x - 500, _y - 1330
    a, m, v = 120.0, 2.0, 1.0
    # Uniform upward flow plus a source/sink pair: streamlines of a closed teardrop body.
    psi = v * xp - m * 100 / (2 * math.pi) * (np.arctan2(xp, yp - a) - np.arctan2(xp, yp + a))
    spacing = 26.0
    gy, gx = np.gradient(psi, float(S) ** -1)
    grad = np.sqrt(gx ** 2 + gy ** 2) + 1e-6
    distance = np.abs(psi / spacing - np.rint(psi / spacing)) * spacing / grad  # in design units
    band = smoothstep(880, 1080, _y) * (1 - smoothstep(1580, 1760, _y))
    band = band * smoothstep(50, 140, _x) * (1 - smoothstep(860, 950, _x))
    # Inside the body the source/sink streamlines flip sign; leave the teardrop itself black.
    lines = np.exp(-(distance / .55) ** 2) * band * (xp * psi >= 0)
    near = np.exp(-np.maximum(np.abs(xp) - 70, 0) / 120)
    add(rgb, PAPAYA, lines * (.75 + .35 * near))
    add(rgb, IVORY, lines * near * .5)
    return to_image(rgb, 1001)


def ridge(seed, controls, rough, levels=8, decay=.45):
    """1D midpoint-displacement skyline through the given control heights, sampled per column."""
    rng = np.random.default_rng(seed)
    points = np.asarray(controls, dtype=np.float32)
    for _ in range(levels):
        mids = (points[:-1] + points[1:]) / 2 + rng.uniform(-1, 1, len(points) - 1).astype(np.float32) * rough
        points = np.insert(points, np.arange(1, len(points)), mids)
        rough *= decay
    return np.interp(_x[0], np.linspace(0, 1000, len(points)), points).astype(np.float32)[None, :]


def small_grid(w, h):
    return (np.linspace(0, 1000, w, dtype=np.float32)[None, :],
            np.linspace(0, UNITS_H, h, dtype=np.float32)[:, None])


def blur(small, passes=2, radius=2):
    """Separable box blur repeated into a near-Gaussian, for quarter-resolution layers."""
    for _ in range(passes):
        for axis in (0, 1):
            padded = np.pad(small, [(radius, radius) if a == axis else (0, 0) for a in range(small.ndim)], mode="edge")
            c = np.cumsum(padded, axis=axis, dtype=np.float32)
            c = np.concatenate([np.zeros_like(np.take(c, [0], axis=axis)), c], axis=axis)
            n = small.shape[axis]
            small = (np.take(c, np.arange(2 * radius + 1, n + 2 * radius + 1), axis=axis)
                     - np.take(c, np.arange(0, n), axis=axis)) / (2 * radius + 1)
    return small


def spectrum(t):
    """Visible spectrum from red (0) to violet (1) as an RGB weight triple."""
    t = np.clip(t, 0, 1)
    r = np.clip(1.6 - 3.2 * t, 0, 1) + np.clip(3.2 * t - 2.6, 0, .55)
    g = np.clip(1 - np.abs(t - .42) * 3.1, 0, 1)
    b = np.clip(2.4 * t - .9, 0, 1) * np.clip(4.2 - 3.6 * t, 0, 1)
    return r, g, b


def tinted_stars(draw, seed, count, top, bottom, tint, avoid=None):
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x, y = rng.uniform(70, 930), rng.uniform(top, bottom)
        if avoid and avoid(x, y):
            continue
        v = float(rng.uniform(.25, 1))
        size = float(rng.choice([.45, .7, 1.0]))
        ellipse(draw, (x, y, x + size, y + size), fill=tuple(int(c * v) for c in tint))


def aurora_peaks():
    rgb = canvas()
    w, h = W // 4, H // 4
    ux, uy = small_grid(w, h)
    base = 1420 + 75 * np.sin(ux / 170 + .8) + 26 * np.sin(ux / 53 + 2.0)
    rays = fbm(1201, w, 8, 5, 18)[4:5]  # one noisy row, stretched vertically into curtain rays
    rays = .45 + .75 * smoothstep(.2, .9, rays)
    height = base - uy
    curtain = np.exp(-(np.minimum(height, 0) / 26) ** 2) * np.exp(-np.maximum(height, 0) / (200 * rays))
    curtain *= smoothstep(40, 170, ux) * (1 - smoothstep(830, 960, ux)) * smoothstep(780, 980, uy)
    fold = .65 + .35 * np.sin(ux / 23 + 1.3 * np.sin(ux / 90))
    small = np.zeros((h, w, 3), dtype=np.float32)
    mix = smoothstep(30, 320, height)
    for i, (g, v) in enumerate(zip((48, 236, 150), (130, 70, 190))):
        small[:, :, i] = curtain * fold * (g * (1 - mix) + v * mix) * .9
    small[:, :, :] += (curtain ** 3 * (1 - mix))[:, :, None] * np.array([150, 255, 210], dtype=np.float32) * .35
    rgb += upscale(blur(small, 2, 1))
    far = ridge(1202, [1480, 1370, 1290, 1400, 1310, 1450], 50, 8, .47)
    near = ridge(1203, [1620, 1540, 1470, 1560, 1500, 1590], 40, 8, .47)
    below_far, below_near = smoothstep(-1, 1, _y - far), smoothstep(-1, 1, _y - near)
    rgb *= (1 - below_far)[:, :, None]
    # The aurora lights the snow on the far crest; the near range stays a silhouette.
    add(rgb, (20, 56, 48), below_far * (1 - below_near) * np.exp(-(_y - far) / 60))
    add(rgb, (5, 10, 11), below_far * (1 - below_near))
    add(rgb, (90, 230, 170), np.exp(-((_y - far) / 1.3) ** 2) * (1 - below_near) * .45)
    rgb *= (1 - below_near)[:, :, None]
    add(rgb, (110, 220, 180), np.exp(-((_y - near) / 1.1) ** 2) * .12)
    image = to_image(rgb, 1204)
    tinted_stars(ImageDraw.Draw(image), 1205, 80, 780, 1480, (200, 225, 255),
                 avoid=lambda x, y: y > far[0, min(W - 1, int(x * S))] - 4)
    return image


def papaya_dunes():
    rgb = canvas()
    sun = np.sqrt((_x - 580) ** 2 + (_y - 1120) ** 2)
    add(rgb, PAPAYA, np.exp(-(sun / 125) ** 2) * .5 + np.exp(-sun / 45) * .35)
    add(rgb, IVORY, np.exp(-(sun / 30) ** 2) * .95)
    add(rgb, EMBER, np.exp(-((_y - 1150) / 45) ** 2) * np.exp(-((_x - 580) / 380) ** 2) * .45)
    for k in range(7):
        # Spacing, swell and wavelength grow towards the viewer for a sense of depth.
        crest = (1135 + 62 * k + 9 * k * k + (16 + 9 * k) * np.sin(_x / (120 + 40 * k) + k * 1.7)
                 + (6 + 2 * k) * np.sin(_x / (47 + 9 * k) + k * 2.3))
        depth = k / 6
        inside = smoothstep(-1.2, 1.2, _y - crest)
        rgb *= (1 - inside)[:, :, None]
        # Each crest is rim lit from the low sun; nearer dunes fall darker.
        lit = np.exp(-(_y - crest).clip(0) / (40 + 30 * depth)) * inside
        body = (1 - depth * .85)
        add(rgb, (190, 70, 12), lit * body * .7)
        add(rgb, (40, 10, 3), inside * body * .5 * np.exp(-(_y - crest).clip(0) / 220))
        add(rgb, IVORY, np.exp(-((_y - crest) / 1.4) ** 2) * body * .55)
    rgb *= (1 - smoothstep(1700, 2000, _y))[:, :, None]
    return to_image(rgb, 1301)


def earthrise():
    rgb = canvas()
    radius, top = 1700.0, 1580.0
    d = np.sqrt((_x - 400) ** 2 + (_y - (top + radius)) ** 2) - radius  # < 0 on the lunar surface
    w, h = W // 4, H // 4
    maria = upscale(np.repeat(fbm(1401, w, h, 6, 9)[:, :, None], 3, axis=2))[:, :, 0]
    surface = smoothstep(1.5, -1.5, d) * np.exp(np.minimum(d, 0) / 150) * (.35 + .8 * smoothstep(.2, .8, maria))
    # Sunlight grazes from the left, so the far right of the limb sinks into night.
    for i, c in enumerate((112, 110, 106)):
        rgb[:, :, i] += surface * c * smoothstep(980, 80, _x) ** 1.4
    add(rgb, (225, 222, 215), np.exp(-(d / 1.3) ** 2) * smoothstep(820, 150, _x) * .55)
    rng = np.random.default_rng(1405)
    for _ in range(46):
        x0, below = rng.uniform(90, 860), rng.uniform(15, 400) ** 1.0
        y0 = top + radius - math.sqrt(radius ** 2 - (x0 - 400) ** 2) + below
        size = rng.uniform(5, 22) * (.45 + below / 300)
        squash = .28 + .45 * below / 400  # craters near the limb are seen almost edge on
        r0, r1 = int((y0 - size * squash * 1.6) * S), int((y0 + size * squash * 1.6) * S) + 1
        c0, c1 = int((x0 - size * 1.6) * S), int((x0 + size * 1.6) * S) + 1
        cdx = (_x[:, c0:c1] - x0) / size
        cdy = (_y[r0:r1] - y0) / (size * squash)
        cr = np.sqrt(cdx ** 2 + cdy ** 2)
        # The inner wall facing the sun is lit, the near wall throws a shadow, the outer rim catches light.
        shade = 1 + (cr < 1) * .55 * cdx - np.exp(-((cr - 1) / .14) ** 2) * .45 * cdx
        rgb[r0:r1, c0:c1] *= np.clip(shade, .15, 1.8)[:, :, None]
    cx, cy, r = 640.0, 1150.0, 88.0
    dx, dy = (_x - cx) / r, (_y - cy) / r
    rr = dx ** 2 + dy ** 2
    nz = np.sqrt(np.clip(1 - rr, 0, 1))
    light = np.clip(-.86 * dx - .25 * dy + .45 * nz, 0, 1) * smoothstep(1.0, .98, rr)
    # Land and cloud maps wrap onto the sphere through latitude and longitude.
    r0, r1, c0, c1 = int((cy - r) * S), int((cy + r) * S) + 1, int((cx - r) * S), int((cx + r) * S) + 1
    ldx, ldy = np.clip(dx[:, c0:c1], -1, 1), np.clip(dy[r0:r1], -1, 1)
    lat = np.arcsin(ldy)
    lon = np.arcsin(np.clip(ldx / np.sqrt(np.clip(1 - ldy ** 2, 1e-6, 1)), -1, 1))
    u = ((lon / math.pi + .5) * 511).astype(int)
    v = ((lat / math.pi + .5) * 255).astype(int)
    clouds = smoothstep(.52, .75, fbm(1402, 512, 256, 6, 10)[v, u])
    land = smoothstep(.6, .66, fbm(1406, 512, 256, 5, 4)[v, u]) * (1 - clouds)
    lit = light[r0:r1, c0:c1] ** .8
    for i, (ocean, ground, cloud) in enumerate(zip((14, 54, 150), (74, 98, 54), (240, 244, 250))):
        rgb[r0:r1, c0:c1, i] += lit * (ocean * (1 - clouds - land) + ground * land + cloud * clouds)
    limb = np.sqrt(rr) - 1
    add(rgb, (70, 140, 255), np.where(limb > 0, np.exp(-limb * r / 5), 0) * smoothstep(.2, -.6, dx) * .8)
    image = to_image(rgb, 1403)
    tinted_stars(ImageDraw.Draw(image), 1404, 60, 820, 1500, (220, 228, 255),
                 avoid=lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 < 110 ** 2)
    return image


def papaya_lattice():
    p = 62.0
    s3 = math.sqrt(3)
    t = _y / (p * s3 / 2)
    s = _x / p - t / 2
    i, j = np.floor(s), np.floor(t)
    upper = (s - i) + (t - j) > 1
    # Barycentric distance to the nearest triangle edge, in design units.
    fs, ft = s - i, t - j
    edge = np.where(upper, np.minimum(np.minimum(1 - fs, 1 - ft), fs + ft - 1),
                    np.minimum(np.minimum(fs, ft), 1 - fs - ft)) * p * s3 / 2
    cell = (i * 73856093 + j * 19349663 + upper * 83492791) % 1000 / 1000
    cx = (i + .5 * j + np.where(upper, 2 / 3, 1 / 3) + np.where(upper, 1 / 3, 1 / 6)) * p
    cy = (j + np.where(upper, 2 / 3, 1 / 3)) * p * s3 / 2
    band = np.exp(-(((cx * .55 - cy * .83) + 820) / 150) ** 2)
    papaya = (cell < band * 1.1) * band
    facet = smoothstep(1.2, 3.0, edge)
    shade = .55 + .45 * cell
    rgb = canvas()
    for k, (carbon, warm) in enumerate(zip((12, 13, 15), PAPAYA)):
        rgb[:, :, k] = facet * (carbon * shade * (1 - papaya) + warm * (.55 + .45 * cell) * papaya)
    add(rgb, (70, 72, 80), np.exp(-(edge / .7) ** 2) * (1 - papaya) * .3)
    add(rgb, IVORY, np.exp(-(edge / .7) ** 2) * papaya * .55)
    window = smoothstep(800, 980, _y) * (1 - smoothstep(1800, 2000, _y)) * smoothstep(30, 90, _x)
    window = window * (1 - smoothstep(910, 970, _x)) * np.exp(-(((_x * .55 - _y * .83) + 820) / 380) ** 2)
    return to_image(rgb * window[:, :, None], 1501)


def nebula_ribbon():
    w, h = W // 4, H // 4
    ux, uy = small_grid(w, h)
    centre = 500 + 190 * np.sin((uy - 900) / 250) + 40 * np.sin(uy / 90)
    warp = (fbm(1601, w, h, 4, 6) - .5) * 160
    across = ux - centre + warp
    width = 105 + 55 * np.sin(uy / 170 + 1)
    core = np.exp(-(across / width) ** 2)
    cloud = fbm(1602, w, h, 6, 7)
    density = core * smoothstep(.2, .8, cloud) * smoothstep(860, 1060, uy) * (1 - smoothstep(1750, 1950, uy))
    hue = smoothstep(-80, 80, across)
    small = np.zeros((h, w, 3), dtype=np.float32)
    for i, (a, b, hot) in enumerate(zip((210, 40, 150), (80, 60, 220), (255, 214, 240))):
        small[:, :, i] = density * (a * (1 - hue) + b * hue) * .85 + density ** 3 * hot * .55
    small = blur(small, 1, 1)
    image = to_image(upscale(small), 1603)
    d = ImageDraw.Draw(image)
    tinted_stars(d, 1604, 80, 860, 1950, (230, 220, 255))
    for x, y in [(430, 1180), (585, 1420), (520, 1660)]:
        ellipse(d, (x - 2, y - 2, x + 2, y + 2), fill=(255, 240, 250))
    return image


def star_spiral():
    w, h = W // 2, H // 2
    rng = np.random.default_rng(1701)
    n = 260000
    arm = rng.integers(0, 2, n)
    r = rng.gamma(2.2, 84, n)
    theta = np.log(1 + r / 30) * 2.4 + arm * math.pi + rng.normal(0, .26, n) * (1 + 60 / (r + 20))
    gx, gy = r * np.cos(theta), r * np.sin(theta)
    tilt, spin = .42, math.radians(-28)
    gy = gy * tilt
    px = 500 + gx * math.cos(spin) - gy * math.sin(spin)
    py = 1330 + gx * math.sin(spin) + gy * math.cos(spin)
    col = np.clip(px / 1000 * w, 0, w - 1).astype(int)
    row = np.clip(py / UNITS_H * h, 0, h - 1).astype(int)
    keep = (px > 60) & (px < 940) & (py > 820) & (py < 1900)
    density = np.zeros((h, w), dtype=np.float32)
    np.add.at(density, (row[keep], col[keep]), 1)
    density = blur(density, 2, 1)
    ux, uy = small_grid(w, h)
    lx, ly = ux - 500, uy - 1330
    ex = lx * math.cos(spin) + ly * math.sin(spin)
    ey = (-lx * math.sin(spin) + ly * math.cos(spin)) / tilt
    core = np.exp(-np.sqrt(ex ** 2 + ey ** 2) / 34)
    glow = 1 - np.exp(-density * .9)
    small = np.zeros((h, w, 3), dtype=np.float32)
    warm = np.exp(-np.sqrt(ex ** 2 + ey ** 2) / 110)
    for i, (cool, gold) in enumerate(zip((120, 160, 255), (255, 214, 160))):
        small[:, :, i] = glow * (cool * (1 - warm) + gold * warm) * .85 + core * gold * .95
    image = to_image(upscale(small), 1702)
    tinted_stars(ImageDraw.Draw(image), 1703, 90, 820, 1950, (225, 230, 255))
    return image


def prism_light():
    rgb = canvas()
    cx, cy, hw, hh = 470.0, 1290.0, 120.0, 250.0
    inside = np.abs(_x - cx) / hw + np.abs(_y - cy) / hh
    glass = smoothstep(1.0, .985, inside)
    add(rgb, (40, 46, 60), glass * (.35 + .65 * smoothstep(cy + hh, cy - hh, _y)) * .9)
    rim = np.exp(-(((inside - 1) * hw) / 1.0) ** 2)
    add(rgb, IVORY, rim * (.35 + .5 * smoothstep(cx + hw, cx - hw, _x)) * .8)
    bx, by = cx - hw * .5, cy + hh * .5
    ex, ey = cx + hw * .5, cy - hh * .5

    def segment(ax, ay, bx, by):
        vx, vy = bx - ax, by - ay
        length = math.hypot(vx, vy)
        along = ((_x - ax) * vx + (_y - ay) * vy) / length
        return along, ((_x - ax) * vy - (_y - ay) * vx) / length, length

    # A narrow white beam enters the lower-left facet and crosses the glass, already softening.
    along, across, length = segment(60.0, 1620.0, bx, by)
    span = smoothstep(0, 160, along) * smoothstep(length + 1, length - 1, along)
    add(rgb, (255, 250, 240), (np.exp(-(across / 2.4) ** 2) * .95 + np.exp(-np.abs(across) / 14) * .12) * span)
    along, across, length = segment(bx, by, ex, ey)
    span = smoothstep(-1, 1, along) * smoothstep(length + 1, length - 1, along)
    add(rgb, (235, 235, 250), np.exp(-(across / (2.4 + along / length * 5)) ** 2) * span * .55)
    for px, py in ((bx, by), (ex, ey)):
        add(rgb, (255, 250, 240), np.exp(-np.sqrt((_x - px) ** 2 + (_y - py) ** 2) / 9) * .5)
    # It leaves the upper-right facet split into a widening fan.
    phi = np.arctan2(_y - ey, _x - ex)
    dist = np.sqrt((_x - ex) ** 2 + (_y - ey) ** 2)
    lo, hi = math.radians(-8), math.radians(34)
    t = (phi - lo) / (hi - lo)
    fan = smoothstep(-.02, .06, t) * smoothstep(1.02, .94, t) * smoothstep(0, 40, dist) * np.exp(-dist / 900)
    fan *= (inside > 1) * (1 - smoothstep(760, 930, _x))
    r, g, b = spectrum(t)
    rgb[:, :, 0] += fan * r * 250
    rgb[:, :, 1] += fan * g * 230
    rgb[:, :, 2] += fan * b * 255
    return to_image(rgb, 1801)


def contour_lines():
    w, h = W // 4, H // 4
    height = blur(fbm(1901, w, h, 3, 3), 2, 3)
    height = (height - height.min()) / (height.max() - height.min())
    height = upscale(np.repeat(height[:, :, None], 3, axis=2))[:, :, 0]
    levels = 24.0
    v = height * levels
    gy, gx = np.gradient(v, float(S) ** -1)
    grad = np.sqrt(gx ** 2 + gy ** 2) + 1e-4
    distance = np.abs(v - np.rint(v)) / grad
    major = (np.rint(v) % 5) == 0
    lines = np.exp(-(distance / np.where(major, .8, .5)) ** 2) * np.where(major, 1.0, .5)
    envelope = 1 - smoothstep(.45, 1.0, ((_x - 500) / 470) ** 2 + ((_y - 1360) / 560) ** 2)
    envelope *= smoothstep(800, 980, _y) * smoothstep(40, 110, _x) * (1 - smoothstep(890, 960, _x))
    rgb = canvas()
    add(rgb, (232, 178, 92), lines * envelope * (.45 + .55 * height))
    add(rgb, IVORY, lines * envelope * major * height ** 3 * .4)
    return to_image(rgb, 1902)


def papaya_ring():
    rgb = canvas()
    cx, cy, radius = 500.0, 1300.0, 220.0
    dx, dy = _x - cx, _y - cy
    r = np.sqrt(dx ** 2 + dy ** 2)
    theta = np.arctan2(dy, dx)
    sweep = .18 + .82 * (.5 + .5 * np.cos(theta - math.radians(135))) ** 1.6
    d = np.abs(r - radius)
    add(rgb, PAPAYA, (np.exp(-(d / 2.2) ** 2) + .45 * np.exp(-d / 16)) * sweep)
    add(rgb, EMBER, np.exp(-d / 70) * sweep * .3)
    add(rgb, IVORY, np.exp(-(d / 1.0) ** 2) * sweep ** 3 * .6)
    return to_image(rgb, 2001)


def alpine_lake():
    rgb = canvas()
    shore = 1460.0
    mx, my = 655.0, 1040.0
    add(rgb, (16, 32, 72), np.exp(-((_x - mx) / 560) ** 2 - ((_y - shore) / 300) ** 2))
    moon = np.sqrt((_x - mx) ** 2 + (_y - my) ** 2)
    add(rgb, (236, 240, 255), smoothstep(30, 28, moon))
    add(rgb, (120, 150, 220), np.exp(-(moon / 105) ** 2) * .55 + np.exp(-moon / 30) * .3)
    # Three ranges recede into the haze; the far one catches the moon along its crest.
    far = ridge(2101, [1420, 1290, 1340, 1190, 1310, 1400], 60, 9, .47)
    mid = ridge(2106, [1400, 1380, 1330, 1420, 1360, 1440], 40, 9, .47)
    near = ridge(2102, [1462, 1440, 1400, 1445, 1425], 25, 9, .5)
    for edge, tone in ((far, (24, 36, 64)), (mid, (13, 20, 38)), (near, (7, 10, 18))):
        below = smoothstep(-1, 1, _y - edge)
        rgb *= (1 - below)[:, :, None]
        add(rgb, tone, below * (.7 + .3 * np.exp(-(_y - edge).clip(0) / 80)))
    add(rgb, (170, 190, 235), np.exp(-((_y - far) / 1.2) ** 2) * np.exp(-np.abs(_x - mx) / 260)
        * (1 - smoothstep(-1, 1, _y - mid)) * .7)
    rgb[np.broadcast_to(_y >= shore, (H, W))] = 0
    image = to_image(rgb, 2103)
    tinted_stars(ImageDraw.Draw(image), 2104, 90, 790, 1380, (220, 228, 255),
                 avoid=lambda x, y: y > far[0, min(W - 1, int(x * S))] - 4 or (x - mx) ** 2 + (y - my) ** 2 < 60 ** 2)
    sky = np.asarray(image, dtype=np.float32)
    # The lake mirrors the sky with ripples that widen towards the viewer.
    rows = np.arange(H)
    yrow = rows / S
    depth = np.clip(yrow - shore, 0, None)
    mirror = np.clip(np.rint((2 * shore - yrow) * S), 0, H - 1).astype(int)
    shift = (np.sin(yrow * 1.7) * .6 + np.sin(yrow * .53 + 1) * .9) * (.2 + depth / 40) * S
    cols = np.clip(np.arange(W)[None, :] + np.rint(shift)[:, None], 0, W - 1).astype(int)
    reflected = sky[mirror[:, None], cols] * (.55 * np.exp(-depth / 380))[:, None, None]
    out = np.where((yrow >= shore)[:, None, None], reflected, sky)
    path = np.exp(-((_x - mx) / (14 + depth[:, None] * .12)) ** 2) * (_y > shore)
    glints = smoothstep(.55, .9, .5 + .5 * np.sin(_y * 2.3 + np.sin(_x / 9) * 2)) * np.exp(-depth[:, None] / 300)
    add(out, (190, 205, 240), path * glints * .55)
    waterline = np.exp(-((_y - shore) / .9) ** 2) * smoothstep(60, 160, _x) * (1 - smoothstep(840, 940, _x))
    add(out, (150, 170, 220), waterline * .25)
    return to_image(out, 2105)


def comet():
    rgb = canvas()
    hx, hy = 650.0, 1060.0
    a = math.radians(122)
    u = (_x - hx) * math.cos(a) + (_y - hy) * math.sin(a)
    v = -(_x - hx) * math.sin(a) + (_y - hy) * math.cos(a)
    behind = smoothstep(-6, 20, u)
    # Dust tail bends away, the ion tail stays straight and blue.
    dv = v + .00055 * u ** 2
    dust = np.exp(-(dv / (5 + .11 * u.clip(0))) ** 2) * np.exp(-u.clip(0) / 520) * behind
    add(rgb, (255, 226, 190), dust * .75)
    streamers = .7 + .3 * np.sin(v / 3.1 + 1.0) * np.sin(v / 7.3)
    ion = np.exp(-((v + 4) / (2.5 + .035 * u.clip(0))) ** 2) * np.exp(-u.clip(0) / 800) * behind * streamers
    add(rgb, (90, 160, 255), ion * .9)
    r = np.sqrt((_x - hx) ** 2 + (_y - hy) ** 2)
    add(rgb, (170, 220, 255), np.exp(-r / 16) * .9)
    add(rgb, (255, 255, 255), np.exp(-(r / 3.4) ** 2))
    rgb *= (smoothstep(30, 120, _x) * (1 - smoothstep(880, 970, _x)) * (1 - smoothstep(1800, 1950, _y)))[:, :, None]
    image = to_image(rgb, 2201)
    tinted_stars(ImageDraw.Draw(image), 2202, 110, 820, 1960, (215, 225, 255))
    return image


def abyssal_bloom():
    rgb = canvas()
    cx, top, bw, bh = 500.0, 1080.0, 150.0, 120.0
    dx, dy = (_x - cx) / bw, (_y - (top + bh)) / bh
    rr = np.sqrt(dx ** 2 + dy ** 2)
    dome = dy <= 0
    bell = smoothstep(1.02, .96, rr) * dome
    rim = np.exp(-((rr - 1) * bh / 2.2) ** 2) * (dy <= .02)
    angle = np.arctan2(dx, -dy)
    ribs = np.exp(-((np.abs(np.sin(angle * 4.5)) * rr * bh) / 2.0) ** 2) * bell
    add(rgb, (40, 200, 230), bell * (.15 + .25 * rr ** 2))
    add(rgb, (120, 240, 255), rim * .9 + ribs * .45)
    hem = np.exp(-((_y - (top + bh)) / 3) ** 2) * smoothstep(1.02, .95, np.abs(dx))
    add(rgb, (170, 250, 255), hem * .8)
    for k, x0 in enumerate(np.linspace(cx - bw * .85, cx + bw * .85, 9)):
        lam, phase, amp = 70 + 9 * k, k * 1.3, 10 + 3 * (k % 3)
        hang = (_y - (top + bh)).clip(0)
        centre = x0 + amp * np.sin(_y / lam + phase) * smoothstep(0, 120, hang) + (x0 - cx) * hang / 900
        slope = amp / lam
        d = np.abs(_x - centre) / math.sqrt(1 + slope ** 2)
        length = 520 + 140 * math.sin(k * 2.1)
        fade = smoothstep(-2, 6, hang) * np.exp(-hang / length) * (_y > top + bh - 2)
        add(rgb, (60, 210, 240), (np.exp(-(d / 1.1) ** 2) + .25 * np.exp(-d / 8)) * fade * .8)
    add(rgb, (10, 60, 90), np.exp(-((_x - cx) ** 2 + (_y - top - 160) ** 2) / 330 ** 2) * .5)
    image = to_image(rgb, 2301)
    d = ImageDraw.Draw(image)
    rng = np.random.default_rng(2302)
    for _ in range(170):
        x, y = rng.uniform(70, 930), rng.uniform(860, 1950)
        size = float(rng.choice([.5, .8, 1.2, 1.8]))
        v = float(rng.uniform(.2, 1))
        ellipse(d, (x, y, x + size, y + size), fill=(int(40 * v), int(220 * v), int(230 * v)))
    return image


ENTRIES = [
    ("device_carbon_weave", "Carbon Weave", "Papaya Edition", "Orange", carbon_weave),
    ("device_papaya_horizon", "Papaya Horizon", "Papaya Edition", "Orange", papaya_horizon),
    ("device_edge_glow", "Edge Glow", "Papaya Edition", "Orange", edge_glow),
    ("device_papaya_eclipse", "Papaya Eclipse", "Space", "Orange", papaya_eclipse),
    ("device_rise", "Rise", "Papaya Edition", "Orange", rise),
    ("device_ninety", "Ninety", "Papaya Edition", "Orange", ninety),
    ("device_carbon_chamfer", "Carbon Chamfer", "Papaya Edition", "Orange", carbon_chamfer),
    ("device_papaya_orbit", "Papaya Orbit", "Space", "Orange", papaya_orbit),
    ("device_ember_nebula", "Ember Nebula", "Space", "Orange", ember_nebula),
    ("device_aero_contours", "Aero Contours", "Papaya Edition", "Orange", aero_contours),
    ("device_midnight_weave", "Midnight Weave", "Papaya Edition", "Blue", midnight_weave),
    ("device_aurora_peaks", "Aurora Peaks", "Nature", "Green", aurora_peaks),
    ("device_papaya_dunes", "Papaya Dunes", "Papaya Edition", "Orange", papaya_dunes),
    ("device_earthrise", "Earthrise", "Space", "Blue", earthrise),
    ("device_papaya_lattice", "Papaya Lattice", "Geometry", "Orange", papaya_lattice),
    ("device_nebula_ribbon", "Nebula Ribbon", "Space", "Purple", nebula_ribbon),
    ("device_star_spiral", "Star Spiral", "Space", "White", star_spiral),
    ("device_prism_light", "Prism Light", "Abstract", "White", prism_light),
    ("device_contour_lines", "Contour Lines", "Abstract", "Gold", contour_lines),
    ("device_papaya_ring", "Papaya Ring", "Minimal", "Orange", papaya_ring),
    ("device_alpine_lake", "Alpine Lake", "Nature", "Blue", alpine_lake),
    ("device_comet", "Comet", "Space", "Blue", comet),
    ("device_abyssal_bloom", "Abyssal Bloom", "Nature", "Blue", abyssal_bloom),
]

# Explore's "Best for your phone" row, in order. Bundled favourites and the live Sun scene
# sit beside the new designs; every id must exist in one of the catalogs.
PICKS = ["device_carbon_weave", "device_papaya_horizon", "device_aurora_peaks", "extra_carbon_fold",
         "device_edge_glow", "extra_racing_line", "device_papaya_eclipse", "extra_papaya_apex", "wall_5_3",
         "device_ninety", "scene_sun", "wall_5_6"]


def render(create):
    return finish(create())


def main(out=OUT, only=None):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    catalog = []
    for ident, title, category, color, create in ENTRIES:
        if only and ident not in only:
            continue
        image = render(create)
        image.save(out / f"{ident}.webp", quality=94, method=6)
        image.resize((360, 780), Image.Resampling.LANCZOS).save(
            out / f"{ident}_thumb.webp", quality=88, method=6)
        catalog.append(dict(id=ident, title=title, category=category, color=color,
                            kind="STATIC", asset=f"wallpapers/{ident}.webp",
                            thumbnail=f"wallpapers/{ident}_thumb.webp",
                            author="DepthWall", license="Original artwork",
                            sourceUrl=SOURCE, width=W, height=H))
        print(f"Generated {title}: {W} x {H}, {(out / f'{ident}.webp').stat().st_size // 1024} KB")
    if only:
        return catalog
    (out.parent / "device_catalog.json").write_text(json.dumps(catalog, indent=2) + "\n")
    (out.parent / "device_picks.json").write_text(json.dumps(PICKS, indent=2) + "\n")
    # A review sheet belongs to build artifacts, never the shipped catalog.
    artifacts = ROOT / "artifacts"
    artifacts.mkdir(exist_ok=True)
    columns = 6
    rows = math.ceil(len(catalog) / columns)
    sheet = Image.new("RGB", (columns * 240, rows * 550), (12, 12, 12))
    d = ImageDraw.Draw(sheet)
    for i, item in enumerate(catalog):
        x, y = i % columns * 240, i // columns * 550
        sheet.paste(Image.open(out / f"{item['id']}_thumb.webp").resize((240, 520)), (x, y))
        d.text((x + 10, y + 531), item["title"], fill=(235, 232, 227))
    sheet.save(artifacts / "device-contact-sheet.jpg", quality=92)
    return catalog


if __name__ == "__main__":
    main(only=set(sys.argv[1:]) or None)
