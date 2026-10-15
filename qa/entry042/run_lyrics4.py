"""Drive one already owned .4 run; never launch MC, no commands without --execute."""
from pathlib import Path
import argparse,json,time,os,urllib.request
from qa_io import atomic_json
from run_matrix1438 import Driver,read,PRODUCT
from run_supplement3 import ready,seed_reload

def fresh_inventory_receipt(value,run,not_before,now):
    if not isinstance(value,dict) or value.get('runId')!=run:return None
    stamp=value.get('atUtcMillis',0)
    if stamp<not_before or not 0<=now-stamp<=2500 or not isinstance(value.get('cases'),list):return None
    if not all(x.get('total')==1 for x in value['cases']):return None
    return value

class Dual:
    def __init__(self,d):self.d=d;self.id=int((read(d.base/'entry042-command-guest.json') or {}).get('id',-1));prior=read(d.base/'entry042-lyrics-native-results.json') or {};self.points=list(prior.get('points',[])) if prior.get('runId')==d.run and prior.get('productSha256')==PRODUCT else []
    def send(self,cmd):
        self.id+=1;path=self.d.base/'entry042-command-guest.json';atomic_json(path,{'runId':self.d.run,'id':self.id,'cmd':cmd})
        def ack():
            for row in read(self.d.base/'entry042-client-results-guest.json') or []:
                if row.get('id')==self.id:assert row.get('ok'),row;return row
        self.d.wait(ack,'guest original '+cmd,90)
    def pair(self,reference=None,active=True):
        rows=[]
        for role in ['host','guest']:
            value=read(self.d.base/('entry042-playback-observed-'+role+'.json')) or {}
            if value.get('runId')!=self.d.run or int(time.time()*1000)-value.get('atUtcMillis',0)>2000:return None
            sounds=[x for x in value.get('sounds',[]) if not x.get('stopped') and (reference is None or x.get('reference')==reference)]
            if active and value.get('playerAlive') is False:raise AssertionError('owned fixture listener died during playback: '+role)
            if active and len(sounds)!=1:return None
            rows.append({'role':role,'observation':value,'sound':sounds[0] if len(sounds)==1 else None})
        return rows
    def record(self,label,rows,**facts):
        self.points.append({'label':label,'atUtcMillis':int(time.time()*1000),'clients':rows,**facts});self.save()
    def save(self):
        path=self.d.base/'entry042-lyrics-native-results.json';temp=path.with_suffix('.pending');temp.write_text(json.dumps({'runId':self.d.run,'productSha256':PRODUCT,'points':self.points,'fixtureTextAndAudio':True,'actualOriginalSoundAndOpenAL':True,'realIdentity':False,'actualPlatformCalls':0,'ossRequests':0},indent=2));os.replace(temp,path)
    def origin(self,reference):
        backend=read(self.d.base/'entry042-backend-status.json');assert backend['runId']==self.d.run and backend['productSha256']==PRODUCT and backend['loopbackOnly'] and backend['ready']
        request=urllib.request.Request(f'http://127.0.0.1:{backend["port"]}/api/launcher/music/lyrics',data=json.dumps({'gameSession':'00000000-0000-4000-8000-000000000042','reference':reference}).encode(),headers={'Authorization':'Bearer entry042-owned-account-fixture','X-Entry042-Role':'host','Content-Type':'application/json'})
        with urllib.request.urlopen(request,timeout=8) as response:return json.loads(response.read())['lyricOrigin']
    def inventory(self):
        requested=int(time.time()*1000);self.d.send('audit-discs')
        value=self.d.wait(lambda:fresh_inventory_receipt(self.d.counts(),self.d.run,requested,int(time.time()*1000)),
                          'fresh same-run receipt with all original CD counts')
        return [x for x in value['cases'] if x.get('reference') and x.get('inventory')==1]
    def player_ready(self,case,site,not_before,now=None):
        """Read actual client block state; command ACK and screen closure are insufficient."""
        now=int(time.time()*1000) if now is None else now;rows=[]
        for role in ['host','guest']:
            value=read(self.d.base/('entry042-playback-observed-'+role+'.json')) or {};stamp=value.get('atUtcMillis',0)
            if value.get('runId')!=self.d.run or stamp<=not_before or not 0<=now-stamp<=2000:return None
            if value.get('dimension')!='minecraft:overworld' or value.get('screen')!='NONE' or value.get('overlay')!='NONE' or value.get('playerAlive') is not True:return None
            blocks=[b for b in value.get('blocks',[]) if b.get('site')==site and b.get('present') is True and b.get('chunkLoaded') is True and b.get('marker')==case['marker'] and b.get('count')==1 and b.get('reference')==case['reference'] and b.get('isPlay') is False]
            if len(blocks)!=1 or not isinstance(blocks[0].get('position'),list) or len(blocks[0]['position'])!=3:return None
            rows.append({'role':role,'observation':value,'block':blocks[0],'sound':None})
        return rows if rows[0]['block']['position']==rows[1]['block']['position'] else None
    def start_ready(self,case,site=0,mute_guest=False):
        placed=int(time.time()*1000);self.d.send('playback-place',marker=case['marker'],site=site);self.d.wait(lambda:self.d.case(case['caseId']).get('playerBlock')==1,'original CD in original player');self.send('join-listener')
        previous=None
        def settled():
            nonlocal previous
            rows=self.player_ready(case,site,placed)
            if rows is None:previous=None;return None
            stamps=tuple(x['observation']['atUtcMillis'] for x in rows)
            if previous is not None and all(a>b for a,b in zip(stamps,previous)):return rows
            if previous is None:previous=stamps
            return None
        rows=self.d.wait(settled,'both clients loaded same original marked CD at fixture site',90)
        self.record('original-player-ready-before-start',rows,caseId=case['caseId'],site=site)
        # Volume commands may precede a reload; inspect the actual runtime listener after it.
        self.d.send('audio-enable-owned');self.send('audio-enable-owned')
        def audible_engine():
            rows=self.player_ready(case,site,placed)
            return rows if rows and all(x['observation'].get('audioEnvironment',{}).get('engineLoaded') is True and x['observation']['audioEnvironment'].get('listenerGain',0)>0 and x['observation']['audioEnvironment'].get('masterVolume',0)>0 and x['observation']['audioEnvironment'].get('recordsVolume',0)>0 for x in rows) else None
        engines=self.d.wait(audible_engine,'both actual engines loaded with owned audible volume',15)
        self.record('original-audio-engine-ready-before-start',engines,caseId=case['caseId'],site=site)
        if mute_guest:self.send('audio-mute-owned')
        self.d.send('playback-start')
    def play(self,case,label,seconds=30,site=0,force_no_lyrics=False):
        reference=case['reference'];origin=self.origin(reference);failure=None
        try:
            self.start_ready(case,site)
            expected=not force_no_lyrics and origin['platform']!='none' and origin['identifier']!='BV0000000000'
            def loaded():
                rows=self.pair(reference)
                return rows if rows and all(r['sound'].get('hasLyrics')==expected and r['sound'].get('privateClock',{}).get('ready') is True and r['sound'].get('audio',{}).get('actualStreamType')=='net.muxigame.musiccompat.burn.PrivatePcmStream' and r['sound'].get('audio',{}).get('sourceState')==4114 for r in rows) else None
            rows=self.d.wait(loaded,'both original sounds, actual audio and expected lyric readiness',30);start=time.monotonic()
            for target in [0,5,15,seconds]:
                self.d.wait(lambda:time.monotonic()-start>=target,'original playback wall time '+str(target),target+10)
                rows=self.d.wait(lambda:self.pair(reference),'fresh two-client original samples',10)
                for row in rows:
                    sound=row['sound'];audio=sound.get('audio',{});assert sound['soundEngineActive'] and audio.get('sourceState')==4114 and audio.get('gain',0)>0 and int(time.time()*1000)-audio.get('atUtcMillis',0)<2000,'actual OpenAL playing required'
                    if not expected:assert not sound['hasLyrics'],'no invented or stale lyric'
                    else:
                        assert sound['hasLyrics'] and abs(sound['soundTick']/20-audio['elapsedSeconds'])<=2,'audio/lyric sound-clock drift';assert 'Owned' in sound['line'],'actual self-authored fixture line'
                        assert sound['lyricTick']==max(k for k in [0,100,300,600,900] if k<=sound['soundTick']),'lyric rolling did not follow actual clock'
                        translated=origin['platform']!='qq' and not (origin['platform']=='netease' and origin['identifier']=='12345')
                        if translated:assert sound.get('translationTick')==sound['lyricTick'] and 'Owned translation' in sound.get('translation',''),'original translated lyric timeline'
                        else:assert 'translation' not in sound,'no invented translation' 
                assert abs(rows[0]['sound']['audio']['elapsedSeconds']-rows[1]['sound']['audio']['elapsedSeconds'])<=2,'multi-client audio drift'
                if expected:assert rows[0]['sound'].get('line')==rows[1]['sound'].get('line'),'multi-client lyric drift'
                self.record(label+'-'+str(target)+'s',rows,caseId=case['caseId'],origin=origin)
        except Exception as error:
            failure=error;self.record('FAILED-playback',self.pair(reference,False) or [],scenario=label,caseId=case['caseId'],origin=origin,errorType=type(error).__name__,reason=str(error));raise
        finally:
            try:
                self.d.send('playback-return');self.d.wait(lambda:self.d.once(case['caseId'],'inventory',True),'same original disc returned from original player');self.d.wait(lambda:all(x['sound'] is None for x in (self.pair(reference,False) or [{'sound':1}])),'stopped/removed CD has no lingering original sound',15)
            except Exception as error:
                self.record('FAILED-playback-cleanup',self.pair(reference,False) or [],scenario=label,errorType=type(error).__name__,reason=str(error))
                if failure is None:raise
    def wait_world_entry(self):
        def entered():
            return all((read(self.d.base/('status-'+role+'.json')) or {}).get('dimension')=='minecraft:overworld' and (read(self.d.base/('status-'+role+'.json')) or {}).get('screen')=='NONE' and (read(self.d.base/('status-'+role+'.json')) or {}).get('health',0)>0 for role in ['host','guest'])
        self.d.wait(entered,'both live clients completed original world-entry screen',90)
        start=time.monotonic();self.d.wait(lambda:time.monotonic()-start>=2 and entered(),'original chunk-entry stabilization before start',10)
    def rewrite(self,case,platform,value,label):
        self.d.send('open-existing',marker=case['marker']);self.d.wait(lambda:self.d.status().get('screenClass')=='ComputerMenuScreen','same original writable CD menu')
        self.d.send('configure',platform=platform,value=value);self.d.send('readonly-false');self.d.send('permission');self.d.started=int(time.time()*1000);self.d.send('craft');self.d.wait(lambda:self.d.progress(case['caseId'],'DONE'),'actual rewrite through product and isolated real backend',120);self.d.close(case['caseId'],True);self.record('actual-original-cd-rewrite-'+label,[],caseId=case['caseId']);return self.d.case(case['caseId'])
    def run(self):
        self.d.send('audio-enable-owned');self.send('audio-enable-owned');self.d.send('pause-focus-false');self.send('pause-focus-false');self.d.send('lyrics-on');self.send('lyrics-on');self.d.send('audio-resume');self.send('audio-resume');self.send('join-listener')
        cases=self.inventory();groups={}
        for case in cases:groups.setdefault(self.origin(case['reference'])['platform'],[]).append(case)
        for platform in ['netease','qq','bilibili','none']:
            assert groups.get(platform),'actually burned '+platform+' CD required'
            self.play(groups[platform][0],platform,site=0)
        # Choose actual writable original stacks; do not create extra CDs or bypass readOnly.
        for platform,value,label,source in [('qq_music','003ENTRY042abc','qq-songmid','qq'),('bilibili','https://www.bilibili.com/video/BV0000000000?p=2','bilibili-without-subtitles','bilibili'),('netease','https://music.163.com/song?id=12345','netease-existing-id','netease'),('qq_music','https://entry042.qqmusic.qq.com/owned.wav','direct-without-lyrics','none')]:
            candidates=[x for x in groups[source] if not x.get('readOnly')];assert candidates,'writable original CD required';case=self.rewrite(candidates[-1],platform,value,label);self.play(case,label,site=1)
        case=next(x for x in self.inventory() if self.origin(x['reference'])['platform']=='netease');self.d.send('lyrics-off');self.send('lyrics-off');self.play(case,'original-lyric-switch-off',seconds=15,force_no_lyrics=True);self.d.send('audio-enable-owned');self.send('audio-enable-owned');self.d.send('pause-focus-false');self.send('pause-focus-false');self.d.send('lyrics-on');self.send('lyrics-on');reference=case['reference'];self.d.send('playback-place',marker=case['marker'],site=0);self.send('join-listener');self.d.send('playback-start')
        try:
            self.d.wait(lambda:self.pair(reference),'real pause scenario sounds',30);self.d.send('audio-pause');self.send('audio-pause');paused=int(time.time()*1000);before=self.d.wait(lambda:self.pair(reference) if self.pair(reference) and all(r['sound'].get('audio',{}).get('atUtcMillis',0)>paused and r['sound']['audio'].get('sourceState')==4115 for r in self.pair(reference)) else None,'paused samples',10);start=time.monotonic();self.d.wait(lambda:time.monotonic()-start>=5,'original pause five seconds',10);after=self.d.wait(lambda:self.pair(reference),'fresh paused samples',10)
            for a,b in zip(before,after):
                assert b['observation']['soundManagerPaused'] and b['sound']['audio']['sourceState']==4115,'original audio pause state required'
                assert abs(a['sound']['audio']['elapsedSeconds']-b['sound']['audio']['elapsedSeconds'])<=.25 and abs(a['sound']['soundTick']-b['sound']['soundTick'])<=1,'paused lyric/audio must not advance'
            self.record('original-pause-five-seconds',after);self.d.send('audio-resume');self.send('audio-resume');self.d.wait(lambda:all(x['sound']['audio']['sourceState']==4114 for x in (self.pair(reference) or [{'sound':{'audio':{'sourceState':0}}}])),'original audio resumed',10)
            self.send('disconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is False,'actual second-client disconnect',90);self.send('reconnect');self.d.wait(lambda:(read(self.d.base/'status-guest.json') or {}).get('connected') is True,'actual second-client rejoin',180)
            rows=self.d.wait(lambda:self.pair(reference),'late/rejoined client resumes same original media',30);assert abs(rows[0]['sound']['audio']['elapsedSeconds']-rows[1]['sound']['audio']['elapsedSeconds'])<=2,'rejoin audio timeline drift';self.record('actual-rejoin-same-CD',rows)
        finally:self.d.send('audio-resume');self.send('audio-resume');self.d.send('playback-return');self.d.wait(lambda:self.d.once(case['caseId'],'inventory',True),'final same CD return')
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--prior-root',type=Path);ap.add_argument('--part',choices=['playback','lifecycle-playback-stop','reload-playback-stop'],default='playback');ap.add_argument('--execute',action='store_true');args=ap.parse_args();d=Driver(args.instance_root,args.frozen_inputs);assert d.owner['clientNames']=={'host':'10000','guest':'10001'};dual=Dual(d)
    if not args.execute:print(json.dumps({'verifiedDualCandidate4':True,'commandsSent':False,'mcStarted':False}));return
    try:
        ready(d,True);d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') and (read(d.base/'entry042-client-status-guest.json') or {}).get('fixtureCookiesInstalled'),'guest ready',600)
        if args.part=='reload-playback-stop':assert args.prior_root;seed_reload(d,args.prior_root,args.frozen_inputs)
        if args.part=='lifecycle-playback-stop':d.lifecycle()
        dual.run()
        if args.part=='reload-playback-stop':
            # The earlier lifecycle run tests active-task stop. Keep every restored original CD;
            # do not create a 37th fixture stack in this full, saved inventory.
            dual.send('finish');d.send('close-menu');d.send('audit-discs');d.send('stop-server-owned')
            def saved_inventory():
                history=(read(d.base/'entry042-disc-lifecycle.json') or {}).get('history',[])
                return next((row for row in history if row.get('event')=='server-stopping-after-product-return' and row.get('cases') and all(x.get('total')==1 and x.get('inventory')==1 for x in row['cases'])),None)
            d.wait(saved_inventory,'normal reload stop retains every original saved CD');d.record('inventory','none','normal-reload-stop',d.current_case())
        elif args.part.endswith('stop'):dual.send('finish');d.stop()
    except Exception as error:dual.record('FAILED',[],errorType=type(error).__name__,reason=str(error));raise
if __name__=='__main__':main()
