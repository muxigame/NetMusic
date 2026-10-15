"""Current two-client PCM/timeline/removal and real late-join tests. Never launches MC."""
from pathlib import Path
import argparse,json,time,uuid
from qa_io import atomic_json
from run_matrix1438 import Driver,PRODUCT,read
from run_lyrics4 import Dual
from run_lifecycle10 import Lifecycle
from run_legacy_current import prepare_player,clean_for_forty_ticks


def sound_ready(row,now):
    sound=(row or {}).get('sound') or {};audio=sound.get('audio',{})
    return (sound.get('soundEngineActive') is True and sound.get('stopped') is False
        and sound.get('privateClock',{}).get('ready') is True and sound['privateClock'].get('cancelled') is False
        and audio.get('actualStreamType')=='net.muxigame.musiccompat.burn.PrivatePcmStream'
        and audio.get('sourceState')==4114 and audio.get('gain',0)>0
        and 0<=now-audio.get('atUtcMillis',0)<=2000 and sound.get('hasLyrics') is True
        and abs(sound.get('soundTick',-999)/20-audio.get('elapsedSeconds',999))<=2)


def pair_ready(rows,now):
    return bool(rows) and {r.get('role') for r in rows}=={'host','guest'} and all(sound_ready(r,now) for r in rows)


