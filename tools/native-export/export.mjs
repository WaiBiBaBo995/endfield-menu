// Deterministic, attachment-specific HTML/CSS/SVG -> native planes and sampled tracks.
// Legacy 1.3 exporter retained for comparison. 1.4 runs the original HTML through AUI.
import { chromium } from 'playwright';
import { PNG } from 'pngjs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.join(root, 'src/main/resources/assets/endfieldmenu/native');
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
const page = await browser.newPage({ viewport:{width:1920,height:1080}, deviceScaleFactor:1 });
await page.route('https://**', r => r.abort());
await page.goto(pathToFileURL(path.join(root,'reference/endfield_menu-main/index.html')).href);
await page.waitForTimeout(1100);
await page.evaluate(() => { EF.scene.pause(); EF.news.pause(); document.body.className=''; });
await fs.mkdir(out,{recursive:true});
await page.addScriptTag({path:path.join(root,'tools/native-export/extract.js')});
const manifest = await page.evaluate(() => ExportScene.discover());
console.log(`Discovered ${manifest.nodes.length} nodes, ${manifest.planes.length} paint planes`);
const rate = 120;
for (const [phase,duration] of [['in',900],['out',520],['idle',90000]]) {
  const sampleRate=phase==='idle'?2:rate;
  console.log(`Sampling ${phase} at ${sampleRate} Hz`);
  manifest.tracks[phase] = await page.evaluate(async ({phase,duration,rate}) => ExportScene.sample(phase,duration,rate),{phase,duration,rate:sampleRate});
}
// Flat, isolated paint on a second page. Alpha is captured directly, without chroma keys.
const raster = await browser.newPage({viewport:{width:2400,height:1600},deviceScaleFactor:2});
await page.evaluate(() => ExportScene.rest());
const atlases=[]; let atlas=new PNG({width:2048,height:2048}); let ax=2,ay=2,rowH=0;
async function flushAtlas(){if(ax===2&&ay===2)return; const name=`atlas-${atlases.length}.png`;await fs.writeFile(path.join(out,name),PNG.sync.write(atlas));atlases.push(name);atlas=new PNG({width:2048,height:2048});ax=ay=2;rowH=0;}
let done=0;
for(const plane of manifest.planes){
  if(plane.kind!=='paint')continue;
  const snapshot=await page.evaluate(id=>ExportScene.paint(id),plane.id);
  if(!snapshot){plane.empty=true;continue;}
  if(snapshot.width>2400||snapshot.height>1600)await raster.setViewportSize({width:Math.max(2400,snapshot.width),height:Math.max(1600,snapshot.height)});
  await raster.setContent(snapshot.html);
  const bytes=await raster.screenshot({omitBackground:true,clip:{x:0,y:0,width:snapshot.width,height:snapshot.height},animations:'disabled'});
  const png=PNG.sync.read(bytes);let x0=png.width,y0=png.height,x1=0,y1=0;
  for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++)if(png.data[(y*png.width+x)*4+3]>1){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x+1);y1=Math.max(y1,y+1);}
  if(x1<=x0){plane.empty=true;continue;}
  const w=x1-x0,h=y1-y0;
  if(w>2044||h>2044){ // Large decorative planes get their own texture; never silently downsample.
    const name=`plane-${plane.id}.png`,image=new PNG({width:w,height:h});PNG.bitblt(png,image,x0,y0,w,h,0,0);await fs.writeFile(path.join(out,name),PNG.sync.write(image));plane.texture=name;plane.uv=[0,0,1,1];
  }else{
    if(ax+w>2046){ax=2;ay+=rowH+3;rowH=0;}
    if(ay+h>2046)await flushAtlas();
    PNG.bitblt(png,atlas,x0,y0,w,h,ax,ay);plane.texture=`atlas-${atlases.length}.png`;plane.uv=[ax/2048,ay/2048,(ax+w)/2048,(ay+h)/2048];ax+=w+3;rowH=Math.max(rowH,h);
  }
  plane.bounds=[snapshot.x+x0/2,snapshot.y+y0/2,w/2,h/2];
  if(++done%25===0)console.log(`Rasterized ${done} planes`);
}
await flushAtlas();
manifest.schema=1;manifest.rate=rate;manifest.width=1920;manifest.height=1080;
manifest.source='endfield_menu-main/index.html + components/*.js (unmodified)';
// Compact per-node tracks; keep adjacent equal samples at a step boundary, interpolate only between samples.
const same=(a,b)=>a.every((v,i)=>Math.abs(v-b[i])<.00001);
function compact(values){const r=[];for(let i=0;i<values.length;i++){if(i===0||i===values.length-1||!same(values[i].slice(1),values[i-1].slice(1))||!same(values[i].slice(1),values[i+1].slice(1)))r.push(values[i]);}return r;}
for(const n of manifest.nodes){n.tracks={};for(const phase of ['in','out','idle'])n.tracks[phase]=compact(manifest.tracks[phase].map(f=>[f.t,...f.n[n.id]]));}
for(const p of manifest.planes){p.tracks={};for(const phase of ['in','out','idle'])p.tracks[phase]=compact(manifest.tracks[phase].map(f=>[f.t,...f.fx[p.id]]));}
delete manifest.tracks;
await fs.writeFile(path.join(out,'scene.json'),JSON.stringify(manifest));
await fs.mkdir(path.join(root,'tools/native-export/preview-data'),{recursive:true});
await fs.copyFile(path.join(out,'scene.json'),path.join(root,'tools/native-export/preview-data/scene.json'));
for(const name of new Set(manifest.planes.filter(p=>p.texture).map(p=>p.texture)))await fs.writeFile(path.join(out,name+'.mcmeta'),JSON.stringify({texture:{blur:true,clamp:true}}));
await browser.close();
console.log(`Export complete: ${done} textures, ${atlases.length} atlases. ${out}`);
