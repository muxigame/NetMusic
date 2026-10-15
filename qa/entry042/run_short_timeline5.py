"""Candidate10 short native checks in an already owned lab. Never launches MC."""
from pathlib import Path
import argparse,hashlib,json,os,time,traceback
from run_matrix1438 import Driver,read,WORKSPACE
from run_lyrics4 import Dual
from run_supplement3 import ready,seed_reload

AUDIO_BUDGET_SECONDS=90
LYRIC_BUDGET_SECONDS=60
PCM_STREAM='net.muxigame.musiccompat.burn.PrivatePcmStream'

def audio_ready(rows,now_ms=None):
 now_ms=int(time.time()*1000) if now_ms is None else now_ms
 return bool(rows) and all(x.get('sound') and x['sound'].get('soundEngineActive') is True and x['sound'].get('privateClock',{}).get('ready') is True and x['sound'].get('audio',{}).get('actualStreamType')==PCM_STREAM and x['sound']['audio'].get('sourceState')==4114 and x['sound']['audio'].get('gain',0)>0 and 0<=now_ms-x['sound']['audio'].get('atUtcMillis',0)<=2000 for x in rows)

def lyric_expected(origin):return origin['platform']!='none' and origin['identifier']!='BV0000000000'

def lyrics_ready(rows,origin):
 if not rows or any(not x.get('sound') for x in rows):return False
 if not lyric_expected(origin):return all(x['sound'].get('hasLyrics') is False for x in rows)
 translated=origin['platform']!='qq' and not(origin['platform']=='netease' and origin['identifier']=='12345')
 return all(x['sound'].get('hasLyrics') is True and 'Owned' in x['sound'].get('line','') and (('Owned translation' in x['sound'].get('translation','')) if translated else 'translation' not in x['sound']) for x in rows)

