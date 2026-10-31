import sys; from ggfig import *
o=[]; w=128; g=30; x0=(1180-(7*w+6*g))/2
cards=[("Data Collection",["Creek video and stills","in varied light"],"#128C84"),("Annotation",["Boxes drawn in","Roboflow: bottle, bag,","food container"],"#2FB3A8"),("Dataset Split",["70 / 10 / 20","train, validation, test"],"#B7791F"),("Training",["YOLO26n on the","RTX 2070 Max-Q","laptop, 640 px"],"#E8590C"),("Evaluation",["Precision, recall,","mAP@0.5 on the","frozen test set"],"#7048E8"),("NCNN Export",["Converted to the","format the Pi 5","runs fastest"],"#2F9E44"),("Deployment",["Default model on","the edge unit"],"#40C057")]
for i,(t,s,c) in enumerate(cards):
    x=x0+i*(w+g); cx=x+w/2
    o.append(f'<rect x="{x+4}" y="44" width="{w}" height="140" rx="10" fill="{INK}"/><rect x="{x}" y="40" width="{w}" height="140" rx="10" fill="#E9E1D6" stroke="{INK}" stroke-width="2.5"/><rect x="{x}" y="40" width="{w}" height="7" rx="3.5" fill="{c}"/>')
    o.append(f'<circle cx="{cx}" cy="74" r="13" fill="{c}" stroke="{INK}" stroke-width="2"/><text x="{cx}" y="79" class="hd" text-anchor="middle" style="font-size:12px;letter-spacing:0">{i+1}</text>')
    o.append(label(cx,108,t,"middle","ct"))
    for k,l in enumerate(s): o.append(label(cx,126+k+k*13,l,"middle","cs"))
    if i<6: o.append(arrow([(x+w+4,110),(x+w+g-2,110)],2.2))
x5=x0+4*(w+g)+w/2; x1=x0+w/2
o.append(arrow([(x5,188),(x5,236),(x1,236),(x1,188)],2.2,dashed=True))
o.append(label((x1+x5)/2,228,"Weak per-class results send the work back for more collection and annotation"))
open(sys.argv[1],'w').write(page("Garbage-Guard Model Training Pipeline",1180,262,"".join(o)))
