"""Verify a current owned lab; --execute tests real upstream packets, never launches MC."""
from pathlib import Path
import argparse,json,time,uuid
from qa_io import atomic_json
from run_matrix1438 import Driver,PRODUCT,read
from run_lyrics4 import Dual
from run_lifecycle10 import Lifecycle,cleanup_sample


def playback_ready(rows,now):
    if not rows or {r.get('role') for r in rows}!={'host','guest'}:return False
    for row in rows:
        sound=row.get('sound') or {};audio=sound.get('audio',{});ctor=sound.get('constructorObservation',{})
        if not (ctor.get('privateStatePresentAfterConstruction') is True and ctor.get('cancelledAfterConstruction') is False
                and ctor.get('observationPoint')=='upstream-factory-return-before-sound-manager-play'
                and ctor.get('soundIdentity')==sound.get('soundIdentity')
                and sound.get('soundEngineActive') is True and sound.get('stopped') is False
                and sound.get('privateClock',{}).get('ready') is True
                and audio.get('actualStreamType')=='net.muxigame.musiccompat.burn.PrivatePcmStream'
                and audio.get('sourceState')==4114 and audio.get('gain',0)>0
                and 0<=now-audio.get('atUtcMillis',0)<=2000 and sound.get('hasLyrics') is True
                and row['observation'].get('stageObservation',{}).get('stateTrackCount')==0):return False
    return True


def clean_for_forty_ticks(rows):
    return bool(rows) and {r.get('role') for r in rows}=={'host','guest'} and all(
        cleanup_sample(r['sample']) and r['sample']['clientTick']-r['sample']['invalidTick']>=40
        and r['sample']['lastEmissionTick']<=r['sample']['invalidTick'] for r in rows)


