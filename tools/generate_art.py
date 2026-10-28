"""Deterministic original artwork. Run with Python + Pillow + numpy.

These are authored generative illustrations, not photographs or Xiaomi assets.
All 48 static illustrations and 12 layer pairs are reproducible from their seeds.
"""
from pathlib import Path
import json, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/assets/wallpapers'
OUT.mkdir(parents=True, exist_ok=True)
W, H = 1440, 3120
Y, X = np.mgrid[0:H, 0:W].astype(np.float32)
catalog = []
palettes = [(255,135,10),(91,135,230),(96,189,161),(192,113,226),(218,172,113),(224,92,84),(112,171,204),(238,224,199)]

def background(col, strength=.12):
    radial = np.exp(-(((X-W*.65)/W)**2 + ((Y-H*.44)/(H*.4))**2)*3)
    arr = np.stack([radial*c*strength for c in col], -1).astype('uint8')
    return Image.fromarray(arr)

def stars(im, seed):
    r=random.Random(seed); d=ImageDraw.Draw(im)
    for _ in range(160):
        x=r.randrange(W); y=r.randrange(int(H*.83)); k=r.choice([1,1,2,3]); b=r.randrange(45,180)
        d.ellipse((x,y,x+k,y+k), fill=(b,b,min(255,b+22)))

def planet(im, kind, seed, cx=.5, cy=.48, radius=.34):
    r=radius*W; dx=(X-W*cx)/r; dy=(Y-H*cy)/r
    rr=dx*dx+dy*dy; inside=rr<=1; z=np.sqrt(np.maximum(0,1-rr))
    light=np.clip(-.58*dx-.45*dy+.62*z, .018, 1)
    lat=np.arcsin(np.clip(dy,-1,1)); lon=np.arctan2(dx,z+.0001)
    noise=(np.sin(lon*7+np.sin(lat*11+seed))*np.cos(lat*13)+np.sin(lon*19+lat*12)*.35+np.cos(lon*41-lat*24)*.16)
    if kind=='earth':
        land=noise>.22
        rgb=np.stack([np.where(land,66,13),np.where(land,100,65),np.where(land,56,133)],-1)
        cloud=np.clip((np.sin(lon*25+lat*14+noise*3)+np.cos(lat*27-lon*8)-1.25)*.6,0,.7)
        rgb=rgb*(1-cloud[...,None])+np.array([230,242,250])*cloud[...,None]
    elif kind=='sun':
        t=np.clip(noise*.25+.6,0,1);rgb=np.stack([t*80+175,t*155+65,t*45+5],-1);light=np.clip(z*.6+.5,0,1)
    elif kind=='saturn':
        band=np.sin(lat*65+noise*.5)*.08+.82;rgb=np.stack([band*224,band*184,band*126],-1)
    elif kind=='mars':
        t=np.clip(.68+noise*.15,0,1);rgb=np.stack([t*208,t*95,t*54],-1)
    else:
        t=np.clip(.62+noise*.1,0,1);rgb=np.stack([t*195,t*200,t*208],-1)
    a=np.asarray(im).copy().astype(np.float32)
    glow=np.exp(-np.abs(np.sqrt(rr)-1)*38)*np.where(rr>1,1,0)
    tint=np.array({'earth':(24,100,245),'sun':(255,95,5),'mars':(132,42,16)}.get(kind,(80,87,100)))
    a+=glow[...,None]*tint*.55
    a[inside]=(rgb*light[...,None])[inside]
    im=Image.fromarray(np.clip(a,0,255).astype('uint8'))
    if kind=='saturn':
        layer=Image.new('RGBA',(W,H));d=ImageDraw.Draw(layer)
        for k in range(26):
            rx=r*(1.35+k*.022);ry=rx*.25
            d.ellipse((W*cx-rx,H*cy-ry,W*cx+rx,H*cy+ry),outline=(200,167,119,110+k*3),width=4)
        # Ring plane is intentionally a stylised orbital illustration.
        layer=layer.rotate(-23,resample=Image.Resampling.BICUBIC,center=(W*cx,H*cy))
        im=Image.alpha_composite(im.convert('RGBA'),layer).convert('RGB')
    return im

def landscape(seed):
    r=random.Random(seed); col=palettes[seed%8]; bg=background(col,.18); stars(bg,seed)
    d=ImageDraw.Draw(bg);sx=W*(.3+.4*r.random());sy=H*.33;rr=W*.105
    d.ellipse((sx-rr,sy-rr,sx+rr,sy+rr),fill=tuple(int(c*.8) for c in col))
    fg=Image.new('RGBA',(W,H));d=ImageDraw.Draw(fg)
    for layer in range(5):
        base=H*(.51+layer*.10); pts=[(-60,H+20)]
        for x in range(-60,W+120,90):
            yy=base-r.random()*H*(.15-layer*.018)
            pts.append((x,yy))
        pts.extend([(W+60,H+20)])
        shade=.32-layer*.065
        d.polygon(pts,fill=(*[max(3,int(c*shade)) for c in col],255))
        if layer<2:
            d.line(pts[1:-1],fill=(*[int(c*(shade+.15)) for c in col],255),width=3)
    return bg,fg