def synchronized_pair(rows):
    if not rows or len(rows)!=2:return False
    a,b=(r['sound'] for r in rows)
    return abs(a['audio']['elapsedSeconds']-b['audio']['elapsedSeconds'])<=2 and a.get('line')==b.get('line') and 'Owned' in a.get('line','')


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True)
    ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--execute',action='store_true')
    ap.add_argument('--part',choices=['same-disc','late-join','all'],default='all');a=ap.parse_args()
    lock=read(a.frozen_inputs);assert lock and lock.get('scope')=='unified-current-v1','current_scope_required'
    d=Driver(a.instance_root,a.frozen_inputs);assert d.owner['clientNames']=={'host':'10000','guest':'10001'}
    if not a.execute:
        print(json.dumps({'ownedCurrentInputsVerified':True,'commandsSent':False,'mcStarted':False,'part':a.part}));return
    dual=Dual(d);life=Lifecycle(d,dual)
    result={'runId':d.run,'productSha256':PRODUCT,'part':a.part,'cases':[],'passed':False,
            'mcStartedByDriver':False,'physicalAudibilityVerified':False,'syntheticIdentityAndOwnedAudio':True,'ossRequests':0}
    evidence=d.base/('entry042-current-playback-results-'+uuid.uuid4().hex+'.json')
    def save():
        atomic_json(evidence,result);atomic_json(d.base/'entry042-current-playback-results.json',result)
    d.send('close-menu')
    cases=[c for c in dual.inventory() if dual.origin(c['reference'])['platform']=='netease']
    assert cases,'actual_burned_owned_netease_cd_required'
    case=cases[0]
    def loaded():
        rows=dual.pair(case['reference']);now=int(time.time()*1000)
        return rows if pair_ready(rows,now) else None
    def host_loaded():
        rows=dual.pair(case['reference'],False)
        return rows if rows and sound_ready(rows[0],int(time.time()*1000)) else None
    def return_once():
        d.send('playback-return');d.wait(lambda:d.once(case['caseId'],'inventory',True),'same original burned disc returned once',30)
    for mode in (['same-disc','late-join'] if a.part=='all' else [a.part]):
        row={'case':mode,'caseId':case['caseId'],'passed':False,'samples':[]};result['cases'].append(row);save()
        guest_away=False
        try:
            row['prepared']=prepare_player(d,dual,case)
            if mode=='late-join':
                dual.send('disconnect');guest_away=True
                d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is False,'actual guest disconnect before host starts',90)
            d.send('playback-start')
            if mode=='same-disc':
                first=d.wait(loaded,'both current-path actual private PCM and lyrics ready',95)
                assert all(r['observation'].get('stageObservation',{}).get('stateTrackCount')==1 for r in first),'current_state_path_required'
                started=time.monotonic();identities={r['role']:r['sound']['soundIdentity'] for r in first}
                for seconds in [0,5,15,30]:
                    d.wait(lambda:time.monotonic()-started>=seconds,'same disc wall time '+str(seconds),seconds+10)
                    samples=d.wait(loaded,'fresh actual audio/lyric samples',10)
                    assert synchronized_pair(samples),'two_client_audio_or_lyric_drift'
                    for sample,initial in zip(samples,first):
                        assert sample['sound']['soundIdentity']==identities[sample['role']],'unexpected_sound_restart'
                        delta=sample['sound']['audio']['elapsedSeconds']-initial['sound']['audio']['elapsedSeconds']
                        assert abs(delta-seconds)<=2,'actual_PCM_clock_failed_to_advance'
                    row['samples'].append({'seconds':seconds,'clients':samples});save()
                for role in ['host','guest']:life.send('arm',role)
                def notes():
                    rows=life.pair()
                    return rows if rows and all(r['sample'].get('noteEmissions',0)>0 and r['sample'].get('taggedActualNotes',0)>0 and r['sample'].get('maxNoteLifetime')==6 for r in rows) else None
                row['beforeDiscRemoval']=d.wait(notes,'both actual NOTE particle emissions before disc extraction',15);save()
                return_once()
                def clean():
                    rows=life.pair();return rows if clean_for_forty_ticks(rows) else None
                row['afterDiscRemoval']=d.wait(clean,'forty native ticks without new NOTE plus sound lyrics channel cleanup',25)
            else:
                host=d.wait(host_loaded,'host private PCM plays while guest disconnected',95);identity=host[0]['sound']['soundIdentity']
                row['hostBeforeJoin']=host;started=time.monotonic()
                d.wait(lambda:time.monotonic()-started>=5,'host plays at least five seconds before late join',10)
                advanced=d.wait(host_loaded,'actual host audio progress before guest joins',10)
                assert advanced[0]['sound']['audio']['elapsedSeconds']-host[0]['sound']['audio']['elapsedSeconds']>=3,'host_did_not_advance'
                row['hostAfterFiveSeconds']=advanced;save()
                dual.send('reconnect')
                d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is True,'actual guest reconnect',180);guest_away=False
                dual.send('join-listener')
                joined=d.wait(loaded,'late guest actual private PCM and lyrics ready',95)
                assert joined[0]['sound']['soundIdentity']==identity,'late_join_restarted_host'
                assert synchronized_pair(joined),'late_join_audio_or_lyric_drift'
                row['joined']=joined;save();started=time.monotonic()
                d.wait(lambda:time.monotonic()-started>=5,'both late-join sounds advance five seconds',10)
                after=d.wait(loaded,'fresh two-client late-join samples',10)
                assert synchronized_pair(after) and after[0]['sound']['soundIdentity']==identity
                for initial,current in zip(joined,after):
                    assert current['sound']['soundIdentity']==initial['sound']['soundIdentity']
                    assert 3<=current['sound']['audio']['elapsedSeconds']-initial['sound']['audio']['elapsedSeconds']<=9,'late_join_audio_stalled'
                row['afterFiveSeconds']=after;return_once()
            row['passed']=True;save()
        except Exception as error:
            row.update(errorType=type(error).__name__,reason=str(error),failurePlayback=dual.pair(case['reference'],False),failureLifecycle=life.pair());save();raise
        finally:
            if guest_away:
                try:
                    dual.send('reconnect');d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') is True,'restore disconnected owned guest',180)
                except Exception as error:row['guestRestoreError']=str(error);save()
            if not d.once(case['caseId'],'inventory',True):
                try:return_once()
                except Exception as error:row['cleanupError']=str(error);save()
    result['passed']=all(r['passed'] for r in result['cases']);save()
    print(json.dumps({'currentPlaybackPassed':result['passed'],'cases':len(result['cases']),'evidence':str(evidence),'physicalAudibilityVerified':False}))


if __name__=='__main__':main()