def prepare_player(d,dual,case):
    """Use owned placement to enter the actual vanilla fixture dimension, then verify it."""
    d.send('close-menu')
    for cmd in ('audio-enable-owned','pause-focus-false','lyrics-on','audio-resume'):
        d.send(cmd);dual.send(cmd)
    before=int(time.time()*1000)
    d.send('playback-place',marker=case['marker'],site=0);dual.send('join-listener')
    previous=None
    def prepared():
        nonlocal previous
        rows=dual.player_ready(case,0,before)
        if not rows:return None
        if not all(r['observation'].get('audioEnvironment',{}).get('engineLoaded') is True
                   and r['observation']['audioEnvironment'].get('listenerGain',0)>0
                   and r['observation']['audioEnvironment'].get('masterVolume',0)>0
                   and r['observation']['audioEnvironment'].get('recordsVolume',0)>0 for r in rows):return None
        stamps=tuple(r['observation']['atUtcMillis'] for r in rows)
        if previous is not None and all(x>y for x,y in zip(stamps,previous)):return rows
        if previous is None:previous=stamps
        return None
    return d.wait(prepared,'both actual stopped original discs, vanilla site and loaded audible engines',90)


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True)
    ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args()
    lock=read(a.frozen_inputs);assert lock and lock.get('scope')=='unified-current-v1','explicit_current_scope_required'
    d=Driver(a.instance_root,a.frozen_inputs)
    assert d.owner['clientNames']=={'host':'10000','guest':'10001'},'two_owned_clients_required'
    if not a.execute:
        print(json.dumps({'ownedCurrentInputsVerified':True,'commandsSent':False,'mcStarted':False,'productSha256':PRODUCT}));return
    dual=Dual(d);lifecycle=Lifecycle(d,dual);case=None;placed=False
    evidence=d.base/('entry042-legacy-native-results-'+uuid.uuid4().hex+'.json')
    result={'runId':d.run,'productSha256':PRODUCT,'passed':False,'mcStartedByDriver':False,
            'physicalAudibilityVerified':False,'syntheticIdentityAndOwnedAudio':True,'ossRequests':0}
    def save():
        atomic_json(evidence,result)
        atomic_json(d.base/'entry042-legacy-native-results.json',result)
    try:
        d.send('close-menu')
        cases=[c for c in dual.inventory() if dual.origin(c['reference'])['platform']=='netease']
        assert cases,'actual_burned_owned_netease_cd_required'
        case=cases[0];result['caseId']=case['caseId'];placed=True
        result['prepared']=prepare_player(d,dual,case)
        dispatched=int(time.time()*1000);d.send('playback-start-legacy')
        def dispatch():
            row=read(d.base/'entry042-legacy-playback-dispatch.json') or {}
            if row.get('runId')!=d.run or row.get('atUtcMillis',0)<dispatched:return None
            assert row.get('actualPayload')=='netmusic:music_to_client' and row.get('usedPlaybackStatePayload') is False
            assert set(row.get('recipients',[]))=={'10000','10001'}
            return row
        result['dispatch']=d.wait(dispatch,'actual upstream packet delivered to both owned listeners',15)
        def loaded():
            rows=dual.pair(case['reference'])
            return rows if playback_ready(rows,int(time.time()*1000)) else None
        first=d.wait(loaded,'legacy constructor bound private state and real two-client PCM/OpenAL',95)
        result['playbackBefore']=first;save()
        began=time.monotonic();d.wait(lambda:time.monotonic()-began>=5,'legacy real audio advancement five seconds',10)
        after=d.wait(loaded,'fresh legacy audio samples after five seconds',10)
        for left,right in zip(first,after):
            assert left['role']==right['role'] and left['sound']['soundIdentity']==right['sound']['soundIdentity']
            delta=right['sound']['audio']['elapsedSeconds']-left['sound']['audio']['elapsedSeconds']
            assert 3<=delta<=9,'actual_audio_did_not_advance_with_wall_time'
            assert abs(right['sound']['soundTick']/20-right['sound']['audio']['elapsedSeconds'])<=2,'lyric_clock_drift'
        result['playbackAfter']=after
        for role in ('host','guest'):lifecycle.send('arm',role)
        def notes():
            rows=lifecycle.pair()
            return rows if rows and all(r['sample'].get('noteEmissions',0)>0 and r['sample'].get('taggedActualNotes',0)>0 and r['sample'].get('maxNoteLifetime')==6 for r in rows) else None
        result['beforeDiscRemoval']=d.wait(notes,'actual original NOTE particles on both clients',15);save()
        d.send('playback-return')
        d.wait(lambda:d.once(case['caseId'],'inventory',True),'same original burned disc returned exactly once',30)
        def clean():
            rows=lifecycle.pair();return rows if clean_for_forty_ticks(rows) else None
        first_clean=d.wait(clean,'both sounds lyrics channels stopped and no NOTE after forty native ticks',25)
        def stable():
            rows=clean()
            if not rows:return None
            if any(b['sample']['clientTick']-aa['sample']['clientTick']<10 for aa,b in zip(first_clean,rows)):return None
            for aa,b in zip(first_clean,rows):
                assert aa['sample']['soundIdentity']==b['sample']['soundIdentity'] and aa['sample']['noteEmissions']==b['sample']['noteEmissions'],'new_NOTE_after_disc_removal'
            return rows
        result['afterDiscRemoval']=d.wait(stable,'additional ten ticks keep original NOTE count fixed',10)
        result['passed']=True;save();print(json.dumps({'legacyNativePassed':True,'result':str(d.base/'entry042-legacy-native-results.json'),'physicalAudibilityVerified':False}))
    except Exception as error:
        result.update(errorType=type(error).__name__,reason=str(error),failurePlayback=dual.pair(case['reference'] if case else None,False),failureLifecycle=lifecycle.pair())
        save();raise
    finally:
        if placed and case and not d.once(case['caseId'],'inventory',True):
            try:
                d.send('playback-return');d.wait(lambda:d.once(case['caseId'],'inventory',True),'failure cleanup returns same original disc',30)
            except Exception as error:
                result['cleanupError']={'errorType':type(error).__name__,'reason':str(error)};save()


if __name__=='__main__':main()