def abstract(seed,cat):
    r=random.Random(seed);col=palettes[seed%8];im=background(col,.055);d=ImageDraw.Draw(im)
    if cat=='Abstract':
        for k in range(35):
            pts=[]
            for y in range(int(H*.22),int(H*.86),8):
                x=W*.5+math.sin(y/H*7+seed*.7+k*.035)*W*.32+k*8-140
                pts.append((x,y))
            shade=.25+.75*k/35;d.line(pts,fill=tuple(int(c*shade) for c in col),width=3+k//9)
    elif cat=='Minimal':
        if seed%3==0:
            for k in range(9):
                rad=100+k*36;d.ellipse((W*.5-rad,H*.48-rad,W*.5+rad,H*.48+rad),outline=tuple(int(c*(1-k*.08)) for c in col),width=3)
        elif seed%3==1:
            d.line((W*.5,H*.26,W*.5,H*.75),fill=col,width=3)
            d.ellipse((W*.5-75,H*.46-75,W*.5+75,H*.46+75),fill=col)
        else:
            d.arc((W*.19,H*.39,W*.81,H*.39+W*.62),25,335,fill=col,width=12)
            d.ellipse((W*.71,H*.4,W*.75,H*.4+W*.04),fill=(245,237,222))
    elif cat=='Geometry':
        for k in range(14):
            rad=W*(.12+k*.028);rot=seed*.2+k*.07
            pts=[(W*.5+math.cos(rot+j*math.pi/3)*rad,H*.48+math.sin(rot+j*math.pi/3)*rad) for j in range(7)]
            d.line(pts,fill=tuple(int(c*(.25+k*.05)) for c in col),width=4)
    else:
        col=(255,135,10)
        style=seed%8
        if style==0:
            for k in range(7):
                y=H*(.28+k*.085)
                d.polygon([(0,y),(W,y-W*.5),(W,y-W*.5+80),(0,y+80)],fill=(int(255*(.25+k*.1)),int(135*(.25+k*.1)),10))
        elif style==1:
            for k in range(14):
                radius=190+k*34
                d.arc((W*.18-radius,H*.55-radius,W*.18+radius,H*.55+radius),-105,105,fill=(100+k*11,40+k*7,4),width=5)
        elif style==2:
            for k in range(8):
                x=150+k*36;y=H*.29+k*36
                d.rounded_rectangle((x,y,W-x,H*.71-k*36),radius=130-k*10,outline=(255-k*22,135-k*11,10),width=5)
        elif style==3:
            rad=W*.32;cx=W*.5;cy=H*.48
            d.ellipse((cx-rad,cy-rad,cx+rad,cy+rad),fill=col)
            for k in range(19):
                y=cy+k*24;d.rectangle((0,y,W,y+8+k),fill=(0,0,0))
        elif style==4:
            d.polygon([(W*.26,H*.26),(W*.68,H*.26),(W*.48,H*.51),(W*.76,H*.51),(W*.3,H*.79),(W*.43,H*.57),(W*.18,H*.57)],fill=(245,113,7))
            d.line((W*.16,H*.83,W*.82,H*.83),fill=(245,231,204),width=3)
        elif style==5:
            for k in range(18):
                x=-W+k*W*.16;d.line((x,H*.68,x+W,H*.34),fill=(80+k*8,35+k*4,5),width=3)
            d.line((W*.55,H*.2,W*.55,H*.8),fill=col,width=11)
        elif style==6:
            for k in range(30):
                pts=[(x,H*.45+math.sin(x/W*5+k*.12)*180+k*20) for x in range(-20,W+20,8)]
                d.line(pts,fill=(120+k*4,45+k*3,5),width=3)
        else:
            d.ellipse((W*.17,H*.36,W*.83,H*.36+W*.66),fill=col)
            d.ellipse((W*.19,H*.36+20,W*.81,H*.36+W*.64),fill=(0,0,0))
            d.line((W*.5,H*.2,W*.5,H*.4),fill=(245,231,204),width=4)
    return im

names={
'Space':['Blue Hour','Red Frontier','Ringlight','Lunar Silence','Solar Flare','Ocean World','Distant Orbit','Nightfall'],
'Nature':['Amber Summit','Alpine Blue','Emerald Valley','Violet Ridge','Dune Light','Rust Horizon','Glacial Night','Quiet Peaks'],
'Abstract':['Solar Current','Blue Frequency','Verdant Flow','Ultraviolet','Desert Silk','Crimson Wave','Arctic Ribbon','Pearl Motion'],
'Minimal':['One Orbit','Blue Thread','Quiet Arc','Violet Focus','Warm Silence','Red Meridian','Polar Line','Ivory Loop'],
'Geometry':['Hexa Ember','Blue Structure','Emerald Grid','Violet Prism','Golden Form','Red Lattice','Ice Geometry','Porcelain'],
'Orange / Black':['Apex','Slipstream','Papaya Circuit','Afterburn','Orange Line','Night Drive','Velocity','Finish Line']}
def save(im, ident):
    im=im.convert('RGB');im.save(OUT/f'{ident}.webp',quality=91,method=4)
    im.resize((360,780),Image.Resampling.LANCZOS).save(OUT/f'{ident}_thumb.webp',quality=84)

for ci,(cat,titles) in enumerate(names.items()):
    for i,title in enumerate(titles):
        ident=f'wall_{ci}_{i}';seed=ci*16+i
        if cat=='Space':
            im=background(palettes[i],.08);stars(im,seed)
            im=planet(im,['earth','mars','saturn','moon','sun','earth','saturn','moon'][i],seed,radius=.28+(i%3)*.04)
        elif cat=='Nature':
            bg,fg=landscape(i);im=Image.alpha_composite(bg.convert('RGBA'),fg)
        else:im=abstract(seed,cat)
        save(im,ident)
        catalog.append(dict(id=ident,title=title,category=cat,color=['Orange','Blue','Green','Purple','Gold','Red','Blue','White'][i] if ci!=5 else 'Orange',kind='STATIC',asset=f'wallpapers/{ident}.webp',thumbnail=f'wallpapers/{ident}_thumb.webp',author='DepthWall generative studio',license='Original artwork',sourceUrl='https://github.com/Srimi1/depthwall/blob/main/tools/generate_art.py'))

for i in range(12):
    ident=f'depth_{i}';bg,fg=landscape(i+16)
    bg.save(OUT/f'{ident}_bg.webp',quality=91);fg.save(OUT/f'{ident}_fg.webp',lossless=True)
    save(Image.alpha_composite(bg.convert('RGBA'),fg),ident)
    catalog.append(dict(id=ident,title=['Solar Ridge','Cobalt Valley','Jade Peaks','Amethyst Night','Desert Dawn','Crimson Hills','Blue Hour Peaks','Moonlit Summit','Ember Valley','Indigo Horizon','Forest Skyline','Violet Silence'][i],category='Depth',color=['Orange','Blue','Green','Purple','Gold','Red','Blue','White'][i%8],kind='LAYERED_DEPTH',asset=f'wallpapers/{ident}_bg.webp',foreground=f'wallpapers/{ident}_fg.webp',thumbnail=f'wallpapers/{ident}_thumb.webp',author='DepthWall generative studio',license='Original artwork',sourceUrl='https://github.com/Srimi1/depthwall/blob/main/tools/generate_art.py'))

for i,kind in enumerate(['earth','mars','saturn','moon','snow','geometry','sun']):
    ident=f'scene_{kind}'
    if kind=='snow':
        bg,fg=landscape(22);im=Image.alpha_composite(bg.convert('RGBA'),fg)
    elif kind=='geometry':im=abstract(51,'Geometry')
    else:
        im=background(palettes[i],.1);stars(im,97+i);im=planet(im,kind,5+i,radius=.34)
    save(im,ident)
    catalog.append(dict(id=ident,title={'snow':'Snow Mountain'}.get(kind,kind.title()),category='Native scenes',color=['Blue','Red','Gold','White','Blue','Purple','Orange'][i],kind='NATIVE_SCENE',asset=f'wallpapers/{ident}.webp',thumbnail=f'wallpapers/{ident}_thumb.webp',scene=kind,author='DepthWall',license='Original artwork',sourceUrl='https://github.com/Srimi1/depthwall'))
(OUT.parent/'catalog.json').write_text(json.dumps(catalog,indent=2))
# Contact sheet documents every bundled original without opening 67 full-resolution files.
sheet=Image.new('RGB',(8*180,9*420),(8,8,8));d=ImageDraw.Draw(sheet)
for i,item in enumerate(catalog):
    thumb=Image.open(OUT/f"{item['id']}_thumb.webp").resize((180,390))
    x=(i%8)*180;y=(i//8)*420;sheet.paste(thumb,(x,y));d.text((x+8,y+396),item['title'],fill='white')
sheet.save(ROOT/'branding/catalog-contact-sheet.jpg',quality=90)
print(f'Generated {len(catalog)} entries: 48 static, 12 layered, 7 native previews')
