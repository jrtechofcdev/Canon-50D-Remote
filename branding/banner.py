"""Repository banner: app icon + "JR C50D Sync" wordmark + tagline on brand graphite.

Run from the repository root:  python3 branding/banner.py
"""
import sys
sys.path.insert(0, 'branding')
from PIL import Image, ImageDraw
from render import symbol, text, MINT, CYAN

W, H = 1600, 480
GRAPHITE = (41, 41, 41, 255)
TEXT = (242, 242, 242, 255)
MUTED = (166, 166, 166, 255)

img = Image.new('RGBA', (W, H), GRAPHITE)

# A thin mint → cyan rule along the bottom edge, echoing the logo gradient.
rule = ImageDraw.Draw(img)
for x in range(W):
    t = x / (W - 1)
    c = tuple(int(MINT[i] + (CYAN[i] - MINT[i]) * t) for i in range(3)) + (255,)
    rule.line([(x, H - 6), (x, H)], fill=c)

sym = Image.open('branding/icone_jr_c50d_sync.png').convert('RGBA').resize((250, 250), Image.LANCZOS)
word = text('JR C50D Sync', 120, TEXT, tracking=0.04)
tag = text('Captura vinculada USB para a Canon EOS 50D', 44, MUTED, tracking=0.02)
brand = text('por Jr_Tech_OFC · Nossa Arte, Seu Sucesso', 36, CYAN + (255,), tracking=0.03)

block_w = sym.width + 70 + max(word.width, tag.width, brand.width)
x0 = (W - block_w) // 2
y_sym = (H - sym.height) // 2 - 6
img.alpha_composite(sym, (x0, y_sym))

tx = x0 + sym.width + 70
y = y_sym + 10
img.alpha_composite(word, (tx, y)); y += word.height + 34
img.alpha_composite(tag, (tx, y)); y += tag.height + 22
img.alpha_composite(brand, (tx, y))

img.convert('RGB').save('docs/images/banner.png', optimize=True)
print('docs/images/banner.png', img.size)
