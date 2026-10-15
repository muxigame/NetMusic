"""Only drive an already owned candidate4 lab; historical filename retained. No MC launch; verify-only by default."""
from pathlib import Path
import argparse,json,os,time
from run_matrix1438 import Driver,read,PRODUCT,WORKSPACE

def write(path,value):
 tmp=path.with_suffix('.pending');tmp.write_text(json.dumps(value,indent=2),encoding='utf-8');os.replace(tmp,path)
def ready(d,settle=False):
 def available():
  if (d.root/'run-result.json').exists():raise RuntimeError('owned_run_ended')
  s=d.status();h=read(d.base/'status-host.json') or {};p=d.root/'host/boot.log';log=p.read_text(encoding='utf-8',errors='replace') if p.exists() else ''
  return h.get('connected') and s.get('fixtureCookiesInstalled') and s.get('screenClass')=='NONE' and s.get('overlayClass')=='NONE' and log.rfind('resource reload: FINISHED')>log.rfind('resource reload: STARTING')
 d.wait(available,'actual connection, no overlay, resource reload complete',600)
 if settle:
  start=time.monotonic()
  d.wait(lambda:time.monotonic()-start>=90 and available(),'post-join resource reload settled',300)
def seed_reload(d,prior,frozen):
 prior=prior.resolve(strict=True);owner=read(prior/'local-mc-owner.json');result=read(prior/'run-result.json');old=read(prior/'coordinator/entry042-matrix-results.json');lock=read(frozen)
 assert (WORKSPACE/'better-mc-remake/build/local-mc-debug').resolve() in prior.parents
 assert owner and result and old and owner['runId']==result['runId']==old['runId']
 assert Path(owner['instanceRoot']).resolve()==prior and owner['offlineLoopback'] and not owner['productionMutation'] and owner['clientNames'] in [{'host':'10000'},{'host':'10000','guest':'10001'}]
 assert result['normalExit'] and result['normalStopOnly'] and all(x==0 for x in result['exitCodes'].values())
 expected_prior=lock.get('importWorldProductSha256',PRODUCT)
 assert old['productSha256']==expected_prior and any(x['sha256']==expected_prior for x in owner['inputArtifacts'])
 assert any(x['sha256']==lock.get('importWorldQaSha256',lock['qaSha256']) for x in owner['inputArtifacts']),'same_frozen_QA_required'
 cases=read(prior/'coordinator/entry042-disc-cases.json');loaded=read(d.base/'entry042-disc-cases.json');assert cases==loaded and len(cases)>0
 d.send('audit-discs');d.wait(lambda:len(d.counts().get('cases',[]))==len(cases) and all(x['total']==1 for x in d.counts()['cases']),'every actual saved original CD restored')
 assert not d.results,'new reloaded run required; do not overwrite current evidence'
 d.results=[{**x,'originRunId':owner['runId'],'originRoot':str(prior),'originProductSha256':expected_prior} for x in old['cases'] if x.get('counts',{}).get('total')==1]
 d.record('inventory','none','normal-reload',d.current_case())
def stash_starter(d):
 state=read(d.base/'status-server.json');inv=state['byName']['10000']['inventoryBySlot'];wanted={'muxi_terminal:player_terminal','ftbquests:book','minecraft:chainmail_helmet','minecraft:chainmail_chestplate','minecraft:chainmail_leggings','minecraft:chainmail_boots','minecraft:iron_sword','minecraft:bread'}
 selected=[(int(slot),row) for slot,row in inv.items() if row['item'] in wanted];assert len(selected)<=8 and all(slot<36 for slot,row in selected)
 seq=int((read(d.base/'command-server.json') or {}).get('id',0));commands=[]
 def native(command):
  nonlocal seq
  seq+=1;write(d.base/'command-server.json',{'runId':d.run,'id':seq,'type':'execute','command':command});row=d.wait(lambda:read(d.base/f'result-server-{seq}.json'),'original owned vanilla fixture transfer');assert row['ok'];commands.append({'command':command,'resultId':seq})
 if selected:
  native('execute as 10000 at @s run setblock ~2 ~ ~ minecraft:chest')
  for dest,(slot,row) in enumerate(selected):
   current=read(d.base/'status-server.json')['byName']['10000']['inventoryBySlot'];assert current[str(slot)]['item']==row['item'] and current[str(slot)]['count']==row['count']
   source='hotbar.'+str(slot) if slot<9 else 'inventory.'+str(slot-9)
   native(f'execute as 10000 at @s run item replace block ~2 ~ ~ container.{dest} from entity @s {source}')
   native(f'item replace entity 10000 {source} with minecraft:air')
 write(d.base/'entry042-starter-transfer.json',{'runId':d.run,'fixtureOnly':True,'musicDiscsUntouched':True,'originalNonMusicStacksRetainedInChest':True,'before':selected,'commands':commands});d.send('audit-discs')
