"""Aggregate saved QA measurements without interpreting overlapping process/GPU memory as totals."""
import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs' / 'qa-1.5.0'
OUT.mkdir(exist_ok=True)

def read(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]

def summary(values):
    values = [v for v in values if isinstance(v, (int, float)) and v >= 0]
    return {'count': len(values), 'min': min(values), 'median': statistics.median(values), 'max': max(values)} if values else None

def run(label):
    capture = ROOT / 'run' / f'optimization-{label}.jsonl'
    processes = ROOT / '.cache' / f'memory-{label}.jsonl'
    rows = read(capture if capture.exists() else OUT / capture.name)
    memory = read(processes if processes.exists() else OUT / processes.name)
    result = {'label': label, 'last': {k:rows[-1][k] for k in ('elapsed','cycles','worlds','screen')}, 'timing':{}, 'memory':{}}
    for phase in (1,2):
        samples = [r for r in rows if r['phase'] == phase and 'menu' in r]
        result['timing'][str(phase)] = {
            k: summary([r['menu']['browser'].get(k,-1) for r in samples])
            for k in ('htmlPresentedFps','htmlFrameP95Ms','sourceAgeMs','uploadMs')
        }
        result['timing'][str(phase)]['decodeMs'] = summary([r['menu']['browser']['stream']['decodeMs'] for r in samples])
        result['timing'][str(phase)]['gameFrameP95Ms'] = summary([r['menu']['frameP95Ms'] for r in samples])
        result['timing'][str(phase)]['firstDrawMs'] = summary([r['menu']['firstDrawMs'] for r in samples])
    for phase in sorted(set(r['phase'] for r in memory)):
        samples = [r for r in memory if r['phase'] == phase]
        result['memory'][str(phase)] = {
            'samples': len(samples),
            'gamePrivateMiB': summary([r['game']['privateBytes']/2**20 for r in samples]),
            'browserPrivateMiB': summary([sum(p['privateBytes'] for p in r['browser'])/2**20 for r in samples]),
            'browserProcessCount': summary([len(r['browser']) for r in samples]),
        }
    result['peaks'] = {
        'heapUsedMiB': max(r['heapUsed'] for r in rows)/2**20,
        'heapCommittedMiB': max(r['heapCommitted'] for r in rows)/2**20,
        'jmxDirectMiB': max(r['buffers']['direct']['bytes'] for r in rows)/2**20,
        'gamePrivateMiB': max(r['game']['privateBytes'] for r in memory)/2**20,
        'browserPrivateMiB': max(sum(p['privateBytes'] for p in r['browser']) for r in memory)/2**20,
        'gameGpuCounterDedicatedMiB': max(r.get('gpu',{}).get(str(r['game']['pid']),{}).get('dedicated',0) for r in memory)/2**20,
    }
    result['end'] = rows[-1]
    return result

result = {label: run(label) for label in ('baseline142','optimized150c')}
(OUT / 'summary.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
for label,r in result.items():
    print(label, json.dumps({k:r[k] for k in ('last','peaks')},ensure_ascii=False))
    for phase,t in r['timing'].items(): print('timing',phase,{k:round(v['median'],2) if v else None for k,v in t.items()})
    for phase,m in r['memory'].items(): print('memory',phase,{k:[round(v['min'],1),round(v['max'],1)] for k,v in m.items() if k!='samples'})
