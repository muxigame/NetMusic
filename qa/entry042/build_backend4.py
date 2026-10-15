"""Build/hash the isolated backend; use canonical source and existing offline dependencies."""
from pathlib import Path
import argparse,hashlib,json,py_compile,subprocess,sys,os
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parents[2]
ap=argparse.ArgumentParser();ap.add_argument('--output',type=Path,required=True);ap.add_argument('--python-deps',type=Path,required=True);args=ap.parse_args();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
sources=[ROOT/'backend4.py',ROOT/'backend4_test.py',ROOT/'owned_audio4.py',REPO/'app/music_upload.py',REPO/'app/music_lyrics.py',REPO/'app/music_receipt.py',REPO/'app/security.py',REPO/'app/music_audio.py',REPO/'app/music_production_storage.py',REPO/'app/music_oss_transport.py',REPO/'app/music_timing.py']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
pins={str(p):sha(p) for p in sources}
from owned_audio4 import data
audio=data();(out/'entry042-owned-cc0.wav').write_bytes(audio)
for n,p in enumerate(sources):py_compile.compile(str(p),cfile=str(out/(str(n)+'.pyc')),doraise=True)
env=dict(os.environ);env['PYTHONPATH']=str(args.python_deps.resolve())+os.pathsep+str(REPO)+os.pathsep+str(ROOT)
test=subprocess.run([sys.executable,str(ROOT/'backend4_test.py')],env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
(out/'backend-tests.log').write_text(test.stdout+test.stderr,encoding='utf-8');assert test.returncode==0,test.stdout+test.stderr
record={'sourceSha256':pins,'audioPath':str(out/'entry042-owned-cc0.wav'),'audioSha256':sha(out/'entry042-owned-cc0.wav'),'audioBytes':len(audio),'dependencySha256':{str(p):sha(p) for p in sorted(args.python_deps.rglob('*')) if p.is_file() and p.suffix in ['.py','.pyd']},'pythonDeps':str(args.python_deps.resolve()),'productSha256':'9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216','actualService':True,'actualRsaProof':True,'realIdentity':False,'realPlatformCalls':0,'ossRequests':0,'mcStarted':False,'normalLoopbackTestShutdown':True}
assert all(sha(Path(p))==v for p,v in pins.items());(out/'receipt.json').write_text(json.dumps(record,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in record.items() if k not in ['sourceSha256','dependencySha256']}))
