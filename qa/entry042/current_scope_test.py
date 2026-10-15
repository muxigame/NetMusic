"""No Minecraft launch: current native boundary rejection tests."""
from pathlib import Path
import copy, json, tempfile, unittest
from unittest.mock import patch
import current_scope

class CurrentScopeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.runs=Path(self.temp.name)/'runs';self.root=self.runs/'music-test';self.root.mkdir(parents=True)
        self.product='a'*64;self.qa='b'*64;self.run='c'*32
        self.owner={'instanceRoot':str(self.root),'runId':self.run,'hidden':True,'offlineLoopback':True,'productionMutation':False,'clientNames':{'host':'10000','guest':'10001'},'inputArtifacts':[{'sha256':self.product},{'sha256':self.qa}]}
        self.scope={'schema':1,'instanceRoot':str(self.root),'runsRoot':str(self.runs.resolve()),'runId':self.run,'productSha256':self.product,'qaSha256':self.qa}
        self.patch=patch.object(current_scope,'RUNS',self.runs);self.patch.start();self.addCleanup(self.patch.stop)
        self.save()
    def save(self):
        (self.root/'local-mc-owner.json').write_text(json.dumps(self.owner),encoding='utf-8')
        (self.root/'entry042-current-scope.json').write_text(json.dumps(self.scope),encoding='utf-8')
    def verify(self):return current_scope.verify(self.root,self.product,self.qa,self.run)
    def test_exact_owner_scope(self):self.assertEqual(self.owner,self.verify())
    def test_numbered_slots_and_exact_depth(self):
        for slot in ('client-1','client-2','client-19'):
            root=self.runs.parent/slot/'runs/music-test'
            self.assertEqual(root.parent,current_scope.runs_root(root))
        for leaf in ('client-0/runs/music-test','client-01/runs/music-test','client-x/runs/music-test','client-1/music-test','client-1/runs','client-1/runs/nested/music-test','runs/nested/music-test','other/runs/music-test'):
            with self.assertRaises(ValueError):current_scope.runs_root(self.runs.parent/leaf)
    def test_slot_scope_bound_to_exact_parent(self):
        self.root=self.runs.parent/'client-2/runs/music-test';self.root.mkdir(parents=True)
        self.owner['instanceRoot']=str(self.root);self.scope['instanceRoot']=str(self.root)
        self.scope['runsRoot']=str(self.root.parent);self.save();self.verify()
        self.scope['runsRoot']=str(self.runs.parent/'client-1/runs');self.save()
        with self.assertRaises(ValueError):self.verify()
    def test_wrong_product_qa_or_run(self):
        for product,qa,run in [('d'*64,self.qa,self.run),(self.product,'d'*64,self.run),(self.product,self.qa,'d'*32)]:
            with self.assertRaises(ValueError):current_scope.verify(self.root,product,qa,run)
    def test_legacy_or_external_root_refused(self):
        with self.assertRaises(ValueError):current_scope.verify(self.runs.parent,self.product,self.qa,self.run)
    def test_owner_production_flags_and_client_names_refused(self):
        original=copy.deepcopy(self.owner)
        for key,value in [('hidden',False),('offlineLoopback',False),('productionMutation',True),('clientNames',{'host':'10000'})]:
            self.owner={**original,key:value};self.save()
            with self.assertRaises(ValueError):self.verify()
    def test_scope_owner_run_and_artifact_mismatch_refused(self):
        self.scope['runId']='d'*32;self.save()
        with self.assertRaises(ValueError):self.verify()
        self.scope['runId']=self.run;self.owner['inputArtifacts'].pop();self.save()
        with self.assertRaises(ValueError):self.verify()
    def test_noncanonical_case_or_hash_rejected(self):
        for value in ['a'*63,'A'*64,'../product.jar','']:
            with self.assertRaises(ValueError):current_scope.digest(value)

if __name__=='__main__':unittest.main(verbosity=2)
