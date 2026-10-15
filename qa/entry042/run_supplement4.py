"""Remaining native checks only; never launch MC or repeat long menu cases."""
from pathlib import Path
import argparse,json,time,traceback
from run_matrix1438 import Driver,read,PRODUCT
from run_lyrics4 import Dual
from run_supplement3 import ready,stash_starter,seed_reload
def pause_or_rejoin(d,dual,case,rejoin):
 reference=case['reference'];d.send('lyrics-on');dual.send('lyrics-on');dual.start_ready(case,site=0)
 try:
  def active():
   rows=dual.pair(reference);return rows if rows and all(x['sound'].get('audio',{}).get('sourceState')==4114 and x['sound'].get('hasLyrics') and x['sound'].get('privateClock',{}).get('ready') is True and x['sound'].get('audio',{}).get('actualStreamType')=='net.muxigame.musiccompat.burn.PrivatePcmStream' for x in rows) else None
  initial=d.wait(active,'fresh two-client audio and lyrics',30);dual.record('rejoin-before' if rejoin else 'pause-before',initial)
  if rejoin:
   identity=initial[0]['sound']['soundIdentity'];dual.send('disconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is False,'actual guest disconnect',90);dual.send('reconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is True,'actual guest reconnect',180);dual.send('join-listener');rows=d.wait(active,'rejoined same original media',30);dual.record('rejoin-observed',rows,hostSoundIdentityBefore=identity)
   assert rows[0]['sound']['soundIdentity']==identity,'host restarted/looped while guest rejoined; continuous playback not verified'
   assert abs(rows[0]['sound']['audio']['elapsedSeconds']-rows[1]['sound']['audio']['elapsedSeconds'])<=2,'rejoined audio timeline drift'
   assert rows[0]['sound'].get('line')==rows[1]['sound'].get('line'),'rejoined lyric timeline drift'
  else:
   d.send('audio-pause');dual.send('audio-pause');paused=int(time.time()*1000)
   def fresh_paused():
    rows=dual.pair(reference);return rows if rows and all(x['sound'].get('audio',{}).get('atUtcMillis',0)>paused and x['sound']['audio'].get('sourceState')==4115 for x in rows) else None
   before=d.wait(fresh_paused,'both actual OpenAL paused',10);start=time.monotonic();d.wait(lambda:time.monotonic()-start>=5,'actual five second pause',10);after=d.wait(lambda:dual.pair(reference),'fresh paused samples',10);dual.record('pause-observed-five-seconds',after,before=before)
   try:
    for a,b in zip(before,after):assert b['observation']['soundManagerPaused'] and b['sound']['audio']['sourceState']==4115 and abs(a['sound']['audio']['elapsedSeconds']-b['sound']['audio']['elapsedSeconds'])<=.25 and abs(a['sound']['soundTick']-b['sound']['soundTick'])<=1,'paused lyric/audio clock advanced'
   finally:
    d.send('audio-resume');dual.send('audio-resume');rows=d.wait(active,'both original audio resumed',10);dual.record('resume-observed',rows)
    resumed=rows;start=time.monotonic();d.wait(lambda:time.monotonic()-start>=5,'five seconds of actual resumed audio and lyric progression',10);advanced=d.wait(active,'resumed fresh two-client PCM/lyrics',10);dual.record('resume-observed-five-seconds',advanced,before=resumed)
    for a,b in zip(resumed,advanced):
     assert a['sound']['soundIdentity']==b['sound']['soundIdentity'],'resume replaced original sound'
     assert b['sound']['audio']['elapsedSeconds']-a['sound']['audio']['elapsedSeconds']>=4.5 and b['sound']['soundTick']-a['sound']['soundTick']>=80,'resumed audio/lyric clock failed to advance'
     assert abs(b['sound']['soundTick']/20-b['sound']['audio']['elapsedSeconds'])<=2,'resumed PCM/lyric drift'

 except Exception as error:dual.record('FAILED-rejoin' if rejoin else 'FAILED-pause',dual.pair(reference,False) or [],reason=str(error));raise
 finally:
  d.send('audio-resume');dual.send('audio-resume');d.send('playback-return');d.wait(lambda:d.once(case['caseId'],'inventory',True),'same original CD returned');d.wait(lambda:all(x['sound'] is None for x in (dual.pair(reference,False) or [{'sound':1}])),'no original audio after disc return',15)
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--prior-root',type=Path,required=True);ap.add_argument('--part',choices=['first','reload'],required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args();d=Driver(a.instance_root,a.frozen_inputs);assert d.owner['clientNames']=={'host':'10000','guest':'10001'};dual=Dual(d);results=[]
 if not a.execute:print(json.dumps({'verifiedOnly':True,'mcStarted':False}));return
 def attempt(label,callback):
  try:callback();row={'case':label,'passed':True}
  except Exception as error:row={'case':label,'passed':False,'reason':str(error)};dual.record('FAILED-supplement',dual.pair(active=False) or [],scenario=label,reason=str(error),traceback=traceback.format_exc())
  results.append(row);(d.base/'entry042-supplement4-results.json').write_text(json.dumps({'runId':d.run,'cases':results,'realIdentity':False,'ossRequests':0},indent=2),encoding='utf-8');print(json.dumps(row),flush=True)
 try:
  ready(d,True);d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') and (read(d.base/'entry042-client-status-guest.json') or {}).get('fixtureCookiesInstalled'),'guest ready',600)
  if a.part=='first':
   prior=read(a.prior_root/'local-mc-owner.json');pr=read(a.prior_root/'run-result.json');pe=read(a.prior_root/'coordinator/entry042-backend-exit.json');assert pr['runId']==prior['runId']==pe['runId'] and pr['normalExit'] and pe['normalExit'] and all(x==0 for x in pr['exitCodes'].values()) and pe['exitCode']==0
   assert any(x['sha256']==PRODUCT for x in prior['inputArtifacts']);assert read(a.prior_root/'coordinator/entry042-disc-cases.json')==read(d.base/'entry042-disc-cases.json');d.send('audit-discs');d.wait(lambda:len(d.counts()['cases'])==4 and all(x['total']==1 for x in d.counts()['cases']),'four saved original discs restored')
   d.record('inventory','none','normal-rootA-reload-with-repaired-QA',d.current_case());stash_starter(d)
   for platform,value,label in [('netease','https://music.163.com/song?id=1868207326','prepare-netease'),('qq_music','https://entry042.qqmusic.qq.com/owned.wav','prepare-owned-direct')]:
    ident=d.open('computer');d.send('configure',platform=platform,value=value);d.send('readonly-false');d.send('permission');d.gate('acquiring','off');d.started=int(time.time()*1000);d.send('craft');d.wait(lambda:d.progress(ident,'DONE'),'prepare self-owned original disc',120);d.close(ident,True);d.record('computer','fixture-preparation',label,ident)
  else:seed_reload(d,a.prior_root,a.frozen_inputs)
  d.send('audio-enable-owned');dual.send('audio-enable-owned');d.send('pause-focus-false');dual.send('pause-focus-false');d.send('lyrics-on');dual.send('lyrics-on');d.send('audio-resume');dual.send('audio-resume')
  groups={}
  for case in dual.inventory():groups.setdefault(dual.origin(case['reference'])['platform'],[]).append(case)
  assert all(groups.get(x) for x in ['netease','qq','bilibili','none'])
  for platform in ['netease','qq','bilibili','none']:attempt(platform,lambda p=platform:dual.play(groups[p][0],p))
  for platform,value,label,source in [('qq_music','003ENTRY042abc','qq-songmid','qq'),('bilibili','https://www.bilibili.com/video/BV0000000000?p=2','bilibili-without-subtitles','bilibili'),('netease','https://music.163.com/song?id=12345','netease-no-translation','netease')]:
   def variant(p=platform,v=value,l=label,s=source):
    case=dual.rewrite(groups[s][-1],p,v,l);dual.play(case,l,site=1)
   attempt(label,variant)
  case=next(x for x in dual.inventory() if dual.origin(x['reference'])['platform']=='netease');d.send('lyrics-off');dual.send('lyrics-off');attempt('original-lyric-switch-off',lambda:dual.play(case,'original-lyric-switch-off',seconds=15,force_no_lyrics=True));d.send('lyrics-on');dual.send('lyrics-on')
  attempt('actual-pause-resume',lambda:pause_or_rejoin(d,dual,case,False));attempt('actual-rejoin',lambda:pause_or_rejoin(d,dual,case,True))
 finally:
  # A runner boot failure already stops normally; do not send to exited clients.
  if not (d.root/'run-result.json').exists():
   dual.send('finish');d.send('close-menu');d.send('audit-discs');d.send('stop-server-owned')
   d.wait(lambda:any(x.get('event')=='server-stopping-after-product-return' and x.get('cases') and all(v['total']==1 and v['inventory']==1 for v in x['cases']) for x in (read(d.base/'entry042-disc-lifecycle.json') or {}).get('history',[])),'normal stop preserves every original disc',30)
   d.record('inventory','none','supplement-normal-stop',d.current_case())
 print(json.dumps({'supplementFinished':True,'failedCases':sum(not x['passed'] for x in results),'longMenuCasesRepeated':False}),flush=True)
if __name__=='__main__':main()
