// Run against the unmodified geographer Vite app. Requires Playwright on NODE_PATH
// or an absolute module path in PLAYWRIGHT_MODULE and optionally BROWSER_PATH.
import fs from 'node:fs/promises';
import path from 'node:path';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const output=path.resolve(process.argv[2] || 'desktop/src/test/resources/geogebra-reference');
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({executablePath:process.env.BROWSER_PATH,headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
const page=await browser.newPage({viewport:{width:1080,height:760},deviceScaleFactor:1});
const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(process.env.REFERENCE_URL || 'http://127.0.0.1:4380');
await page.getByRole('button',{name:'3D',exact:true}).click();
await page.waitForFunction(()=>window.ggbApplet3D?.getAllObjectNames,null,{timeout:120000});
await page.waitForTimeout(1200);
await page.addStyleTag({content:'main > div.absolute.top-3, main > div.absolute.bottom-3, main > button {visibility:hidden!important}'});
const baseSettings=await page.evaluate(async()=> (await import('/src/types/calculator.ts')).DEFAULT_SETTINGS);
const scenes=[
    {name:'empty',expressions:[]},
    {name:'wave',expressions:[['z=sin(x)*cos(y)','#2d70b3']]},
    {name:'sphere',expressions:[['x^2+y^2+z^2=9','#c74440']]},
    {name:'wave-plane',expressions:[['z=a*sin(x)*cos(y)','#2d70b3'],['a=1.5','#c74440'],['x+y+z=4','#fa7e19']]},
    {name:'helix-point',expressions:[['z=0.2*(x^2+y^2)','#388c46'],['(2*cos(t),2*sin(t),0.35*t){0<=t<=4*pi}','#c74440'],['(0,0,2)','#000000']]},
    {name:'cylinder-curve',expressions:[['y=a*sin(b*x)','#2d70b3'],['a=2','#c74440'],['b=1.5','#388c46'],['x^2+y^2=9','#c74440']]},
];
for(const scene of scenes) {
    await page.evaluate(async({expressions,settings})=>{
        const api=window.ggbApplet3D;api.getAllObjectNames().forEach(n=>api.deleteObject(n));
        const {GeoGebraEngineAdapter}=await import('/src/engine/geogebraAdapter.ts');
        const {createExpressionItem}=await import('/src/engine/presets.ts');
        const adapter=new GeoGebraEngineAdapter(api,'3d');
        adapter.applySettings(settings,true);
        window.referenceAdapter=adapter;
        window.referenceFeedback=adapter.syncExpressions(expressions.map(([latex,color],index)=>createExpressionItem({id:`fixture_${index}`,type:'expression',latex,color})),settings);
    },{expressions:scene.expressions,settings:baseSettings});
    await page.waitForTimeout(1500);
    const fixture=await page.evaluate(({name,expressions,settings})=>{
        const api=window.ggbApplet3D;
        const canvases=Array.from(document.querySelectorAll('[data-testid="geogebra-viewport-3d"] canvas'));
        const canvas=canvases.find(c=>c.width>100 && c.getBoundingClientRect().width>100);
        const bounds=canvas?.getBoundingClientRect().toJSON();
        return {name,expressions,settings,xml:api.getXML(),feedback:window.referenceFeedback,
            objects:api.getAllObjectNames().map(label=>({label,type:api.getObjectType(label),definition:api.getDefinitionString(label),xml:api.getXML(label)})),
            bounds,canvases:canvases.map(c=>({width:c.width,height:c.height,bounds:c.getBoundingClientRect().toJSON(),className:c.className})),png:canvas.toDataURL('image/png').split(',')[1]};
    },{...scene,settings:baseSettings});
    if(!fixture.xml.includes('version="5.4.920.0"'))throw new Error('Reference deployment changed; keep captures aligned with the pinned source version');
    await page.screenshot({path:path.join(output,`${scene.name}.png`),clip:fixture.bounds});
    delete fixture.png;
    await fs.writeFile(path.join(output,`${scene.name}.json`),JSON.stringify(fixture,null,2)+'\n');
    console.log(scene.name,fixture.bounds,fixture.feedback);
}
await page.addStyleTag({content:'main > div.absolute.top-3 {visibility:visible!important}'});
// Same button behavior and native pointer sequence, with captured camera matrices.
await page.evaluate(()=>{const api=window.ggbApplet3D;api.getAllObjectNames().forEach(n=>api.deleteObject(n));});
await page.evaluate(settings=>window.referenceAdapter.applySettings(settings,true),baseSettings);
await page.waitForTimeout(1200);
const rect=await page.locator('[data-testid="geogebra-viewport-3d"] canvas').first().boundingBox();
const interactions=[];
const save=async(name,events)=>interactions.push({name,events,xml:await page.evaluate(()=>window.ggbApplet3D.getXML())});
await save('initial',[]);
for(const [name,button,modifiers] of [['left-orbit','left',[]],['shift-pan','left',['Shift']],['middle-drag','middle',[]],['right-drag','right',[]]]) {
    const events=[];for(const key of modifiers)await page.keyboard.down(key);
    await page.mouse.move(rect.x+130,rect.y+150);await page.mouse.down({button});
    for(let i=1;i<=8;i++){const x=130+10*i,y=150+5*i;await page.mouse.move(rect.x+x,rect.y+y);events.push({x,y});await page.waitForTimeout(25);}
    await page.mouse.up({button});for(const key of modifiers)await page.keyboard.up(key);
    await page.waitForTimeout(100);await save(name,{button,modifiers,start:{x:130,y:150},moves:events});
    // Wait for possible rotation inertia and record its result separately.
    await page.waitForTimeout(1200);await save(name+'-settled',[]);
}
await page.mouse.move(rect.x+250,rect.y+300);await page.mouse.wheel(0,-100);await page.waitForTimeout(1000);await save('wheel-in',{x:250,y:300,delta:-100});
await page.getByRole('button',{name:'Zoom in',exact:true}).click();await page.waitForTimeout(1000);await save('zoom-button',[]);
await page.getByRole('button',{name:'Zoom in',exact:true}).click();await page.waitForTimeout(1000);await save('zoom-button-repeat',[]);
await page.getByRole('button',{name:'Reset view',exact:true}).click();await page.waitForTimeout(1000);await save('home-button',[]);
await fs.writeFile(path.join(output,'interactions.json'),JSON.stringify({viewport:rect,interactions,errors},null,2)+'\n');
// Object picking/highlighting and keyboard mechanics at the same fixed view.
await page.evaluate(xml=>window.ggbApplet3D.setXML(xml),interactions[0].xml);
await page.evaluate(()=>{
    const api=window.ggbApplet3D;
    api.getAllObjectNames().forEach(n=>api.deleteObject(n));
    api.evalCommand('A=(1,2,3)');api.setColor('A',128,64,176);api.setPointSize('A',5);api.setLabelVisible('A',false);
});
await page.waitForTimeout(700);
const point={x:395.81595092024537,y:228.38650306748465};
const canvas=page.locator('[data-testid="geogebra-viewport-3d"] canvas').first();
const objectBounds=await canvas.boundingBox();
const records=[];
const saveObject=async(name)=>{
    await canvas.screenshot({path:path.join(output,`${name}.png`)});
    records.push({name,xml:await page.evaluate(()=>window.ggbApplet3D.getXML()),bounds:objectBounds});
};
await saveObject('point');
await page.mouse.move(objectBounds.x+point.x,objectBounds.y+point.y);await page.waitForTimeout(500);await saveObject('point-hover');
await page.mouse.click(objectBounds.x+point.x,objectBounds.y+point.y);await page.waitForTimeout(400);await saveObject('point-selected');
const keys=[];
for(const key of ['ArrowRight','Shift+ArrowUp','Delete','Control+=']) {
    await page.keyboard.press(key);await page.waitForTimeout(500);
    keys.push({key,xml:await page.evaluate(()=>window.ggbApplet3D.getXML()),objects:await page.evaluate(()=>window.ggbApplet3D.getAllObjectNames())});
}
await fs.writeFile(path.join(output,'object-interactions.json'),JSON.stringify({point,records,keys},null,2)+'\n');
await browser.close();
if(errors.length)throw new Error(errors.join('\n'));
