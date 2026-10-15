"""Native lifecycle cases in an already-owned frozen lab. Never launches or terminates MC."""
from pathlib import Path
import argparse,json,os,time
from qa_io import atomic_json
from run_matrix1438 import Driver,read
from run_lyrics4 import Dual

ROLES=('host','guest')
def cleanup_sample(sample):
    return (sample.get('armed') is True and sample.get('cancelled') is True
            and sample.get('soundStopped') is True and sample.get('engineActive') is False
            and sample.get('soundLyricsPresent') is False and sample.get('ownerLyricsPresent') is False
            and sample.get('channelReleased') is True and sample.get('invalidTick',-1)>=0
            and sample.get('clientTick',0)-sample['invalidTick']>=8
            and sample.get('actualOwnedAliveNotes')==0 and sample.get('taggedActualNotes',0)>0
            and sample.get('noteTrackingErrorType')=='')

def resolver_safe(before,after):
    return (after.get('gatePresent') is True and after.get('sourceDone') is True
            and after.get('sourceExceptional') is False and after.get('heldFutureDone') is True
            and after.get('released') is True and after.get('oldConsumerFuturePresent') is True
            and after.get('oldConsumerFutureDone') is True and after.get('oldConsumerFutureExceptional') is False
            and after.get('oldRemoved') is True
            and after.get('oldPlaying') is False and after.get('oldTime')==0
            and after.get('oldCallbacks')==0 and after.get('dispatches')==before.get('dispatches')
            and after.get('newCallbacks')==before.get('newCallbacks'))

