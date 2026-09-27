import math
# Tiles traced from Logo_Simbolo.png (500×500): TL, TR, BR, BL.
TILES = [
    [(142.4, 40), (341.4, 40), (286.6, 235), (87.6, 235)],
    [(338.4, 125), (450.4, 125), (419.6, 235), (307.6, 235)],
    [(79.4, 264), (191.4, 264), (160.6, 374), (48.6, 374)],
    [(212.4, 264), (411.4, 264), (356.6, 459), (157.6, 459)],
]
R = 9.0

def rounded(poly, r=R):
    n = len(poly); out = []
    for i in range(n):
        p0, p1, p2 = poly[i - 1], poly[i], poly[(i + 1) % n]
        def toward(a, b, d):
            dx, dy = b[0] - a[0], b[1] - a[1]; L = math.hypot(dx, dy)
            return (a[0] + dx / L * d, a[1] + dy / L * d)
        out.append((toward(p1, p0, r), p1, toward(p1, p2, r)))
    d = "M%.1f,%.1f " % out[0][2]
    for i in range(1, n + 1):
        a, c, b = out[i % n]
        d += "L%.1f,%.1f Q%.1f,%.1f %.1f,%.1f " % (a[0], a[1], c[0], c[1], b[0], b[1])
    return d + "Z"

PATHS = [rounded(t) for t in TILES]

def gradient():
    return '''<aapt:attr name="android:fillColor">
                <gradient android:type="linear" android:startX="150" android:startY="40" android:endX="330" android:endY="460">
                    <item android:offset="0" android:color="#FF00FCA8" />
                    <item android:offset="1" android:color="#FF1CDAFF" />
                </gradient>
            </aapt:attr>'''

def vector(size_dp, viewport, scale, tx, ty, fill=None, header=""):
    paths = ""
    for d in PATHS:
        if fill:
            paths += '        <path android:fillColor="%s" android:pathData="%s" />\n' % (fill, d)
        else:
            paths += '        <path android:pathData="%s">\n            %s\n        </path>\n' % (d, gradient())
    return '''<?xml version="1.0" encoding="utf-8"?>
%s<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="%ddp"
    android:height="%ddp"
    android:viewportWidth="%d"
    android:viewportHeight="%d">
    <group android:pivotX="249.5" android:pivotY="249.5" android:scaleX="%.4f" android:scaleY="%.4f" android:translateX="%.2f" android:translateY="%.2f">
%s    </group>
</vector>
''' % (header, size_dp, size_dp, viewport, viewport, scale, scale, tx, ty, paths)

res = "app/src/main/res/drawable/"
note = "<!-- Jr_Tech_OFC symbol, traced from the brand kit (Logo_Simbolo.png). -->\n"
# Adaptive icon foreground: 108 viewport, symbol ~56 tall centred in the 66 safe zone.
s = 56 / 419.0
open(res + "ic_launcher_foreground.xml", "w").write(vector(108, 108, s, 54 - 249.5, 54 - 249.5, header=note))
open(res + "ic_launcher_monochrome.xml", "w").write(vector(108, 108, s, 54 - 249.5, 54 - 249.5, fill="#FFFFFFFF", header=note))
# In-app mark, full size.
open(res + "ic_brand_symbol.xml", "w").write(vector(48, 500, 1.0, 0, 0, header=note))
# Notification small icon: white, fills the 24dp box.
s2 = 22 / 419.0
open(res + "ic_stat_symbol.xml", "w").write(vector(24, 24, s2, 12 - 249.5, 12 - 249.5, fill="#FFFFFFFF", header=note))
print("ok")
