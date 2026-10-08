from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/'app/build.gradle.kts'
s=p.read_text(encoding='utf-8')
s=s.replace('versionCode = 6','versionCode = 7')
s=s.replace('versionName = "1.5.0"','versionName = "1.6.0"')
if 'net.qiujuer.lame:lame:1.0.0' not in s:
    s += '\n\ndependencies {\n    implementation("net.qiujuer.lame:lame:1.0.0")\n}\n'
p.write_text(s,encoding='utf-8')
