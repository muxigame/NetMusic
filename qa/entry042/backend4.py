"""Owned loopback fixture backend using actual MusicUploads/BurnReceipts, never OSS."""
from pathlib import Path
import argparse,hashlib,json,os,re,sys,time,uuid,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
REPO=Path(__file__).resolve().parents[3]/'muxi-auth'
sys.path.insert(0,str(REPO))
from app.music_upload import MusicUploads,MusicError,post_policy,Credentials,MAX_BYTES
from app.music_receipt import BurnReceipts
from app.security import OidcSigner
PRODUCT='9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216'
BUCKET='muxigame-music-cn-hangzhou';REGION='cn-hangzhou'
SESSIONS={'host':('10000','00000000-0000-4000-8000-000000000042'),'guest':('10001','00000000-0000-4000-8000-000000000043')}
def write(path,value):
    temp=path.with_suffix('.pending');temp.write_text(json.dumps(value,indent=2),encoding='utf-8');os.replace(temp,path)
class OwnedStorage:
    bucket,region=BUCKET,REGION
    def __init__(self,root):self.root=root;root.mkdir(exist_ok=True)
    def path(self,key):
        if not re.fullmatch(r'(incoming|media)/(10000|10001)/[0-9a-f-]{36}\.(wav|mp3)',key):raise MusicError(400,'invalid_fixture_key')
        p=self.root/key;assert self.root.resolve() in p.resolve().parents;return p
    def ticket(self,key,size,now):return post_policy(BUCKET,REGION,key,size,Credentials('OWNED_FIXTURE_ID','OWNED_FIXTURE_NOT_CLOUD','OWNED_FIXTURE_STS'),now)
    def read(self,key,limit):
        p=self.path(key)
        if not p.is_file() or p.stat().st_size>limit:raise MusicError(400,'invalid_audio_size')
        return p.read_bytes()
    def publish(self,key,data):
        p=self.path(key);p.parent.mkdir(parents=True,exist_ok=True)
        with p.open('xb') as f:f.write(data)
    def delete(self,key):self.path(key).unlink(missing_ok=True)
    def signed_read(self,key,duration,size,refresh=False):
        # Synthetic lease shape, not an OSS credential/signature. Reads stay loopback.
        now=int(time.time());date=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime(now));day=date[:8]
        from urllib.parse import urlencode
        query=urlencode({'x-oss-signature-version':'OSS4-HMAC-SHA256','x-oss-credential':f'OWNED_FIXTURE_ID/{day}/{REGION}/oss/aliyun_v4_request','x-oss-date':date,'x-oss-expires':'180','x-oss-signature':'a'*64,'x-oss-security-token':'OWNED_FIXTURE_NOT_CLOUD'})
        return {'url':f'https://{BUCKET}.oss-{REGION}.aliyuncs.com/{key}?{query}','expiresAt':now+180,'maxBytes':size}
class Backend:
    def __init__(self,root,marker):
        self.root,self.marker=root,marker;self.data=root/'coordinator/entry042-backend-data';self.data.mkdir(parents=True,exist_ok=True)
        self.storage=OwnedStorage(self.data/'objects');self.signer=OidcSigner(self.data/'owned-fixture-signing.pem')
        self.service=MusicUploads(self.data/'uploads.db',self.storage,receipts=BurnReceipts(self.signer,'https://account.muxigame.com'))
        self.events=[];self.lock=threading.Lock()
    def event(self,kind,**facts):
        with self.lock:
            self.events.append({'kind':kind,'atUtcMillis':int(time.time()*1000),**facts})
            write(self.root/'coordinator/entry042-backend-events.json',{'runId':self.marker['runId'],'actualServiceAndCrypto':True,'realAccount':False,'actualPlatformCalls':0,'ossRequests':0,'events':self.events[-500:]})
    def identity(self,body,role,token):
        if role not in self.marker['clientNames'] or role not in SESSIONS or token!='Bearer entry042-owned-account-fixture':raise MusicError(401,'owned_fixture_identity_required')
        uid,session=SESSIONS[role]
        if self.marker['clientNames'][role]!=uid or body.get('gameSession')!=session:raise MusicError(401,'game_connection_mismatch')
        return int(uid),session
    def live(self,uid,session):
        if not any(self.marker['clientNames'].get(role)==name and int(name)==uid and expected==session for role,(name,expected) in SESSIONS.items()):raise MusicError(401,'owned_fixture_identity_required')
    def dispatch(self,path,body,role,token):
        if path=='/oauth/jwks.json':return {'keys':[self.signer.jwk]}
        if path=='/api/minecraft/music/burn-receipt/redeem':
            if role!='server':raise MusicError(401,'owned_server_required')
            result=self.service.redeem(body.get('proof'),self.live);self.event('actual-rsa-proof-redeemed');return result
        uid,session=self.identity(body,role,token)
        if path=='/api/launcher/music/uploads':
            if set(body)-{'gameSession','requestId','burnNonce','bytes','sha256','license','credit','title','lyricOrigin'}:raise MusicError(400,'invalid_fixture_request')
            result=self.service.create(uid,session,body.get('requestId'),body.get('bytes'),body.get('sha256'),body.get('license'),body.get('credit'),body.get('title'),body.get('burnNonce',''),body.get('lyricOrigin'))
            self.event('actual-upload-intent',uid=uid);return result
        match=re.fullmatch(r'/api/launcher/music/uploads/([0-9a-f-]{36})/(complete|cancel)',path)
        if match:
            job,action=match.groups();result=getattr(self.service,action)(uid,session,job);self.event('actual-'+action,uid=uid);return result
        if path=='/api/launcher/music/playback':return self.service.playback(uid,session,body.get('reference'),body.get('refresh',False))
        if path=='/api/launcher/music/lyrics':
            result=self.service.lyrics(uid,session,body.get('reference'));self.event('actual-durable-lyric-origin',uid=uid,platform=result['lyricOrigin']['platform'],page=result['lyricOrigin']['page']);return result
        if path=='/fixture/stage':
            job=body.get('jobId');source=self.root/'entry042-owned-cc0.wav';data=source.read_bytes()
            if hashlib.sha256(data).hexdigest()!='71d1443e9214c162e3efa1884a624182cc5ffa4afa315e137ca027f16fcf6c82':raise MusicError(400,'owned_audio_required')
            with self.service.connect() as db:row=self.service.own(db,job,uid,session)
            if row['state']!='pending' or len(data)!=row['size'] or hashlib.sha256(data).hexdigest()!=row['sha256']:raise MusicError(400,'actual_source_digest_required')
            dest=self.storage.path(f'incoming/{uid}/{job}.wav');dest.parent.mkdir(parents=True,exist_ok=True)
            if dest.exists() and dest.read_bytes()!=data:raise MusicError(400,'actual_source_digest_required')
            if not dest.exists():dest.write_bytes(data)
            self.event('owned-generated-audio-staged',uid=uid,bytes=len(data));return {'staged':True}
        if path=='/fixture/media':
            reference=body.get('reference');self.service.lyrics(uid,session,reference)
            from urllib.parse import urlsplit
            key=urlsplit(reference).path.lstrip('/');data=self.storage.read(key,MAX_BYTES);self.event('actual-verified-media-read',uid=uid,bytes=len(data));return data
        raise MusicError(404,'owned_fixture_route_refused')
