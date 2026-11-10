"""Repository banner: the app's name and promise on the left, three real screens on the right.

The screens are the README captures (docs/images), so the banner always shows the app as it is.
Run from the repository root:  python3 branding/banner.py   (needs Pillow)
"""
import sys
sys.path.insert(0, 'branding')
from PIL import Image, ImageDraw, ImageFilter
from render import text, MINT, CYAN

W, H = 1600, 720
BG = (31, 31, 31, 255)          # app background #1F1F1F
TEXT = (242, 242, 242, 255)
MUTED = (166, 166, 166, 255)

img = Image.new('RGBA', (W, H), BG)

# A soft pool of mint light behind the phones, the only glow on the page (the brand's accent).
glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([980, 120, 1520, 660], fill=MINT + (46,))
img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(120)))

# Thin mint → cyan rule along the bottom, as on the logo.
rule = ImageDraw.Draw(img)
for x in range(W):
    t = x / (W - 1)
    c = tuple(int(MINT[i] + (CYAN[i] - MINT[i]) * t) for i in range(3)) + (255,)
    rule.line([(x, H - 6), (x, H)], fill=c)


def phone(path, width):
    """A screen in a dark phone frame with rounded corners and a soft shadow."""
    shot = Image.open(path).convert('RGBA')
    h = round(shot.height * width / shot.width)
    shot = shot.resize((width, h), Image.LANCZOS)
    bezel = 10
    fw, fh = width + 2 * bezel, h + 2 * bezel
    frame = Image.new('RGBA', (fw + 80, fh + 80), (0, 0, 0, 0))
    shadow = Image.new('RGBA', frame.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([40, 52, 40 + fw, 52 + fh], radius=44, fill=(0, 0, 0, 150))
    frame.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(22)))
    body = Image.new('RGBA', (fw, fh), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle([0, 0, fw - 1, fh - 1], radius=40, fill=(12, 12, 12, 255), outline=(70, 70, 70, 255), width=2)
    mask = Image.new('L', (width, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, width - 1, h - 1], radius=30, fill=255)
    body.paste(shot, (bezel, bezel), mask)
    frame.alpha_composite(body, (40, 40))
    return frame


# Three screens, the middle one in front: capture, a photo, the client's page.
left = phone('docs/images/tela-fotos.png', 230)
mid = phone('docs/images/tela-visualizador.png', 268)
right = phone('docs/images/portal_celular.png', 230)
top = 70
img.alpha_composite(left, (900, top + 70))
img.alpha_composite(right, (1240, top + 70))
img.alpha_composite(mid, (1050, top))

# Name and promise.
x = 110
word = text('JR C50D Sync', 104, TEXT, tracking=0.04)
img.alpha_composite(word, (x, 196))
y = 196 + word.height + 40
for line in ('Captura vinculada USB', 'para a Canon EOS 50D'):
    t = text(line, 46, MUTED, tracking=0.02)
    img.alpha_composite(t, (x, y))
    y += t.height + 14
y += 26
for label in ('Capturar', 'Controlar', 'Melhorar', 'Entregar'):
    t = text(label, 30, MINT + (255,), tracking=0.03)
    img.alpha_composite(t, (x, y))
    x += t.width + 22
    if label != 'Entregar':
        # Exo 2 has no arrow glyph: a small chevron drawn by hand, centred on the words.
        cy = y + t.height // 2 + 2
        ImageDraw.Draw(img).line([(x, cy - 8), (x + 9, cy), (x, cy + 8)], fill=(110, 110, 110, 255), width=3, joint='curve')
        x += 9 + 22
brand = text('por Jr_Tech_OFC · Nossa Arte, Seu Sucesso', 30, CYAN + (255,), tracking=0.03)
img.alpha_composite(brand, (110, y + 76))

img.convert('RGB').save('docs/images/banner.png', optimize=True)
print('docs/images/banner.png')