class Lifecycle:
    def __init__(self,d,dual):
        self.d,self.dual=d,dual
        self.ids={role:int((read(d.base/('entry042-lifecycle-command-'+role+'.json')) or {}).get('id',-1)) for role in ROLES}
        self.results=[]
    def send(self,cmd,role='host'):
        self.ids[role]+=1;ident=self.ids[role];path=self.d.base/('entry042-lifecycle-command-'+role+'.json')
        atomic_json(path,{'runId':self.d.run,'id':ident,'cmd':cmd})
        def ack():
            result=read(self.d.base/('entry042-lifecycle-results-'+role+'.json')) or {}
            if result.get('runId')!=self.d.run:return None
            for row in result.get('results',[]):
                if row.get('id')==ident:assert row.get('ok'),row;return row
        self.d.wait(ack,'lifecycle '+role+' '+cmd+' client accepted',15)
        if cmd not in ('arm','probe'):
            def server_ack():
                result=read(self.d.base/'entry042-lifecycle-server.json') or {}
                if result.get('runId')!=self.d.run:return None
                return next((row for row in result.get('commands',[]) if row.get('id')==ident and row.get('action')==cmd and row.get('uid')==('10001' if role=='guest' else '10000')),None)
            return self.d.wait(server_ack,'lifecycle '+role+' '+cmd+' actual server command',15)
    def pair(self):
        rows=[];now=int(time.time()*1000)
        for role in ROLES:
            value=read(self.d.base/('entry042-lifecycle-observed-'+role+'.json')) or {};sample=value.get('sample',{})
            if value.get('runId')!=self.d.run or not 0<=now-sample.get('atUtcMillis',0)<=2000:return None
            rows.append({'role':role,'sample':sample})
        return rows
    def server(self):
        value=read(self.d.base/'entry042-lifecycle-server.json') or {}
        if value.get('runId')!=self.d.run or not 0<=int(time.time()*1000)-value.get('atUtcMillis',0)<=2000:return None
        return value
    def record(self,label,**facts):
        self.results.append({'case':label,'atUtcMillis':int(time.time()*1000),**facts})
        path=self.d.base/'entry042-lifecycle-native-results.json';temp=path.with_suffix('.pending');temp.write_text(json.dumps({'runId':self.d.run,'cases':self.results,'actualOriginalFutureAndNoteParticles':True,'fixtureAccountAndAudio':True,'realCloudCalls':0,'mcStartedByDriver':False},indent=2),encoding='utf-8');os.replace(temp,path)
    def live(self,case,kind,paused=False):
        away=False;captured=False;paused_termination=None
        try:
            self.dual.start_ready(case,0)
            def loaded():
                rows=self.dual.pair(case['reference'])
                return rows if rows and all(r['sound'].get('hasLyrics') is True and r['sound'].get('privateClock',{}).get('ready') is True and r['sound'].get('audio',{}).get('sourceState')==4114 and r['sound'].get('soundEngineActive') is True and r['sound'].get('audio',{}).get('gain',0)>0 and r['sound'].get('audio',{}).get('actualStreamType')=='net.muxigame.musiccompat.burn.PrivatePcmStream' and 0<=int(time.time()*1000)-r['sound'].get('audio',{}).get('atUtcMillis',0)<=2000 for r in rows) else None
            ready=self.d.wait(loaded,'lifecycle actual PCM/OpenAL/lyric readiness',95)
            self.send('capture');captured=True
            for role in ROLES:self.send('arm',role)
            def notes():
                rows=self.pair()
                return rows if rows and all(r['sample'].get('noteEmissions',0)>0 and r['sample'].get('taggedActualNotes',0)>0 and r['sample'].get('maxNoteLifetime')==6 for r in rows) else None
            baseline=self.d.wait(notes,'actual original NOTE emissions and native lifetime six',10)
            if paused:
                self.d.send('audio-pause');self.dual.send('audio-pause')
                self.d.wait(lambda:self.dual.pair(case['reference']) if self.dual.pair(case['reference']) and all(r['sound'].get('audio',{}).get('sourceState')==4115 for r in self.dual.pair(case['reference'])) else None,'actual paused channels before removal',10)
            if kind=='unload':
                for role in ROLES:self.send('leave',role)
                away=True
                self.d.wait(lambda:self.pair() if self.pair() and all(r['sample'].get('chunkLoaded') is False for r in self.pair()) else None,'actual network chunk unload for both clients',90)
            else:
                self.send('remove')
                if kind=='replace':self.send('replace')
            if paused:
                def stopped_while_paused():
                    rows=self.pair()
                    if not rows:return None
                    for row in rows:
                        sample=row['sample']
                        if not (sample.get('cancelled') is True and sample.get('soundStopped') is True and sample.get('soundLyricsPresent') is False and sample.get('ownerLyricsPresent') is False):return None
                        if not (sample.get('channelReleased') is True or (sample.get('lastSourceState')==4116 and 0<=int(time.time()*1000)-sample.get('lastSourceAtUtcMillis',0)<=2000)):return None
                    return rows
                paused_termination=self.d.wait(stopped_while_paused,'actual paused sources stopped before resume',15)
                # Engine map/handle disposal can wait for its next unpaused tick; audio stop was proven above.
                self.d.send('audio-resume');self.dual.send('audio-resume')
            def clean():
                rows=self.pair();return rows if rows and all(cleanup_sample(r['sample']) for r in rows) else None
            first=self.d.wait(clean,'old sound/lyrics/channel cancelled with owned NOTE decay',20)
            def stable():
                rows=clean()
                if not rows:return None
                for a,b in zip(first,rows):
                    if b['sample']['clientTick']-a['sample']['clientTick']<10:return None
                    assert b['sample']['soundIdentity']==a['sample']['soundIdentity'] and b['sample']['noteEmissions']==a['sample']['noteEmissions'],'old sound emitted NOTE after lifecycle termination'
                return rows
            after=self.d.wait(stable,'no further old NOTE emissions for ten additional ticks',10)
            if away:
                assert self.server()['facts'].get('oldPlaying') is True and self.server()['facts'].get('oldTime',0)>0,'unload case must finish before natural track completion'
                for role in ROLES:self.send('return',role)
                away=False
                self.d.wait(lambda:self.pair() if self.pair() and all(r['sample'].get('chunkLoaded') is True and r['sample'].get('soundStopped') is True and r['sample'].get('cancelled') is True for r in self.pair()) else None,'reentry cannot revive retained old sound',90)
            replacement_ready=None
            if kind=='replace':
                self.d.wait(lambda:self.server() if self.server() and self.server()['facts'].get('currentPlaying') is True and self.server()['facts'].get('currentIsOriginal') is False else None,'same-position replacement genuinely plays',20)
                def fresh_replacement():
                    rows=loaded()
                    return rows if rows and all(row['sound']['soundIdentity']!=old['sample']['soundIdentity'] for row,old in zip(rows,baseline)) else None
                replacement_ready=self.d.wait(fresh_replacement,'both new replacement identities have real private PCM and AL playback',95)
            self.record(kind+('-paused' if paused else ''),passed=True,before=baseline,after=after,ready=ready,pausedTerminationBeforeResume=paused_termination,replacementReady=replacement_ready,server=self.server())
        finally:
            if paused:self.d.send('audio-resume');self.dual.send('audio-resume')
            if away:
                for role in ROLES:self.send('return',role)
            if captured:self.send('restore')
            else:self.d.send('playback-return')
            self.d.send('audit-discs');self.d.wait(lambda:self.d.once(case['caseId'],'inventory',True),'lifecycle returns exact original CD once',30)
    def stale(self,case,replacement):
        captured=False
        try:
            placed=int(time.time()*1000);self.d.send('playback-place',marker=case['marker'],site=0);self.dual.send('join-listener')
            self.d.wait(lambda:self.dual.player_ready(case,0,placed),'both actual stopped original BE before delayed resolver',90)
            self.send('capture');captured=True;held=self.send('hold')['facts']
            assert held['sourceDone'] and not held['sourceExceptional'] and not held['heldFutureDone'] and held['oldConsumerFuturePresent'] and not held['oldConsumerFutureDone'] and held['sourceIdentity']!=held['returnedGateIdentity'],'hold must wrap the real original future'
            self.send('remove')
            if replacement:
                self.send('replace')
                self.d.wait(lambda:self.server() if self.server() and self.server()['facts'].get('currentPlaying') and self.server()['facts'].get('newCallbacks')==1 else None,'new generation original callback really ran',20)
            before=self.server()['facts'];released=self.send('release')['atUtcMillis']
            def settled():
                value=self.server()
                if not value or value['atUtcMillis']<released+1500:return None
                assert resolver_safe(before,value['facts']),'stale original callback revived old BE or dispatched old playback'
                return value
            after=self.d.wait(settled,'released real future leaves old callback blocked',15)
            self.record('stale-resolver-'+('same-position-replacement' if replacement else 'removed'),passed=True,held=held,before=before,after=after)
        finally:
            if captured:self.send('restore')
            else:self.d.send('playback-return')
            self.d.send('audit-discs');self.d.wait(lambda:self.d.once(case['caseId'],'inventory',True),'stale resolver retains original CD once',30)
    def run(self,case):
        for label,callback in [('remove-ready',lambda:self.live(case,'remove')),('remove-paused',lambda:self.live(case,'remove',True)),('replace-ready',lambda:self.live(case,'replace')),('unload-ready',lambda:self.live(case,'unload')),('stale-removed',lambda:self.stale(case,False)),('stale-replaced',lambda:self.stale(case,True))]:
            try:callback()
            except Exception as error:self.record(label,passed=False,errorType=type(error).__name__,reason=str(error),clients=self.pair(),server=self.server());raise

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args();d=Driver(a.instance_root,a.frozen_inputs)
    if not a.execute:print(json.dumps({'ownedFrozenInputsVerified':True,'commandsSent':0,'mcStarted':False}));return
    assert d.owner['clientNames']=={'host':'10000','guest':'10001'}
    dual=Dual(d)
    for cmd in ['audio-enable-owned','pause-focus-false','lyrics-on','audio-resume']:d.send(cmd);dual.send(cmd)
    cases=[c for c in dual.inventory() if dual.origin(c['reference'])['platform']=='netease'];assert cases,'original burned Netease CD needed for lyric cleanup'
    Lifecycle(d,dual).run(cases[0])
    print(json.dumps({'nativeLifecycleCases':6,'passed':True,'mcStarted':False,'normalShutdownOwnedByMainDriver':True}))
if __name__=='__main__':main()