class ShortCandidate10:
 def __init__(self,d,dual,product):self.d=d;self.dual=dual;self.product=product;self.components={};self.failures={};self.last_progress=0
 def rows(self,reference,roles=('host','guest')):
  result=[];now=int(time.time()*1000)
  for role in roles:
   value=read(self.d.base/('entry042-playback-observed-'+role+'.json')) or {}
   if value.get('runId')!=self.d.run or not 0<=now-value.get('atUtcMillis',0)<=2000:return None
   if value.get('playerAlive') is not True:
    if value.get('playerPresent') is True:raise AssertionError('owned listener not alive: '+role)
    return None
   matching=[s for s in value.get('sounds',[]) if not s.get('stopped') and s.get('reference')==reference]
   if len(matching)!=1:return None
   result.append({'role':role,'observation':value,'sound':matching[0]})
  return result
 def component(self,label,kind,**facts):self.components.setdefault(label,{})[kind]=facts;self.progress(True)
 def progress(self,force=False):
  if not force and time.monotonic()-self.last_progress<2:return
  self.last_progress=time.monotonic();self.write('entry042-short-stage-progress.json',{'runId':self.d.run,'productSha256':self.product,'atUtcMillis':int(time.time()*1000),'components':self.components,'realIdentity':False,'ossRequests':0})
 def write(self,leaf,value):
  path=self.d.base/leaf;pending=path.with_suffix('.pending');pending.write_text(json.dumps(value,indent=2),encoding='utf-8');os.replace(pending,path)
 def capture(self,label,error,reference=None,before_playback_cleanup=True):
  # Called inside each playback exception, before its finally returns the original CD.
  roles={}
  for role in ['host','guest']:
   latest=read(self.d.base/('entry042-playback-observed-'+role+'.json')) or {};history=read(self.d.base/('entry042-playback-history-'+role+'.json')) or {}
   roles[role]={'latest':latest if latest.get('runId')==self.d.run else {},'history':history if history.get('runId')==self.d.run else {}}
  leaf='entry042-short-failure-'+''.join(c if c.isalnum() or c=='-' else '-' for c in label)+'.json'
  captured={'runId':self.d.run,'productSha256':self.product,'atUtcMillis':int(time.time()*1000),'capturedBeforePlaybackCleanup':before_playback_cleanup,'capturedBeforeNormalStop':True,'reference':reference,'reason':str(error),'errorType':type(error).__name__,'components':self.components.get(label,{}),'clients':roles,'realIdentity':False,'ossRequests':0}
  self.write(leaf,captured);self.failures[label]={'evidence':str(self.d.base/leaf),'capturedBeforePlaybackCleanup':before_playback_cleanup,'capturedBeforeNormalStop':True,'reason':str(error),'components':self.components.get(label,{})}
  self.dual.record('FAILED-candidate10-before-cleanup',self.dual.pair(reference,False) or [],scenario=label,reason=str(error),evidence=leaf,components=self.components.get(label,{}))
 def wait_audio(self,label,reference,roles=('host','guest')):
  self.component(label,'audio',passed=None,budgetSeconds=AUDIO_BUDGET_SECONDS,requires='actual PrivatePcmStream and fresh audible OpenAL; independent of lyrics')
  try:
   def audible():
    self.progress();rows=self.rows(reference,roles);return rows if audio_ready(rows) else None
   rows=self.d.wait(audible,'candidate10 '+label+' actual PCM/OpenAL, independent of lyrics',AUDIO_BUDGET_SECONDS)
   self.component(label,'audio',passed=True,pcmOpenalPassed=True,budgetSeconds=AUDIO_BUDGET_SECONDS,atUtcMillis=int(time.time()*1000),positions={x['role']:x['sound']['audio']['elapsedSeconds'] for x in rows})
   self.dual.record('candidate10-'+label+'-actual-audio',rows,lyricRequiredForAudio=False)
   return rows
  except Exception as error:self.component(label,'audio',passed=False,budgetSeconds=AUDIO_BUDGET_SECONDS,reason=str(error));raise
 def wait_lyrics(self,label,reference,origin,not_before,roles=('host','guest')):
  self.component(label,'lyrics',passed=None,budgetSeconds=LYRIC_BUDGET_SECONDS,clock='actual PrivateLyrics future.startedAtUtcMillis',expected=lyric_expected(origin))
  media=hashlib.sha256(reference.encode()).hexdigest()[:16]
  try:
   def applied():
    futures={};now=int(time.time()*1000)
    # Account/future outcomes remain inspectable if a natural sound end removed the channel.
    # Applied lyric fields still require the real current sound; no audio state is a lyric gate.
    for role in roles:
     observation=read(self.d.base/('entry042-playback-observed-'+role+'.json')) or {}
     if observation.get('runId')!=self.d.run or not 0<=now-observation.get('atUtcMillis',0)<=2000:return None
     eligible=[f for f in observation.get('playbackDiagnostics',{}).get('lyrics',[]) if f.get('media')==media and f.get('startedAtUtcMillis',0)>=not_before]
     if not eligible:return None
     future=max(eligible,key=lambda f:f['startedAtUtcMillis']);elapsed=now-future['startedAtUtcMillis'];futures[role]={'id':future['id'],'status':future['status'],'startedAtUtcMillis':future['startedAtUtcMillis'],'completedAtUtcMillis':future.get('completedAtUtcMillis'),'elapsedMillis':elapsed,'remainingMillis':max(0,LYRIC_BUDGET_SECONDS*1000-elapsed)}
     self.components[label]['lyrics']['futures']=futures
     if future['status']=='pending' and elapsed>=LYRIC_BUDGET_SECONDS*1000:raise AssertionError('actual lyric future budget exhausted: '+role)
     if future['status'] in ['cancelled','exceptional'] or (lyric_expected(origin) and future['status']=='completed-empty'):raise AssertionError('actual lyric future ended without required state: '+role+' '+future['status'])
    rows=self.rows(reference,roles);self.components[label]['lyrics']['appliedFieldsReady']=lyrics_ready(rows,origin);self.progress()
    completed=all(f['status']==('completed-lyrics' if lyric_expected(origin) else 'completed-empty') for f in futures.values())
    return rows if completed and lyrics_ready(rows,origin) else None
   rows=self.d.wait(applied,'candidate10 '+label+' lyrics on actual per-future 60s budget',LYRIC_BUDGET_SECONDS)
   self.components[label]['lyrics']['passed']=True;self.progress(True);self.dual.record('candidate10-'+label+'-actual-lyrics',rows,origin=origin,components=self.components[label])
   return rows
  except Exception as error:self.components[label]['lyrics'].update(passed=False,reason=str(error));self.progress(True);raise
 def alignment(self,rows,origin):
  for row in rows:
   sound=row['sound'];audio=sound['audio'];assert abs(sound['soundTick']/20-audio['elapsedSeconds'])<=2,'actual audio/lyric clock drift'
   if lyric_expected(origin):
    assert sound.get('lyricTick')==max(k for k in [0,100,300,600,900] if k<=sound['soundTick']),'original lyric rolling differs from PCM clock'
    if origin['platform']=='bilibili':assert origin['page']==2 and sound.get('line','').startswith('Owned P2 '),'actual second-P subtitle required'
    translated=origin['platform']!='qq' and not(origin['platform']=='netease' and origin['identifier']=='12345')
    if translated:assert sound.get('translationTick')==sound['lyricTick'],'actual translated lyric clock drift'
  if len(rows)==2:
   assert abs(rows[0]['sound']['audio']['elapsedSeconds']-rows[1]['sound']['audio']['elapsedSeconds'])<=2,'actual two-client audio position drift'
   if lyric_expected(origin):assert rows[0]['sound']['line']==rows[1]['sound']['line'],'actual two-client lyric drift'
 def return_cd(self,case,label):
  self.d.send('playback-return');self.d.wait(lambda:self.d.once(case['caseId'],'inventory',True),label+' same original CD returned once');self.d.wait(lambda:all(x['sound'] is None for x in (self.dual.pair(case['reference'],False) or [{'sound':1}])),label+' both original sounds stopped',15)
 def play(self,case,label,seconds=15):
  reference=case['reference'];origin=self.dual.origin(reference);started=int(time.time()*1000)
  try:
   self.dual.start_ready(case);self.wait_audio(label,reference);self.wait_lyrics(label,reference,origin,started);wall=time.monotonic()
   for target in sorted(set([0,5,15,seconds])):
    self.d.wait(lambda:time.monotonic()-wall>=target,'candidate10 original sampling wall time '+str(target),target+10)
    rows=self.d.wait(lambda:self.rows(reference),'fresh candidate10 pair',10);assert audio_ready(rows),'actual OpenAL no longer playing';assert lyrics_ready(rows,origin),'actual lyric/no-lyric state changed';self.alignment(rows,origin);self.dual.record('candidate10-'+label+'-'+str(target)+'s',rows,origin=origin)
  except Exception as error:self.capture(label,error,reference);raise
  finally:self.return_cd(case,label)
 def host_place_ready(self,case):
  placed=int(time.time()*1000);self.d.send('playback-place',marker=case['marker'],site=0);self.d.wait(lambda:self.d.case(case['caseId']).get('playerBlock')==1,'same original CD placed while guest absent');prior=None
  def settled():
   nonlocal prior
   value=read(self.d.base/'entry042-playback-observed-host.json') or {};now=int(time.time()*1000);stamp=value.get('atUtcMillis',0)
   if value.get('runId')!=self.d.run or stamp<=placed or not 0<=now-stamp<=2000 or value.get('playerAlive') is not True or value.get('dimension')!='minecraft:overworld' or value.get('screen')!='NONE' or value.get('overlay')!='NONE':prior=None;return None
   blocks=[b for b in value.get('blocks',[]) if b.get('site')==0 and b.get('present') and b.get('chunkLoaded') and b.get('marker')==case['marker'] and b.get('count')==1 and b.get('reference')==case['reference'] and b.get('isPlay') is False]
   if len(blocks)!=1:prior=None;return None
   env=value.get('audioEnvironment',{})
   if env.get('engineLoaded') is not True or env.get('listenerGain',0)<=0 or env.get('masterVolume',0)<=0 or env.get('recordsVolume',0)<=0:return None
   if prior is not None and stamp>prior:return value
   prior=stamp;return None
  self.d.send('audio-enable-owned');self.d.wait(settled,'host actual marked CD and audible engine ready with guest absent',AUDIO_BUDGET_SECONDS);self.d.send('playback-start')
 def late_join(self,case,label):
  reference=case['reference'];origin=self.dual.origin(reference);started=int(time.time()*1000)
  try:
   self.dual.send('disconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is False,'late listener disconnected before start',90)
   self.host_place_ready(case);before=self.wait_audio(label+'-host',reference,('host',));identity=before[0]['sound']['soundIdentity'];wall=time.monotonic();self.d.wait(lambda:time.monotonic()-wall>=5,'host plays five seconds before late listener',10);before=self.d.wait(lambda:self.rows(reference,('host',)),'fresh continuous host before actual late join',10);assert before[0]['sound']['soundIdentity']==identity;self.dual.record('candidate10-before-actual-late-join',before)
   self.dual.send('reconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is True,'actual late listener joins',180);self.dual.send('join-listener')
   rows=self.wait_audio(label,reference)
   continuous=rows[0]['sound']['soundIdentity']==identity;self.component(label,'hostContinuity',passed=continuous,identityBefore=identity,identityAfter=rows[0]['sound']['soundIdentity'],fixtureDurationSeconds=60,reason='' if continuous else 'host restarted or reached natural 60s fixture loop; audio presence recorded independently')
   self.wait_lyrics(label,reference,origin,started);rows=self.d.wait(lambda:self.rows(reference),'fresh late-join audio and lyrics',10);self.alignment(rows,origin);assert continuous,'continuous host not verified; inspect fixture-duration boundary separately from PCM/OpenAL';self.dual.record('candidate10-actual-late-join-pair',rows,components=self.components[label])
   events=(read(self.d.base/'entry042-events-server.json') or {}).get('events',[])
   for name in ['10000','10001']:assert any(x.get('kind')=='actual-private-delivery-result' and x.get('player')==name and x.get('result')=='SENT' and x.get('protocol')=='PLAY' and x.get('connected') and x.get('accepting') and x.get('stateChannel') and x.get('reportChannel') for x in events),'actual supported-channel delivery missing: '+name
  except Exception as error:self.capture(label,error,reference);raise
  finally:
   if (read(self.d.base/'status-guest.json') or {}).get('connected') is False:self.dual.send('reconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is True,'normal listener reconnect for next independent case',180)
   self.return_cd(case,label)
 def rejected_recovery(self,case,label):
  reference=case['reference'];origin=self.dual.origin(reference);started=int(time.time()*1000)
  try:
   self.dual.start_ready(case,mute_guest=True)
   def rejected():
    rows=self.dual.pair(reference,False)
    return rows if rows and audio_ready(rows[:1]) and rows[1]['sound'] is None and rows[1]['observation'].get('audioEnvironment',{}).get('listenerGain')==0 else None
   before=self.d.wait(rejected,'actual muted-listener prestream rejection; lyrics independent',15);identity=before[0]['sound']['soundIdentity'];self.component(label,'prestreamRejection',passed=True,hostIdentity=identity,guestListenerGain=0,guestOriginalSoundAbsent=True);self.dual.record('candidate10-actual-prestream-rejected',before)
   wall=time.monotonic();self.d.wait(lambda:time.monotonic()-wall>=2,'actual rejected playback interval',5);self.dual.send('audio-enable-owned');rows=self.wait_audio(label,reference)
   assert rows[0]['sound']['soundIdentity']==identity,'ready host restarted during rejected guest retry';assert abs(rows[0]['sound']['audio']['elapsedSeconds']-rows[1]['sound']['audio']['elapsedSeconds'])<=2,'rejected guest failed latest authoritative position';self.wait_lyrics(label,reference,origin,started);self.alignment(self.d.wait(lambda:self.rows(reference),'fresh recovered pair',10),origin);self.dual.record('candidate10-actual-prestream-recovered',rows,before=before,components=self.components[label])
  except Exception as error:self.capture(label,error,reference);raise
  finally:self.dual.send('audio-enable-owned');self.return_cd(case,label)


 def pause_rejoin(self,case,label,rejoin):
  reference=case['reference'];origin=self.dual.origin(reference);started=int(time.time()*1000)
  try:
   self.dual.start_ready(case);initial=self.wait_audio(label,reference);self.wait_lyrics(label,reference,origin,started);initial=self.d.wait(lambda:self.rows(reference),'fresh initial audio/lyrics for '+label,10);self.alignment(initial,origin)
   if rejoin:
    identity=initial[0]['sound']['soundIdentity'];self.dual.record('candidate10-rejoin-before',initial);self.dual.send('disconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is False,'actual guest disconnect',90);self.dual.send('reconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is True,'actual guest reconnect',180);self.dual.send('join-listener');rows=self.wait_audio(label,reference)
    continuous=rows[0]['sound']['soundIdentity']==identity;self.component(label,'hostContinuity',passed=continuous,identityBefore=identity,identityAfter=rows[0]['sound']['soundIdentity'],fixtureDurationSeconds=60)
    self.wait_lyrics(label,reference,origin,started);rows=self.d.wait(lambda:self.rows(reference),'fresh actual rejoined pair',10);self.alignment(rows,origin);assert continuous,'continuous host not verified; inspect natural fixture-loop boundary';self.dual.record('candidate10-rejoin-actual-pair',rows,components=self.components[label])
   else:
    self.dual.record('candidate10-pause-before',initial);self.d.send('audio-pause');self.dual.send('audio-pause');paused=int(time.time()*1000)
    def frozen():
     rows=self.rows(reference)
     return rows if rows and all(x['observation'].get('soundManagerPaused') and x['sound']['audio'].get('sourceState')==4115 and x['sound']['audio'].get('atUtcMillis',0)>paused for x in rows) else None
    before=self.d.wait(frozen,'both actual OpenAL paused',10);wall=time.monotonic();self.d.wait(lambda:time.monotonic()-wall>=5,'actual five-second audio/lyric pause',10);after=self.d.wait(frozen,'fresh actual paused pair',10);self.dual.record('candidate10-pause-five-seconds',after,before=before)
    for a,b in zip(before,after):assert abs(a['sound']['audio']['elapsedSeconds']-b['sound']['audio']['elapsedSeconds'])<=.25 and abs(a['sound']['soundTick']-b['sound']['soundTick'])<=1,'paused PCM/audio/lyric clock advanced'
    self.component(label,'pause',passed=True,actualPauseSeconds=5);self.d.send('audio-resume');self.dual.send('audio-resume');resumed=self.wait_audio(label+'-resume',reference);self.alignment(resumed,origin);self.dual.record('candidate10-actual-resume',resumed)
  except Exception as error:self.capture(label,error,reference);raise
  finally:
   self.d.send('audio-resume');self.dual.send('audio-resume')
   if (read(self.d.base/'status-guest.json') or {}).get('connected') is False:self.dual.send('reconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is True,'normal listener reconnect after pause/rejoin',180)
   self.return_cd(case,label)

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--prior-root',type=Path,required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args();d=Driver(a.instance_root,a.frozen_inputs);dual=Dual(d);results=[];failure_details={};checks=ShortCandidate10(d,dual,read(a.frozen_inputs)['candidateSha256'])
 if not a.execute:print(json.dumps({'verifiedOnly':True,'mcStarted':False,'audioBudgetSeconds':AUDIO_BUDGET_SECONDS,'lyricBudgetSeconds':LYRIC_BUDGET_SECONDS,'audioIndependentOfLyrics':True}));return
 def save():checks.write('entry042-short-results.json',{'runId':d.run,'productSha256':checks.product,'cases':results,'components':checks.components,'failureDetails':{**failure_details,**checks.failures},'ossRequests':0,'actualPlatformCalls':0,'realIdentity':False})
 def attempt(label,callback):
  try:callback();row={'case':label,'passed':True}
  except Exception as error:
   row={'case':label,'passed':False,'reason':str(error)};dual.record('FAILED-short-candidate10',dual.pair(active=False) or [],scenario=label,reason=str(error),traceback=traceback.format_exc(),preCleanupEvidence=checks.failures.get(label))
  row['components']=checks.components.get(label,{});results.append(row);save();print(json.dumps(row),flush=True)
 try:
  ready(d,True);d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') and (read(d.base/'entry042-client-status-guest.json') or {}).get('fixtureCookiesInstalled'),'guest ready',600)
  def restored():
   seed_reload(d,a.prior_root,a.frozen_inputs);counts=d.counts();assert len(counts['cases'])==6 and all(x['total']==1 and x['inventory']==1 for x in counts['cases']);dual.record('six-original-saved-CDs-restored',[],counts=counts)
  attempt('six-original-CDs-full-reload',restored)
  if not results[-1]['passed']:raise RuntimeError('saved_original_inventory_not_verified')
  d.send('audio-enable-owned');dual.send('audio-enable-owned');d.send('pause-focus-false');dual.send('pause-focus-false');d.send('lyrics-on');dual.send('lyrics-on');d.send('audio-resume');dual.send('audio-resume')
  candidates=[x for x in dual.inventory() if dual.origin(x['reference'])['platform']=='netease' and not x.get('readOnly')];assert candidates
  case=dual.rewrite(candidates[0],'netease','https://music.163.com/song?id=1868207326','candidate10-netease-original-translation')
  attempt('netease-first-original-and-translation',lambda:checks.play(case,'netease-first-original-and-translation',seconds=30))
  attempt('actual-pause-resume',lambda:checks.pause_rejoin(case,'actual-pause-resume',False))
  attempt('actual-guest-rejoin-position',lambda:checks.pause_rejoin(case,'actual-guest-rejoin-position',True))
  attempt('actual-late-join-two-client-audio',lambda:checks.late_join(case,'actual-late-join-two-client-audio'))
  # A lyric failure does not suppress independent reject/QQ/P2/direct regressions.
  assert all(x['total']==1 and x['inventory']==1 for x in d.counts()['cases']),'original inventory required before independent regressions'
  attempt('actual-prestream-rejection-recovery',lambda:checks.rejected_recovery(case,'actual-prestream-rejection-recovery'))
  groups={}
  for item in dual.inventory():groups.setdefault(dual.origin(item['reference'])['platform'],[]).append(item)
  for platform in ['qq','bilibili','none']:
   def regression(p=platform):
    assert groups.get(p),'saved original '+p+' CD required';selected=groups[p][0]
    if p=='bilibili':selected=dual.rewrite(selected,'bilibili','https://www.bilibili.com/video/BV1Q5411x7aE?p=2','candidate10-bili-P2')
    checks.play(selected,'short-regression-'+p,seconds=15)
   attempt('short-regression-'+platform,regression)
 except Exception as error:
  failure_details['main']={'type':type(error).__name__,'reason':str(error),'traceback':traceback.format_exc()};checks.capture('main',error,before_playback_cleanup=False);save();raise
 finally:
  try:
   if not (d.root/'run-result.json').exists():
    if any(x.get('playerBlock') for x in d.counts().get('cases',[])):d.send('playback-return');d.wait(lambda:all(x['total']==1 and x['inventory']==1 for x in d.counts()['cases']),'six originals returned before normal stop')
    if not (d.root/'run-result.json').exists():
     d.send('close-menu');d.send('audit-discs');import sys;sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'));import local_mc_debug as runner;runner.stop_files(d.root,d.owner);d.wait(lambda:any(x.get('event')=='server-stopping-after-product-return' and len(x.get('cases',[]))==6 and all(v['total']==1 and v['inventory']==1 for v in x['cases']) for x in (read(d.base/'entry042-disc-lifecycle.json') or {}).get('history',[])),'normal stop preserves six originals',30);d.record('inventory','none','candidate10-short-normal-stop',d.current_case())
  except Exception as error:
   failure_details['cleanup']={'type':type(error).__name__,'reason':str(error),'traceback':traceback.format_exc()};save()
   if not (d.root/'run-result.json').exists():
    import sys
    sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'));import local_mc_debug as runner;assert read(d.root/'local-mc-owner.json')['runId']==d.run;runner.stop_files(d.root,d.owner);failure_details['normalStopFallbackRequested']=True;save()
 print(json.dumps({'shortChecksFinished':True,'failedCases':sum(not x['passed'] for x in results),'longMenuCasesRepeated':False}),flush=True)
if __name__=='__main__':main()
