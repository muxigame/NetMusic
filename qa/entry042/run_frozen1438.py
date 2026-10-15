"""Verify-only by default; start only in the handed-over exclusive music window."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,shutil,sys,os,ctypes
ROOT=Path(__file__).resolve().parent;WORKSPACE=ROOT.parents[3]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--instance-root',type=Path);ap.add_argument('--port',type=int);ap.add_argument('--clients',type=int,choices=[1,2],default=1);ap.add_argument('--backend-port',type=int);ap.add_argument('--start',action='store_true');ap.add_argument('--unsupported-guest',action='store_true');ap.add_argument('--window-granted',action='store_true');ap.add_argument('--world',type=Path);ap.add_argument('--prior-disc-cases',type=Path);ap.add_argument('--server-memory-mb',type=int,default=4096);ap.add_argument('--client-memory-mb',type=int,default=6144);ap.add_argument('--hold-seconds',type=int,default=3600);ap.add_argument('--boot-timeout',type=int,default=180);args=ap.parse_args();assert 120<=args.hold_seconds<=5400 and 180<=args.boot_timeout<=600
 lock=json.loads(args.frozen_inputs.read_text(encoding='utf-8'));assert lock['complete'] and lock['baselineVersion']=='1.4.38' and lock['candidateSha256']=='ea0e0cc6755a2e9936ea9c0cb39fab14b163bbdb5e9e6e1896aa575b218552c1'
 for group in ['clientFiles','serverMods','overlays']:
  for row in lock[group]:p=Path(row['path']);assert p.stat().st_size==row['bytes'] and sha(p)==row['sha256'],'frozen_runtime_changed'
 for group in ['canonicalRunnerSources','musicQaSources']:
  assert all(sha(Path(p))==h for p,h in lock[group].items()),'canonical_source_changed_rebuild_freeze_before_start'
 backend=lock['backend'];assert sha(Path(lock['backendReceiptPath']))==lock['backendReceiptSha256']
 for group in ['sourceSha256','dependencySha256']:assert all(sha(Path(p))==h for p,h in backend[group].items()),'frozen_backend_changed'
 assert sha(Path(lock['fixtureAudio']['path']))==lock['fixtureAudio']['sha256']
 if not args.start:print(json.dumps({'verified':True,'mcStarted':False,'baseline':'1.4.38','product':lock['candidateSha256'],'qa':lock['qaSha256']}));return
 assert args.window_granted and args.instance_root and args.port and args.backend_port and args.backend_port!=args.port,'coordinated_window_required'
 import psutil
 session=ctypes.c_ulong();assert ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session)) and session.value==2,'coordinated_Session2_required'
 required=(args.server_memory_mb+args.client_memory_mb*args.clients)*1.25+4096;assert psutil.virtual_memory().available/1048576>=required,'insufficient_current_free_memory_no_launch'
 for process in psutil.process_iter(['name','cmdline']):
  if (process.info['name'] or '').lower() in ['java.exe','javaw.exe'] and any('local-mc-debug' in part and 'launch.args' in part for part in (process.info['cmdline'] or [])):raise AssertionError('another_owned_MC_run_is_active')
 import socket
 for port in [args.port,args.backend_port]:
  for kind in [socket.SOCK_STREAM,socket.SOCK_DGRAM]:
   with socket.socket(socket.AF_INET,kind) as probe:probe.bind(('127.0.0.1',port))
 canonical=WORKSPACE/'better-mc-remake';lab=args.instance_root.resolve();allowed=(canonical/'build/local-mc-debug').resolve();assert allowed in lab.parents and not lab.exists()
 if args.world:assert args.world.resolve().name=='qa-world' and allowed in args.world.resolve().parents,'only prior owned QA world allowed'
 sys.path.insert(0,str(canonical/'scripts'));import local_mc_debug as runner;import local_mc_runtime as rt
 if args.world:
  prior_root,marker=rt.owned_lab(args.world.resolve().parent.parent)
  result=json.loads((prior_root/'run-result.json').read_text(encoding='utf-8'))
  assert marker['offlineLoopback'] and marker['clientNames'] in [{'host':'10000'},{'host':'10000','guest':'10001'}] and any(a['sha256']==lock.get('importWorldProductSha256',lock['candidateSha256']) for a in marker['inputArtifacts'])
  assert result['runId']==marker['runId'] and result['normalExit'] and result['normalStopOnly'] and all(c==0 for c in result['exitCodes'].values()),'prior_music_lab_must_exit_normally'
  assert args.prior_disc_cases and args.prior_disc_cases.resolve()==prior_root/'coordinator/entry042-disc-cases.json'
 backend_process=None
 original=rt.copy_inputs
 def copy_public(lab,jars,data):
  nonlocal backend_process
  original(lab,jars,data)
  if lab.name=='server':
   owner=json.loads((lab.parent/'local-mc-owner.json').read_text(encoding='utf-8'));assert Path(owner['instanceRoot']).resolve()==lab.parent.resolve() and any(x['sha256']==lock['candidateSha256'] for x in owner['inputArtifacts'])
   fixture=Path(lock['fixtureAudio']['path']);assert sha(fixture)==lock['fixtureAudio']['sha256'];shutil.copyfile(fixture,lab.parent/'entry042-owned-cc0.wav')
   if args.world:
    source=prior_root/'coordinator/entry042-backend-data';target=lab.parent/'coordinator/entry042-backend-data'
    assert allowed in source.resolve().parents and allowed in target.resolve().parents and not target.exists()
    prior_backend=json.loads((prior_root/'coordinator/entry042-backend-exit.json').read_text());assert prior_backend['runId']==marker['runId'] and prior_backend['normalExit'] and prior_backend['exitCode']==0
    shutil.copytree(source,target,ignore=shutil.ignore_patterns('owned-fixture-signing.pem'))
   import subprocess,time
   env=dict(os.environ);env['PYTHONPATH']=backend['pythonDeps']+os.pathsep+str(WORKSPACE/'muxi-auth');env['PYTHONDONTWRITEBYTECODE']='1'
   backend_process=subprocess.Popen([sys.executable,str(ROOT/'backend4.py'),'--instance-root',str(lab.parent),'--port',str(args.backend_port)],cwd=WORKSPACE/'muxi-auth',env=env,stdout=open(lab.parent/'coordinator/entry042-backend.log','w'),stderr=subprocess.STDOUT)
   until=time.monotonic()+20
   while not (lab.parent/'coordinator/entry042-backend-status.json').exists():
    assert backend_process.poll() is None and time.monotonic()<until,'owned_backend_failed_to_start';time.sleep(.1)
   ready=json.loads((lab.parent/'coordinator/entry042-backend-status.json').read_text());assert ready['runId']==owner['runId'] and ready['pid']==backend_process.pid and ready['port']==args.backend_port

  # Only SHA-verified published runtime payload, never live account/config/source snapshots.
  for row in lock['clientFiles']:
   rel=Path(row['relative']);
   if rel.parts[0]=='mods':continue
   dest=lab/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(row['path'],dest)
  if lab.name=='server' and args.prior_disc_cases:
   prior=args.prior_disc_cases.resolve();assert allowed in prior.parents and prior.name=='entry042-disc-cases.json';rows=json.loads(prior.read_text(encoding='utf-8'));assert isinstance(rows,list);target=lab.parent/'coordinator/entry042-disc-cases.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(rows),encoding='utf-8')
 rt.copy_inputs=copy_public
 command=['local_mc_debug.py','run','--project-root',str(canonical),'--instance-root',str(lab),'--server-runtime',str(WORKSPACE/'bmc5server'),'--client-game',str(WORKSPACE/'test-environment/resources/client/game'),'--java-home',r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1','--port',str(args.port),'--clients',str(args.clients),'--client-name','10000','--visible','--accept-eula','--mode','hold','--hold-seconds',str(args.hold_seconds),'--boot-timeout',str(args.boot_timeout),'--approved-heap-budget-mb',str(args.server_memory_mb+args.client_memory_mb*args.clients),'--server-memory-mb',str(args.server_memory_mb),'--client-memory-mb',str(args.client_memory_mb),'--client-max-fps','30','--client-render-distance','3','--client-simulation-distance','3','--jvm-property','muxi.music.entry042.qa=true','--jvm-property','muxi.music.entry042.backendPort='+str(args.backend_port)]
 if args.unsupported_guest:command+=['--jvm-property','muxi.music.entry042.unsupportedGuest=true']
 if args.clients==2:command+=['--client-name','10001']
 # Reuse exactly the dependency overrides shipped in the verified public baseline.
 # The canonical runner recreates fml.toml after staging, so pass its existing API.
 import tomllib
 public_fml=next(Path(r['path']) for r in lock['clientFiles'] if r['relative']=='config/fml.toml')
 for target,dependencies in tomllib.loads(public_fml.read_text(encoding='utf-8')).get('dependencyOverrides',{}).items():
  for dependency in dependencies:command+=['--dependency-override',target+'='+dependency]
 if args.world:command+=['--world',str(args.world.resolve())]
 for row in lock['overlays']:command+=['--mod',row['path']]
 for flag,key in [('--client-mod','clientFiles'),('--server-mod','serverMods')]:
  for row in lock[key]:
   if row['relative'].startswith('mods/') and row['relative'].endswith('.jar') and row['relative']!=lock['replacedMod']:command+=[flag,row['path']]
 old=sys.argv;sys.argv=command
 try:runner.main()
 finally:
  sys.argv=old;rt.copy_inputs=original
  if backend_process is not None:
   owner=json.loads((lab/'local-mc-owner.json').read_text());stop=lab/'coordinator/entry042-backend-stop.json';pending=stop.with_suffix('.pending');pending.write_text(json.dumps({'runId':owner['runId'],'normalStopOnly':True}));os.replace(pending,stop)
   backend_process.wait(timeout=20)
   assert backend_process.returncode==0 and json.loads((lab/'coordinator/entry042-backend-exit.json').read_text())['normalExit'],'backend_normal_shutdown_required'

if __name__=='__main__':main()
