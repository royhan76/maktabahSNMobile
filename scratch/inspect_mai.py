import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.append(r'E:\PROJECT\WEB\epub to json')
from app.exporter.decompiler import decompile_mai
from pathlib import Path

data = decompile_mai(Path(r'E:\PROJECT\WEB\epub to json\output\roudlotuttholibinwaumdatulmuftin.mai'))
types = set()
keys = {}
for ch in data['chapters']:
    for b in ch['blocks']:
        t = b['type']
        types.add(t)
        if t not in keys:
            keys[t] = set(b.keys())
        else:
            keys[t].update(b.keys())

print('types:', types)
for t, k in keys.items():
    print(t, 'keys:', list(k))
