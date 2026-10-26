from pathlib import Path
import cv2, numpy as np, json

OUT=Path(__file__).parent
FILES={
 'A': r'C:\Users\360he\.t3\userdata\attachments\c304925c-625f-47cf-a140-18710b32e7ac-c1805930-ac76-4d79-b652-f4be46fbd4fc-mp4.mp4',
 'B': r'C:\Users\360he\.t3\userdata\attachments\c304925c-625f-47cf-a140-18710b32e7ac-4d42e3b0-fda9-4106-9f99-ffebc0eed287-mp4.mp4'
}
for key,path in FILES.items():
    dest=OUT/f'{key}-touch';dest.mkdir(exist_ok=True)
    cap=cv2.VideoCapture(path); detections=[]; last=-1
    while True:
        ok,im=cap.read()
        if not ok:break
        t=cap.get(cv2.CAP_PROP_POS_MSEC)/1000
        if t-last<.09:continue
        last=t
        small=cv2.resize(im,(540,1200));b,g,r=cv2.split(small)
        mask=((b.astype(int)-r.astype(int)>18)&(b>180)&(r>140)&(r<235)&(g>155)).astype('uint8')*255
        mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8))
        contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        found=[]
        for c in contours:
            x,y,w,h=cv2.boundingRect(c)
            if 13<=w<=48 and 13<=h<=48 and .65<w/h<1.5 and cv2.contourArea(c)>40:
                found.append([2*x+w,2*y+h,2*w,2*h])
        if found:
            detections.append({'t':round(t,3),'points':found})
            cv2.imwrite(str(dest/f'{t:06.2f}.jpg'),im,[cv2.IMWRITE_JPEG_QUALITY,86])
    (OUT/f'{key}-touches.json').write_text(json.dumps(detections,indent=2))
    print(key,len(detections))
    for d in detections:print(d['t'],d['points'])
