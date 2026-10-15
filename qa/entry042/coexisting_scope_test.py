"""Ownership/generation rejection checks; no process launch/control."""
from pathlib import Path
import copy,json,tempfile,unittest
from unittest.mock import patch
import coexisting_scope as scope
import current_scope

class PeerTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.work=Path(self.temp.name);self.runs=self.work/'test-environment/runs';self.root=self.runs/'peer'
        self.root.mkdir(parents=True);self.exe=self.work/'test-environment/toolchains/java-21/bin/java.exe'
        self.marker={'schema':1,'runId':'a'*32,'instanceRoot':str(self.root),'projectRoot':str(self.work/'better-mc-remake'),'hidden':True,'offlineLoopback':True,'productionMutation':False,'serverPort':25991,'roles':['server','host'],'processes':{},'runtime':{'javaHome':str(self.exe.parent.parent)},'resources':{'reservedPhysicalMiB':1000}}
        self.live=[]
        for i,role in enumerate(self.marker['roles']):
            d=self.root/role;d.mkdir();args=d/'launch.args'
            args.write_text('\n'.join('"'+v+'"' for v in ['-Dqa.local.root='+str(self.root),'-Dqa.local.runId='+self.marker['runId'],'-Dqa.local.role='+role,'-Dqa.local.hidden=true']),encoding='utf-8')
            self.marker['processes'][role]={'pid':100+i,'root':str(d),'processCreateTime':123.5+i}
            self.live.append({'pid':100+i,'createTime':123.5+i,'name':'java.exe','cmdline':[str(self.exe),'@'+str(args)],'cwd':str(d),'rssMiB':50})
        (self.root/'server/server.properties').write_text('server-ip=127.0.0.1\nonline-mode=false\nserver-port=25991\n')
        self.save();p=patch.multiple(scope,WORKSPACE=self.work,RUNS=self.runs);p.start();self.addCleanup(p.stop)
        q=patch.object(current_scope,'RUNS',self.runs);q.start();self.addCleanup(q.stop)
    def save(self):(self.root/'local-mc-owner.json').write_text(json.dumps(self.marker),encoding='utf-8')
    def verify(self):return scope.verify_peers([self.root],self.live,{25961,25962})
    def test_exact_owner_and_live_generation(self):
        peers,reserve=self.verify();self.assertEqual(900,reserve);self.assertEqual('a'*32,peers[0]['runId'])
    def test_reused_pid_rejected(self):
        self.live[0]['createTime']+=1
        with self.assertRaises(ValueError):self.verify()
    def test_unknown_java_rejected_even_without_peer_grant(self):
        with self.assertRaises(ValueError):scope.verify_peers([],self.live,{25961})
        self.live.append({**self.live[0],'pid':999})
        with self.assertRaises(ValueError):self.verify()
    def test_actual_command_and_cwd_must_match(self):
        original=copy.deepcopy(self.live)
        for key,value in [('cmdline',[str(self.exe),'@other.args']),('cwd',str(self.root))]:
            self.live=copy.deepcopy(original);self.live[0][key]=value
            with self.assertRaises(ValueError):self.verify()
    def test_hidden_loopback_and_port_guards(self):
        original=copy.deepcopy(self.marker)
        for key,value in [('hidden',None),('offlineLoopback',False),('productionMutation',True),('serverPort',25961)]:
            self.marker={**original,key:value};self.save()
            with self.assertRaises(ValueError):self.verify()
    def test_visible_owned_peer_accepted(self):
        self.marker['hidden']=False;self.save()
        for role in self.marker['roles']:
            p=self.root/role/'launch.args';p.write_text(p.read_text().replace('hidden=true','hidden=false'))
        self.assertEqual(900,self.verify()[1])
    def test_visible_owner_hidden_command_mismatch_refused(self):
        self.marker['hidden']=False;self.save()
        with self.assertRaises(ValueError):self.verify()
    def test_completed_peer_rejected(self):
        (self.root/'run-result.json').write_text('{}')
        with self.assertRaises(ValueError):self.verify()
    def test_launch_file_scope_and_generation_required(self):
        (self.root/'host/launch.args').write_text('"-Dqa.local.hidden=true"')
        with self.assertRaises(ValueError):self.verify()
    def test_forward_and_backward_slashes_resolve_same_owned_root(self):
        for role in self.marker['roles']:
            p=self.root/role/'launch.args'
            text=p.read_text(encoding='utf-8');p.write_text(text.replace(str(self.root),self.root.as_posix()),encoding='utf-8')
        self.verify()
        for role in self.marker['roles']:
            p=self.root/role/'launch.args'
            text=p.read_text(encoding='utf-8');p.write_text(text.replace(self.root.as_posix(),str(self.root)),encoding='utf-8')
        self.verify()
    def test_wrong_root_and_duplicate_property_refused(self):
        p=self.root/'host/launch.args';original=p.read_text(encoding='utf-8')
        for text in (original.replace(str(self.root),str(self.runs/'other')),
                     original+'\n"-Dqa.local.runId='+self.marker['runId']+'"'):
            p.write_text(text,encoding='utf-8')
            with self.assertRaises(ValueError):self.verify()
    def test_outside_unified_runs_rejected(self):
        with self.assertRaises(ValueError):scope.verify_peers([self.work],[],{25961})

if __name__=='__main__':unittest.main(verbosity=2)
