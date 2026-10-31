"""Shared helpers for the Garbage-Guard thesis figures.

fix_arrows(svg): replaces every SVG marker arrowhead with an explicit triangle
whose base sits where the line now ends, so the line never runs into the tip.
whiten(html): beige page background -> white.
"""
import re, math

INK = "#16130F"
FONT = 'system-ui,-apple-system,"Segoe UI","DejaVu Sans",Roboto,sans-serif'


def _attr(tag, name):
    m = re.search(r'\b%s="([^"]*)"' % name, tag)
    return m.group(1) if m else None


def _head(tip, frm, L, hw, color):
    (x2, y2), (x1, y1) = tip, frm
    d = math.hypot(x2 - x1, y2 - y1) or 1
    ux, uy = (x2 - x1) / d, (y2 - y1) / d
    bx, by = x2 - ux * L, y2 - uy * L
    px, py = -uy * hw, ux * hw
    pts = f"{x2:.1f},{y2:.1f} {bx+px:.1f},{by+py:.1f} {bx-px:.1f},{by-py:.1f}"
    return f'<polygon points="{pts}" fill="{color}" stroke="{color}" stroke-width="1" stroke-linejoin="miter"/>', (bx + ux * 0.5, by + uy * 0.5)


def fix_arrows(svg):
    out = []

    def repl(m):
        tag = m.group(0)
        has_end = "marker-end" in tag
        has_start = "marker-start" in tag
        if not (has_end or has_start):
            return tag
        sw = float(_attr(tag, "stroke-width") or 2)
        color = _attr(tag, "stroke") or INK
        L = 9 + 2.0 * sw
        hw = L * 0.46
        if tag.startswith("<line"):
            pts = [(float(_attr(tag, "x1")), float(_attr(tag, "y1"))),
                   (float(_attr(tag, "x2")), float(_attr(tag, "y2")))]
        else:
            nums = [float(v) for v in re.split(r"[ ,]+", _attr(tag, "points").strip())]
            pts = list(zip(nums[0::2], nums[1::2]))
        heads = []
        if has_end:
            h, base = _head(pts[-1], pts[-2], L, hw, color)
            heads.append(h); pts[-1] = base
        if has_start:
            h, base = _head(pts[0], pts[1], L, hw, color)
            heads.append(h); pts[0] = base
        tag = re.sub(r'\s*marker-(end|start)="[^"]*"', "", tag)
        if tag.startswith("<line"):
            for k, v in zip(("x1", "y1", "x2", "y2"), (pts[0][0], pts[0][1], pts[-1][0], pts[-1][1])):
                tag = re.sub(r'\b%s="[^"]*"' % k, f'{k}="{v:.1f}"', tag)
        else:
            tag = re.sub(r'\bpoints="[^"]*"', 'points="%s"' % " ".join(f"{x:.1f},{y:.1f}" for x, y in pts), tag)
        tag = tag.replace("<line ", '<line stroke-linecap="butt" ', 1).replace("<polyline ", '<polyline stroke-linejoin="miter" ', 1)
        return tag + "".join(heads)

    svg = re.sub(r"<(?:line|polyline)\b[^>]*/>", repl, svg)
    svg = re.sub(r"<marker\b.*?</marker>", "", svg, flags=re.S)
    return svg


def whiten(html):
    return re.sub(r"#DED4C8", "#FFFFFF", html, flags=re.I)


def page(title, W, H, body):
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>{title}</title>
<style>
@page {{ size:{W}px {H}px; margin:0 }}
*{{margin:0;box-sizing:border-box}}
html,body{{background:#FFFFFF}}
body{{width:{W}px;-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head><body>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">
<style>
text {{ font-family:{FONT} }}
.ct {{ font-size:13px; font-weight:800; fill:{INK} }}
.cs {{ font-size:10px; font-weight:500; fill:#6A6054 }}
.hd {{ font-size:12.5px; font-weight:800; fill:#FFFFFF; letter-spacing:.13em }}
.al {{ font-size:9.5px; font-weight:700; fill:#6A6054; letter-spacing:.03em }}
.ti {{ font-size:11px; font-weight:800; fill:{INK} }}
.tw {{ font-size:10px; font-weight:500; fill:#6A6054 }}
.lg {{ font-size:10px; font-weight:600; fill:#6A6054 }}
</style>
<rect width="{W}" height="{H}" fill="#FFFFFF"/>
{fix_arrows(body)}
</svg>
</body></html>'''


def card(x, y, w, h, title, subs=(), accent="#5E6B73", ty=None, fill="#E9E1D6"):
    o = [f'<rect x="{x+4}" y="{y+4}" width="{w}" height="{h}" rx="10" fill="{INK}"/>',
         f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{fill}" stroke="{INK}" stroke-width="2.5"/>',
         f'<rect x="{x}" y="{y}" width="{w}" height="6" rx="3" fill="{accent}"/>']
    n = len(subs)
    if ty is None:
        ty = y + h / 2 - (n * 14) / 2 + 4
    cx = x + w / 2
    o.append(f'<text x="{cx}" y="{ty}" class="ct" text-anchor="middle">{title}</text>')
    for i, s in enumerate(subs):
        o.append(f'<text x="{cx}" y="{ty+17+i*14}" class="cs" text-anchor="middle">{s}</text>')
    return "".join(o)


def header(x, y, w, text, color):
    return (f'<rect x="{x+4}" y="{y+4}" width="{w}" height="30" rx="8" fill="{INK}"/>'
            f'<rect x="{x}" y="{y}" width="{w}" height="30" rx="8" fill="{color}" stroke="{INK}" stroke-width="2.5"/>'
            f'<text x="{x+w/2}" y="{y+20}" class="hd" text-anchor="middle">{text}</text>')


def arrow(points, sw=2.4, dashed=False, start=False):
    p = " ".join(f"{x},{y}" for x, y in points)
    d = ' stroke-dasharray="7 5"' if dashed else ""
    s = ' marker-start="url(#a)"' if start else ""
    return f'<polyline points="{p}" fill="none" stroke="{INK}" stroke-width="{sw}"{d}{s} marker-end="url(#a)"/>'


def line(points, sw=2.4, dashed=False):
    p = " ".join(f"{x},{y}" for x, y in points)
    d = ' stroke-dasharray="7 5"' if dashed else ""
    return f'<polyline points="{p}" fill="none" stroke="{INK}" stroke-width="{sw}"{d}/>'


def label(x, y, t, anchor="middle", cls="al"):
    return f'<text x="{x}" y="{y}" class="{cls}" text-anchor="{anchor}">{t}</text>'
