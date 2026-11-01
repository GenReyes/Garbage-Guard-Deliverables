import sys, math; from ggfig import *
W,H=1180,672
K=0.35          # slope of the V edges (dx per dy)
BLUE="#0B5CAD"
o=[]
def panel(pts,fill):
    p=" ".join(f"{x:.1f},{y:.1f}" for x,y in pts); ps=" ".join(f"{x+6:.1f},{y+6:.1f}" for x,y in pts)
    o.append(f'<polygon points="{ps}" fill="{INK}"/><polygon points="{p}" fill="{fill}" stroke="{INK}" stroke-width="2.5" stroke-linejoin="miter"/>')
def box(cx,cy,w,h,t,color,r=9):
    x,y=cx-w/2,cy-h/2
    o.append(f'<rect x="{x+4}" y="{y+4}" width="{w}" height="{h}" rx="{r}" fill="{INK}"/><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{color}" stroke="{INK}" stroke-width="2.5"/>')
    o.append(f'<text x="{cx}" y="{cy+6}" class="bx" text-anchor="middle">{t}</text>')
M=lambda x:W-x
# outer V edge: x = X0 + K*(y-Y0)
X0,Y0=130,100
ex=lambda y:X0+K*(y-Y0)
T1,B1=100,300      # upper panels
T2,B2=330,550
IN_TOP,IN_BOT=490,560   # inner edges of left panel at T1,B1 (parallel)
panel([(ex(T1),T1),(IN_TOP,T1),(IN_TOP+K*(B1-T1),B1),(ex(B1),B1)],"#DDEFEC")
panel([(M(IN_TOP),T1),(M(ex(T1)),T1),(M(ex(B1)),B1),(M(IN_TOP+K*(B1-T1)),B1)],"#DFF0E1")
panel([(ex(T2),T2),(M(ex(T2)),T2),(M(ex(B2)),B2),(ex(B2),B2)],"#F3E6D2")
o.append(header(M(1180)+ (ex(T1)+IN_TOP)/2-125,56,250,"V-MODEL UP-FRONT","#128C84"))
o.append(header(M((ex(T1)+IN_TOP)/2)-125,56,250,"V-MODEL AT-END","#2F9E44"))
o.append(header(590-90,B2+16,180,"SCRUM","#B7791F"))
# stages: two boxes per upper panel, evenly spaced top to bottom and centred between the slanted edges
BW,BH=230,52
gap=(B1-T1-2*BH)/3
y1=T1+gap+BH/2; y2=y1+BH+gap
cen=lambda y:(ex(y)+IN_TOP+K*(y-T1))/2      # centre line of the left panel, parallel to its edges
c1,c2=cen(y1),cen(y2)
box(c1,y1,BW,BH,"Requirement Gathering","#128C84")
box(c2,y2,BW,BH,"System Design","#0E6F68")
box(M(c1),y1,BW,BH,"Acceptance Testing","#2F9E44")
box(M(c2),y2,BW,BH,"System Testing","#237A35")
# arrows down the left, up the right, along the centre line
ya,yb=y1+BH/2+5,y2-BH/2-5
o.append(arrow([(cen(ya),ya),(cen(yb),yb)],2.6))
o.append(arrow([(M(cen(yb)),yb),(M(cen(ya)),ya)],2.6))
# test-plan links (blue, dashed, two-headed, white halo) with two-line labels
for y,(a,b),lab in ((y1,(c1+BW/2+10,M(c1)-BW/2-10),("ACCEPTANCE","TEST PLAN")),(y2,(c2+BW/2+10,M(c2)-BW/2-10),("SYSTEM","TEST PLAN"))):
    o.append(f'<line x1="{a:.1f}" y1="{y}" x2="{b:.1f}" y2="{y}" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>')
    o.append(arrow([(a,y),(b,y)],3.2,dashed=True,start=True).replace(f'stroke="{INK}"',f'stroke="{BLUE}"'))
    o.append(f'<rect x="{590-48}" y="{y-19}" width="96" height="38" rx="9" fill="#FFFFFF" stroke="{BLUE}" stroke-width="2.2"/>')
    o.append(f'<text x="590" y="{y-3}" class="tl" text-anchor="middle">{lab[0]}</text><text x="590" y="{y+12}" class="tl" text-anchor="middle">{lab[1]}</text>')
