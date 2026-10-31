import sys, math; from ggfig import *
W,H=1180,672
K=0.35          # slope of the V edges (dx per dy)
BLUE="#1C7ED6"
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
# stages (same size, stepped along the slope)
BW,BH=230,52
y1,y2=152,250
c1=ex(y1)+30+BW/2; c2=c1+K*(y2-y1)
box(c1,y1,BW,BH,"Requirement Gathering","#128C84")
box(c2,y2,BW,BH,"System Design","#0E6F68")
box(M(c1),y1,BW,BH,"Acceptance Testing","#2F9E44")
box(M(c2),y2,BW,BH,"System Testing","#237A35")
# arrows down the left, up the right
o.append(arrow([(c1+6,y1+BH/2+4),(c1+6+K*(y2-y1-BH-8),y2-BH/2-4)],2.6))
o.append(arrow([(M(c1+6+K*(y2-y1-BH-8)),y2-BH/2-4),(M(c1+6),y1+BH/2+4)],2.6))
# test-plan links (blue, dashed, two-headed)
for y,(a,b),lab in ((y1,(c1+BW/2+10,M(c1)-BW/2-10),"ACCEPTANCE TEST PLAN"),(y2,(c2+BW/2+10,M(c2)-BW/2-10),"SYSTEM TEST PLAN")):
    o.append(arrow([(a,y),(b,y)],2.6,dashed=True,start=True).replace(f'stroke="{INK}"',f'stroke="{BLUE}"'))
    o.append(f'<text x="590" y="{y-9}" class="tl" text-anchor="middle">{lab}</text>')
# scrum pills
PW,PH=200,50
sd=(590-172,394); ut=(590+172,394); dv=(590,512)
box(*sd,PW,PH,"Software Design","#B7791F",25)
box(*ut,PW,PH,"Unit Testing","#B7791F",25)
box(*dv,PW,PH,"Development","#E8590C",25)
# entry and exit of the sprints
o.append(arrow([(c2+10,y2+BH/2+6),(sd[0]-30,sd[1]-PH/2-6)],2.6))
o.append(arrow([(M(sd[0]-30),sd[1]-PH/2-6),(M(c2+10),y2+BH/2+6)],2.6))
# sprint circle on top, with arrowheads (left -> bottom -> right -> top)
cx,cy,r=590,412,82
o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="#FFFFFF" stroke-width="7"/>')
o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{BLUE}" stroke-width="3" stroke-dasharray="9 6"/>')
for th in (135,315):
    t=math.radians(th); px,py=cx+r*math.cos(t),cy+r*math.sin(t)
    dx,dy=math.sin(t),-math.cos(t)   # direction of decreasing angle
    L,hw=16,8; bx,by=px-dx*L/2,py-dy*L/2; tx,ty=px+dx*L/2,py+dy*L/2
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
.sp { font-size:13px; font-weight:800; fill:#1C7ED6; letter-spacing:.12em }
.tl { font-size:10.5px; font-weight:800; fill:#1C7ED6; letter-spacing:.1em }
.ph { font-size:14px; font-weight:800; fill:#6A6054; letter-spacing:.18em }
</style>
<rect""",1)
open(sys.argv[1],'w').write(html)
