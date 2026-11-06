"""Compare packaged runtime dependencies and UI bytes, not ZIP timestamp order."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--maven',default='server/target/tps-server.jar')
parser.add_argument('--gradle',default='server/build/libs/tps-server.jar')
parser.add_argument('--output')
args=parser.parse_args()
def entries(path,prefix):
    with zipfile.ZipFile(path) as z:
        return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith(prefix) and not n.endswith('/')}
a,b=entries(args.maven,'BOOT-INF/lib/'),entries(args.gradle,'BOOT-INF/lib/')
u,v=entries(args.maven,'BOOT-INF/classes/static/'),entries(args.gradle,'BOOT-INF/classes/static/')
c,d=entries(args.maven,'BOOT-INF/classes/'),entries(args.gradle,'BOOT-INF/classes/')
result={'librariesEqual':a==b,'mavenLibraries':len(a),'gradleLibraries':len(b),'staticEqual':u==v,
        'applicationEntriesEqual':c==d,'applicationEntryCount':len(c),
        'onlyMaven':sorted(a.keys()-b.keys()),'onlyGradle':sorted(b.keys()-a.keys()),
        'differentLibraryBytes':[k for k in a.keys() & b.keys() if a[k]!=b[k]],
        'mavenJarSha256':hashlib.sha256(Path(args.maven).read_bytes()).hexdigest(),
        'gradleJarSha256':hashlib.sha256(Path(args.gradle).read_bytes()).hexdigest()}
print(json.dumps(result,indent=2))
if args.output: Path(args.output).write_text(json.dumps(result,indent=2),encoding='utf-8')
raise SystemExit(0 if a==b and u==v and c==d and a and u else 1)
