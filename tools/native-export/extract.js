/* Runs in the source document, exclusively during export. Matrices use CSS column-major order. */
window.ExportScene=(()=>{
  const nodes=[],planes=[],index=new Map(),entries=[];
  const dynamic={'.ef-badge__name':'player','.ef-badge__sub':'dimension','.ef-badge__lv':'level','.ef-badge__uid':'uuid','.ef-badge__permtxt b':'level','.ef-core__lvl b':'level','.ef-cur--a .ef-cur__num':'level','.ef-cur--b .ef-cur__num':'time','.ef-stamina b':'health','.ef-energy b:not(.ef-energy__pct)':'food'};
  const actions={operators:'resume',events:'inventory',headhunt:'advancements',shop:'stats',handbook:'settings',pass:'map-reset',region:'view-reset',friends:'refresh',plan:'map-reset',brief:'refresh',mail:'advancements',alerts:'stats',settings:'settings'};
  const num=v=>parseFloat(v)||0, ns='http://www.w3.org/2000/svg';
  const svgOffsets=new WeakMap();
  const mat=v=>v&&v!=='none'?new DOMMatrix(v):new DOMMatrix();
  const translate=(x=0,y=0,z=0)=>new DOMMatrix().translate(x,y,z);
  function absolute(e){let x=0,y=0;for(let n=e;n;n=n.offsetParent){x+=n.offsetLeft||0;y+=n.offsetTop||0;}return [x,y];}
  function local(e,pseudo){
    const c=getComputedStyle(e,pseudo||null), svg=e instanceof SVGElement&&e.tagName!=='svg';
    const w=svg?e.ownerSVGElement.viewBox.baseVal.width:e.offsetWidth,h=svg?e.ownerSVGElement.viewBox.baseVal.height:e.offsetHeight;
    let x=0,y=0;
    if(!svg){
      if(e instanceof SVGElement){
        let offset=svgOffsets.get(e);
        if(!offset){const proxy=document.createElement('div');for(const k of c)proxy.style.setProperty(k,c.getPropertyValue(k));proxy.style.transform='none';proxy.style.visibility='hidden';if(c.display==='inline')proxy.style.display='inline-block';
          const original=e.style.display;e.style.display='none';e.parentElement.insertBefore(proxy,e);let a=absolute(proxy),p=absolute(e.parentElement);offset=[a[0]-p[0],a[1]-p[1]];proxy.remove();e.style.display=original;svgOffsets.set(e,offset);}
        [x,y]=offset;
      }else{let a=absolute(e),p=absolute(e.parentElement);x=a[0]-p[0];y=a[1]-p[1];}
    }
    if(pseudo){x=num(c.left);y=num(c.top);}
    const origin=c.transformOrigin.split(' ').map(num), t=c.translate.split(' '), s=c.scale.split(' ').map(num);
    const length=(v,n)=>v?.endsWith('%')?num(v)*n/100:num(v);
    let m=translate(x,y).translate(origin[0]||0,origin[1]||0,origin[2]||0);
    if(c.translate!=='none')m=m.translate(length(t[0],w),length(t[1],h),length(t[2],0));
    if(c.rotate!=='none'){
      const r=c.rotate.split(' '),a=num(r.at(-1));
      if(r.length===1)m=m.rotate(0,0,a);else if(r[0]==='x')m=m.rotate(a,0,0);else if(r[0]==='y')m=m.rotate(0,a,0);else if(r[0]==='z')m=m.rotate(0,0,a);else m=m.rotateAxisAngle(num(r[0]),num(r[1]),num(r[2]),a);
    }
    if(c.scale!=='none')m=m.scale(s[0],s[1]??s[0],s[2]??1);
    m=m.multiply(mat(c.transform)).translate(-(origin[0]||0),-(origin[1]||0),-(origin[2]||0));
    if(e.matches('.ef-skel__dark'))m=m.translate(num(c.cx),num(c.cy)).scale(num(c.r)/466);
    return m;
  }
  function childSpace(e){
    const c=getComputedStyle(e);let m=new DOMMatrix();
    if(e.tagName==='svg'){const v=e.viewBox.baseVal;if(v.width){const sx=e.clientWidth/v.width,sy=e.clientHeight/v.height,k=Math.min(sx,sy);m=m.translate((e.clientWidth-v.width*k)/2,(e.clientHeight-v.height*k)/2).scale(k,k).translate(-v.x,-v.y);}}
    if(c.perspective!=='none'){const o=c.perspectiveOrigin.split(' ').map(num),p=new DOMMatrix();p.m34=-1/num(c.perspective);m=translate(o[0],o[1]).multiply(p).translate(-o[0],-o[1]).multiply(m);}
    return m;
  }
  function add(e,pseudo,kind,key){const id=planes.length,c=getComputedStyle(e,pseudo||null),group=e.closest('.ef-card-shadow,.ef-rtile'),button=group?.querySelector('[data-action]')||group;
    const p={id,node:index.get(e),pseudo:pseudo||'',kind,key:key||'',label:e.getAttribute('aria-label')||'',action:e.closest('[data-action]')?.dataset.mcAction||'',group:index.get(group)??-1,hover:button?.dataset.mcAction||'',row:e.closest('.ef-rrow')?Array.from(document.querySelectorAll('.ef-rrow')).indexOf(e.closest('.ef-rrow')):-1,font:num(c.fontSize)||16,color:c.color,clip:index.get(e.closest('.ef-right__viewport'))??-1};
    if(e.matches('.ef-dial__seg')){const a=e.getPointAtLength(0),b=e.getPointAtLength(e.getTotalLength());let start=Math.atan2(a.y-500,a.x-500),end=Math.atan2(b.y-500,b.x-500);p.arc=[500,500,start,(end-start+Math.PI*2)%(Math.PI*2)];}
    if(kind==='avatar'||kind==='text'||kind==='terrain')p.bounds=[0,0,e.offsetWidth,e.offsetHeight];
    if(pseudo)p.pseudoMatrix=Array.from(local(e,pseudo).toFloat64Array());
    planes.push(p);entries[id]={e,pseudo};}
  function rest(){document.body.className='';for(const a of document.getAnimations()){a.pause();a.currentTime=0;}EF.scene.pause();EF.news.pause();getComputedStyle(document.body).opacity;}
  function discover(){
    rest();
    document.querySelectorAll('[data-action]').forEach(e=>e.dataset.mcAction=e.dataset.action==='close'?(e.classList.contains('ef-power')?'quit':'resume'):(actions[e.dataset.action]||'locked'));
    // Text labels are substituted once in the export; live player values remain native glyphs.
    const labels={resume:'返回游戏',inventory:'背包',advancements:'进度',stats:'统计',settings:'设置','map-reset':'地图复位','view-reset':'视角复位',refresh:'刷新地形'};
    document.querySelectorAll('[data-mc-action] svg text').forEach(e=>{const a=e.closest('[data-mc-action]').dataset.mcAction;if(labels[a]){e.textContent=labels[a];e.removeAttribute('x');e.removeAttribute('y');const tile=e.closest('.ef-rtile');e.setAttribute('x',tile?'53.5':'250');e.setAttribute('y',tile?'92':'160');e.setAttribute('text-anchor','middle');}});
    document.querySelector('.ef-right__nav--up').dataset.mcAction='list-up';document.querySelector('.ef-right__nav--dn').dataset.mcAction='list-down';
    const root=document.querySelector('.ef-stage');
    document.querySelectorAll('.ef-disclabels .ef-dctab--plan text').forEach(e=>e.textContent='地图复位');document.querySelectorAll('.ef-disclabels .ef-dctab--brief text').forEach(e=>e.textContent='刷新地形');
    const list=[root,...root.querySelectorAll('*')].filter(e=>!e.closest('defs,symbol,clipPath,filter,linearGradient,radialGradient'));
    for(const e of list){index.set(e,nodes.length);const p=e.parentElement,n={id:nodes.length,parent:index.get(p)??-1,name:e.tagName.toLowerCase()+'.'+(e.getAttribute('class')||''),w:e.offsetWidth||e.clientWidth||0,h:e.offsetHeight||e.clientHeight||0,svg:e instanceof SVGElement&&e.tagName!=='svg',row:e.classList.contains('ef-rrow')?Array.from(document.querySelectorAll('.ef-rrow')).indexOf(e):-1,action:e.dataset.mcAction||'',nav:e.matches('.ef-right__nav span')?(e.closest('.ef-right__nav').classList.contains('ef-right__nav--up')?'up':'down'):'',base:Array.from(local(e).toFloat64Array()),space:Array.from(childSpace(e).toFloat64Array())};
      if(n.action){let d=getComputedStyle(e,'::before').clipPath.match(/path\(["'](.*)["']\)/)?.[1];if(!d&&e instanceof SVGElement)d=e.querySelector('path[pointer-events="visible"]')?.getAttribute('d');
        if(d){const q=document.createElementNS(ns,'path');q.setAttribute('d',d);const len=q.getTotalLength();n.hit=Array.from({length:65},(_,i)=>{const pt=q.getPointAtLength(len*i/64);return [pt.x,pt.y];});}
        else n.hit=[[0,0],[n.w,0],[n.w,n.h],[0,n.h]];
      }
      nodes.push(n);}
    const animated=new Set(document.querySelectorAll('.ef-card__hl'));
    for(const phase of ['in','out','']){document.body.className=phase?'ef-'+phase:'';for(const a of document.getAnimations()){const target=a.effect?.target;if(target instanceof SVGElement&&target.tagName!=='svg')animated.add(target);}}
    rest();
    for(const e of list){
      const c=getComputedStyle(e),insvg=e instanceof SVGElement;
      if(insvg){
        if(e.tagName==='svg'||animated.has(e))add(e,'','paint');
        continue;
      }
      if(e.matches('.ef-core__map')){add(e,'','terrain');continue;}
      if(e.matches('.ef-badge__avatar')){add(e,'','avatar');continue;}
      let key=Object.keys(dynamic).find(s=>e.matches(s));
      if(key){add(e,'','text',dynamic[key]);continue;}
      const ownText=Array.from(e.childNodes).some(n=>n.nodeType===3&&n.textContent.trim());
      const painted=c.backgroundImage!=='none'||!['rgba(0, 0, 0, 0)','transparent'].includes(c.backgroundColor)||c.boxShadow!=='none'||num(c.borderTopWidth)>0||ownText;
      if(painted&&e!==root&&!e.matches('.ef-scene,.ef-world,.ef-overlay,.ef-post,.ef-wallpaper'))add(e,'','paint');
      for(const ps of ['::before','::after']){const q=getComputedStyle(e,ps);if(q.content!=='none'&&q.content!=='normal')add(e,ps,'paint');}
    }
    // All SVG animated branches are independently painted; their parents omit those branches.
    for(const p of planes){const {e}=entries[p.id];p.svgCuts=planes.filter(q=>q.id!==p.id&&q.kind==='paint'&&entries[q.id].e instanceof SVGElement&&e.contains(entries[q.id].e)).map(q=>q.id);}
    return {nodes,planes,tracks:{}};
  }
  async function sample(phase,duration,rate){
    rest();document.body.className=phase==='idle'?'':'ef-'+phase;
    await Promise.resolve();const anims=document.getAnimations();anims.forEach(a=>a.pause());
    const frames=[];
    for(let t=0;t<=duration+.01;t=Math.min(duration,t+1000/rate)){
      anims.forEach(a=>{a.currentTime=t;});
      const transforms=nodes.map(n=>{const e=Array.from(index.keys())[n.id],c=getComputedStyle(e);const a=Array.from(local(e).toFloat64Array()).map(v=>Math.round(v*1e5)/1e5);a.push(c.visibility==='hidden'||c.display==='none'?0:num(c.opacity)*(e.matches('.ef-skel__ret')?(c.color.match(/[\d.]+/g)?.[3]??1):1));return a;});
      const fx=planes.map(p=>{const {e,pseudo}=entries[p.id],c=getComputedStyle(e,pseudo||null);return [num(c.opacity),num(c.getPropertyValue('--ef-reveal')),num(c.getPropertyValue('--ef-vig')),num(c.strokeDashoffset),c.visibility==='hidden'?0:1];});
      frames.push({t:Math.round(t*1000)/1000,n:transforms,fx});if(t===duration)break;
    }
    return frames;
  }
  function inline(source){
    const clone=source.cloneNode(false),c=getComputedStyle(source);
    for(const k of c)clone.style.setProperty(k,c.getPropertyValue(k).replace(/url\("?[^)]*?#([^)"]+)"?\)/g,'url(#$1)'));
    clone.style.animation='none';clone.style.transition='none';clone.style.visibility='visible';if(source.tagName==='use'){const v=source.ownerSVGElement.viewBox.baseVal;if(!source.hasAttribute('width'))clone.setAttribute('width',v.width||source.ownerSVGElement.clientWidth);if(!source.hasAttribute('height'))clone.setAttribute('height',v.height||source.ownerSVGElement.clientHeight);}
    for(const child of source.childNodes)clone.appendChild(child.nodeType===1?inline(child):child.cloneNode(true));
    return clone;
  }
  function paint(id){
    const p=planes[id],{e,pseudo}=entries[id];let clone,svg=e instanceof SVGElement,box,x=0,y=0,w,h;
    if(svg){
      const root=e.tagName==='svg'?e:e.ownerSVGElement,v=root.viewBox.baseVal;
      w=v.width||root.clientWidth;h=v.height||root.clientHeight;x=v.x;y=v.y;
      clone=document.createElementNS(ns,'svg');clone.setAttribute('viewBox',`${x} ${y} ${w} ${h}`);clone.setAttribute('width',w);clone.setAttribute('height',h);
      const defs=document.createElementNS(ns,'defs');document.querySelectorAll('defs').forEach(d=>Array.from(d.children).forEach(n=>defs.appendChild(n.cloneNode(true))));clone.appendChild(defs);
      const branch=inline(e);if(e.matches('.ef-skel__dark')){branch.style.r='466px';branch.style.cx='0px';branch.style.cy='0px';branch.style.strokeWidth='40px';}if(e.matches('.ef-skel__ret')){branch.style.color='rgb(84,192,230)';branch.querySelectorAll('*').forEach(c=>c.style.color='rgb(84,192,230)');}branch.style.transform='none';branch.style.translate='none';branch.style.rotate='none';branch.style.scale='none';branch.style.opacity='1';branch.style.visibility='visible';branch.style.display='block';
      // Remove independently animated descendants by matching their original DOM child path.
      for(const cut of p.svgCuts){const target=entries[cut].e;let indices=[],a=target;while(a!==e){indices.unshift(Array.from(a.parentNode.childNodes).indexOf(a));a=a.parentNode;}let n=branch;for(const i of indices)n=n?.childNodes[i];if(n)n.style.display='none';}
      if(e.tagName==='svg'){clone.style.cssText=branch.style.cssText;clone.style.width=w+'px';clone.style.height=h+'px';for(const n of Array.from(branch.childNodes))if(n.nodeName!=='defs')clone.appendChild(n);}else clone.appendChild(branch);
      // Geometry in the local SVG coordinate system; bounds include blur padding.
      let bb;try{bb=e.getBBox();if(e.matches('.ef-skel__dark'))bb={x:-490,y:-490,width:980,height:980};}catch{}
      if(bb&&bb.width>0&&bb.height>0){x=bb.x-64;y=bb.y-64;w=bb.width+128;h=bb.height+128;clone.setAttribute('viewBox',`${x} ${y} ${w} ${h}`);clone.setAttribute('width',w);clone.setAttribute('height',h);}
      clone.style.width=w+'px';clone.style.height=h+'px';clone.style.position='absolute';clone.style.left='0';clone.style.top='0';clone.style.margin='0';
    }else{
      const c=getComputedStyle(e,pseudo||null);w=pseudo?num(c.width):e.offsetWidth;h=pseudo?num(c.height):e.offsetHeight;
      if(!w||!h)return null;
      clone=document.createElement('div');for(const k of c)clone.style.setProperty(k,c.getPropertyValue(k));
      clone.style.cssText+=';position:absolute!important;left:64px!important;top:64px!important;margin:0!important;transform:none!important;translate:none!important;rotate:none!important;scale:none!important;animation:none!important;transition:none!important;opacity:1!important;visibility:visible!important;';
      if(pseudo)clone.textContent=c.content.startsWith('"')?c.content.slice(1,-1):'';else for(const t of e.childNodes)if(t.nodeType===3)clone.appendChild(t.cloneNode(true));
      x=-64;y=-64;w+=128;h+=128;
    }
    if(w>2300||h>1500){const k=Math.min(2300/w,1500/h);/* SVG can use a larger browser viewport without downsampling. */}
    const globalDefs=svg?'':`<svg width="0" height="0">${Array.from(document.querySelectorAll('defs')).map(d=>d.outerHTML).join('')}</svg>`;
    return {x,y,width:Math.max(1,Math.ceil(w)),height:Math.max(1,Math.ceil(h)),html:`<html><head><style>html,body{margin:0;background:transparent;overflow:hidden}svg{overflow:visible}</style></head><body>${globalDefs}${clone.outerHTML}</body></html>`};
  }
  return {discover,sample,rest,paint};
})();
