import sys; from ggfig import *
W,H=1180,600
o=[]
def panel(pts,fill):
    p=" ".join(f"{x},{y}" for x,y in pts); ps=" ".join(f"{x+6},{y+6}" for x,y in pts)
    o.append(f'<polygon points="{ps}" fill="{INK}"/><polygon points="{p}" fill="{fill}" stroke="{INK}" stroke-width="2.5" stroke-linejoin="round"/>')
def box(cx,cy,w,h,t,color,r=9):
    x,y=cx-w/2,cy-h/2
    o.append(f'<rect x="{x+4}" y="{y+4}" width="{w}" height="{h}" rx="{r}" fill="{INK}"/><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{color}" stroke="{INK}" stroke-width="2.5"/>')
    o.append(f'<text x="{cx}" y="{cy+6}" class="bx" text-anchor="middle">{t}</text>')
def tag(cx,y,t,color,w):
    o.append(header(cx-w/2,y,w,t,color))
# panels
panel([(150,104),(560,104),(585,300),(240,300)],"#DDEFEC")
panel([(620,104),(1030,104),(940,300),(595,300)],"#DFF0E1")
panel([(240,322),(940,322),(840,520),(340,520)],"#F3E6D2")
tag(355,62,"V-MODEL UP-FRONT","#128C84",250)
tag(825,62,"V-MODEL AT-END","#2F9E44",250)
tag(590,536,"SCRUM SPRINTS","#B7791F",250)
# stages
box(340,155,240,54,"Requirement Gathering","#128C84")
box(395,248,200,54,"System Design","#0E6F68")
box(840,155,240,54,"Acceptance Testing","#2F9E44")
box(785,248,200,54,"System Testing","#237A35")
o.append(line([(345,182),(380,221)],2.4)); o.append(line([(835,182),(800,221)],2.4))
# scrum
o.append(f'<circle cx="590" cy="398" r="50" fill="none" stroke="{INK}" stroke-width="2.4" stroke-dasharray="7 5"/>')
o.append('<text x="590" y="404" class="sp" text-anchor="middle">SPRINT</text>')
box(425,392,200,52,"Software Design","#B7791F",26)
box(755,392,200,52,"Unit Testing","#B7791F",26)
box(590,470,220,52,"Development","#E8590C",26)
# flow arrows
o.append(arrow([(410,279),(428,362)],2.6))
o.append(arrow([(752,362),(770,279)],2.6))
# paired dashed arrows (verification <-> validation)
o.append(arrow([(464,155),(716,155)],2.2,dashed=True,start=True))
o.append(arrow([(499,248),(681,248)],2.2,dashed=True,start=True))
# side phase arrows
o.append(arrow([(92,100),(196,470)],3))
o.append(arrow([(984,470),(1088,100)],3))
o.append('<text class="ph" transform="translate(118,300) rotate(74.3)" text-anchor="middle">VERIFICATION PHASE</text>')
o.append('<text class="ph" transform="translate(1062,300) rotate(-74.3)" text-anchor="middle">VALIDATION PHASE</text>')
body="".join(o)
html=page("Garbage-Guard V-Model and Scrum Hybrid",W,H,body)
html=html.replace("</style>\n<rect",""".bx { font-size:15px; font-weight:800; fill:#FFFFFF }
.sp { font-size:13px; font-weight:800; fill:#16130F; letter-spacing:.12em }
.ph { font-size:14px; font-weight:800; fill:#6A6054; letter-spacing:.18em }
</style>
<rect""",1)
open(sys.argv[1],'w').write(html)
