import sys; from ggfig import *
o=[]; xs=[72,288,504,720,936]; w=172
top=[("Video Capture",["Frame from the","camera main stream"],"#128C84"),("Detection",["YOLO26n on","the Pi 5"],"#E8590C"),("Tracking",["ByteTrack keeps","each item's ID steady"],"#E8590C"),("Region Filter",["Detections off the","water discarded"],"#E8590C"),("Counting",["Items in the area,","median of 15 frames"],"#B7791F")]
for x,(t,s,c) in zip(xs,top): o.append(card(x,92,w,116,t,s,c,ty=141))
for x in xs[:-1]: o.append(arrow([(x+w+4,150),(x+216-2,150)]))
o.append(arrow([(1022,212),(1022,300),(764,300)]))
o.append(f'<polygon points="594,256 764,304 594,352 424,304" fill="{INK}"/><polygon points="590,252 760,300 590,348 420,300" fill="#E8A317" stroke="{INK}" stroke-width="2.5" stroke-linejoin="round"/>')
o.append(label(590,296,"Threshold reached?","middle","ct")); o.append(label(590,312,"default 20 items","middle","cs"))
bot=[("Touchscreen Dashboard",["Live feed, count, alert"],"#2F9E44"),("Buzzer",["Audible alarm"],"#D92D20"),("Event Store",["SQLite record and","a saved snapshot"],"#7048E8"),("Firebase",["Cloud sync"],"#1C7ED6"),("Android Application",["Push notification"],"#1C7ED6")]
for x,(t,s,c) in zip(xs,bot): o.append(card(x,460,w,100,t,s,c))
o.append(arrow([(420,300),(158,300),(158,456)])); o.append(label(170,292,"every frame","start"))
o.append(line([(590,348),(590,400)])); o.append(line([(374,400),(590,400)]))
o.append(arrow([(374,400),(374,456)])); o.append(arrow([(590,400),(590,456)])); o.append(label(602,392,"on alert","start"))
o.append(arrow([(680,510),(718,510)])); o.append(arrow([(896,510),(934,510)]))
o.append(label(72,590,"The count is the number of items inside the area now, so it falls as items drift out. An alert is stored with its snapshot; a frame that raises no alert is shown but not stored.","start","lg"))
open(sys.argv[1],'w').write(page("Garbage-Guard Block Diagram",1180,560,'<g transform="translate(0,-50)">'+"".join(o)+"</g>"))
