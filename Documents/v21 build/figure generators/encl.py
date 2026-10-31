import sys
W,H=1240,560
S=12  # px per cm
o=[]
def dim_h(x1,x2,y,t):
    o.append(f'<line x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" stroke="#16130F" stroke-width="1.6" marker-start="url(#a)" marker-end="url(#a)"/>')
    o.append(f'<line x1="{x1}" y1="{y-8}" x2="{x1}" y2="{y+8}" stroke="#16130F" stroke-width="1"/><line x1="{x2}" y1="{y-8}" x2="{x2}" y2="{y+8}" stroke="#16130F" stroke-width="1"/>')
    o.append(f'<rect x="{(x1+x2)/2-26}" y="{y-9}" width="52" height="18" fill="#FFFFFF"/><text x="{(x1+x2)/2}" y="{y+4}" class="dm">{t}</text>')
def dim_v(x,y1,y2,t):
    o.append(f'<line x1="{x}" y1="{y1}" x2="{x}" y2="{y2}" stroke="#16130F" stroke-width="1.6" marker-start="url(#a)" marker-end="url(#a)"/>')
    o.append(f'<line x1="{x-8}" y1="{y1}" x2="{x+8}" y2="{y1}" stroke="#16130F" stroke-width="1"/><line x1="{x-8}" y1="{y2}" x2="{x+8}" y2="{y2}" stroke="#16130F" stroke-width="1"/>')
    o.append(f'<rect x="{x-26}" y="{(y1+y2)/2-9}" width="52" height="18" fill="#FFFFFF"/><text x="{x}" y="{(y1+y2)/2+4}" class="dm">{t}</text>')
def callout(x1,y1,x2,y2,t,anchor='start'):
    o.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#6A6054" stroke-width="1.2"/><circle cx="{x1}" cy="{y1}" r="3" fill="#16130F"/>')
    dx=6 if anchor=='start' else -6
    o.append(f'<text x="{x2+dx}" y="{y2+4}" class="co" text-anchor="{anchor}">{t}</text>')
def fan(cx,cy,r):
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#2B2620" stroke="#16130F" stroke-width="2"/>')
    for k in range(1,5): o.append(f'<circle cx="{cx}" cy="{cy}" r="{r*k/5}" fill="none" stroke="#8C8378" stroke-width="1"/>')
    o.append(f'<line x1="{cx-r}" y1="{cy}" x2="{cx+r}" y2="{cy}" stroke="#8C8378"/><line x1="{cx}" y1="{cy-r}" x2="{cx}" y2="{cy+r}" stroke="#8C8378"/>')
