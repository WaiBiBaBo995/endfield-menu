// Attachment-specific visual checks. Uses installed tools/native-export dependencies.
import {chromium} from '../native-export/node_modules/playwright/index.mjs';
import {PNG} from '../native-export/node_modules/pngjs/lib/png.js';
import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'../..');
const assets=path.join(root,'src/main/resources/assets/apricityui/apricity/endfield');
const avatar=new PNG({width:8,height:8});for(let i=0;i<64;i++)avatar.data.set([0,255,0,255],i*4);
const server=http.createServer(async(req,res)=>{
 try {
  const name=new URL(req.url,'http://localhost').pathname.slice(1);
  if(req.method==='POST'){res.end();return;}
  if(name==='state.json'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({active:true,epoch:1,avatarReady:true,name:'GREEN QA',dimension:'OVERWORLD',labels:{resume:'返回游戏',inventory:'背包',advancements:'进度',stats:'统计',settings:'设置',mapReset:'地图复位',refresh:'刷新地形'}}));return;}
  if(name==='avatar.png'){res.setHeader('Content-Type','image/png');res.end(PNG.sync.write(avatar));return;}
  if(name.includes('..'))throw new Error('path');
  let bytes=await fs.readFile(name==='minecraft.js'?path.join(root,'tools/experiments/packed-alpha.js'):path.join(assets,name));
  if(name==='index.html')bytes=Buffer.from(bytes.toString().replaceAll(/<link[^>]*https:\/\/fonts\.[^>]*>\s*/g,'').replace('</body>','<script src="minecraft.js"></script></body>'));
  res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':'text/html;charset=utf-8');res.end(bytes);
 }catch{res.statusCode=404;res.end();}
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
try {
 const page=await browser.newPage({viewport:{width:1920,height:2224}});
 await page.route('https://**',r=>r.abort());
 await page.goto(`http://127.0.0.1:${server.address().port}/index.html?packed=1`);await page.waitForTimeout(1800);
 await page.evaluate(()=>{EF.scene.pause();EF.news.pause();document.getAnimations().forEach(a=>a.pause());for(let i=0;i<10000;i++){clearTimeout(i);cancelAnimationFrame(i);}window.setTimeout=()=>0;});
 const packed=PNG.sync.read(await page.screenshot());
 await page.addStyleTag({content:'html,body{background:transparent!important}'});
 const reference=PNG.sync.read(await page.screenshot({omitBackground:true}));
 let sum=0,bad=0,max=0;
 for(let y=0;y<1080;y++)for(let x=0;x<1920;x++){
  const a=reference.data[(y*1920+x)*4+3],b=packed.data[((y+1080)*1920+x)*4];
  const d=Math.abs(a-b);sum+=d;if(d>8)bad++;max=Math.max(max,d);
 }
 const result={alphaMeanError:sum/(1920*1080),alphaErrorAbove8Percent:bad/(1920*1080)*100,alphaMaxError:max};
 result.samples=[];
 for(const phase of ['in','out']){
  await page.evaluate(phase=>{document.documentElement.style.background='#000';document.body.style.background='#000';document.body.classList.remove('ef-in','ef-out','ef-closed');void document.body.offsetWidth;document.body.classList.add('ef-'+phase);},phase);
  for(const time of (phase==='in'?[80,240,520,880]:[65,195,330,500])){
   await page.evaluate(time=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=time;}),time);
   await page.addStyleTag({content:'html,body{background:#000!important}'});
   const color=PNG.sync.read(await page.screenshot());
   await page.addStyleTag({content:'html,body{background:transparent!important}'});
   const alpha=PNG.sync.read(await page.screenshot({omitBackground:true}));
   if(time===520)await fs.writeFile(path.join(root,'.cache/page-planes-520.png'),PNG.sync.write(color));
   let error=0,bad=0;
   for(let y=0;y<1080;y++)for(let x=0;x<1920;x++){const d=Math.abs(alpha.data[(y*1920+x)*4+3]-color.data[((y+1080)*1920+x)*4]);error+=d;if(d>8)bad++;}
   result.samples.push({phase,time,meanError:error/(1920*1080),above8Percent:bad/(1920*1080)*100});
  }
 }
 await fs.writeFile(path.join(root,'.cache/page-planes.json'),JSON.stringify(result,null,2));
 await fs.writeFile(path.join(root,'.cache/page-planes.png'),PNG.sync.write(packed));
 console.log(result);
} finally {await browser.close();server.close();}
