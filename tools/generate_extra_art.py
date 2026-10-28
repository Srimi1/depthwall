"""Five original, logo-free 4K portrait illustrations for DepthWall.

Run with Python 3, Pillow and numpy. Output is 2160 x 4680 (the OnePlus
7T Pro's 6:13 aspect ratio), plus 360 x 780 thumbnails. All shapes are
drawn at output resolution; these are illustrations, not photographs.
"""
from pathlib import Path
import json
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/wallpapers"
W, H = 2160, 4680
S = W / 1000
PAPAYA = (255, 130, 24)
BLUE = (89, 167, 255)


def point(p):
    return tuple(round(v * S) for v in p)


def line(draw, points, color, width=1):
    draw.line([point(p) for p in points], fill=color,
              width=max(1, round(width * S)), joint="curve")


def polygon(draw, points, color):
    draw.polygon([point(p) for p in points], fill=color)


def ellipse(draw, box, fill=None, outline=None, width=1):
    draw.ellipse(tuple(round(v * S) for v in box), fill=fill, outline=outline,
                 width=max(1, round(width * S)))


def bezier(start, *segments):
    """Sample connected cubic Beziers in design-space coordinates."""
    points = [start]
    for a, b, end in segments:
        for t in np.linspace(0, 1, 40)[1:]:
            p = tuple((1-t)**3*start[k] + 3*(1-t)**2*t*a[k]
                      + 3*(1-t)*t*t*b[k] + t**3*end[k] for k in (0, 1))
            points.append(p)
        start = end
    return points


def base(color, cx=.5, cy=.65, amount=.12):
    # Analytic atmospheric glow; the upper third remains near AMOLED black.
    x = np.linspace(0, 1, W, dtype=np.float32)[None, :]
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    glow = np.exp(-((x-cx)/.42)**2 - ((y-cy)/.16)**2) * amount
    rgb = np.empty((H, W, 3), dtype=np.uint8)
    for i, c in enumerate(color):
        rgb[:, :, i] = np.clip(glow*c, 0, 255)
    return Image.fromarray(rgb)


def glowing_lines(image, paths, color, width=2, radius=14):
    glow = Image.new("RGB", image.size)
    d = ImageDraw.Draw(glow)
    for path in paths:
        line(d, path, color, width)
    glow = glow.filter(ImageFilter.GaussianBlur(radius*S))
    image = Image.fromarray(np.minimum(
        np.asarray(image).astype(np.uint16) + np.asarray(glow), 255).astype(np.uint8))
    d = ImageDraw.Draw(image)
    for path in paths:
        line(d, path, color, width)
    return image