def picker(d,action,menu):
 path=d.base/'entry042-native-picker-results.json';state=read(path) or {'runId':d.run,'cases':[]};assert state['runId']==d.run
 if action=='picker-open':
  id=d.open(menu);d.configure();d.send('pause-focus-true');before=d.wait(lambda:d.status() if d.status().get('permissionConfirmed') and not d.status().get('readOnlySelected') and not d.status().get('audioJobStarted') and (menu!='computer' or d.status().get('urlCharacters')==len('https://music.163.com/song?id=1868207326')) else None,'fresh configured original widget snapshot');assert not before['audioJobStarted'];opened=int(time.time()*1000);d.send('pick');d.wait(lambda:d.status().get('chooserOpen'),'actual native Windows picker active')
  state['active']={'menu':menu,'caseId':id,'expectedScreen':before['screenClass'],'urlCharacters':before.get('urlCharacters'),'readOnlySelected':before['readOnlySelected'],'permissionConfirmed':before['permissionConfirmed'],'openedAt':opened,'stage':'await-native-x-close'}
 else:
  active=state['active'];assert active['menu']==menu;id=active['caseId']
  if action=='picker-cancel':
   s=d.wait(lambda:d.status() if not d.status().get('chooserOpen') and d.status().get('screenClass')==active['expectedScreen'] else None,'actual native cancellation returned same page')
   assert not s['audioJobStarted'] and s['readOnlySelected']==active['readOnlySelected'] and s['permissionConfirmed']==active['permissionConfirmed'] and s.get('urlCharacters')==active['urlCharacters'];d.wait(lambda:d.once(id,'input',False),'cancel retained same original blank CD');active['stage']='native-cancel-observed';active['cancelStatus']=s
  elif action=='picker-reopen':
   assert active['stage']=='native-cancel-observed';active['openedAt']=int(time.time()*1000);d.send('pick');d.wait(lambda:d.status().get('chooserOpen'),'second actual native picker active');active['stage']='await-native-owned-wav-select'
  else:
   assert active['stage']=='await-native-owned-wav-select';d.started=active['openedAt'];d.wait(lambda:d.progress(id,'DONE'),'real product local import DONE',120);d.wait(lambda:d.once(id,'output',True),'same original local CD written once')
   s=d.wait(lambda:d.status() if d.status().get('outputTitle')=='entry042-owned-cc0' and '未知作者' in d.status().get('outputArtists',[]) else None,'actual local filename and unknown-author fallback');active['actualOutput']=s;d.metadata[id]={'actualTitle':s['outputTitle'],'actualArtists':s['outputArtists'],'originalWidgetsAndCd':True};d.close(id,True);d.send('pause-focus-false');d.record(menu,'local','native-picker-cancel-and-owned-wav',id);state['cases'].append(active);del state['active']
 write(path,state)
 print(json.dumps({'runId':d.run,'action':action,'nextNativeOsAction':state.get('active',{}).get('stage'),'fakeResponses':True,'ossCalls':0}),flush=True)
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--part',choices=['entry-stop','reload-stash','picker-open','picker-cancel','picker-reopen','picker-done','lifecycle-stop','reload-final'],required=True);ap.add_argument('--menu',choices=['computer','burner']);ap.add_argument('--prior-root',type=Path);ap.add_argument('--execute',action='store_true');args=ap.parse_args();d=Driver(args.instance_root,args.frozen_inputs)
 if not args.execute:print(json.dumps({'verifiedOwnedCandidate3':True,'commandsSent':False,'mcStarted':False,'part':args.part}));return
 try:
  if args.part=='entry-stop':
   assert not read(d.base/'entry042-disc-cases.json'),'fresh candidate3 smoke lab required';ready(d,True);d.login()
   for phase in ['uploading','verifying']:
    id=d.begin('burner',phase);d.gate(phase,'off');d.wait(lambda:d.progress(id,'DONE'),'original burner outer-entry DONE');d.wait(lambda:d.once(id,'output',True),'original burner CD written once');d.verify_metadata(id,phase);d.close(id,True);d.record('burner',phase,'success',id)
   d.stop()
  elif args.part in ['reload-stash','reload-final']:
   assert args.prior_root;ready(d,True);seed_reload(d,args.prior_root,args.frozen_inputs)
   if args.part=='reload-stash':stash_starter(d)
  elif args.part.startswith('picker-'):
   assert args.menu
   if args.part=='picker-open':ready(d)
   picker(d,args.part,args.menu)
  else:
   ready(d);p=read(d.base/'entry042-native-picker-results.json') or {};assert {x['menu'] for x in p.get('cases',[])}=={'computer','burner'};d.lifecycle();d.stop()
 except Exception as error:d.results.append({'passed':False,'errorType':type(error).__name__,'reason':str(error)});d.save();raise
if __name__=='__main__':main()
