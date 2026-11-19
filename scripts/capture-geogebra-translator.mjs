// Capture the actual reference translator without starting its browser engine.
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
const reference=path.resolve(process.argv[2] || '/home/jesush/Downloads/geographer');
const output=process.argv[3] || fileURLToPath(new URL('../desktop/src/test/resources/geogebra-reference/translation-cases.json',import.meta.url));
const {createServer}=await import(pathToFileURL(path.join(reference,'node_modules/vite/dist/node/index.js')));
const vite=await createServer({root:reference,server:{middlewareMode:true}});
try {
 const {translateDesmosExpression,preprocessLatex}=await vite.ssrLoadModule('/src/engine/desmosTranslator.ts');
 const cases=['','a=+2.5','a=-2.5','c=pi','A=(1,2,3)','a=(1,2,3)','f(x)=x^2','f(x,y)=sin(x)*cos(y)','y=f(x)+a','y=2x^2+3x-1','y=mx+b','y=x^2{x>0}','y=sin(x){-pi<=x<=pi}','r=2sin(3θ)','r=theta{0<=theta<=4*pi}','z=sin(x)cos(y)','x^2+y^2+z^2=25','x^2+y^2<=9','x^2+y^2+z^2<9','-2<x<3','y={x<0:-x,x^2}','y={x<0:-x,x<2:x^2,3}','L=[1,2,3,4]','L=[1..10]','y=L[1]','y=sum(n=1,4,x^n/n)','y=prod(n=1,4,x+n)','y=d/dx(x^3-2x)',"y=f\'(x)",'integral(x^2,x,0,3)','integral(x^2,x)','sin(x)*cos(y)','x+y','sin(x)','y=|x-2|','y=abs(x-2)','z=sqrt(x^2+y^2)','(cos(t),sin(t),t){0<=t<=4*pi}','(cos(t),sin(t),t)','(1,2,3)','[1,2,3]','[1..10]','y=log(x)','y=arcsin(x)','y=sin(pi*x)','2+3*4','y=sin(x','y=sin(x))','y=\\frac{x^2+1}{2}','y=\\frac{d}{dx}(x^3)','y=\\sqrt{x^2+1}','y=\\sqrt[3]{x}','y=\\sum_{n=1}^{4} x^n/n','y=\\prod_{n=1}^{4} (x+n)','\\int_{0}^{3} x^2 dx','\\int sin(x) dx','y=\\left|x-2\\right|','y=\\sin(x)\\cos(x)','y=\\frac{\\sqrt{x}}{\\frac{x}{2}}'];
 const data=cases.map(input=>({input,preprocessed:preprocessLatex(input),...translateDesmosExpression(input,{internalLabel:'g',definedSymbols:new Set(['a','m','f','L']),definedFunctions:new Set(['f'])})}));
 await fs.writeFile(output,JSON.stringify(data,null,2));
 console.log(`Captured ${data.length} reference translations`);
} finally{await vite.close();}
