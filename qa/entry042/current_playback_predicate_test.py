"""Regression tests for native evidence rejection; no Minecraft or network."""
import copy,unittest
from run_legacy_current import playback_ready,clean_for_forty_ticks
from run_playback_current import pair_ready,synchronized_pair


def pair():
    return [{'role':role,'observation':{'stageObservation':{'stateTrackCount':0}},'sound':{
        'soundIdentity':index,'soundEngineActive':True,'stopped':False,'soundTick':200,
        'hasLyrics':True,'line':'Owned fixture line','privateClock':{'ready':True,'cancelled':False},
        'constructorObservation':{'privateStatePresentAfterConstruction':True,'cancelledAfterConstruction':False,
            'observationPoint':'upstream-factory-return-before-sound-manager-play','soundIdentity':index},
        'audio':{'actualStreamType':'net.muxigame.musiccompat.burn.PrivatePcmStream','sourceState':4114,
            'gain':1,'atUtcMillis':10000,'elapsedSeconds':10}}} for index,role in enumerate(['host','guest'])]


class NativeEvidenceTest(unittest.TestCase):
    def test_valid_real_observation_shape(self):
        self.assertTrue(playback_ready(pair(),10000));self.assertTrue(pair_ready(pair(),10000));self.assertTrue(synchronized_pair(pair()))
    def test_early_constructor_injection_is_not_accepted(self):
        rows=pair();rows[0]['sound']['constructorObservation']['observationPoint']='constructor-return-injection'
        self.assertFalse(playback_ready(rows,10000))
    def test_observation_must_reference_same_constructed_sound(self):
        rows=pair();rows[0]['sound']['constructorObservation']['soundIdentity']=123
        self.assertFalse(playback_ready(rows,10000))
    def test_legacy_does_not_pass_using_new_state_path(self):
        rows=pair();rows[0]['observation']['stageObservation']['stateTrackCount']=1
        self.assertFalse(playback_ready(rows,10000))
    def test_both_paths_reject_old_decoder_paused_muted_stale_audio(self):
        for field,value in [('actualStreamType','legacy.NetMusicAudioStream'),('sourceState',4115),('gain',0),('atUtcMillis',7999)]:
            rows=pair();rows[1]['sound']['audio'][field]=value
            with self.subTest(field=field):
                self.assertFalse(playback_ready(rows,10000));self.assertFalse(pair_ready(rows,10000))
    def test_two_distinct_clients_required(self):
        rows=pair();rows[1]['role']='host'
        self.assertFalse(playback_ready(rows,10000));self.assertFalse(pair_ready(rows,10000))
    def test_lyric_and_audio_drift_rejected(self):
        rows=pair();rows[1]['sound']['soundTick']=260;self.assertFalse(pair_ready(rows,10000))
        rows=pair();rows[1]['sound']['audio']['elapsedSeconds']=13;self.assertFalse(synchronized_pair(rows))
        rows=pair();rows[1]['sound']['line']='Other';self.assertFalse(synchronized_pair(rows))
    def test_removal_requires_forty_ticks_and_no_late_note(self):
        sample={'armed':True,'cancelled':True,'soundStopped':True,'engineActive':False,'soundLyricsPresent':False,
                'ownerLyricsPresent':False,'channelReleased':True,'invalidTick':10,'clientTick':50,
                'actualOwnedAliveNotes':0,'taggedActualNotes':2,'noteTrackingErrorType':'','lastEmissionTick':9}
        rows=[{'role':role,'sample':copy.deepcopy(sample)} for role in ['host','guest']]
        self.assertTrue(clean_for_forty_ticks(rows))
        for field,value in [('clientTick',49),('lastEmissionTick',11),('actualOwnedAliveNotes',1),('ownerLyricsPresent',True),('channelReleased',False),('noteTrackingErrorType','unobserved')]:
            changed=copy.deepcopy(rows);changed[0]['sample'][field]=value
            with self.subTest(field=field):self.assertFalse(clean_for_forty_ticks(changed))


if __name__=='__main__':unittest.main()
