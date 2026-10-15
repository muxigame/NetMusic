from pathlib import Path
import tempfile,json,uuid,threading,hashlib,urllib.request,urllib.error,unittest
from backend4 import Backend,serve,SESSIONS,REPO,watch_stop,write
class BackendTest(unittest.TestCase):
    def test_actual_http_two_identities_origin_proof_restart_and_normal_exit(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'coordinator').mkdir();audio=__import__('owned_audio4').data();(root/'entry042-owned-cc0.wav').write_bytes(audio)
            marker={'runId':uuid.uuid4().hex,'clientNames':{'host':'10000','guest':'10001'}};backend=Backend(root,marker);server=serve(backend,0);thread=threading.Thread(target=server.serve_forever);thread.start();stop=threading.Event();threading.Thread(target=watch_stop,args=(root,marker['runId'],server,stop),daemon=True).start();base=f'http://127.0.0.1:{server.server_port}'
            def call(path,body=None,role='host'):
                request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers={'Authorization':'Bearer entry042-owned-account-fixture','X-Entry042-Role':role,'Content-Type':'application/json'})
                with urllib.request.urlopen(request,timeout=5) as response:return response.read()
            try:
                origins=[{'platform':'netease','identifier':'12345','page':1},{'platform':'qq','identifier':'12345','page':1},{'platform':'bilibili','identifier':'BV1Q5411x7aE','page':2},{'platform':'none','identifier':'','page':1}]
                references=[]
                for origin in origins:
                    body={'gameSession':SESSIONS['host'][1],'requestId':str(uuid.uuid4()),'burnNonce':str(uuid.uuid4()),'bytes':len(audio),'sha256':hashlib.sha256(audio).hexdigest(),'license':'CC0-1.0','credit':'Self-generated fixture','title':'Owned fixture','lyricOrigin':origin}
                    ticket=json.loads(call('/api/launcher/music/uploads',body));job=ticket['jobId'];call('/fixture/stage',{'gameSession':body['gameSession'],'jobId':job})
                    receipt=json.loads(call(f'/api/launcher/music/uploads/{job}/complete',{'gameSession':body['gameSession']}));self.assertTrue(receipt['proof'].count('.')==2)
                    call('/api/minecraft/music/burn-receipt/redeem',{'proof':receipt['proof']},'server');references.append(receipt['url'])
                    guest={'gameSession':SESSIONS['guest'][1],'reference':receipt['url']}
                    self.assertEqual(origin,json.loads(call('/api/launcher/music/lyrics',guest,'guest'))['lyricOrigin']);self.assertEqual(audio,call('/fixture/media',guest,'guest'))
                    self.assertIn('x-oss-signature',json.loads(call('/api/launcher/music/playback',guest,'guest'))['url'])
                with self.assertRaises(urllib.error.HTTPError):call('/api/launcher/music/lyrics',{'gameSession':SESSIONS['guest'][1],'reference':references[0]},'host')
                reopened=Backend(root,marker);self.assertEqual(origins[2],reopened.service.lyrics(10001,SESSIONS['guest'][1],references[2])['lyricOrigin'])
                self.assertEqual(audio,reopened.storage.read(references[2].split('.aliyuncs.com/')[1],len(audio)))
            finally:write(root/'coordinator/entry042-backend-stop.json',{'runId':marker['runId'],'normalStopOnly':True});thread.join(5);stop.set();server.server_close();self.assertFalse(thread.is_alive())
            print(json.dumps({'actualLoopbackHttp':True,'actualServiceAndRsa':True,'twoFixtureIdentities':True,'durableOriginsAndActualBytes':True,'normalShutdown':True,'ossRequests':0,'realAccount':False,'mcStarted':False}))
if __name__=='__main__':unittest.main()