def side_car():
    im = base(PAPAYA, cy=.665, amount=.16)
    d = ImageDraw.Draw(im)
    # A single thin horizon and two reflection traces anchor the silhouette.
    line(d, [(65, 1575), (935, 1575)], (43, 25, 14), 1)
    for y, shade in [(1621, 26), (1648, 12)]:
        line(d, [(190, y), (810, y)], (shade, shade//3, 4), 2)
    outline = bezier((91, 1450), ((114, 1415), (176, 1403), (234, 1388)),
                     ((320, 1316), (368, 1282), (461, 1282)),
                     ((542, 1275), (589, 1328), (656, 1356)),
                     ((744, 1363), (851, 1387), (909, 1434)),
                     ((936, 1453), (929, 1497), (890, 1515)),
                     ((656, 1529), (355, 1523), (135, 1514)),
                     ((110, 1503), (89, 1470), (91, 1450)))
    polygon(d, outline, (25, 16, 11))
    lower = bezier((112, 1468), ((317, 1437), (567, 1403), (900, 1445)))
    line(d, lower, (86, 43, 16), 4)
    # Sloping glasshouse and open side intake, without marque-specific details.
    glass = bezier((280, 1381), ((347, 1318), (388, 1303), (462, 1302)),
                   ((515, 1297), (564, 1335), (605, 1358)),
                   ((524, 1368), (388, 1377), (280, 1381)))
    polygon(d, glass, (3, 7, 10))
    line(d, [(452, 1306), (491, 1366)], (99, 53, 25), 3)
    polygon(d, [(574, 1413), (699, 1398), (662, 1482), (546, 1492)], (0, 0, 0))
    line(d, [(567, 1415), (693, 1400), (659, 1474)], (164, 75, 17), 2)
    line(d, [(492, 1378), (513, 1402), (493, 1489), (341, 1493)], (79, 40, 15), 2)
    line(d, [(110, 1434), (157, 1424)], (224, 51, 11), 5)
    line(d, [(830, 1410), (895, 1436)], (243, 225, 197), 4)
    line(d, [(769, 1488), (900, 1481)], (1, 1, 2), 10)
    # Wheels cut through the body; individual spokes are visible at full size.
    for x in (269, 756):
        ellipse(d, (x-74, 1435, x+74, 1583), fill=(0, 0, 0))
        ellipse(d, (x-57, 1452, x+57, 1566), fill=(8, 9, 10), outline=(75, 65, 50), width=2)
        ellipse(d, (x-50, 1459, x+50, 1559), outline=(171, 94, 34), width=2)
        for a in range(0, 360, 45):
            r = math.radians(a)
            line(d, [(x+12*math.cos(r), 1509+12*math.sin(r)),
                     (x+45*math.cos(r+.16), 1509+45*math.sin(r+.16))], (111, 115, 118), 3)
        ellipse(d, (x-12, 1497, x+12, 1521), fill=(20, 22, 24), outline=(121, 126, 127), width=2)
    upper = outline[:157]
    im = glowing_lines(im, [upper], PAPAYA, width=2, radius=8)
    return im


def front_car():
    im = base(BLUE, cy=.66, amount=.13)
    d = ImageDraw.Draw(im)
    # Swept front elevation, deliberately a generic concept car.
    body = bezier((146, 1480), ((130, 1425), (148, 1394), (219, 1387)),
                  ((287, 1300), (326, 1274), (500, 1275)),
                  ((674, 1274), (713, 1300), (781, 1387)),
                  ((852, 1394), (870, 1425), (854, 1480)),
                  ((866, 1536), (825, 1574), (770, 1580)),
                  ((650, 1595), (350, 1595), (230, 1580)),
                  ((175, 1574), (134, 1536), (146, 1480)))
    polygon(d, body, (11, 17, 24))
    polygon(d, [(295, 1376), (336, 1307), (413, 1293), (587, 1293),
                (664, 1307), (705, 1376)], (1, 4, 8))
    # Bonnet contours converge towards the nose.
    for side in (1, -1):
        line(d, [(500+side*159, 1381), (500+side*104, 1460), (500+side*80, 1506)], (31, 47, 62), 3)
        polygon(d, [(500+side*209, 1512), (500+side*298, 1502),
                    (500+side*288, 1545), (500+side*196, 1555)], (0, 0, 0))
        line(d, [(500+side*194, 1563), (500+side*282, 1557)], (41, 87, 127), 2)
    polygon(d, [(370, 1519), (630, 1519), (605, 1560), (395, 1560)], (0, 0, 0))
    line(d, [(239, 1583), (761, 1583)], (27, 45, 61), 3)
    headlights = [[(195, 1437), (256, 1448), (331, 1461)],
                  [(805, 1437), (744, 1448), (669, 1461)]]
    im = glowing_lines(im, headlights, (162, 212, 255), 4, 12)
    im = glowing_lines(im, [[(184, 1475), (209, 1505)], [(816, 1475), (791, 1505)]], BLUE, 2, 7)
    d = ImageDraw.Draw(im)
    # Soft horizontal ground reflections keep the OLED background mostly dark.
    for y, half, value in [(1620, 330, 20), (1660, 260, 12), (1681, 180, 7)]:
        line(d, [(500-half, y), (500+half, y)], (value//3, value*2//3, value), 2)
    return im


def racing_line():
    im = base(PAPAYA, cx=.72, cy=.72, amount=.055)
    d = ImageDraw.Draw(im)
    polygon(d, [(608, 860), (757, 860), (180, 2167), (31, 2167)], (182, 72, 10))
    polygon(d, [(630, 860), (757, 860), (180, 2167), (53, 2167)], PAPAYA)
    polygon(d, [(796, 860), (811, 860), (234, 2167), (219, 2167)], (215, 196, 172))
    polygon(d, [(591, 860), (596, 860), (19, 2167), (14, 2167)], (85, 33, 6))
    # A shallow cut adds an engineered junction without any lettering/logo.
    polygon(d, [(418, 1284), (589, 1242), (579, 1264), (408, 1306)], (10, 8, 6))
    return im


def carbon_fold():
    im = base((93, 100, 112), cy=.7, amount=.11)
    d = ImageDraw.Draw(im)
    polygon(d, [(1000, 793), (1000, 2167), (0, 2167), (0, 1661)], (11, 13, 16))
    # Carbon-like weave is authored geometry, not a third-party texture.
    texture = Image.new("RGB", im.size)
    td = ImageDraw.Draw(texture)
    for y in range(810, 2175, 15):
        for x in range(-20, 1030, 15):
            if (x//15+y//15) % 2:
                line(td, [(x, y), (x+10, y+10)], (17, 19, 23), 4)
            else:
                line(td, [(x+10, y), (x, y+10)], (11, 13, 17), 4)
    mask = Image.new("L", im.size)
    md = ImageDraw.Draw(mask)
    polygon(md, [(1000, 793), (1000, 2167), (0, 2167), (0, 1661)], 255)
    im.paste(texture, (0, 0), mask)
    d = ImageDraw.Draw(im)
    polygon(d, [(1000, 1291), (1000, 2167), (325, 2167)], (3, 4, 5))
    line(d, [(0, 1661), (1000, 793)], (43, 46, 52), 2)
    im = glowing_lines(im, [[(325, 2167), (1000, 1291)]], (226, 102, 13), 2, 7)
    return im


def orbital_blue():
    im = base(BLUE, cx=.55, cy=.63, amount=.1)
    d = ImageDraw.Draw(im)
    rng = random.Random(71204)
    for _ in range(95):
        x, y = rng.uniform(40, 960), rng.uniform(900, 1900)
        v = rng.randrange(24, 90)
        ellipse(d, (x, y, x+rng.choice([.5, .8, 1.3]), y+1), fill=(v//2, v*3//4, v))
    paths = []
    for i in range(5):
        # Tilted planetary orbits, generous black area above for the lock clock.
        rx, ry = 355+i*16, 138+i*9
        a = math.radians(-34)
        points = []
        for t in np.linspace(0, math.tau, 480):
            x, y = rx*math.cos(t), ry*math.sin(t)
            points.append((500+x*math.cos(a)-y*math.sin(a),
                           1410+x*math.sin(a)+y*math.cos(a)))
        paths.append(points)
    im = glowing_lines(im, [paths[0]], (85, 165, 255), 3, 16)
    d = ImageDraw.Draw(im)
    for i, path in enumerate(paths[1:]):
        line(d, path, (20+i*3, 44+i*5, 79+i*9), .8)
    ellipse(d, (477, 1387, 523, 1433), fill=(3, 9, 17), outline=(92, 160, 220), width=2)
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    entries = [
        ("extra_papaya_apex", "Papaya Apex", "Cars", "Orange", side_car),
        ("extra_midnight_gt", "Midnight GT", "Cars", "Blue", front_car),
        ("extra_racing_line", "Papaya Racing Line", "Themes", "Orange", racing_line),
        ("extra_carbon_fold", "Carbon Fold", "Themes", "Orange", carbon_fold),
        ("extra_orbital_blue", "Orbital Blue", "Themes", "Blue", orbital_blue),
    ]
    catalog = []
    for ident, title, category, color, create in entries:
        im = create()
        im.save(OUT / f"{ident}.webp", quality=94, method=6)
        im.resize((360, 780), Image.Resampling.LANCZOS).save(
            OUT / f"{ident}_thumb.webp", quality=88, method=6)
        catalog.append(dict(id=ident, title=title, category=category, color=color,
                            kind="STATIC", asset=f"wallpapers/{ident}.webp",
                            thumbnail=f"wallpapers/{ident}_thumb.webp",
                            author="DepthWall", license="Original artwork",
                            sourceUrl="https://github.com/Srimi1/depthwall/blob/main/tools/generate_extra_art.py",
                            width=W, height=H))
        print(f"Generated {title}: {W} x {H}")
    (OUT.parent / "extra_catalog.json").write_text(json.dumps(catalog, indent=2) + "\n")
    # A review sheet belongs to build artifacts, never the shipped catalog.
    artifacts = ROOT / "artifacts"
    artifacts.mkdir(exist_ok=True)
    sheet = Image.new("RGB", (len(entries)*240, 550), (12, 12, 12))
    d = ImageDraw.Draw(sheet)
    for i, item in enumerate(catalog):
        thumb = Image.open(OUT / f"{item['id']}_thumb.webp").resize((240, 520))
        sheet.paste(thumb, (i*240, 0))
        d.text((i*240+10, 531), item["title"], fill=(235, 232, 227))
    sheet.save(artifacts / "extra-contact-sheet.jpg", quality=92)


if __name__ == "__main__":
    main()
