"""Real background burn acceptance in an already owned lab; never launches MC.

Creates only locally marked original fixture CDs. Run after the strict six-CD playback
reload, or in a separately coordinated owned lab. Does not change product/version pins.
"""
from pathlib import Path
import argparse,json,os,time
from run_matrix1438 import Driver,read,PRODUCT,PHASES

class BackgroundBurn:
 def __init__(self,driver):
  self.d=driver;prior=read(driver.base/'entry042-background-burn-results.json') or {}
  valid=prior.get('runId')==driver.run and prior.get('productSha256')==PRODUCT
  self.cases=list(prior.get('cases',[])) if valid else [];self.points=list(prior.get('points',[])) if valid else []
 def save(self):
  d=self.d;value={'runId':d.run,'productSha256':PRODUCT,'burnLifecycle':'connection-escrow-v1','cases':self.cases,'points':self.points,'mcStarted':False,'actualPlatformCalls':0,'ossRequests':0,'syntheticMediaAndAccounts':True,'nativeTestPerformed':bool(self.cases),'osWindowCloseTested':False}
  p=d.base/'entry042-background-burn-results.json';pending=p.with_suffix('.pending');pending.write_text(json.dumps(value,indent=2),encoding='utf-8');os.replace(pending,p)
 def point(self,label,id,**facts):
  d=self.d;self.points.append({'label':label,'caseId':id,'requestId':d.jobs.get(id),'atUtcMillis':int(time.time()*1000),'case':d.case(id),'serverTask':d.counts().get('activeTask',{}),'clientTask':d.background(),'screen':d.status().get('screenClass'),'receiptPending':d.counts().get('receiptPending'),**facts});self.save()
 def done(self,menu,phase,scenario):
  return any(x.get('passed') is True and x.get('menu')==menu and x.get('phase')==phase and x.get('scenario')==scenario and x.get('counts',{}).get('total')==1 and x.get('counts',{}).get('escrow',0)==0 for x in self.cases)
 def task_alive(self,id,request,closed=False):
  d=self.d;case=d.once(id,'escrow',False);server=d.counts().get('activeTask',{});client=d.background()
  if not case or case.get('input')!=0 or case.get('output')!=0 or case.get('inventory')!=0:return None
  if server.get('requestId')!=request or not server.get('active') or not server.get('samePlayerObject'):return None
  if not d.status_fresh() or not client.get('active') or not client.get('connectionCurrent') or not client.get('noncePresent') or client.get('requestId')!=request or client.get('generation')!=request:return None
  if closed and server.get('menuAttached') is not False:return None
  return {'case':case,'server':server,'client':client}
 def query(self,id):
  d=self.d;request=d.jobs[id];leaf=d.base/'entry042-burn-query-host.json';before=read(leaf) or {};count=before.get('replyCount',0) if before.get('runId')==d.run and before.get('requestId')==request else 0;sent=int(time.time()*1000);d.send('query-active')
  def received():
   row=read(leaf) or {}
   return row if row.get('runId')==d.run and row.get('requestId')==request and row.get('replyCount',0)>count and row.get('atUtcMillis',0)>=sent and row.get('status')==4 and row.get('sameRequest') and row.get('sameNonce') and row.get('sameOriginalConnection') else None
  reply=d.wait(received,'actual nonce-bound active-task query response',15)
  self.point('actual-status-query-roundtrip',id,queryCommandSent=True,queryResponseObserved=True,queryReply={key:reply.get(key) for key in ['runId','requestId','replyCount','atUtcMillis','status','sameRequest','sameNonce','sameOriginalConnection']})
 def close(self,id,route):
  d=self.d;request=d.jobs[id];d.send('close-ui-'+route)
  d.wait(lambda:self.task_alive(id,request,True),'same original escrow after native '+route+' close',30)
  actions=(read(d.base/'entry042-ui-close-actions.json') or {}).get('actions',[])
  assert any(x.get('route')==route and x.get('screenClosed') and x.get('originalScreenInputInvoked') and x.get('beforeTask',{}).get('requestId')==request and x.get('afterTask',{}).get('requestId')==request for x in actions),'actual_close_action_evidence_required'
  self.point('native-'+route+'-closed-background-continues',id)
 def reopen(self,id,menu):
  d=self.d;request=d.jobs[id];d.send('reopen-existing-active',menu=menu);expected='ComputerMenuScreen' if menu=='computer' else 'CDBurnerMenuScreen'
  d.wait(lambda:d.status().get('screenClass')==expected and self.task_alive(id,request),'same connection reopens same existing task',30)
  background=d.background();assert background.get('progressMatchesRequest') and background.get('stage') in PHASES.values(),'actual_progress_subscription_replayed'
  self.point('reopened-same-request-and-original-CD',id,newTaskCreated=False,newDiscCreated=False)
 def complete(self,id,phase):
  d=self.d;d.gate(phase,'off');d.wait(lambda:d.progress(id,'DONE'),'actual hidden job server-confirmed completion',150);d.wait(lambda:d.once(id,'inventory',True),'one original CD delivered to real inventory',30);d.verify_metadata(id,phase);self.point('actual-completion-single-inventory-CD',id)
 def background(self,id,menu,phase,route):
  self.close(id,route);self.query(id);self.reopen(id,menu);self.query(id)
  # Close again: prove final completion while the original UI really stays hidden.
  self.close(id,'esc');self.complete(id,phase);self.d.send('audit-discs')
 def cancel(self,id,menu,phase):
  d=self.d;self.close(id,'esc');self.reopen(id,menu);d.send('cancel');d.wait(lambda:d.progress(id,'CANCELLED'),'actual explicit cancellation',30)
  d.wait(lambda:d.returned(id,False),'explicit cancel refunds the original blank CD once',30);d.gate(phase,'off');d.wait(lambda:d.counts().get('receiptPending') is False,'late verification callback cannot write after cancel',30);d.send('audit-discs')
  d.wait(lambda:d.background().get('discReturned') is True,'matching server acknowledgement of refund',15);self.point('explicit-cancel-and-late-callback-no-extra-CD',id);d.close(id,False)
 def disconnect(self,id,menu,phase):
  d=self.d;request=d.jobs[id];self.close(id,'esc');d.send('disconnect')
  d.wait(lambda:d.counts().get('event')=='logout-after-product-unlock' and d.returned(id,False),'logout original CD refunded before player save',90);self.point('actual-disconnect-single-original-return',id)
  d.gate(phase,'off');d.wait(lambda:d.counts().get('receiptPending') is False,'old connection receipt callback finishes without write',30);d.send('reconnect');d.wait(lambda:d.status().get('screenClass')=='NONE','actual host reconnect',180);d.send('audit-discs')
  d.wait(lambda:d.once(id,'inventory',False),'same original disc preserved in reloaded player inventory',30)
  current=d.background();assert not current.get('active') and current.get('requestId')!=request,'old task must not bind to replacement connection';self.point('replacement-connection-no-old-task',id,oldRequestId=request)
 def cleanup(self,phase,id):
  d=self.d
  try:
   if d.background().get('active'):
    if d.status().get('screenClass') not in ['ComputerMenuScreen','CDBurnerMenuScreen']:d.send('reopen-existing-active',menu='computer');d.wait(lambda:d.status().get('screenClass')=='ComputerMenuScreen','owned failure cleanup existing menu',30)
    d.send('cancel')
  finally:d.gate(phase,'off')
  if id:d.wait(lambda:d.returned(id,False) or d.returned(id,True),'owned failure cleanup one actual original disc',60)
 def run_case(self,menu,phase,scenario,route=None):
  if self.done(menu,phase,scenario):return
  d=self.d;id=None;d.openedCase=None
  try:
   id=d.begin(menu,phase);self.point('real-gated-burn-and-escrow-attacks',id,menu=menu,phase=phase)
   if scenario.startswith('background-'):self.background(id,menu,phase,route)
   elif scenario=='cancel':self.cancel(id,menu,phase)
   elif scenario=='disconnect':self.disconnect(id,menu,phase)
   else:raise ValueError('unknown_owned_burn_scenario')
   result={'menu':menu,'phase':phase,'scenario':scenario,'caseId':id,'requestId':d.jobs.get(id),'passed':True,'counts':d.case(id),'automaticMetadata':d.metadata.get(id)}
  except Exception as error:
   if id is None:id=d.openedCase
   result={'menu':menu,'phase':phase,'scenario':scenario,'caseId':id,'passed':False,'errorType':type(error).__name__,'reason':str(error),'counts':d.case(id) if id else {}}
   if route=='x' and ('native_close_x_not_present' in str(error) or 'native_close_x_disabled' in str(error)):result['notCovered']=True
   self.cases.append(result);self.save()
   try:self.cleanup(phase,id)
   except Exception as cleanup:self.points.append({'label':'FAILED-owned-burn-cleanup','reason':str(cleanup)});self.save()
   raise
  self.cases.append(result);self.save();print(json.dumps({'burnCase':scenario,'menu':menu,'phase':phase,'passed':True,'caseId':id}),flush=True)

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--menus',default='computer,burner');ap.add_argument('--phases',default='uploading,verifying,committing');ap.add_argument('--routes',default='esc,onclose');ap.add_argument('--part',choices=['background','cancel','disconnect','all'],default='all');ap.add_argument('--probe-x',action='store_true');ap.add_argument('--execute',action='store_true');args=ap.parse_args()
 menus=args.menus.split(',');phases=args.phases.split(',');routes=args.routes.split(',');assert set(menus)<=set(['computer','burner']) and set(phases)<=set(PHASES) and set(routes)<=set(['esc','onclose','x'])
 if args.probe_x and 'x' not in routes:routes.append('x')
 d=Driver(args.instance_root,args.frozen_inputs);burn=BackgroundBurn(d)
 if not args.execute:print(json.dumps({'verifiedOwnedLab':True,'commandsSent':False,'mcStarted':False,'productSha256':PRODUCT,'additionalLocalFixtureDiscs':True,'osWindowCloseTested':False}));return
 for menu in menus:
  for phase in phases:
   if args.part in ['background','all']:
    for route in routes:burn.run_case(menu,phase,'background-'+route,route)
   if args.part in ['cancel','all']:burn.run_case(menu,phase,'cancel')
   if args.part in ['disconnect','all']:burn.run_case(menu,phase,'disconnect')
 print(json.dumps({'backgroundBurnFinished':True,'passedCases':sum(x.get('passed') is True for x in burn.cases),'failedCases':sum(x.get('passed') is False for x in burn.cases),'mcStarted':False,'osWindowCloseTested':False}),flush=True)
if __name__=='__main__':main()
