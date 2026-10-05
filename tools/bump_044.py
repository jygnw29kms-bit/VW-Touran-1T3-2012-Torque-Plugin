from pathlib import Path
p=Path(r'C:\Users\dezen\Documents\GitHub\VW-Touran-1T3-2012-Torque-Plugin\android-app\app\build.gradle')
s=p.read_text(encoding='utf-8-sig')
import re
s=re.sub(r'versionCode\s+\d+','versionCode 9',s,1)
s=re.sub(r'versionName\s+"[^"]+"','versionName "0.4.4"',s,1)
p.write_text(s,encoding='utf-8')
print('version 0.4.4 / 9')
