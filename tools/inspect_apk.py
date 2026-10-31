"""Read-only APK triage. Usage: python3 tools/inspect_apk.py file.apk output-dir.

Requires Android SDK build-tools; uses aapt2 and apksigner without executing APKs.
"""
import hashlib, json, os, re, subprocess, sys, zipfile
from pathlib import Path

COMPONENTS={'application','activity','service','receiver','provider'}
ATTRIBUTE=re.compile(r'A: (?:http://schemas\.android\.com/apk/res/android:)?([\w.]+)(?:\(0x[0-9a-f]+\))?=(.*)')

def attribute_value(raw):
    raw=raw.strip()
    quoted=re.match(r'"([^"]*)"',raw)
    if quoted:return quoted.group(1)
    if raw.endswith('0xffffffff'):return True
    if raw.endswith(')0x0'):return False
    return raw

def summarize_manifest(text):
    """Components with the attributes that decide reboot behaviour, from `aapt2 dump xmltree`."""
    stack=[];found=[]
    for line in text.splitlines():
        depth=len(line)-len(line.lstrip())
        body=line.strip()
        if body.startswith('E: '):
            tag=body[3:].split(' ')[0]
            while stack and stack[-1]['depth']>=depth:stack.pop()
            element={'depth':depth,'tag':tag,'attributes':{}}
            stack.append(element)
            if tag in COMPONENTS:
                element['summary']={'element':tag,'actions':[]}
                found.append(element['summary'])
        elif body.startswith('A: ') and stack:
            match=ATTRIBUTE.match(body)
            if not match:continue
            name,value=match.group(1),attribute_value(match.group(2))
            element=stack[-1]
            element['attributes'][name]=value
            if 'summary' in element and name in ('name','process','directBootAware','exported','permission'):
                element['summary'][name]=value
            if element['tag']=='action' and name=='name':
                owner=next((e for e in reversed(stack) if 'summary' in e),None)
                if owner:owner['summary']['actions'].append(value)
    return found

def main():
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
        report={'artifact':apk.name,'sha256':hashlib.file_digest(apk.open('rb'),'sha256').hexdigest(),'bytes':apk.stat().st_size,'abis':sorted({n.split('/')[1] for n in native}),'native_libraries':native,'badging_exit':badging,'manifest_exit':manifest,'signature_verification_exit':signing,'execution':'Not executed; static inspection only'}
    # directBootAware, process and boot receivers decide what Android can show right after a restart.
    report['components']=summarize_manifest((out/'manifest.txt').read_text()) if manifest==0 else []
    (out/'summary.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))

if __name__=='__main__':
    main()
