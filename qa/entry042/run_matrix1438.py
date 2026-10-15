"""Drive ONLY an already running, handed-over owned music QA lab; never launches MC."""
from pathlib import Path
import argparse,json,os,subprocess,sys,time
from qa_io import atomic_json
ROOT=Path(__file__).resolve().parent;WORKSPACE=ROOT.parents[2];PRODUCT='9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216'
PHASES={'acquiring':'ACQUIRING','uploading':'UPLOADING','verifying':'VERIFYING','committing':'COMMITTING'}
def read(p):
 try:return json.loads(p.read_text(encoding='utf-8'))
 except (OSError,ValueError):return None
class Driver:
 def __init__(self,root,frozen):
  self.root=root.resolve(strict=True);self.owner=read(root/'local-mc-owner.json');o=self.owner
  assert o and Path(o['instanceRoot']).resolve()==self.root and o['offlineLoopback'] and not o['productionMutation'] and o['clientNames'] in [{'host':'10000'},{'host':'10000','guest':'10001'}]
  lock=read(frozen)
  if lock and lock.get('scope')=='unified-current-v1':
   from current_scope import verify
   verify(self.root,PRODUCT,lock['qaSha256'])
  else:assert (WORKSPACE/'better-mc-remake/build/local-mc-debug').resolve() in self.root.parents
  assert any(p['sha256']==PRODUCT for p in o['inputArtifacts'])
  lock=read(frozen);assert lock and lock['candidateSha256']==PRODUCT and any(p['sha256']==lock['qaSha256'] for p in o['inputArtifacts']), 'exact_frozen_QA_required'
  self.run=o['runId'];self.base=self.root/'coordinator';self.id=int((read(self.base/'entry042-command.json') or {}).get('id',-1));prior=read(self.base/'entry042-matrix-results.json') or {};self.results=list(prior.get('cases',[])) if prior.get('runId')==self.run and prior.get('productSha256')==PRODUCT and prior.get('burnLifecycle')=='connection-escrow-v1' else [];self.metadata={};self.lastNativeSent=0;self.jobs={};self.openedCase=None;self.preCraftGeneration=None
 def wait(self,predicate,label,seconds=30):
  end=time.monotonic()+seconds;report=time.monotonic()
  while True:
   value=predicate()
   if value:return value
   if time.monotonic()>end:raise AssertionError('native_condition_timeout: '+label)
   if time.monotonic()>report:print(json.dumps({'waiting':label,'runId':self.run}),flush=True);report=time.monotonic()+20
   time.sleep(.1)
 def send(self,cmd,**params):
  if cmd=='craft':
   self.preCraftGeneration=self.background().get('generation')
   if self.openedCase:self.jobs.pop(self.openedCase,None)
  if cmd in ['open-computer','open-burner','lock-matrix','slot-attacks','audit-discs','close-menu','stop-server-owned','playback-place','playback-start','playback-return','playback-stop','join-listener','open-existing','reopen-existing-active']:
   delay=1.25-(time.monotonic()-self.lastNativeSent)
   if delay>0:time.sleep(delay)
   self.lastNativeSent=time.monotonic()
  self.id+=1;row={'runId':self.run,'id':self.id,'cmd':cmd,**params};path=self.base/'entry042-command.json';atomic_json(path,row)
  def acknowledged():
   rows=read(self.base/'entry042-client-results.json') or []
   for r in rows:
    if r.get('id')==self.id:
     assert r.get('ok'), 'actual_client_command_failed: '+cmd+' '+str(r)
     return r
  result=self.wait(acknowledged,cmd+' actual-client-command',90)
  if cmd=='open-existing':
   self.openedCase=next((r['caseId'] for r in (read(self.base/'entry042-disc-cases.json') or []) if r.get('marker')==params.get('marker')),None)
  return result
 def status(self):
  value=read(self.base/'entry042-client-status.json') or {};return value if value.get('runId')==self.run else {}
 def background(self):return self.status().get('backgroundTask',{})
 def status_fresh(self):
  path=self.base/'entry042-client-status.json'
  try:stamp=path.stat().st_mtime_ns//1_000_000
  except OSError:return False
  return stamp>=getattr(self,'started',0) and 0<=int(time.time()*1000)-stamp<=2500
 def bind_job(self,id):
  task=self.counts().get('activeTask',{});case=self.case(id)
  if task.get('active') and case.get('escrow')==1 and task.get('marker')==case.get('marker'):
   self.jobs[id]=task['requestId'];return task['requestId']
  return None
 def returned(self,id,burned):
  x=self.case(id)
  return x if x.get('total')==1 and x.get('burned')==burned and x.get('escrow',0)==0 and x.get('input',0)==0 and x.get('output',0)==0 and x.get('carried',0)==0 and x.get('playerBlock',0)==0 and x.get('inventory',0)+x.get('loose',0)==1 else None
 def counts(self):
  x=read(self.base/'entry042-disc-counts.json') or {};return x if x.get('runId')==self.run else {}
 def current_case(self):return (read(self.base/'entry042-disc-cases.json') or [])[-1]['caseId']
 def case(self,id):return next((r for r in self.counts().get('cases',[]) if r['caseId']==id),{})
 def once(self,id,place,burned):
  x=self.case(id);return x if x.get('total')==1 and x.get(place)==1 and x.get('burned')==burned and (place=='escrow' or x.get('escrow',0)==0) else None
 def open(self,menu):
  n=len(read(self.base/'entry042-disc-cases.json') or []);self.send('open-'+menu)
  self.wait(lambda:len(read(self.base/'entry042-disc-cases.json') or [])>n,'fresh marked original CD')
  expected='ComputerMenuScreen' if menu=='computer' else 'CDBurnerMenuScreen';self.wait(lambda:self.status().get('screenClass')==expected,'real original menu visible')
  self.openedCase=self.current_case();return self.openedCase
 def configure(self,phase='acquiring'):
  platform,value=('bilibili','https://www.bilibili.com/video/BV1Q5411x7aE?p=2') if phase=='uploading' else ('qq_music','12345') if phase=='verifying' else ('netease','https://music.163.com/song?id=1868207326')
  self.send('configure',platform=platform,value=value);self.send('readonly-false');self.send('permission')
 def verify_metadata(self,id,phase):
  title,artist=('Owned Bili fixture title / Second part','Owned Bili fixture author') if phase=='uploading' else ('Owned QQ fixture title','Owned QQ fixture artist') if phase=='verifying' else ('Owned Netease fixture title','Owned Netease fixture artist')
  marker=self.case(id)['marker']
  def inventory():
   if not self.status_fresh():return None
   rows=[x for x in self.status().get('inventoryFixtureCds',[]) if x.get('marker')==marker and x.get('count')==1 and x.get('title')==title and artist in x.get('artists',[])]
   return rows[0] if len(rows)==1 and self.once(id,'inventory',True) else None
  actual=self.wait(inventory,'same original inventory CD automatic title and artist')
  s=self.status()
  if s.get('screenClass') in ['ComputerMenuScreen','CDBurnerMenuScreen']:assert s['permissionConfirmed'] and s.get('originalTitleConnected',True) and s.get('durationAutomatic',True)
  self.metadata[id]={'actualTitle':actual['title'],'actualArtists':actual['artists'],'discMarker':marker,'actualInventorySlot':actual['slot'],'originalWidgetsAndCd':True,'delivery':'inventory'}
 def disconnect_return(self,id,burned):
  self.send('disconnect');self.wait(lambda:self.once(id,'inventory',burned),'actual logout original CD returned before player save',90);self.send('reconnect');self.wait(lambda:self.status().get('screenClass')=='NONE','real reconnect',180);self.send('audit-discs')
 def gate(self,phase,mode):self.send('gate',phase=phase,mode=mode)
 def progress(self,case,stage):
  # Hidden screens no longer render status text. Observe the actual connection-owned job.
  expected=self.jobs.get(case);background=self.background()
  if self.status_fresh() and background.get('connectionCurrent') and background.get('progressMatchesRequest') and background.get('generation')==background.get('requestId') and background.get('generation')!=self.preCraftGeneration:
   generation=background.get('generation')
   if generation and (expected is None or generation==expected):
    if expected is None:self.jobs[case]=generation
    if background.get('stage')==stage:return {'generation':generation,'stage':stage,'code':background.get('code',''),'discReturned':background.get('discReturned',False),'actualBackgroundTask':True,'atUtcMillis':int(time.time()*1000)}
  x=read(self.base/'entry042-progress.json') or {};rows=x.get('rows',[]) if x.get('runId')==self.run else []
  expected=self.jobs.get(case)
  if rows:
   row=rows[-1];generation=row.get('generation')
   if row.get('atUtcMillis',0)>=self.started and row.get('stage')==stage and generation and generation!=self.preCraftGeneration and (expected is None or generation==expected):
    if expected is None:self.jobs[case]=generation
    return row
  return None
 def begin(self,menu,phase):
  id=self.open(menu);self.configure(phase);self.gate(phase,'hold');self.started=int(time.time()*1000);self.send('craft')
  self.wait(lambda:self.once(id,'escrow',False),'same marked original CD in server escrow');self.wait(lambda:self.bind_job(id),'actual escrow task correlation');self.wait(lambda:self.progress(id,PHASES[phase]),'actual product '+PHASES[phase]);assert self.case(id).get('input')==0 and self.case(id).get('output')==0
  marker=self.case(id)['marker'];self.send('slot-attacks');self.wait(lambda:any(r.get('discMarker')==marker and r.get('event')=='real-task-locked-attacks' for r in (read(self.base/'entry042-slot-attacks.json') or {}).get('events',[])),'real task native multi-path slot attacks')
  for click,button in [('PICKUP',0),('QUICK_MOVE',0),('SWAP',40),('THROW',0)]:
   count=len((read(self.base/'entry042-click-packets.json') or {}).get('packets',[]));self.send('network-click',slot=0,button=button,click=click)
   def accepted():
    rows=(read(self.base/'entry042-click-packets.json') or {}).get('packets',[])
    if len(rows)<=count:return None
    row=rows[-1];assert row['discMarker']==marker and row['inputCount']==0 and row['outputCount']==0 and row['carriedCount']==0 and row['escrowCount']==1 and row['markedInventoryCount']==0 and row['serverLocked'] and row.get('activeTask',{}).get('requestId')==self.jobs[id];return row
   self.wait(accepted,'actual '+click+' client packet, authoritative original CD remains in escrow')
  return id
 def record(self,menu,phase,outcome,id):
  row={'passed':True,'requestId':self.jobs.get(id),'actualBackgroundTask':self.background(),'menu':menu,'phase':phase,'outcome':outcome,'caseId':id,'counts':self.case(id),'automaticMetadata':self.metadata.get(id),'actualProgressRows':(read(self.base/'entry042-progress.json') or {}).get('rows',[])[-12:]};self.results.append(row);self.save();print(json.dumps({'passedCase':outcome,'menu':menu,'phase':phase,'caseId':id,'total':row['counts'].get('total')}),flush=True)
 def save(self):
  value={'runId':self.run,'productSha256':PRODUCT,'burnLifecycle':'connection-escrow-v1','actualProductProgress':True,'actualServerOriginalStacks':True,'fixtureResponses':True,'ossCalls':0,'mcLaunchedByDriver':False,'cases':self.results};p=self.base/'entry042-matrix-results.json';tmp=p.with_suffix('.pending');tmp.write_text(json.dumps(value,indent=2),encoding='utf-8');os.replace(tmp,p)
 def close(self,id,burned):
  if self.case(id).get('escrow')==1:self.wait(lambda:self.returned(id,burned),'background task completes before normal menu close',150)
  self.send('close-menu');self.wait(lambda:self.returned(id,burned),'original CD delivered exactly once');self.send('audit-discs')
 def done(self,menu,phase,outcomes):
  return any(r.get('menu')==menu and r.get('phase')==phase and r.get('outcome') in outcomes and r.get('passed') is True and r.get('counts',{}).get('total')==1 and r.get('counts',{}).get('escrow',0)==0 and r.get('counts',{}).get('input',0)==0 and r.get('counts',{}).get('output',0)==0 and r.get('counts',{}).get('inventory',0)+r.get('counts',{}).get('loose',0)==1 for r in self.results)
 def lifecycle(self):
  # Resume only successful evidence from THIS exact owned run and candidate.
  for menu in ['computer','burner']:
   for phase in PHASES:
    if not self.done(menu,phase,['success-inventory','success-inventory-disconnect']):
     id=self.begin(menu,phase);self.gate(phase,'off');self.wait(lambda:self.progress(id,'DONE'),'server-acknowledged DONE');self.wait(lambda:self.once(id,'inventory',True),'same original CD delivered to inventory once');self.verify_metadata(id,phase);self.disconnect_return(id,True) if phase=='committing' else self.close(id,True);self.record(menu,phase,'success-inventory-disconnect' if phase=='committing' else 'success-inventory',id)
    if not self.done(menu,phase,['failure']):
     id=self.begin(menu,phase);self.gate(phase,'fail');self.wait(lambda:self.progress(id,'FAILED'),'actual FAILED after fixture refusal');self.wait(lambda:self.returned(id,False),'failed task returns original blank CD');self.gate(phase,'off');self.close(id,False);self.record(menu,phase,'failure',id)
   for phase in ['acquiring','committing']:
    if not self.done(menu,phase,['cancel']):
     id=self.begin(menu,phase);self.send('cancel');self.wait(lambda:self.progress(id,'CANCELLED'),'actual cancelled task');self.wait(lambda:self.counts().get('menuLocked') is False,'server processed actual cancel');self.gate(phase,'off');self.wait(lambda:self.counts().get('receiptPending') is False,'late receipt callback finished without write');self.close(id,False);self.record(menu,phase,'cancel',id)
    if not self.done(menu,phase,['close-background-success']):
     id=self.begin(menu,phase);request=self.jobs[id];self.send('close-ui-esc');self.wait(lambda:self.counts().get('activeTask',{}).get('requestId')==request and self.counts()['activeTask'].get('menuAttached') is False and self.once(id,'escrow',False),'real close retains same background task');self.gate(phase,'off');self.wait(lambda:self.progress(id,'DONE'),'hidden original job reaches server-confirmed DONE',150);self.wait(lambda:self.once(id,'inventory',True),'background completion delivers original CD');self.send('audit-discs');self.record(menu,phase,'close-background-success',id)
   if not self.done(menu,'acquiring',['real-wall-timeout']):
    id=self.begin(menu,'acquiring');self.wait(lambda:self.progress(id,'FAILED'),'unchanged real 600-second server expiry',650);assert int(time.time()*1000)-self.started>=599000 and self.progress(id,'FAILED')['code']=='server_commit_failed','not_the_real_server_expiry';self.gate('acquiring','off');self.wait(lambda:self.returned(id,False),'timeout returns exact original');self.close(id,False);self.record(menu,'acquiring','real-wall-timeout',id)
   if not self.done(menu,'none',['unstarted-disc-disconnect']):
    id=self.open(menu);self.disconnect_return(id,False);self.record(menu,'none','unstarted-disc-disconnect',id)
   if not self.done(menu,'acquiring',['disconnect-reconnect']):
    id=self.begin(menu,'acquiring');self.send('disconnect');self.wait(lambda:self.counts().get('event')=='logout-after-product-unlock','real server logout audit',90);self.wait(lambda:self.once(id,'inventory',False),'disconnect exact original return',90);self.gate('acquiring','off');self.send('reconnect');self.wait(lambda:self.status().get('screenClass')=='NONE','real reconnect',180);self.send('audit-discs');self.record(menu,'acquiring','disconnect-reconnect',id)
 def login(self):
  id=self.open('computer');self.send('unconfirm');self.send('readonly');self.send('clear-local-login');self.send('configure',platform='qq_music',value='https://music.163.com/song?id=1868207326');self.send('craft');self.wait(lambda:self.status().get('nativeLoginScreen') is True,'actual automatic BetterLogin screen')
  self.send('login-return');self.wait(lambda:self.status().get('screenClass')=='ComputerMenuScreen','return original menu');s=self.status();assert s['urlCharacters']==len('https://music.163.com/song?id=1868207326') and s['readOnlySelected'] and not s['permissionConfirmed'] and not s['audioJobStarted']
  self.send('login');self.send('login-success-fixture');self.wait(lambda:self.status().get('nativeLoginSuccessScreen') is True,'original success page with explicitly fake account');self.send('login-confirm');self.wait(lambda:self.status().get('screenClass')=='ComputerMenuScreen','original successful-login return');s=self.status();assert s['urlCharacters']==len('https://music.163.com/song?id=1868207326') and s['readOnlySelected'] and not s['permissionConfirmed'];self.close(id,False);self.record('computer','none','native-login-return',id)
 def lock_matrix(self):
  for menu in ['computer','burner']:
   id=self.open(menu);marker=(read(self.base/'entry042-disc-cases.json') or [])[-1]['marker'];self.send('lock-matrix');self.wait(lambda:self.once(id,'inventory',False) and any(r.get('discMarker')==marker and r.get('event')=='native-lock-matrix-passed' for r in (read(self.base/'entry042-lock-matrix.json') or {}).get('events',[])),'compiled native constructor/cancel/duplicate-return matrix');self.record(menu,'none','native-lock-matrix',id)
 def stop(self):
  id=self.begin('computer','acquiring');self.send('audit-discs');self.send('stop-server-owned')
  def returned():
   history=(read(self.base/'entry042-disc-lifecycle.json') or {}).get('history',[])
   for row in history:
    if row['event']=='server-stopping-after-product-return':
     for item in row['cases']:
      if item['caseId']==id and item['total']==1 and item['inventory']==1 and not item['burned']:return item
  self.wait(returned,'actual ServerStopping exact original return before normal save');self.record('computer','acquiring','server-normal-stop',id)

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--part',choices=['login','lock','lifecycle','stop','reload','all'],default='all');ap.add_argument('--execute',action='store_true');args=ap.parse_args();d=Driver(args.instance_root,args.frozen_inputs)
 if not args.execute:print(json.dumps({'verifiedOwnedLab':True,'mcStarted':False,'commandsSent':False,'part':args.part,'product':PRODUCT}));return
 try:
  if args.part in ['login','all']:d.login()
  if args.part in ['lock','all']:d.lock_matrix()
  if args.part in ['lifecycle','all']:d.lifecycle()
  if args.part in ['stop','all']:d.stop()
  if args.part=='reload':d.send('audit-discs');d.wait(lambda:all(r['total']==1 for r in d.counts().get('cases',[])) and len(d.counts().get('cases',[]))>0,'persisted exact original discs after server/player reload');d.record('inventory','none','normal-reload',d.current_case())
 except Exception as error:d.results.append({'passed':False,'errorType':type(error).__name__,'reason':str(error)});d.save();raise
if __name__=='__main__':main()
