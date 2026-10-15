"""Reuse SHA-verified public payload, freeze current QA; never launch MC."""
from pathlib import Path
import argparse,hashlib,json,datetime,shutil
ROOT=Path(__file__).resolve().parent;WORKSPACE=ROOT.parents[3]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--prior-frozen',type=Path,required=True);ap.add_argument('--qa-build',type=Path,required=True);ap.add_argument('--product-build',type=Path,required=True);ap.add_argument('--backend-build',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);a=ap.parse_args()
 lock=json.loads(a.prior_frozen.read_text(encoding='utf-8'));assert lock['complete'] and lock['baselineVersion']=='1.4.38' and lock['baselineManifestSha256']=='157799764fbd5741684f879fb69d0150d73002d76eae89dd7de7ffccb0e4ce71' and lock['candidateSha256']=='023738e78b595afbd2c57847f005256c360f47de63e5cc3d26fa2852c10e3199'
 assert len(lock['clientFiles'])==9257 and len(lock['serverMods'])==350 and lock['publicClientPayloadOnly']
 for group in ['clientFiles','serverMods']:
  for row in lock[group]:
   p=Path(row['path']);assert p.is_file() and p.stat().st_size==row['bytes'] and sha(p)==row['sha256'],'public_frozen_payload_changed'
 receipt=json.loads((a.qa_build/'receipt.json').read_text(encoding='utf-8'));assert receipt['productSha256']=='ea0e0cc6755a2e9936ea9c0cb39fab14b163bbdb5e9e6e1896aa575b218552c1' and receipt['compiledSources']==27 and receipt['fixtureTests']['scopeAndFixtureChecks']==22 and receipt['actualJavaBackendCryptoProtocolPassed']
 assert all(sha(ROOT/leaf)==value for leaf,value in receipt['sourceSha256'].items()),'latest_QA_must_be_compiled'
 product=json.loads((a.product_build/'artifact.json').read_text(encoding='utf-8'));assert product['sha256']==receipt['productSha256'];product_jar=Path(product['artifact']);assert sha(product_jar)==product['sha256']
 backend_path=a.backend_build/'receipt.json';backend=json.loads(backend_path.read_text(encoding='utf-8'));assert backend['productSha256']==product['sha256'] and backend['actualService'] and backend['actualRsaProof'] and backend['ossRequests']==0
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);overlays=[]
 dest=out/'overlay'/product_jar.name;dest.parent.mkdir(parents=True,exist_ok=True);assert not dest.exists() or sha(dest)==product['sha256'];shutil.copyfile(product_jar,dest);overlays.append({'role':'product','path':str(dest),'source':str(product_jar),'sha256':product['sha256'],'bytes':dest.stat().st_size})
 lock.update({'candidateSha256':product['sha256'],'importWorldProductSha256':'635969b8ee91ab60b1606d3928d7bd8e426bfc611fddc363ba56ffef144320b9','importWorldQaSha256':'1a28043b2b9a8cb1b784481c963feab7f703364052fb8c1acca20975c6d1e178','backendReceiptPath':str(backend_path.resolve()),'backendReceiptSha256':sha(backend_path),'backend':backend})
 jar=Path(receipt['jar']);assert sha(jar)==receipt['sha256'];dest=out/'overlay'/jar.name;dest.parent.mkdir(parents=True,exist_ok=True);assert not dest.exists() or sha(dest)==receipt['sha256'];shutil.copyfile(jar,dest)
 overlays.append({'role':'qa','path':str(dest),'source':str(jar),'sha256':receipt['sha256'],'bytes':dest.stat().st_size})
 assert sha(Path(lock['backendReceiptPath']))==lock['backendReceiptSha256']
 for group in ['sourceSha256','dependencySha256']:assert all(sha(Path(p))==h for p,h in backend[group].items()),'backend_source_or_dependency_changed'
 assert sha(Path(lock['fixtureAudio']['path']))==lock['fixtureAudio']['sha256']
 canonical=WORKSPACE/'better-mc-remake';runner=[canonical/'scripts/local_mc_debug.py',canonical/'scripts/local_mc_runtime.py',*sorted((canonical/'tests/local-mc-debug').rglob('*.java'))]
 lock.update({'frozenAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'qaSha256':receipt['sha256'],'overlays':overlays,'canonicalRunnerSources':{str(p):sha(p) for p in runner},'musicQaSources':{str(p):sha(p) for p in [*sorted(ROOT.glob('*.py')),*sorted(ROOT.rglob('*.java'))]},'publicPayloadReuseFrom':str(a.prior_frozen.resolve()),'publicPayloadReuseReceiptSha256':sha(a.prior_frozen),'mcStarted':False,'sourceCopies':False,'candidate9NativeAccepted':False})
 (out/'frozen-inputs.json').write_text(json.dumps(lock,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'verifiedPublicReuse':True,'qaSha256':receipt['sha256'],'productSha256':lock['candidateSha256'],'mcStarted':False,'ossRequests':0}))
if __name__=='__main__':main()
