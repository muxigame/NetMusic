"""Offline regressions for the actual native player readiness gate. Never launches MC."""
from copy import deepcopy
from pathlib import Path
import unittest
from unittest.mock import patch
import run_lyrics4 as runner

CASE={'caseId':'owned-case','marker':1234,'reference':'owned-stable-reference'}
RUN='owned-run'

def frame(stamp=10001,**changes):
    block={'site':0,'position':[2,-60,0],'present':True,'chunkLoaded':True,'marker':1234,'count':1,'reference':CASE['reference'],'isPlay':False}
    value={'runId':RUN,'atUtcMillis':stamp,'dimension':'minecraft:overworld','screen':'NONE','overlay':'NONE','playerAlive':True,'blocks':[block],'sounds':[],'audioEnvironment':{'engineLoaded':True,'listenerGain':1.0,'masterVolume':1.0,'recordsVolume':1.0}}
    value.update(changes)
    return value

def pair(stamp=10001):return {'host':frame(stamp),'guest':frame(stamp)}

class NativeReadinessLab:
    """Only the snapshot reader/clock/command sink are substituted; gate code is real."""
    def __init__(self,frames):
        self.frames=frames;self.current={};self.now=10000;self.commands=[];self.records=[];self.run=RUN;self.base=Path('read-only-native-snapshots');self.index=-1
        self.dual=object.__new__(runner.Dual);self.dual.d=self
        self.dual.send=lambda cmd:self.commands.append((self.index,'guest',cmd))
        self.dual.record=lambda label,rows,**facts:self.records.append((label,rows,facts))
    def send(self,cmd,**params):self.commands.append((self.index,'host',cmd))
    def case(self,ident):return {'playerBlock':1} # Server placement/ACK can precede client chunk entry.
    def wait(self,predicate,label,seconds=30):
        if label=='original CD in original player':return predicate()
        if seconds==15:
            value=predicate()
            if value:return value
            raise AssertionError('native_condition_timeout: '+label)
        assert seconds==90
        for self.index,self.current in enumerate(self.frames):
            self.now=10000+100*(self.index+1)
            value=predicate()
            if value:return value
        raise AssertionError('native_condition_timeout: '+label)
    def read(self,path):
        return self.current.get('guest' if path.name.endswith('-guest.json') else 'host',{})
    def start(self):
        with patch.object(runner,'read',self.read),patch.object(runner.time,'time',lambda:self.now/1000):
            self.dual.start_ready(CASE)
    def starts(self):return [x for x in self.commands if x[2]=='playback-start']

class ActualReadinessRegression(unittest.TestCase):
    def blocked(self,frames):
        lab=NativeReadinessLab(frames)
        with self.assertRaisesRegex(AssertionError,'native_condition_timeout'):lab.start()
        self.assertEqual(lab.starts(),[])
        self.assertIn((-1,'guest','join-listener'),lab.commands)
        return lab
    def test_ack_before_dimension_entry_never_starts(self):
        early=pair();early['guest']['dimension']='muxi_game_core:overworld'
        self.blocked([early,deepcopy(early)])
    def test_closed_screen_without_block_entity_never_starts(self):
        absent=pair();absent['guest']['blocks']=[]
        self.blocked([absent,deepcopy(absent)])
    def test_stale_previous_run_and_future_samples_never_start(self):
        for kind in ['stale','previous-run','future','before-placement']:
            with self.subTest(kind=kind):
                value=pair()
                for sample in value.values():
                    if kind=='stale':sample['atUtcMillis']=7000
                    if kind=='previous-run':sample['runId']='previous-run'
                    if kind=='future':sample['atUtcMillis']=20000
                    if kind=='before-placement':sample['atUtcMillis']=10000
                self.blocked([value,deepcopy(value)])
    def test_other_cd_in_ready_block_never_starts(self):
        for field,bad in [('marker',4321),('reference','other-CD'),('count',2),('site',1)]:
            with self.subTest(field=field):
                value=pair();value['guest']['blocks'][0][field]=bad
                self.blocked([value,deepcopy(value)])
    def test_live_world_overlay_chunk_play_and_position_guard(self):
        for field,bad in [('playerAlive',False),('screen','ReceivingLevelScreen'),('overlay','LoadingOverlay'),('chunkLoaded',False),('isPlay',True),('position',[5,-60,0])]:
            with self.subTest(field=field):
                value=pair()
                if field in ['playerAlive','screen','overlay']:value['guest'][field]=bad
                else:value['guest']['blocks'][0][field]=bad
                self.blocked([value,deepcopy(value)])
    def test_unloaded_or_muted_actual_engine_never_starts(self):
        for field,bad in [('engineLoaded',False),('listenerGain',0.0),('masterVolume',0.0),('recordsVolume',0.0)]:
            with self.subTest(field=field):
                first=pair();second=pair(10201)
                for sample in [first['guest'],second['guest']]:sample['audioEnvironment'][field]=bad
                self.blocked([first,second])
    def test_two_new_samples_start_once_even_with_no_sounds(self):
        first=pair();duplicate=deepcopy(first);host_update=pair();host_update['host']['atUtcMillis']=10201
        final=pair(10301)
        lab=NativeReadinessLab([first,duplicate,host_update,final,pair(10401)]);lab.start()
        self.assertEqual(lab.starts(),[(3,'host','playback-start')])
        self.assertEqual(lab.records[0][0],'original-player-ready-before-start')
        self.assertTrue(all(not x['observation']['sounds'] for x in lab.records[0][1]))
    def test_readiness_interrupted_requires_two_new_valid_samples(self):
        invalid=pair(10101);invalid['guest']['blocks']=[]
        self.blocked([pair(),invalid,pair(10201)])
        lab=NativeReadinessLab([pair(),invalid,pair(10201),pair(10301)]);lab.start()
        self.assertEqual(lab.starts(),[(3,'host','playback-start')])

if __name__=='__main__':unittest.main(verbosity=2)
