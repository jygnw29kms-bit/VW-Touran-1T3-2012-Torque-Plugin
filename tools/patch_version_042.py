from pathlib import Path
p=Path(r'C:\Users\dezen\Documents\GitHub\VW-Touran-1T3-2012-Torque-Plugin\android-app\app\build.gradle')
s=p.read_text(encoding='utf-8-sig')
s=s.replace('versionCode 6','versionCode 7').replace('versionName "0.4.1"','versionName "0.4.2"')
p.write_text(s,encoding='utf-8')
print('version patched')