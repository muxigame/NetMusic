from pathlib import Path
import json,sys,tempfile,time,unittest
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
qa=Path(__file__).resolve().parents[2]/'entry042'
sys.path.insert(0,str(qa))
sys.dont_write_bytecode=True
from run_matrix1438 import Driver
from run_burn10 import BackgroundBurn

class DriverPredicates(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.base=Path(self.tmp.name);self.d=Driver.__new__(Driver);self.d.base=self.base;self.d.run='a'*32;self.d.jobs={};self.d.started=int(time.time()*1000)-2000;self.d.preCraftGeneration='old';self.d.results=[]
 def write(self,name,value):self.base.joinpath(name).write_text(json.dumps(value),encoding='utf-8')
 def counts(self,**fields):
  row={'caseId':'case','marker':123,'total':1,'inventory':0,'input':0,'output':0,'escrow':0,'carried':0,'loose':0,'playerBlock':0,'burned':False,**fields};self.write('entry042-disc-counts.json',{'runId':self.d.run,'cases':[row]});return row
 def bg(self,**fields):
  value={'connectionCurrent':True,'progressMatchesRequest':True,'generation':'new','requestId':'new','stage':'DONE','code':'','active':False,'noncePresent':True,**fields};self.write('entry042-client-status.json',{'runId':self.d.run,'backgroundTask':value});return value
 def test_escrow_is_not_input_or_delivery(self):
  self.counts(escrow=1);self.assertTrue(self.d.once('case','escrow',False));self.assertFalse(self.d.once('case','input',False));self.assertFalse(self.d.returned('case',False))
 def test_inventory_and_loose_terminal(self):
  self.counts(inventory=1);self.assertTrue(self.d.returned('case',False));self.counts(loose=1);self.assertTrue(self.d.returned('case',False));self.counts(output=1);self.assertFalse(self.d.returned('case',False))
 def test_duplicate_not_delivery(self):
  self.counts(inventory=1,escrow=1,total=2);self.assertFalse(self.d.once('case','inventory',False));self.assertFalse(self.d.returned('case',False))
 def test_old_done_rejected_and_new_bound(self):
  self.bg(generation='old',requestId='old');self.assertFalse(self.d.progress('case','DONE'))
  self.bg();self.assertTrue(self.d.progress('case','DONE'));self.assertEqual(self.d.jobs['case'],'new')
 def test_other_job_or_connection_rejected(self):
  self.d.jobs['case']='expected';self.bg();self.assertFalse(self.d.progress('case','DONE'))
  self.d.jobs={};self.bg(connectionCurrent=False);self.assertFalse(self.d.progress('case','DONE'))
 def test_render_history_must_match_new_generation(self):
  self.write('entry042-progress.json',{'runId':self.d.run,'rows':[{'generation':'old','stage':'DONE','atUtcMillis':int(time.time()*1000)}]});self.assertFalse(self.d.progress('case','DONE'))
  self.write('entry042-progress.json',{'runId':self.d.run,'rows':[{'generation':'new','stage':'DONE','atUtcMillis':int(time.time()*1000)}]});self.assertTrue(self.d.progress('case','DONE'))
 def test_old_output_resume_is_not_new_pass(self):
  row={'menu':'computer','phase':'uploading','outcome':'success-inventory','passed':True,'counts':{'total':1,'inventory':0,'input':0,'output':1,'escrow':0}}
  self.d.results=[row];self.assertFalse(self.d.done('computer','uploading',['success-inventory']))
  row['counts'].update(inventory=1,output=0);self.assertTrue(self.d.done('computer','uploading',['success-inventory']))
 def test_close_unstarted_and_active_have_different_order(self):
  calls=[];self.d.send=lambda cmd,**kw:calls.append(cmd);self.d.wait=lambda predicate,label,seconds=30:calls.append('wait') or True
  self.counts(input=1);self.d.close('case',False);self.assertEqual(calls,['close-menu','wait','audit-discs'])
  calls.clear();self.counts(escrow=1);self.d.close('case',True);self.assertEqual(calls,['wait','close-menu','wait','audit-discs'])
 def test_background_test_requires_same_task_and_no_second_disc(self):
  self.counts(escrow=1);body=json.loads(self.base.joinpath('entry042-disc-counts.json').read_text());body['activeTask']={'active':True,'requestId':'new','marker':123,'samePlayerObject':True,'menuAttached':False};self.write('entry042-disc-counts.json',body);self.bg(active=True)
  burn=BackgroundBurn.__new__(BackgroundBurn);burn.d=self.d;self.assertTrue(burn.task_alive('case','new',True));self.assertFalse(burn.task_alive('case','other',True));self.counts(escrow=1,input=1,total=2);self.assertFalse(burn.task_alive('case','new',True))

if __name__=='__main__':
 for name in ['run_matrix1438.py','run_burn10.py']:
  path=qa/name;compile(path.read_text(encoding='utf-8'),str(path),'exec')
 suite=unittest.defaultTestLoader.loadTestsFromTestCase(DriverPredicates);result=unittest.TextTestRunner(verbosity=2).run(suite)
 print(json.dumps({'driverModelTests':result.testsRun,'failed':len(result.failures)+len(result.errors),'nativeTestPerformed':False,'mcStarted':False,'sourcePinsChanged':False}));sys.exit(not result.wasSuccessful())
