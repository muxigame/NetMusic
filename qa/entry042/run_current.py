"""Launch current hash-bound music QA in the unified environment; preflight by default."""
from pathlib import Path
import argparse, json, os, re, shutil, socket, subprocess, sys, time, zipfile, tomllib
from current_scope import WORKSPACE, RUNS, child, digest, sha, run_path, runs_root
ROOT=Path(__file__).resolve().parent
REPO=WORKSPACE/'netmusic'
AUTH=WORKSPACE/'muxi-auth'

def backend_interpreter():
    # Windows venv python.exe can be a redirector spawning another PID. Launch
    # the real interpreter with this environment's explicit dependency directory
    # so the Popen PID must equal the backend's authenticated readiness PID.
    import site
    executable=Path(getattr(sys,'_base_executable',sys.executable)).resolve(strict=True)
    dependencies=[str(Path(p).resolve(strict=True)) for p in site.getsitepackages() if Path(p).is_dir()]
    env=dict(os.environ);env['PYTHONDONTWRITEBYTECODE']='1';env['PYTHONNOUSERSITE']='1'
    env['PYTHONPATH']=os.pathsep.join(dependencies)
    return str(executable),env

def _main(reservation):
    ap=argparse.ArgumentParser()
    ap.add_argument('--product-build',type=Path,required=True);ap.add_argument('--qa-build',type=Path,required=True)
    ap.add_argument('--expected-product-sha256',required=True);ap.add_argument('--expected-qa-sha256',required=True)
    ap.add_argument('--mod',type=Path,action='append',default=[])
    ap.add_argument('--coexisting-instance',type=Path,action='append',default=[])
    ap.add_argument('--name',required=True);ap.add_argument('--port',type=int,required=True);ap.add_argument('--backend-port',type=int,required=True)
    ap.add_argument('--inputs',type=Path,required=True,help='New preflight receipt path, or existing identical receipt when starting')
    ap.add_argument('--start',action='store_true');ap.add_argument('--preflight',action='store_true');ap.add_argument('--accept-eula',action='store_true')
    ap.add_argument('--java-home',type=Path,default=WORKSPACE/'test-environment/toolchains/java-21')
    ap.add_argument('--server-memory-mb',type=int,default=4096);ap.add_argument('--client-memory-mb',type=int,default=6144)
    ap.add_argument('--unix-temp',type=Path,default=Path('C:/Temp'))
    ap.add_argument('--hold-seconds',type=int,default=3600);ap.add_argument('--boot-timeout',type=int,default=600)
    a=ap.parse_args()
    if a.start and a.preflight:ap.error('choose preflight or start')
    if not re.fullmatch('[a-z0-9]+(?:-[a-z0-9]+)*',a.name):ap.error('lowercase hyphenated name required')
    receipt=child(a.inputs,REPO/'.local-validation')
    sys.path.insert(0,str(WORKSPACE/'scripts/ops'))
    import test_environment as environment
    if receipt.exists():
        previous=json.loads(receipt.read_text(encoding='utf-8'))
        lease=previous['slotLease']
        lab=run_path(previous['instanceRoot'])
        if lab.name!=a.name or str(lab)!=lease['instanceRoot']:raise ValueError('slot receipt mismatch')
    else:
        if a.start:raise ValueError('preflight receipt required before start')
        lease=environment.reserve_slot(a.name)
        reservation.update(lease=lease, environment=environment)
        lab=run_path(lease['instanceRoot'])
    if lab.exists():raise ValueError('new run required')
    jdk=child(a.java_home,WORKSPACE/'test-environment/toolchains')
    game=child(WORKSPACE/'test-environment/resources/client/game',WORKSPACE/'test-environment/resources')
    for p in (jdk/'bin/java.exe',jdk/'bin/javac.exe',game/'versions/BatterMC5Remake/BatterMC5Remake.json'):
        if not p.is_file():raise ValueError('required runtime input missing: '+str(p))
    if not 120<=a.hold_seconds<=7200 or not 180<=a.boot_timeout<=900:raise ValueError('bounded runtime duration required')
    if not 1024<=a.port<=65535 or not 1024<=a.backend_port<=65535 or a.port==a.backend_port:raise ValueError('distinct loopback ports required')
    product=json.loads((a.product_build/'artifact.json').read_text(encoding='utf-8'))
    qa=json.loads((a.qa_build/'receipt.json').read_text(encoding='utf-8'))
    ph,qh=digest(a.expected_product_sha256),digest(a.expected_qa_sha256)
    if product['sha256']!=ph or qa['sha256']!=qh or qa['productSha256']!=ph or qa.get('nativeReady') is not True or qa.get('compileOnly') is not False:raise ValueError('current artifact receipts required')
    productjar,qajar=Path(product['artifact']).resolve(),Path(qa['jar']).resolve()
    if sha(productjar)!=ph or sha(qajar)!=qh:raise ValueError('expected jar hash mismatch')
    with zipfile.ZipFile(qajar) as z:
        if z.read('entry042-current.properties').decode()!='scope=unified-current-v1\nproductSha256='+ph+'\n':raise ValueError('embedded scope mismatch')
    for rel,h in qa['sourceSha256'].items():
        if sha(ROOT/rel)!=h:raise ValueError('QA source changed: '+rel)
    module=ROOT.parents[1]
    for rel,h in product['sourceSha256'].items():
        if sha(module/rel)!=h:raise ValueError('product source changed: '+rel)
    for path,expected in product.get('compileOnlyDependencySha256',{}).items():
        if sha(Path(path))!=expected:raise ValueError('compile input changed')
    jars=[productjar,qajar,*[p.resolve(strict=True) for p in a.mod]]
    if len({p.name.casefold() for p in jars})!=len(jars):raise ValueError('duplicate mod filename')
    rows=[{'path':str(p),'sha256':sha(p),'bytes':p.stat().st_size} for p in jars]
    required_build_hashes={h for p,h in product.get('runtimeDependencySha256',product['binaryDependencySha256']).items() if 'mixinextras-' not in Path(p).name}
    if not required_build_hashes <= {row['sha256'] for row in rows}:raise ValueError('fixed product runtime dependencies missing')
    modids={'minecraft','neoforge','java'};required=[];metadata=[]
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            name=next((n for n in ('META-INF/neoforge.mods.toml','META-INF/mods.toml') if n in archive.namelist()),None)
            if name is None:raise ValueError('explicit mod metadata required: '+jar.name)
            record=tomllib.loads(archive.read(name).decode('utf-8'));ids={m['modId'] for m in record['mods']}
            if ids & modids:raise ValueError('duplicate mod ID')
            modids.update(ids)
            dependencies=[d for group in record.get('dependencies',{}).values() for d in group if d.get('type')=='required' or d.get('mandatory') is True]
            required.extend(d['modId'] for d in dependencies);metadata.append({'jar':jar.name,'mods':record['mods'],'required':dependencies})
    if set(required)-modids:raise ValueError('required dependency closure missing: '+str(set(required)-modids))
    # NeoForge enforces actual Maven version ranges at boot; keep metadata for review.
    sources=[*ROOT.glob('*.py'),*ROOT.rglob('*.java'),*AUTH.glob('app/music_*.py'),AUTH/'app/security.py',
             WORKSPACE/'scripts/ops/test_environment.py',
             *[WORKSPACE/'better-mc-remake/scripts'/name for name in ('local_mc_debug.py','local_mc_runtime.py','local_mc_linked.py')],
             *sorted((WORKSPACE/'better-mc-remake/tests/local-mc-debug').rglob('*.java'))]
    plan={'scope':'unified-current-v1','candidateSha256':ph,'qaSha256':qh,'mods':rows,'modMetadata':metadata,'instanceRoot':str(lab),'slotLease':lease,'port':a.port,'backendPort':a.backend_port,'clients':2,'sourceSha256':{str(p):sha(p) for p in sources},'javaHome':str(jdk),'clientGame':str(game),'clientMetadataSha256':sha(game/'versions/BatterMC5Remake/BatterMC5Remake.json'),'realAccounts':False,'ossRequests':0}
    receipt=child(a.inputs,REPO/'.local-validation')
    sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'))
    import local_mc_runtime as rt
    capacity=rt.resources();free=capacity['availableMemoryMiB']
    required=int((a.server_memory_mb+2*a.client_memory_mb)*1.25)+4096
    if free is None or free<required:raise ValueError('insufficient or unknown available RAM')
    if os.name!='nt':raise ValueError('this native fixture entry targets Windows')
    peers=[];peer_reserve=0
    if a.coexisting_instance:
        from coexisting_scope import verify_peers
        base=str(Path(getattr(sys,'_base_executable',sys.executable)).resolve(strict=True))
        observed=subprocess.run([base,'-I',str(ROOT/'coexisting_probe.py')],capture_output=True,text=True,encoding='utf-8',timeout=20,check=True)
        peers,peer_reserve=verify_peers(a.coexisting_instance,json.loads(observed.stdout),{a.port,a.backend_port})
        gpu=capacity['gpuTelemetry']
        if not gpu or min(row['freeMiB'] for row in gpu)<2048:raise ValueError('coexisting clients require measured 2048 MiB GPU headroom')
        if free<required+peer_reserve:raise ValueError('insufficient RAM including coexisting reserved growth')
    else:
        import csv
        for image in ('java.exe','javaw.exe'):
            probe=subprocess.run(['tasklist.exe','/FO','CSV','/NH','/FI','IMAGENAME eq '+image],capture_output=True,text=True,timeout=15,check=True)
            if any(row and row[0].casefold()==image for row in csv.reader(probe.stdout.splitlines())):raise ValueError('existing Java process must be inspected and window coordinated first')
    plan['coexistingInstances']=peers
    if receipt.exists():
        if json.loads(receipt.read_text(encoding='utf-8'))!=plan:raise ValueError('preflight inputs changed; use new receipt')
    else:receipt.parent.mkdir(parents=True,exist_ok=True);receipt.write_text(json.dumps(plan,indent=2),encoding='utf-8')
    with receipt.with_name(receipt.stem+'-resource-checks.jsonl').open('a',encoding='utf-8') as stream:
        stream.write(json.dumps({'atUnix':time.time(),'stage':'start' if a.start else 'preflight','capacity':capacity,'requiredMemoryMiB':required,'peerRemainingReservationMiB':peer_reserve,'coexistingInstances':peers,'minimumGpuFreeMiB':2048 if peers else None})+'\n')
    for port in (a.port,a.backend_port):
        for kind in (socket.SOCK_STREAM,socket.SOCK_DGRAM):
            with socket.socket(socket.AF_INET,kind) as probe:probe.bind(('127.0.0.1',port))
    if not a.start:
        reservation['keep']=True
        print(json.dumps({'preflight':True,'inputs':str(receipt),'availableMemoryMiB':free,'requiredMemoryMiB':required,'mcStarted':False}));return
    if not a.accept_eula:raise ValueError('--accept-eula required')
    sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'))
    import local_mc_runtime as rt, local_mc_debug as runner, local_mc_linked as linked
    from owned_audio4 import data
    backend=None;backend_log=None;original=rt.copy_inputs
    def stage(role,jars,data_dirs):
        nonlocal backend,backend_log
        if data_dirs:raise ValueError('no private input directory accepted')
        linked.copy_inputs(role,jars,[],rt.environment.CACHE)
        if role.name!='server':return
        owner=json.loads((lab/'local-mc-owner.json').read_text(encoding='utf-8'))
        scope={'schema':1,'instanceRoot':str(lab),'runsRoot':str(runs_root(lab)),'runId':owner['runId'],'productSha256':ph,'qaSha256':qh}
        (lab/'entry042-current-scope.json').write_text(json.dumps(scope),encoding='utf-8')
        (lab/'entry042-owned-cc0.wav').write_bytes(data())
        backend_log=(lab/'coordinator/entry042-backend.log').open('w',encoding='utf-8')
        executable,env=backend_interpreter()
        backend=subprocess.Popen([executable,'-B',str(ROOT/'backend4.py'),'--instance-root',str(lab),'--port',str(a.backend_port),'--expected-product-sha256',ph,'--expected-qa-sha256',qh,'--expected-run-id',owner['runId']],cwd=REPO,env=env,stdout=backend_log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        until=time.monotonic()+30
        ready=lab/'coordinator/entry042-backend-status.json'
        while not ready.exists():
            if backend.poll() is not None or time.monotonic()>until:raise RuntimeError('fixture backend startup failed')
            time.sleep(.1)
        value=json.loads(ready.read_text(encoding='utf-8'))
        if value['runId']!=owner['runId'] or value['pid']!=backend.pid or value['port']!=a.backend_port:raise ValueError('backend owner mismatch')
    linked.NEW_BYTES=2*256*1024**2
    command=['local_mc_debug.py','run','--project-root',str(WORKSPACE/'better-mc-remake'),'--instance-root',str(lab),'--server-runtime',str(WORKSPACE/'bmc5server'),'--client-game',str(game),'--java-home',str(jdk),'--port',str(a.port),'--clients','2','--client-name','10000','--client-name','10001','--accept-eula','--mode','hold','--hold-seconds',str(a.hold_seconds),'--boot-timeout',str(a.boot_timeout),'--server-memory-mb',str(a.server_memory_mb),'--client-memory-mb',str(a.client_memory_mb),'--client-max-fps','30','--client-render-distance','3','--client-simulation-distance','3','--jvm-property','muxi.music.entry042.qa=true','--jvm-property','muxi.music.entry042.backendPort='+str(a.backend_port)]
    if not a.unix_temp.resolve().is_dir():raise ValueError('existing short temporary socket path required')
    command+=['--unix-temp',str(a.unix_temp.resolve())]
    for p in jars:command+=['--mod',str(p)]
    old=sys.argv;rt.copy_inputs=stage;sys.argv=command
    environment.claim_slot(lease)
    reservation.update(lease=lease, environment=environment)
    try:return runner.main()
    finally:
        sys.argv=old;rt.copy_inputs=original
        if backend is not None:
            owner=json.loads((lab/'local-mc-owner.json').read_text(encoding='utf-8'))
            target=lab/'coordinator/entry042-backend-stop.json';tmp=target.with_suffix('.pending')
            tmp.write_text(json.dumps({'runId':owner['runId'],'normalStopOnly':True}),encoding='utf-8');os.replace(tmp,target)
            backend.wait(timeout=30)
            if backend_log:backend_log.close()
            if backend.returncode!=0:raise RuntimeError('backend failed normal shutdown')
        environment.release_slot(lease)
        reservation.pop('lease',None)

def main():
    reservation={}
    try:
        return _main(reservation)
    finally:
        # Failed preflight must not strand an empty slot. A successful preflight
        # deliberately retains its short reservation for the matching start.
        if reservation.get('lease') and not reservation.get('keep'):
            try:
                reservation['environment'].release_slot(reservation['lease'])
            except Exception as error:
                # The allocator refuses live owner roles; preserve their lease
                # and the original failure rather than hiding either condition.
                print('Slot reservation retained: '+str(error),file=sys.stderr)
if __name__=='__main__':raise SystemExit(main())
