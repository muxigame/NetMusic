"""Freeze ONLY verified public 1.4.38 runtime data and existing candidate binaries; no launch/network."""
from pathlib import Path
import argparse,hashlib,json,shutil,os,uuid,datetime
ROOT=Path(__file__).resolve().parent;WORKSPACE=ROOT.parents[3]
PRODUCT='023738e78b595afbd2c57847f005256c360f47de63e5cc3d26fa2852c10e3199'
MANIFEST='157799764fbd5741684f879fb69d0150d73002d76eae89dd7de7ffccb0e4ce71'
def digest(path,kind='sha256'):
 h=hashlib.new(kind)
 with path.open('rb') as f:
  while chunk:=f.read(1048576):h.update(chunk)
 return h.hexdigest()
def matches(p,kind,wanted,size):
 try:return p.is_file() and p.stat().st_size==size and digest(p,kind)==wanted
 except OSError:return False
def relative(raw):
 p=Path(raw.replace('\\','/'))
 assert not p.is_absolute() and '..' not in p.parts and ':' not in str(p) and '.env' not in p.name.lower(), 'public_relative_payload_required'
 return p
def freeze(source,dest,kind,wanted,size):
 assert source.stat().st_size==size and digest(source,kind)==wanted,'baseline_source_changed: '+str(source)
 dest.parent.mkdir(parents=True,exist_ok=True)
 if not dest.exists() or digest(dest,kind)!=wanted:
  temp=dest.with_name(dest.name+'.'+uuid.uuid4().hex+'.pending');shutil.copyfile(source,temp)
  assert temp.stat().st_size==size and digest(temp,kind)==wanted,'source_changed_during_freeze'
  os.replace(temp,dest)
 return {'path':str(dest),'sha256':digest(dest),'bytes':size,'source':str(source)}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--manifest',type=Path,required=True);ap.add_argument('--input-lock',type=Path,required=True);ap.add_argument('--source-map',type=Path,required=True);ap.add_argument('--cache-discovery',type=Path,required=True);ap.add_argument('--product-build',type=Path,required=True);ap.add_argument('--qa-build',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--backend-build',type=Path,required=True);args=ap.parse_args()
 assert digest(args.manifest)==MANIFEST
 manifest=json.loads(args.manifest.read_text(encoding='utf-8'));lock=json.loads(args.input_lock.read_text(encoding='utf-8'));sources=json.loads(args.source_map.read_text(encoding='utf-8'));cache=json.loads(args.cache_discovery.read_text(encoding='utf-8'))
 assert manifest['pack']['version']==lock['baseVersion']=='1.4.38' and lock['baseManifestSha256']==MANIFEST and len(lock['clientFiles'])==9257 and len(lock['serverMods'])==350
 expected={x['path'].replace('\\','/'):x for x in lock['clientFiles']};mapping={x['path'].replace('\\','/'):x for x in sources};assert set(mapping)==set(expected)
 out=args.output.resolve();out.mkdir(parents=True,exist_ok=True);client=[];server=[];clientByName={}
 for leaf,row in expected.items():
  rel=relative(leaf);source=Path(mapping[leaf]['sourcePath'])
  alternatives=[source,Path(cache['found'].get(rel.name,str(source)))]
  source=next((p for p in alternatives if matches(p,'sha1',row['sha1'],row['size'])),None)
  assert source is not None,'public_payload_missing: '+leaf
  record=freeze(source,out/'client'/rel,'sha1',row['sha1'],row['size']);record['relative']=leaf;client.append(record)
  if leaf.startswith('mods/') and leaf.endswith('.jar'):clientByName[rel.name]=record
 for row in lock['serverMods']:
  name=relative(row['name']);assert len(name.parts)==1
  candidates=[Path(cache.get('serverFound',{}).get(str(name),'missing')),WORKSPACE/'bmc5server/mods'/name,Path(clientByName.get(str(name),{}).get('path','missing')),WORKSPACE/'.local-operations/release-20261008-takeover/production-clean-rev2/server/mods'/name]
  source=next((p for p in candidates if matches(p,'sha256',row['sha256'],row['size'])),None)
  assert source is not None,'server_payload_missing: '+str(name)
  record=freeze(source,out/'server/mods'/name,'sha256',row['sha256'],row['size']);record['relative']='mods/'+str(name);server.append(record)
 product=json.loads((args.product_build/'artifact.json').read_text());qa=json.loads((args.qa_build/'receipt.json').read_text());assert product['sha256']==PRODUCT and qa['productSha256']==PRODUCT and qa['compiledSources']==25 and qa['actualJavaBackendCryptoProtocolPassed'] and qa['fixtureTests']['scopeAndFixtureChecks']==22
 overlays=[]
 for role,a,key in [('product',product,'artifact'),('qa',qa,'jar')]:
  p=Path(a[key]);x=freeze(p,out/'overlay'/p.name,'sha256',a['sha256'],p.stat().st_size);x['role']=role;overlays.append(x)
 original='mods/muxi-music-compat-0.4.1-repair-candidate.2.jar'
 assert any(x['relative']==original for x in client) and any(x['relative']==original for x in server)
 canonical=WORKSPACE/'better-mc-remake';runner=[canonical/'scripts/local_mc_debug.py',canonical/'scripts/local_mc_runtime.py',*sorted((canonical/'tests/local-mc-debug').rglob('*.java'))]
 own=[*sorted(ROOT.glob('*.py')),*sorted(ROOT.rglob('*.java'))]
 backend_path=args.backend_build/'receipt.json';backend=json.loads(backend_path.read_text(encoding='utf-8'))
 assert backend['productSha256']==PRODUCT and backend['actualService'] and backend['actualRsaProof'] and backend['ossRequests']==0
 for group in ['sourceSha256','dependencySha256']:assert all(digest(Path(p))==h for p,h in backend[group].items())
 fixture=freeze(Path(backend['audioPath']),out/'fixtures/entry042-owned-cc0.wav','sha256',backend['audioSha256'],backend['audioBytes'])
 receipt={'complete':True,'frozenAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'baselineVersion':'1.4.38','baselineManifestSha256':MANIFEST,'baselineInputLockSha256':digest(args.input_lock),'sourceMapSha256':digest(args.source_map),'clientFiles':client,'serverMods':server,'overlays':overlays,'candidateSha256':PRODUCT,'qaSha256':qa['sha256'],'backendReceiptPath':str(backend_path.resolve()),'backendReceiptSha256':digest(backend_path),'backend':backend,'fixtureAudio':fixture,'replacedMod':original,'clientBaselineModCount':len(clientByName),'serverBaselineModCount':len(server),'publicClientPayloadOnly':True,'canonicalRunnerSources':{str(p):digest(p) for p in runner},'musicQaSources':{str(p):digest(p) for p in own},'mcStarted':False,'ossCalls':0,'remoteEnvRead':False,'sourceCopies':False,'handoff':{'afterOwner':'parent-current-GUI-owner','parent':'01a11735-2ae7-7446-8ba3-7c3d3bc62036','registration':'music offline ready; await current parent-coordinated GUI owner cleanup and explicit window grant','crossThreadSendAvailable':False,'requiresCleanupMessageAndInputWindow':True},'runtimeClaim':'candidate overlay replaces repair.2 in NEW owned lab only; no production changes'}
 (out/'frozen-inputs.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:receipt[k] for k in ['complete','baselineVersion','baselineManifestSha256','clientBaselineModCount','serverBaselineModCount','candidateSha256','qaSha256','mcStarted','ossCalls']},indent=2))
if __name__=='__main__':main()
