"""Read-only per-process sampler. No command lines or browser debug credentials are recorded."""
import sys,time,json,re,subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'.cache/qa-deps'))
import psutil
root=Path(__file__).resolve().parents[2]
label=sys.argv[1];source=root/f'run/optimization-{label}.jsonl';dest=root/f'.cache/memory-{label}.jsonl'
start=time.monotonic();game=None;gpu={};last_gpu=0
while time.monotonic()-start<2000:
 try:
  lines=source.read_text(encoding='utf-8').splitlines();state=json.loads(lines[-1]);game=psutil.Process(state['pid'])
 except (OSError,IndexError,ValueError,psutil.Error):time.sleep(2);continue
 if not game.is_running():break
 browser={}
 for p in psutil.process_iter(['pid','name','cmdline']):
  try:
   if p.info['name'].lower()=='msedgewebview2.exe' and any('endfield-webview' in arg for arg in p.info['cmdline']):
    browser[p.pid]=p
    for child in p.children(recursive=True):browser[child.pid]=child
  except (psutil.Error,TypeError):pass
 def mem(p):
  m=p.memory_info();t=p.cpu_times()
  return {'pid':p.pid,'privateBytes':getattr(m,'private',m.vms),'workingSet':m.rss,'cpuSeconds':t.user+t.system,'threads':p.num_threads()}
 try:
  row={'seconds':time.monotonic()-start,'qaElapsed':state['elapsed'],'phase':state['phase'],'cycles':state['cycles'],'worlds':state['worlds'],'screen':state['screen'],'game':mem(game),'browser':[mem(p) for p in browser.values()],'systemAvailable':psutil.virtual_memory().available}
  if time.monotonic()-last_gpu>30:
   cmd="Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUProcessMemory -ErrorAction SilentlyContinue | Select-Object Name,DedicatedUsage,SharedUsage | ConvertTo-Json -Compress"
   r=subprocess.run(['powershell','-NoProfile','-WindowStyle','Hidden','-Command',cmd],capture_output=True,text=True,timeout=15)
   try:
    items=json.loads(r.stdout);items=items if isinstance(items,list) else [items];pids={game.pid,*browser}
    gpu={str(pid):{'dedicated':sum(int(i['DedicatedUsage']) for i in items if re.match(f'pid_{pid}_',i['Name'])),'shared':sum(int(i['SharedUsage']) for i in items if re.match(f'pid_{pid}_',i['Name']))} for pid in pids}
   except (ValueError,TypeError,KeyError):gpu={'unavailable':True}
   last_gpu=time.monotonic()
  row['gpu']=gpu
  with dest.open('a',encoding='utf-8') as f:f.write(json.dumps(row)+'\n')
 except (psutil.Error,OSError,subprocess.TimeoutExpired):pass
 time.sleep(5)
