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
]

# Explore's "Best for your phone" row, in order. Bundled favourites and the live Sun scene
# sit beside the new designs; every id must exist in one of the catalogs.
PICKS = ["device_carbon_weave", "device_papaya_horizon", "extra_carbon_fold", "device_edge_glow",
         "extra_racing_line", "device_papaya_eclipse", "extra_papaya_apex", "wall_5_3",
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
