"""Send only scoped QA commands; no launch or arbitrary game commands."""
from pathlib import Path
import argparse,json,os
ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--id',type=int,required=True);ap.add_argument('--cmd',choices=['open-burner','open-computer','unverified-write','lock-matrix','slot-attacks','network-click','audit-discs','close-menu','stop-server-owned','gate','disconnect','reconnect','configure','permission','craft','pick','cancel','pause-focus-true','pause-focus-false','observe','finish','unconfirm','readonly','clear-local-login','login','login-return','login-success-fixture','login-confirm'],required=True);ap.add_argument('--platform',choices=['netease','qq_music','bilibili']);ap.add_argument('--value');ap.add_argument('--phase',choices=['acquiring','uploading','verifying','committing']);ap.add_argument('--mode',choices=['off','hold','fail']);ap.add_argument('--slot',type=int,choices=[0,1]);ap.add_argument('--button',type=int,choices=[0,1,8,40]);ap.add_argument('--click',choices=['PICKUP','QUICK_MOVE','SWAP','THROW','QUICK_CRAFT','PICKUP_ALL','CLONE']);args=ap.parse_args()
root=args.instance_root.resolve(strict=True);owner=json.loads((root/'local-mc-owner.json').read_text())
assert Path(owner['instanceRoot']).resolve()==root and owner['clientNames'] in [{'host':'10000'},{'host':'10000','guest':'10001'}] and owner['offlineLoopback'] is True
assert any(p['sha256']=='023738e78b595afbd2c57847f005256c360f47de63e5cc3d26fa2852c10e3199' for p in owner['inputArtifacts'])
assert args.id>=0
row={'runId':owner['runId'],'id':args.id,'cmd':args.cmd}
if args.cmd=='configure':assert args.platform and args.value and len(args.value)<8192;row.update(platform=args.platform,value=args.value)
if args.cmd=='network-click':assert args.slot is not None and args.button is not None and args.click;row.update(slot=args.slot,button=args.button,click=args.click)
if args.cmd=='gate':assert args.phase and args.mode;row.update(phase=args.phase,mode=args.mode)
file=root/'coordinator/entry042-command.json';tmp=file.with_suffix('.pending');tmp.write_text(json.dumps(row),encoding='utf-8');os.replace(tmp,file)
print(json.dumps({'sent':True,'id':args.id,'cmd':args.cmd,'runId':owner['runId'],'physicalOSInput':False,'mcLaunched':False}))