# FRONT VIEW
fx,fy,fw,fh=90,90,25*S,30*S
o.append(f'<text x="{fx+fw/2}" y="58" class="vt">Front View</text>')
o.append(f'<rect x="{fx+5}" y="{fy+5}" width="{fw}" height="{fh}" fill="#16130F"/>')
o.append(f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" fill="#F4F1EC" stroke="#16130F" stroke-width="2.5"/>')
o.append(f'<rect x="{fx+10}" y="{fy+10}" width="{fw-20}" height="{fh-20}" fill="none" stroke="#B9B1A6" stroke-width="1.2"/>')
sw,sh=17*S,11.5*S; sx=fx+5.5*S; sy=fy+8*S
o.append(f'<rect x="{sx}" y="{sy}" width="{sw}" height="{sh}" rx="3" fill="#16130F"/><rect x="{sx+9}" y="{sy+9}" width="{sw-18}" height="{sh-18}" fill="#3B6E8F"/>')
hx,hy=fx+15*S,fy+21.5*S
o.append(f'<rect x="{hx-1.6*S}" y="{hy}" width="{3.2*S}" height="{1.8*S}" rx="3" fill="#C9C2B8" stroke="#16130F" stroke-width="1.5"/>')
o.append(f'<rect x="{hx+0.2*S}" y="{hy+0.9*S}" width="{0.9*S}" height="{3.6*S}" rx="4" fill="#E2DDD5" stroke="#16130F" stroke-width="1.5"/>')
for hy2 in (fy+5*S, fy+25*S):
    o.append(f'<rect x="{fx-4}" y="{hy2}" width="8" height="{2.2*S}" fill="#8C8378" stroke="#16130F" stroke-width="1"/>')
dim_h(fx,fx+fw,fy+fh+34,'25 cm')
dim_v(fx-34,fy,fy+fh,'30 cm')
callout(fx,fy+6*S,fx+fw+40,fy+20,'Door hinges (left edge)')
callout(sx+sw-20,sy+30,fx+fw+40,sy+40,'7-inch touchscreen in a door cut-out')
callout(hx+0.7*S,hy+3*S,fx+fw+40,hy+30,'Door handle and lock')
# SIDE VIEW (right)
rx,ry,rw,rh=690,90,15*S,30*S
o.append(f'<text x="{rx+rw/2}" y="58" class="vt">Right Side View</text>')
o.append(f'<rect x="{rx+5}" y="{ry+5}" width="{rw}" height="{rh}" fill="#16130F"/>')
o.append(f'<rect x="{rx}" y="{ry}" width="{rw}" height="{rh}" fill="#F4F1EC" stroke="#16130F" stroke-width="2.5"/>')
o.append(f'<line x1="{rx+14}" y1="{ry}" x2="{rx+14}" y2="{ry+rh}" stroke="#B9B1A6" stroke-width="1.2"/>')
fan(rx+rw/2,ry+10*S,6*S)
holes=[(rx+5.5*S,ry+27.5*S),(rx+9*S,ry+27.5*S),(rx+12.5*S,ry+27.5*S)]
for hx,hy in holes:
    o.append(f'<circle cx="{hx}" cy="{hy}" r="11" fill="#16130F"/><circle cx="{hx}" cy="{hy}" r="5" fill="#6A6054"/>')
dim_h(rx,rx+rw,ry+rh+34,'15 cm')
dim_v(rx-34,ry,ry+rh,'30 cm')
callout(rx+rw/2+5*S,ry+8*S,rx+rw+40,ry+60,'USB case fan, one on each side')
callout(holes[0][0],holes[0][1]-11,rx+rw+40,ry+rh-125,'Cable entry 1: AC mains extension cable')
callout(holes[1][0],holes[1][1]-11,rx+rw+40,ry+rh-100,'Cable entry 2: Ethernet to the Wi-Fi extender')
callout(holes[2][0],holes[2][1]-11,rx+rw+40,ry+rh-75,'Cable entry 3: camera Ethernet and 12 V cable')
callout(rx+8,ry+200,rx+rw+40,ry+200,'Door edge (front)')
o.append(f'<text x="{W/2}" y="{H-14}" class="nt">Metal waterproof CCTV junction box. Dimensions are outside measurements; drawn to scale. Cable entries are sealed with rubber sealant.</text>')
svg=f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Garbage-Guard Enclosure Drawing</title>
<style>@page {{ size:{W}px {H}px; margin:0 }} *{{margin:0;box-sizing:border-box}} html,body{{background:#FFFFFF}}</style></head><body>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">
<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,1 L9,5 L0,9 z" fill="#16130F"/></marker></defs>
<style>text {{ font-family:system-ui,-apple-system,"Segoe UI","DejaVu Sans",Roboto,sans-serif }}
.vt {{ font-size:15px; font-weight:800; fill:#16130F; text-anchor:middle; letter-spacing:.06em }}
.dm {{ font-size:12px; font-weight:800; fill:#16130F; text-anchor:middle }}
.co {{ font-size:11.5px; font-weight:600; fill:#16130F }}
.nt {{ font-size:11px; fill:#4A4238; text-anchor:middle }}</style>
<rect width="{W}" height="{H}" fill="#FFFFFF"/>
{chr(10).join(o)}
</svg></body></html>'''
open(sys.argv[1],'w').write(svg)
