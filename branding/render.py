import sys, math
sys.path.insert(0, 'branding')
from symbol import TILES, R
from PIL import Image, ImageDraw, ImageFont, ImageFilter

MINT = (0, 252, 168); CYAN = (28, 218, 255); GRAPHITE = (41, 41, 41)
FONT = 'app/src/main/res/font/exo2_regular.ttf'

def rounded_points(poly, r=R, steps=8):
    n = len(poly); pts = []
    for i in range(n):
        p0, p1, p2 = poly[i-1], poly[i], poly[(i+1) % n]
        def toward(a, b, d):
            dx, dy = b[0]-a[0], b[1]-a[1]; L = math.hypot(dx, dy); return (a[0]+dx/L*d, a[1]+dy/L*d)
        a, b = toward(p1, p0, r), toward(p1, p2, r)
        for k in range(steps+1):
            t = k/steps
            x = (1-t)**2*a[0] + 2*(1-t)*t*p1[0] + t**2*b[0]
            y = (1-t)**2*a[1] + 2*(1-t)*t*p1[1] + t**2*b[1]
            pts.append((x, y))
    return pts

def symbol(size):
    """Symbol cropped to its bounds (48.6..450.4 × 40..459), gradient like the logo, size = height px."""
    ss = 4; x0, y0, x1, y1 = 48.6, 40, 450.4, 459
    s = size / (y1 - y0) * ss
    W, H = int((x1-x0)*s)+2, int((y1-y0)*s)+2
    mask = Image.new('L', (W, H), 0); d = ImageDraw.Draw(mask)
    for t in TILES:
        d.polygon([((x-x0)*s, (y-y0)*s) for x, y in rounded_points(t)], fill=255)
    # Diagonal gradient (150,40) -> (330,460) in logo coordinates.
    gx0, gy0, gx1, gy1 = (150-x0)*s, (40-y0)*s, (330-x0)*s, (460-y0)*s
    vx, vy = gx1-gx0, gy1-gy0; L2 = vx*vx+vy*vy
    grad = Image.new('RGB', (W, H))
    px = grad.load()
    for yy in range(0, H):
        for xx in range(0, W, 1):
            t = max(0, min(1, ((xx-gx0)*vx + (yy-gy0)*vy) / L2))
            px[xx, yy] = tuple(int(MINT[i] + (CYAN[i]-MINT[i])*t) for i in range(3))
    out = grad.convert('RGBA'); out.putalpha(mask)
    return out.resize((W//ss, H//ss), Image.LANCZOS)

def text(txt, height, color, tracking=0.06):
    f = ImageFont.truetype(FONT, height)
    widths = [f.getlength(c) for c in txt]
    track = height * tracking
    W = int(sum(widths) + track*(len(txt)-1)) + 4
    asc, desc = f.getmetrics()
    img = Image.new('RGBA', (W, asc+desc), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    x = 0
    for c, w in zip(txt, widths):
        d.text((x, 0), c, font=f, fill=color); x += w + track
    return img.crop(img.getbbox())

def horizontal(word_color, tag_color, h=420):
    sym = symbol(h)
    word = text('Jr_Tech_OFC', int(h*0.46), word_color)
    tag = text('Nossa Arte, Seu Sucesso', int(h*0.2), tag_color, tracking=0.03)
    gap = int(h*0.16)
    W = sym.width + gap + max(word.width, tag.width)
    img = Image.new('RGBA', (W, h), (0, 0, 0, 0))
    img.alpha_composite(sym, (0, 0))
    img.alpha_composite(word, (sym.width+gap, int(h*0.12)))
    img.alpha_composite(tag, (sym.width+gap, int(h*0.12) + word.height + int(h*0.14)))
    return img.crop(img.getbbox())

def stacked(h=900):
    sym = symbol(int(h*0.55))
    word = text('Jr_Tech_OFC', int(h*0.14), MINT + (255,))
    tag = text('Nossa Arte, Seu Sucesso', int(h*0.065), CYAN + (255,), tracking=0.03)
    W = max(sym.width, word.width, tag.width)
    img = Image.new('RGBA', (W, h), (0, 0, 0, 0))
    y = 0
    img.alpha_composite(sym, ((W-sym.width)//2, y)); y += sym.height + int(h*0.06)
    img.alpha_composite(word, ((W-word.width)//2, y)); y += word.height + int(h*0.035)
    img.alpha_composite(tag, ((W-tag.width)//2, y))
    return img.crop(img.getbbox())

if __name__ == '__main__':
    assets = 'app/src/main/assets/watermarks/'
    import os; os.makedirs(assets, exist_ok=True)
    symbol(900).save(assets + 'simbolo.png', optimize=True)
    horizontal((255, 255, 255, 255), (255, 255, 255, 210)).save(assets + 'horizontal_branco.png', optimize=True)
    horizontal(MINT + (255,), CYAN + (255,)).save(assets + 'horizontal_cores.png', optimize=True)
    stacked().save(assets + 'empilhado.png', optimize=True)

    # Legacy launcher icons (Android 7.x): graphite tile / circle with the symbol.
    res = 'app/src/main/res/'
    for folder, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        for name, round_ in [('ic_launcher', False), ('ic_launcher_round', True)]:
            ss = 4; S = px*ss
            base = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(base)
            m = int(S*0.04)
            if round_: d.ellipse((m, m, S-m, S-m), fill=GRAPHITE + (255,))
            else: d.rounded_rectangle((m, m, S-m, S-m), radius=int(S*0.18), fill=GRAPHITE + (255,))
            sym = symbol(int(S*0.56))
            base.alpha_composite(sym, ((S-sym.width)//2, (S-sym.height)//2))
            path = res + 'mipmap-%s/%s.webp' % (folder, name)
            base.resize((px, px), Image.LANCZOS).save(path, 'WEBP', lossless=True)
    print('done')
