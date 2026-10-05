from pathlib import Path
p=Path(r'C:\Users\dezen\Documents\GitHub\VW-Touran-1T3-2012-Torque-Plugin\android-app\app\build.gradle')
s=p.read_text(encoding='utf-8-sig')
p.write_text(s,encoding='utf-8')
print('BOM removed')