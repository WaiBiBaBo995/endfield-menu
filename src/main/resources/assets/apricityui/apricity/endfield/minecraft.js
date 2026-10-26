/* Runtime adapter: supplied HTML/components stay unchanged. AUI forwards real input. */
(() => {
  'use strict';
  const nativeHeight=Object.getOwnPropertyDescriptor(Window.prototype,'innerHeight')||Object.getOwnPropertyDescriptor(window,'innerHeight');
  const physicalHeight=()=>nativeHeight.get?nativeHeight.get.call(window):document.documentElement.getBoundingClientRect().height;
  const contentHeight=()=>Math.max(64,Math.floor(physicalHeight()-64));
  Object.defineProperty(window,'innerHeight',{configurable:true,get:contentHeight});
  Object.defineProperty(document.documentElement,'clientHeight',{configurable:true,get:contentHeight});
  const fitContent=()=>document.documentElement.style.setProperty('--ef-content-height',contentHeight()+'px');
  fitContent();addEventListener('resize',fitContent);

  const post=(path,body)=>fetch(path,{method:'POST',headers:{'X-Endfield-Menu':'1'},body}).catch(()=>{});
  const errors=[],inputs=[];
  let inputSequence=0,inputTime=0;
  addEventListener('error',e=>errors.push(String(e.message)));
  addEventListener('unhandledrejection',e=>errors.push(String(e.reason)));
  const mapping={operators:'resume',events:'inventory',headhunt:'advancements',shop:'stats',
    handbook:'settings',pass:'map-reset',region:'view-reset',friends:'refresh',plan:'map-reset',brief:'refresh',mail:'advancements',alerts:'stats',settings:'settings'};
  document.querySelectorAll('[data-action]').forEach(b=>{
    const old=b.dataset.action,a=old==='close'?(b.classList.contains('ef-power')?'quit':'resume'):mapping[old];
    if(a)b.dataset.action=a;else{b.removeAttribute('data-action');b.disabled=true;b.dataset.locked='true';}
  });
  const style=document.createElement('style');
  style.textContent=`
    html,body{background:#00ff00!important}
    .ef-overlay{bottom:auto!important;height:var(--ef-content-height)!important;overflow:hidden!important}
    body.ef-mc-lossless,html.ef-mc-lossless{background:transparent!important}
    .ef-wallpaper,.ef-post,.ef-closed-hint{display:none!important}
    body.ef-mc-suspended *{animation-play-state:paused!important}
    .ef-core__map{pointer-events:auto;touch-action:none;cursor:grab;background:none!important}
    .ef-core__map:active{cursor:grabbing}
    .ef-core__win::after{display:none!important}
    .ef-mc-portal{position:absolute;inset:0;width:100%;height:100%;pointer-events:none}
    .ef-badge__avatar{background:#24272a!important}
    .ef-badge__avatar img{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;image-rendering:pixelated;opacity:0}
    .ef-badge__avatar img.ready{opacity:1}
    .ef-mc-legend{position:absolute;left:34%;right:20%;bottom:3.2%;text-align:center;color:#b9bdc0;font:12px var(--ef-font-mono);pointer-events:none}
    .ef-mc-legend i{display:inline-block;width:18px;height:3px;vertical-align:middle;margin:0 5px 0 16px}
    .ef-mc-legend small{display:block;font:11px var(--ef-font-cn);margin-top:8px}
    [data-locked]{opacity:.42;cursor:default}
  `;
  document.head.append(style);
  // Binary luminance blocks survive both JPEG and PNG, unlike individual float pixels.
  const map=document.querySelector('.ef-core__map'),portal=document.createElement('div');
  portal.className='ef-mc-portal';portal.style.background='#ff00ff';map.append(portal);
  const probes=[[0,0],[100,0],[100,100],[0,100]].map(([x,y])=>{
    const p=document.createElement('i');p.style.cssText=`position:absolute;left:${x}%;top:${y}%;width:0;height:0;pointer-events:none`;map.append(p);return p;
  });
  const avatarProbes=[[0,0],[100,0],[100,100],[0,100]].map(([x,y])=>{
    const p=document.createElement('i');p.style.cssText=`position:absolute;left:${x}%;top:${y}%;width:0;height:0;pointer-events:none`;
    document.querySelector('.ef-badge__avatar').append(p);return p;
  });
  const source=document.querySelector('.ef-overlay');
  dispatchEvent(new Event('resize'));
  const header=document.createElement('canvas');header.width=256;header.height=64;
  header.style.cssText='position:fixed;left:0;top:var(--ef-content-height);width:256px;height:64px;z-index:2147483647;pointer-events:none';document.body.append(header);
  const headerContext=header.getContext('2d',{alpha:false});
  const headerImage=headerContext.createImageData(256,64),headerPixels=new Uint32Array(headerImage.data.buffer);
  const stampBytes=new Uint8Array(96),previousStamp=new Int16Array(96).fill(-1),stampValues=new DataView(stampBytes.buffer);
  let stampHandle=0,stampTimer=0;
  function stamp(){
    cancelAnimationFrame(stampHandle);clearTimeout(stampTimer);
    const bytes=stampBytes,values=stampValues;bytes.set([69,70,77,50]);
    probes.forEach((p,i)=>{const r=p.getBoundingClientRect();values.setFloat32(4+i*8,r.x,true);values.setFloat32(8+i*8,r.y,true);});
    bytes[36]=state.lossless?0:1;bytes[37]=active?1:0;values.setInt32(40,epoch,true);
    values.setFloat32(44,yaw,true);values.setFloat32(48,tilt,true);values.setFloat32(52,zoom,true);
    values.setUint32(56,inputSequence,true);values.setUint32(60,inputTime,true);
    avatarProbes.forEach((p,i)=>{const r=p.getBoundingClientRect();values.setFloat32(64+i*8,r.x,true);values.setFloat32(68+i*8,r.y,true);});
    let changed=false;
    for(let n=0;n<96;n++)if(previousStamp[n]!==bytes[n]){
      previousStamp[n]=bytes[n];changed=true;
      for(let bit=0;bit<8;bit++){const i=n*8+bit,color=(bytes[n]&(1<<bit))?0xffffffff:0xff000000;
        for(let row=0;row<4;row++){const start=(Math.floor(i/64)*4+row)*256+(i%64)*4;headerPixels.fill(color,start,start+4);}
      }
    }
    if(changed)headerContext.putImageData(headerImage,0,0);
    if(active)stampHandle=requestAnimationFrame(stamp);else stampTimer=setTimeout(stamp,1000);
  }
  const legend=document.createElement('div');legend.className='ef-mc-legend';document.querySelector('.ef-stage').append(legend);
  let state={},active=false,closing=false,epoch=0,yaw=0,tilt=.95,zoom=1.45,dragging=false,dragX=0,dragY=0,entranceTimer=0,closeTimer=0,reportHandle=0,lastReport=0,labelState='';
  stamp();
  document.body.classList.add('ef-closed','ef-mc-suspended');
  window.EF.scene.pause();window.EF.news.pause();
  // app.js schedules its initial entrance 80 ms later; keep hidden until activated.
  setTimeout(()=>{if(!active){document.body.classList.remove('ef-in');document.body.classList.add('ef-closed');}},100);
  window.endfieldSetActive=(value,serial=state.epoch||0)=>{
    if(serial<epoch)return;
    if(active===value&&(!value||epoch===serial))return;epoch=serial;
    active=value;dragging=false;clearTimeout(closeTimer);clearTimeout(entranceTimer);
    document.body.classList.toggle('ef-mc-suspended',!active);
    if(active){
      closing=false;document.body.classList.remove('ef-out','ef-closed','ef-in');void document.body.offsetWidth;
      document.body.classList.add('ef-in');entranceTimer=setTimeout(()=>document.body.classList.remove('ef-in'),900);
      window.EF.scene.resume();window.EF.news.resume();report();stamp();
    }else{document.body.classList.add('ef-closed');window.EF.scene.pause();window.EF.news.pause();}
  };
  function action(name){
    if(closing)return;
    if(name==='map-reset'||name==='view-reset'){
      yaw=0;tilt=.95;zoom=1.45;
      if(name==='view-reset')document.dispatchEvent(new MouseEvent('mousemove',{clientX:innerWidth/2,clientY:innerHeight/2}));
      report();return;
    }
    if(name==='resume'){
      closing=true;clearTimeout(entranceTimer);window.EF.scene.pause();window.EF.news.pause();
      document.body.classList.remove('ef-in');void document.body.offsetWidth;document.body.classList.add('ef-out');
      post('action','close');
    }else post('action',name);
  }
  window.endfieldRequestClose=()=>action('resume');
  function insideMap(e){
    if(e.target.closest('[data-action],.ef-right,.ef-badge,.ef-news'))return false;
    const r=map.getBoundingClientRect();
    return Math.hypot((e.clientX-r.x-r.width/2)/(r.width/2),(e.clientY-r.y-r.height/2)/(r.height/2))<.95;
  }
  let pressed=null,pressX=0,pressY=0,lastAction=0;
  ['pointerdown','pointerup','wheel','keydown'].forEach(type=>document.addEventListener(type,e=>{
    inputSequence++;inputTime=Date.now()>>>0;
    inputs.push({type,x:e.clientX,y:e.clientY,key:e.key,target:e.target.closest('[data-action]')?.dataset.action||e.target.tagName});if(inputs.length>12)inputs.shift();
  },true));
  document.addEventListener('pointerdown',e=>{
    if(!active||closing||e.button!==0)return;
    if(insideMap(e)){
      dragging=true;dragX=e.clientX;dragY=e.clientY;map.setPointerCapture(e.pointerId);e.preventDefault();e.stopImmediatePropagation();return;
    }
    pressed=e.target.closest('[data-action]')?.dataset.action||null;pressX=e.clientX;pressY=e.clientY;
  },true);
  document.addEventListener('pointermove',e=>{
    if(!dragging)return;const s=window.EF.stageScale||1;
    yaw+=(e.clientX-dragX)/s*.008;tilt=Math.max(.25,Math.min(1.25,tilt+(e.clientY-dragY)/s*.004));
    dragX=e.clientX;dragY=e.clientY;report();e.preventDefault();e.stopImmediatePropagation();
  },true);
  document.addEventListener('pointerup',e=>{
    if(dragging){dragging=false;report();e.preventDefault();e.stopImmediatePropagation();return;}
    const a=pressed;pressed=null;
    if(a&&Math.hypot(e.clientX-pressX,e.clientY-pressY)<6){lastAction=performance.now();action(a);}
  },true);
  document.addEventListener('pointercancel',()=>{pressed=null;dragging=false;},true);
  document.addEventListener('click',e=>{
    const a=e.target.closest('[data-action]');if(!a){if(e.target.id==='efMenu'){e.preventDefault();e.stopImmediatePropagation();action('resume');}return;}
    e.preventDefault();e.stopImmediatePropagation();if(performance.now()-lastAction>500)action(a.dataset.action);
  },true);
  document.addEventListener('wheel',e=>{
    if(!insideMap(e))return;zoom=Math.max(.5,Math.min(3,zoom*Math.pow(1.12,-Math.sign(e.deltaY))));report();e.preventDefault();e.stopImmediatePropagation();
  },{capture:true,passive:false});
  document.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();e.stopImmediatePropagation();action('resume');}},true);

  function text(selector,value){const el=document.querySelector(selector);if(el&&el.textContent!==String(value))el.textContent=String(value);}
  function labelText(label,value,maxWidth){
    if(!value||label.dataset.mcValue===value)return;label.dataset.mcValue=value;
    if(!label.dataset.mcCenter){const a=(label.getAttribute('x')||'0').split(/\s+/).map(Number);label.dataset.mcCenter=(a[0]+a[a.length-1])/2;label.dataset.mcSize=label.getAttribute('font-size')||22;}
    const chars=Array.from(value),units=chars.reduce((n,c)=>n+(/[^\x00-\x7f]/.test(c)?1:.56),0),size=Math.min(+label.dataset.mcSize,maxWidth/Math.max(1,units));
    const advances=chars.map(c=>size*(/[^\x00-\x7f]/.test(c)?1.02:.56));
    let x=+label.dataset.mcCenter-advances.reduce((a,b)=>a+b,0)/2+advances[0]/2;
    label.setAttribute('font-size',size);label.setAttribute('x',advances.map(w=>{const p=x;x+=w;return p;}).join(' '));label.textContent=value;
  }
  function updateLabels(){
    const labels=state.labels||{};
    source.querySelectorAll('[data-action]').forEach(b=>{
      const key={'map-reset':'mapReset','view-reset':'viewReset'}[b.dataset.action]||b.dataset.action;
      if(!labels[key])return;b.title=labels[key];b.setAttribute('aria-label',labels[key]);
      const t=b.querySelector('text');if(t)labelText(t,labels[key],b.classList.contains('ef-card')?215:92);
    });
    source.querySelectorAll('[data-locked]').forEach(b=>b.title=labels.locked||'');
    text('.ef-badge__name',state.name||'');text('.ef-badge__uid','UID:'+(state.id||''));text('.ef-badge__sub',state.dimension||'');text('.ef-badge__lv',state.level||0);
    text('.ef-badge__permtxt small',labels.advancements||'');text('.ef-badge__permtxt b',state.level||0);
    source.querySelectorAll('.ef-disclab text,.ef-disclab-sh text').forEach(t=>labelText(t,t.closest('.ef-dctab--plan')?labels.mapReset:labels.refresh,130));
    text('.ef-cur--a .ef-cur__num',state.level||0);text('.ef-cur--b .ef-cur__num',Math.floor(state.x||0)+', '+Math.floor(state.z||0));
    text('.ef-stamina b',(state.health||0)+'/20');text('.ef-energy > b:first-of-type',state.food||0);text('.ef-energy > small:first-of-type','/20');text('.ef-energy__pct',Math.round((state.food||0)*5));text('.ef-lvl__face > b',state.level||0);
    text('.ef-netinfo span:first-of-type',state.worldState||'');text('.ef-netinfo span:last-of-type',(state.dimension||'')+' · '+Math.floor(state.x||0)+' / '+Math.floor(state.y||0)+' / '+Math.floor(state.z||0));
    const avatar=document.querySelector('.ef-badge__avatar');
    if(!avatar.querySelector('img')){[...avatar.childNodes].filter(n=>!avatarProbes.includes(n)).forEach(n=>n.remove());avatar.append(document.createElement('img'));}
    const img=avatar.querySelector('img');if(state.avatarReady&&!img.getAttribute('src')){img.onload=()=>img.classList.add('ready');img.onerror=()=>img.removeAttribute('src');img.src='avatar.png';}
    const key=JSON.stringify(state.legend);if(legend.dataset.key!==key){legend.dataset.key=key;legend.replaceChildren();
      (state.legend||[]).forEach(a=>{const i=document.createElement('i');i.style.background=a.color;legend.append(i,document.createTextNode('Y '+a.y));});
      legend.append(document.createElement('small'));
    }
    const small=legend.querySelector('small');if(small){const value=[labels.controls,state.survey,state.interval].filter(Boolean).join(' · ');if(small.textContent!==value)small.textContent=value;}
  }
  function controlPoint(b){
    const r=(b.querySelector('text')||b).getBoundingClientRect(),candidates=[[r.x+r.width/2,r.y+r.height/2]];
    const box=b.getBoundingClientRect();for(let y=1;y<5;y++)for(let x=1;x<5;x++)candidates.push([box.x+box.width*x/5,box.y+box.height*y/5]);
    for(const [x,y]of candidates){const hit=document.elementFromPoint(x,y);if(hit&&(hit===b||b.contains(hit)))return{x,y,hit:true,visible:true};}
    return{x:r.x+r.width/2,y:r.y+r.height/2,hit:false,visible:false};
  }
  function report(){
    clearTimeout(reportHandle);
    const now=performance.now();if(active&&state.diagnostics&&now-lastReport<120){reportHandle=setTimeout(report,120-(now-lastReport));return;}
    if(active&&state.diagnostics){lastReport=now;const controls={};source.querySelectorAll('[data-action]').forEach(b=>{const p=controlPoint(b),old=controls[b.dataset.action];if(!old||!old.hit&&p.hit)controls[b.dataset.action]=p;});
      const r=map.getBoundingClientRect(),right=document.querySelector('.ef-right__viewport'),world=document.querySelector('.ef-world');
      const tile=document.querySelector('.ef-rtile'),tileRect=tile?.getBoundingClientRect();
      post('report',JSON.stringify({ready:true,active,closing,epoch,yaw,tilt,zoom,width:innerWidth,height:innerHeight,map:{x:r.x+r.width/2,y:r.y+r.height/2},right:controlPoint(right),tileY:tileRect?.y,camera:getComputedStyle(world).transform,backgroundBackend:'live-gpu-shader',controls,inputs,errors:errors.slice(-8)}));
    }
    reportHandle=setTimeout(report,active&&state.diagnostics?120:1000);
  }
  async function poll(){try{state=await(await fetch('state.json')).json();
    document.documentElement.classList.toggle('ef-mc-lossless',!!state.lossless);document.body.classList.toggle('ef-mc-lossless',!!state.lossless);
    const labels=JSON.stringify([state.name,state.id,state.dimension,state.level,Math.floor(state.x||0),Math.floor(state.y||0),Math.floor(state.z||0),state.health,state.food,state.labels,state.legend,state.avatarReady,state.survey,state.interval,state.worldState]);
    if(labels!==labelState){labelState=labels;updateLabels();}
    if(typeof state.active==='boolean'&&(!epoch||state.epoch>epoch))window.endfieldSetActive(state.active,state.epoch);
  }catch(e){errors.push(String(e));}setTimeout(poll,active?200:500);}
  poll();report();
})();
