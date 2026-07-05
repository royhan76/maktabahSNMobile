import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.append(r'E:\PROJECT\WEB\epub to json')
from app.exporter.decompiler import decompile_mai
from pathlib import Path

for name in ['fathulmuin.mai', 'roudlotuttholibinwaumdatulmuftin.mai']:
    print('=== BOOK:', name)
    data = decompile_mai(Path(rf'E:\PROJECT\WEB\epub to json\output\{name}'))
    print('metadata:', data['metadata'])
    print('first 10 chapter titles:')
    for i in range(min(10, len(data['chapters']))):
        ch = data['chapters'][i]
        print(f"  {ch['id']}: {ch['title']}")