def serve(backend,port):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_GET(self):self.respond({})
        def do_POST(self):
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<=length<=65536:raise ValueError()
                body=json.loads(self.rfile.read(length))
                if type(body) is not dict:raise ValueError()
                self.respond(body)
            except (ValueError,KeyError,TypeError):self.reply(400,{'error':'invalid_fixture_request'})
        def reply(self,status,value):
            data=value if isinstance(value,bytes) else json.dumps(value).encode();self.send_response(status);self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(data)
        def respond(self,body):
            try:self.reply(200,backend.dispatch(self.path,body,self.headers.get('X-Entry042-Role',''),self.headers.get('Authorization','')))
            except MusicError as error:self.reply(error.status,{'error':error.code})
            except Exception:self.reply(500,{'error':'owned_fixture_failure'})
    return ThreadingHTTPServer(('127.0.0.1',port),Handler)
def watch_stop(root,run_id,server,stop):
    while not stop.wait(.25):
        p=root/'coordinator/entry042-backend-stop.json'
        try:
            if p.exists() and p.stat().st_size<1024 and json.loads(p.read_text(encoding='utf-8'))=={'runId':run_id,'normalStopOnly':True}:server.shutdown();return
        except (OSError,ValueError):continue
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--port',type=int,required=True)
    ap.add_argument('--expected-product-sha256');ap.add_argument('--expected-qa-sha256');ap.add_argument('--expected-run-id');args=ap.parse_args()
    root=args.instance_root.resolve(strict=True);product=PRODUCT
    current=[args.expected_product_sha256,args.expected_qa_sha256,args.expected_run_id]
    if any(current):
        if not all(current):raise ValueError('all current scope pins required')
        from current_scope import verify
        verify(root,*current);product=args.expected_product_sha256
    else:
        allowed=REPO.parent/'better-mc-remake/build/local-mc-debug';assert allowed.resolve() in root.parents
    marker=json.loads((root/'local-mc-owner.json').read_text(encoding='utf-8'));assert Path(marker['instanceRoot']).resolve()==root and marker['offlineLoopback'] and not marker['productionMutation'] and any(a['sha256']==product for a in marker['inputArtifacts'])
    assert marker['clientNames'] in [{'host':'10000'},{'host':'10000','guest':'10001'}]
    backend=Backend(root,marker);server=serve(backend,args.port);stop=threading.Event()
    threading.Thread(target=watch_stop,args=(root,marker['runId'],server,stop),daemon=True).start()
    write(root/'coordinator/entry042-backend-status.json',{'runId':marker['runId'],'pid':os.getpid(),'port':server.server_port,'loopbackOnly':True,'productSha256':product,'ready':True,'ossRequests':0,'actualPlatformCalls':0,'realAccount':False})
    try:server.serve_forever(poll_interval=.2)
    finally:stop.set();server.server_close();write(root/'coordinator/entry042-backend-exit.json',{'runId':marker['runId'],'normalExit':True,'exitCode':0,'ossRequests':0})
if __name__=='__main__':main()