# scrum pills: the entry and exit run on the same centre line, so they are straight and parallel to the V
PW,PH=200,50
ys=394
sdx=cen(ys)
sd=(sdx,ys); ut=(M(sdx),ys); dv=(590,512)
box(*sd,PW,PH,"Software Design","#B7791F",25)
box(*ut,PW,PH,"Unit Testing","#B7791F",25)
box(*dv,PW,PH,"Development","#E8590C",25)
def blue_arrow(p,q):
    o.append(f'<line x1="{p[0]:.1f}" y1="{p[1]:.1f}" x2="{q[0]:.1f}" y2="{q[1]:.1f}" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>')
    a=arrow([p,q],3).replace(f'stroke="{INK}"',f'stroke="{BLUE}"')
    o.append(a)
y3,y4=y2+BH/2+6,ys-PH/2-8
blue_arrow((cen(y3),y3),(cen(y4),y4))
blue_arrow((M(cen(y4)),y4),(M(cen(y3)),y3))
# sprint circle inside the Scrum band, with arrowheads
cx,cy,r=590,412,58
o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="9"/>')
o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{BLUE}" stroke-width="3.4" stroke-dasharray="9 6"/>')
for th in (135,315):
    t=math.radians(th); px,py=cx+r*math.cos(t),cy+r*math.sin(t)
    dx,dy=math.sin(t),-math.cos(t)
    L,hw=18,8.5; bx,by=px-dx*L/2,py-dy*L/2; tx,ty=px+dx*L/2,py+dy*L/2
    o.append(f'<polygon points="{tx:.1f},{ty:.1f} {bx-dy*hw:.1f},{by+dx*hw:.1f} {bx+dy*hw:.1f},{by-dx*hw:.1f}" fill="{BLUE}" stroke="#FFFFFF" stroke-width="1.5"/>')
o.append(f'<text x="{cx}" y="{cy+5}" class="sp" text-anchor="middle">SPRINT</text>')
# phase arrows, parallel to the V edges and along the bottom
off=58
o.append(arrow([(ex(T1-6)-off,T1-6),(ex(B2)-off,B2)],3))
o.append(arrow([(M(ex(B2)-off),B2),(M(ex(T1-6)-off),T1-6)],3))
ang=math.degrees(math.atan2(1,K))
mx=(ex(T1)+ex(B2))/2-off-20; my=(T1+B2)/2
o.append(f'<text class="ph" transform="translate({mx:.1f},{my:.1f}) rotate({ang:.1f})" text-anchor="middle">VERIFICATION PHASE</text>')
o.append(f'<text class="ph" transform="translate({M(mx):.1f},{my:.1f}) rotate({-ang:.1f})" text-anchor="middle">VALIDATION PHASE</text>')
o.append(arrow([(ex(B2)+10,624),(M(ex(B2))-10,624)],3))
o.append('<text x="590" y="652" class="ph" text-anchor="middle">DEVELOPMENT PHASE</text>')
html=page("Garbage-Guard V-Model and Scrum Hybrid",W,H,"".join(o))
html=html.replace("</style>\n<rect",""".bx { font-size:15px; font-weight:800; fill:#FFFFFF }
.sp { font-size:14px; font-weight:800; fill:#0B5CAD; letter-spacing:.12em }
.tl { font-size:11.5px; font-weight:800; fill:#0B5CAD; letter-spacing:.08em }
.ph { font-size:14px; font-weight:800; fill:#6A6054; letter-spacing:.18em }
</style>
<rect""",1)
open(sys.argv[1],'w').write(html)
