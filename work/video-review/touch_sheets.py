from pathlib import Path
from PIL import Image,ImageDraw
import json
ROOT=Path(r'C:\Users\360he\AppData\Local\Temp\video-bug-review')
OUT=Path(__file__).parent
def sheet(key,name,times,box=(0,240,1080,1570),width=540):
    src=ROOT/f'{key}-seq';index=json.loads((src/'index.json').read_text())
    h=round((box[3]-box[1])*width/(box[2]-box[0])); stride=h+30
    im=Image.new('RGB',(2*width,((len(times)+1)//2)*stride),'#ddd');d=ImageDraw.Draw(im)
    for j,t in enumerate(times):
        actual,p=min(index,key=lambda p:abs(p[0]-t))
        frame=Image.open(src/p).crop(box).resize((width,h))
        x=j%2*width;y=j//2*stride;im.paste(frame,(x,y+30));d.text((x+8,y+7),f'{key} {actual:.3f}s',fill='black')
    im.save(OUT/f'{key}-tap-{name}.jpg',quality=94)

sheet('A','caret1',[9.94,10.2,10.54,10.8,11.0,11.25,11.54,11.8],(80,680,780,980))
sheet('A','caret2',[14.63,14.9,15.3,15.6,15.97,16.25,17.5,18.15],(80,680,780,980))
sheet('A','paren',[20.81,21.15,21.92,22.2,23.07,23.4,23.74,24.1],(80,680,780,980))
sheet('A','caret3',[25.94,26.2,26.58,26.85,27.76,28.1,29.64,29.95],(80,680,780,980))
sheet('A','args',[30.37,30.7,31.12,31.4,32.61,32.95,33.76,34.1],(80,680,780,1280))
sheet('A','pi',[35.23,35.5,35.72,36.0,37.45,37.8,38.64,39.0],(80,980,780,1290))
sheet('A','blank',[39.55,40.2,40.97,41.5,42.3,42.7,43.37,44.0])
sheet('B','start',[1.32,1.6,2.25,2.6,3.0,3.5,4.0,5.0],(80,240,780,920))
sheet('B','caret1',[6.85,7.1,7.48,7.6,7.8,8.3,8.62,9.2],(80,240,780,540))
sheet('B','caret2',[9.54,9.85,10.7,11.0,11.52,11.8,12.16,12.4],(80,240,780,540))
sheet('B','longpress',[12.6,12.95,13.3,14.0,15.25,15.7,16.22,16.5],(0,240,900,690))
sheet('B','drag',[16.97,17.15,17.57,17.91,18.33,18.85,19.4,19.7],(80,240,780,540))
sheet('B','select',[19.96,20.36,20.7,21.2,21.7,22.4,22.8,23.3],(80,240,780,540))
sheet('B','blank1',[23.3,24.0,25.46,25.9])
sheet('B','blank2',[26.53,26.9,27.28,27.6])
sheet('B','rowfocus',[28.8,29.0,29.25,29.6,30.32,30.7],(0,500,1080,1430))
sheet('B','divide',[32.8,33.1,33.5,34.0],(80,1050,1080,1390))
sheet('B','blank3',[36.49,36.85,38.27,38.5])
sheet('B','blank4',[38.76,39.15,39.83,40.2])
sheet('B','scroll',[41.03,41.46,41.8,42.4])
print('done')
