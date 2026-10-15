"""Owned native negotiation/late-join regression. Never starts MC; requires --execute."""
from pathlib import Path
import argparse,json,time,sys
from run_matrix1438 import Driver,read,WORKSPACE
from run_lyrics4 import Dual
from run_supplement3 import ready,seed_reload
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--prior-root',type=Path,required=True);ap.add_argument('--mode',choices=['unsupported','late-join'],required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args();d=Driver(a.instance_root,a.frozen_inputs);dual=Dual(d);results=[]
 if not a.execute:print(json.dumps({'verifiedOnly':True,'mcStarted':False}));return
 def host_audio(ref,guest_required=True):
  if guest_required:rows=dual.pair(ref,False)
  else:
   value=read(d.base/'entry042-playback-observed-host.json') or {}
   if value.get('runId')!=d.run or not 0<=int(time.time()*1000)-value.get('atUtcMillis',0)<=2000:return None
   sounds=[x for x in value.get('sounds',[]) if not x.get('stopped') and x.get('reference')==ref]
   if len(sounds)!=1:return None
   assert (read(d.base/'status-guest.json') or {}).get('connected') is False
   rows=[{'role':'host','observation':value,'sound':sounds[0]}]
  return rows if rows and rows[0]['sound'] and rows[0]['sound'].get('privateClock',{}).get('ready') is True and rows[0]['sound'].get('audio',{}).get('sourceState')==4114 and rows[0]['sound']['audio'].get('gain',0)>0 else None
 def events():return [x for x in (read(d.base/'entry042-events-server.json') or {}).get('events',[]) if x.get('kind')=='actual-private-delivery-result']
 try:
  ready(d,True);seed_reload(d,a.prior_root,a.frozen_inputs);d.send('pause-focus-false');dual.send('pause-focus-false');d.send('lyrics-on');dual.send('lyrics-on');case=next(x for x in dual.inventory() if dual.origin(x['reference'])['platform']=='netease');dual.start_ready(case);ref=case['reference'];before=d.wait(lambda:host_audio(ref),'actual host PCM/OpenAL starts',30);ident=before[0]['sound']['soundIdentity'];dual.record('network9-host-start',before)
  if a.mode=='unsupported':
   omitted=read(d.base/'entry042-events-guest.json') or {};assert any(x.get('kind')=='owned-private-payload-registration-omitted' for x in omitted.get('events',[]))
   missing=d.wait(lambda:next((x for x in events() if x.get('player')=='10001' and x.get('result')=='UNSUPPORTED' and x.get('protocol')=='PLAY' and x.get('connected') and x.get('accepting') and x.get('stateChannel') is False and x.get('reportChannel') is False),None),'actual optional negotiation omits both guest channels',20)
   start=time.monotonic();d.wait(lambda:time.monotonic()-start>=10,'ten seconds with unsupported receiver',15);after=d.wait(lambda:host_audio(ref),'host survives repeated unsupported recipients',10);assert after[0]['sound']['soundIdentity']==ident and after[0]['sound']['audio']['elapsedSeconds']-before[0]['sound']['audio']['elapsedSeconds']>=8;assert after[1]['sound'] is None;dual.record('network9-unsupported-host-continuous',after,negotiation=missing)
   dual.send('disconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is False,'unsupported guest actually disconnected',90);dual.send('reconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is True,'unsupported guest actually reconnects',180);dual.send('join-listener');after=d.wait(lambda:host_audio(ref),'same host playing after unsupported rejoin',15);assert after[0]['sound']['soundIdentity']==ident;assert after[1]['sound'] is None;dual.record('network9-unsupported-rejoin-host-continuous',after)
   assert any(x.get('player')=='10000' and x.get('result')=='SENT' and x.get('stateChannel') and x.get('reportChannel') for x in events());results.append({'case':'actual-unsupported-receiver-start-stop-rejoin','passed':True})
  else:
   d.send('playback-return');d.wait(lambda:d.once(case['caseId'],'inventory',True),'prior original CD returned');dual.send('disconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is False,'late listener leaves before start',90)
   d.send('playback-place',marker=case['marker'],site=0);d.wait(lambda:d.case(case['caseId']).get('playerBlock')==1,'same original CD placed');d.send('playback-start');before=d.wait(lambda:host_audio(ref,False),'host starts while guest absent',30);ident=before[0]['sound']['soundIdentity'];start=time.monotonic();d.wait(lambda:time.monotonic()-start>=5,'host actually plays before late join',10);dual.send('reconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is True,'actual late guest joins',180);dual.send('join-listener')
   def both():
    rows=dual.pair(ref)
    return rows if rows and all(x['sound'].get('privateClock',{}).get('ready') is True and x['sound'].get('hasLyrics') and x['sound'].get('audio',{}).get('sourceState')==4114 and x['sound']['audio'].get('gain',0)>0 for x in rows) else None
   rows=d.wait(both,'both real clients audible after late join',30);assert rows[0]['sound']['soundIdentity']==ident;assert abs(rows[0]['sound']['audio']['elapsedSeconds']-rows[1]['sound']['audio']['elapsedSeconds'])<=2;assert rows[0]['sound']['line']==rows[1]['sound']['line'];dual.record('network9-actual-late-join-two-client-audio',rows);results.append({'case':'actual-late-join-two-client-audio','passed':True})
  d.send('playback-return');d.wait(lambda:d.once(case['caseId'],'inventory',True),'original disc returned once');d.wait(lambda:all(x['sound'] is None for x in (dual.pair(ref,False) or [{'sound':1}])),'both no sound after original return',15)
 except Exception as error:results.append({'passed':False,'reason':str(error)});dual.record('FAILED-network9',dual.pair(active=False) or [],reason=str(error));raise
 finally:
  (d.base/'entry042-network9-results.json').write_text(json.dumps({'runId':d.run,'mode':a.mode,'cases':results,'actualNegotiation':True,'ossRequests':0,'actualPlatformCalls':0},indent=2),encoding='utf-8')
  if not (d.root/'run-result.json').exists():
   if any(x.get('playerBlock') for x in d.counts().get('cases',[])):d.send('playback-return');d.wait(lambda:all(x['total']==1 and x['inventory']==1 for x in d.counts()['cases']),'all six originals returned')
   sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'));import local_mc_debug as runner;runner.stop_files(d.root,d.owner)
 print(json.dumps({'network9Finished':True,'failedCases':sum(not x.get('passed') for x in results)}),flush=True)
if __name__=='__main__':main()
