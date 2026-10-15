from pathlib import Path
import ast,sys,json,unittest,hashlib,tempfile
from unittest.mock import patch
from copy import deepcopy
QA_ROOT=Path(__file__).resolve().parents[2]/'entry042'
TEMP_ROOT=Path(tempfile.gettempdir()).resolve()
qa=QA_ROOT
sys.dont_write_bytecode=True;sys.path.insert(0,str(qa));import run_short_timeline5 as target
reference='https://muxigame-music-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/media/10000/00000000-0000-4000-8000-000000000042.wav';origin={'platform':'netease','identifier':'1868207326','page':1}
def rows(now=200000,lyrics=False):
 sound={'soundEngineActive':True,'soundIdentity':42,'reference':reference,'stopped':False,'privateClock':{'ready':True},'audio':{'atUtcMillis':now,'actualStreamType':target.PCM_STREAM,'sourceState':4114,'gain':1,'elapsedSeconds':1.0},'soundTick':20,'hasLyrics':lyrics}
 if lyrics:sound.update(line='Owned first',translation='Owned translation first',lyricTick=0,translationTick=0)
 return [{'role':r,'observation':{'runId':'test-run','atUtcMillis':now,'playerAlive':True,'playerPresent':True,'sounds':[deepcopy(sound)]},'sound':deepcopy(sound)} for r in ['host','guest']]
class Driver:
 def __init__(self,output_root):self.run='test-run';self.base=output_root/'coordinator';self.base.mkdir(exist_ok=True);self.waits=[]
 def wait(self,predicate,label,seconds=30):
  self.waits.append((label,seconds));answer=predicate()
  if answer:return answer
  raise AssertionError('native_condition_timeout: '+label)
class Dual:
 def __init__(self,rs):self.rs=rs;self.records=[]
 def record(self,*args,**kw):self.records.append((args,kw))
 def pair(self,*args,**kw):return self.rs
