"""Read-only APK triage. Usage: python3 tools/inspect_apk.py file.apk output-dir.

Requires Android SDK build-tools; uses aapt2 and apksigner without executing APKs.
"""
import hashlib, json, os, subprocess, sys, zipfile
from pathlib import Path
apk=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]).resolve()
if not apk.is_file():raise SystemExit('APK does not exist')
out.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'Library/Android/sdk'))
versions=sorted((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(x) for x in p.name.split('.') if x.isdigit()))
build=versions[-1]
def run(name,args):
    result=subprocess.run(args,text=True,capture_output=True)
    (out/name).write_text(result.stdout+result.stderr)
    return result.returncode
badging=run('badging.txt',[str(build/'aapt2'),'dump','badging',str(apk)])
manifest=run('manifest.txt',[str(build/'aapt2'),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'])
signing=run('signatures.txt',[str(build/'apksigner'),'verify','--verbose','--print-certs',str(apk)])
with zipfile.ZipFile(apk) as archive:
    native=[n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
    permissions=[]
    report={'artifact':apk.name,'sha256':hashlib.file_digest(apk.open('rb'),'sha256').hexdigest(),'bytes':apk.stat().st_size,'abis':sorted({n.split('/')[1] for n in native}),'native_libraries':native,'badging_exit':badging,'manifest_exit':manifest,'signature_verification_exit':signing,'execution':'Not executed; static inspection only'}
(out/'summary.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