class Regression(unittest.TestCase):
 def setUp(self):
  self.workspace=tempfile.TemporaryDirectory(prefix='music-short10-model-',dir=TEMP_ROOT)
  self.out=Path(self.workspace.name).resolve();assert self.out.parent==TEMP_ROOT
  self.addCleanup(self.workspace.cleanup)
 def test_actual_audio_passes_without_lyrics(self):
  rs=rows();self.assertTrue(target.audio_ready(rs,200000));self.assertFalse(target.lyrics_ready(rs,origin))
 def test_lyrics_predicate_has_no_pcm_openal_gate(self):
  rs=rows(lyrics=True)
  for row in rs:row['sound']['privateClock']['ready']=False;row['sound']['audio']['sourceState']=4115
  self.assertTrue(target.lyrics_ready(rs,origin));self.assertFalse(target.audio_ready(rs,200000))
 def test_stale_or_wrong_or_muted_audio_is_not_a_pass(self):
  for field,value in [('atUtcMillis',197000),('actualStreamType','original-stream'),('sourceState',4115),('gain',0)]:
   rs=rows()
   with self.subTest(field=field):rs[1]['sound']['audio'][field]=value;self.assertFalse(target.audio_ready(rs,200000))
 def test_no_lyrics_and_qq_translation_constraints(self):
  rs=rows();self.assertTrue(target.lyrics_ready(rs,{'platform':'none','identifier':'','page':1}));rs[1]['sound']['hasLyrics']=True;self.assertFalse(target.lyrics_ready(rs,{'platform':'none','identifier':'','page':1}));rs=rows(lyrics=True);self.assertFalse(target.lyrics_ready(rs,{'platform':'qq','identifier':'12345','page':1}));[row['sound'].pop('translation') for row in rs];self.assertTrue(target.lyrics_ready(rs,{'platform':'qq','identifier':'12345','page':1}))
 def make(self,rs,status,started):
  d=Driver(self.out);dual=Dual(rs);check=target.ShortCandidate10(d,dual,'unbound-test-product');media=hashlib.sha256(reference.encode()).hexdigest()[:16]
  for row in rs:row['observation']['playbackDiagnostics']={'lyrics':[{'id':7,'media':media,'startedAtUtcMillis':started,'status':status}]}
  values={row['role']:row['observation'] for row in rs};reader=lambda p:values.get('guest' if p.name.endswith('-guest.json') else 'host',{})
  return d,check,reader
 def test_audio_pass_is_preserved_when_actual_lyric_budget_expires(self):
  d,check,reader=self.make(rows(),'pending',139000);check.components['late']={'audio':{'passed':True,'pcmOpenalPassed':True}}
  with patch.object(target,'read',reader),patch.object(target.time,'time',lambda:200):
   with self.assertRaisesRegex(AssertionError,'actual lyric future budget exhausted'):check.wait_lyrics('late',reference,origin,100000)
  self.assertTrue(check.components['late']['audio']['passed']);self.assertFalse(check.components['late']['lyrics']['passed']);self.assertEqual(d.waits[-1][1],60)
 def test_actual_completed_lyrics_can_pass_independently_of_pcm(self):
  rs=rows(lyrics=True)
  for row in rs:row['sound']['privateClock']['ready']=False;row['observation']['sounds'][0]['privateClock']['ready']=False
  d,check,reader=self.make(rs,'completed-lyrics',199000)
  with patch.object(target,'read',reader),patch.object(target.time,'time',lambda:200):check.wait_lyrics('lyrics-only',reference,origin,100000)
  self.assertTrue(check.components['lyrics-only']['lyrics']['passed']);self.assertFalse(target.audio_ready(rs,200000))
 def test_future_timeout_is_attributed_even_after_sound_disappears(self):
  rs=rows()
  for row in rs:row['observation']['sounds']=[]
  d,check,reader=self.make(rs,'pending',139000)
  with patch.object(target,'read',reader),patch.object(target.time,'time',lambda:200):
   with self.assertRaisesRegex(AssertionError,'actual lyric future budget exhausted'):check.wait_lyrics('no-sound',reference,origin,100000)
  self.assertEqual(check.components['no-sound']['lyrics']['futures']['host']['elapsedMillis'],61000)
 def test_capture_retains_pre_cleanup_sound_and_history(self):
  rs=rows();d,check,reader=self.make(rs,'pending',199000);history={'runId':d.run,'samples':[{'atUtcMillis':199500,'sounds':[deepcopy(rs[0]['sound'])]}]}
  def read(p):return history if 'history-' in p.name else reader(p)
  with patch.object(target,'read',read):check.capture('before-clear',AssertionError('lyrics pending'),reference)
  saved=json.loads((d.base/'entry042-short-failure-before-clear.json').read_text(encoding='utf8'));history['samples']=[];rs[0]['observation']['sounds']=[]
  self.assertTrue(saved['capturedBeforePlaybackCleanup']);self.assertEqual(len(saved['clients']['host']['history']['samples'][0]['sounds']),1);self.assertTrue(saved['clients']['host']['latest']['sounds'][0]['privateClock']['ready'])
 def test_audio_budget_matches_product_preparation(self):self.assertEqual(target.AUDIO_BUDGET_SECONDS,90);self.assertEqual(target.LYRIC_BUDGET_SECONDS,60)
def main():
 source=(qa/'run_short_timeline5.py').read_text(encoding='utf8');ast.parse(source)
 assert "if all(x['passed'] for x in results):" not in source and "attempt('actual-prestream-rejection-recovery'" in source and "for platform in ['qq','bilibili','none']:" in source
 with tempfile.TemporaryDirectory(prefix='music-short10-results-',dir=TEMP_ROOT) as name:
  output=Path(name).resolve();assert output.parent==TEMP_ROOT
  log_path=output/'tests.log'
  with log_path.open('w',encoding='utf8') as log:result=unittest.TextTestRunner(stream=log,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Regression))
  receipt={'testsRun':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),'audioBudgetSeconds':target.AUDIO_BUDGET_SECONDS,'lyricBudgetSeconds':target.LYRIC_BUDGET_SECONDS,'audioLyricPredicatesIndependent':True,'regressionSuppressionRemoved':True,'sourceSha256':hashlib.sha256((qa/'run_short_timeline5.py').read_bytes()).hexdigest(),'mcStarted':False,'networkCalls':0,'temporaryResultsOnly':True}
  (output/'result.json').write_text(json.dumps(receipt,indent=2),encoding='utf8')
  print(json.dumps(receipt));assert result.wasSuccessful(),log_path.read_text(encoding='utf8')
if __name__=='__main__':main()
