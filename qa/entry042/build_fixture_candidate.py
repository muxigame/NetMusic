"""Current-product compilation/protocol probe only; frozen runtime scope is unchanged.

This helper is NOT a candidate11 native-launch artifact. command.py, frozen10 and
Entry042Scope retain their historical pins. No MC launch or cloud request.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parent
WORKSPACE=ROOT.parents[3]
ap=argparse.ArgumentParser();ap.add_argument('--product-build',type=Path,required=True);ap.add_argument('--output',type=Path,required=True)
ap.add_argument('--backend-build',type=Path)
ap.add_argument('--expected-product-sha256',required=True)
ap.add_argument('--jdk',type=Path,default=WORKSPACE/'.local-operations/local-tools-and-raw-logs-20261015/tools/temurin-21.0.12.1+1')
args=ap.parse_args()
for name in ('product_build','output','backend_build','jdk'):
    value=getattr(args,name)
    if value is not None:setattr(args,name,value.resolve())
if args.output.exists():ap.error('--output must be a new directory')
if not __import__('re').fullmatch('[0-9a-f]{64}',args.expected_product_sha256):ap.error('explicit lowercase SHA-256 required')
for tool in ('java.exe','javac.exe'):
    if not (args.jdk/'bin'/tool).is_file():ap.error('reviewed existing JDK required')
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
product=json.loads((args.product_build/'artifact.json').read_text(encoding='utf-8'))
jar=Path(product['artifact']).resolve()
if jar.parent!=args.product_build or sha(jar)!=args.expected_product_sha256 or product['sha256']!=args.expected_product_sha256:
    ap.error('product artifact/report/expected SHA mismatch')
if product['version']!='0.4.3-burn-flow-candidate.11' or jar.stat().st_size!=product['bytes']:
    ap.error('candidate11 version/size required')
module=ROOT.parents[1]
if Path(product['sourceRoot']).resolve()!=module:ap.error('registered source root required')
actual={p.relative_to(module).as_posix():sha(p) for directory in ('src/main/java','src/test/java') for p in (module/directory).rglob('*.java')}
if actual!=product['sourceSha256'] or len(actual)!=product['compiledSources']:
    ap.error('product source inventory/hash changed since build')
with zipfile.ZipFile(jar) as archive:
    metadata=__import__('tomllib').loads(archive.read('META-INF/neoforge.mods.toml').decode())
    if not any(m['modId']=='muxi_music_compat' and m['version']==product['version'] for m in metadata['mods']):
        ap.error('actual JAR metadata does not match candidate11')
for dependency,digest in product['binaryDependencySha256'].items():
    if sha(Path(dependency).resolve())!=digest:ap.error('product dependency changed')
lines=(args.product_build/'compile.args').read_text(encoding='utf-8').splitlines();index=lines.index('"-classpath"');cp=lines[index+1].strip('"').replace('\\\\','\\')+os.pathsep+str(jar)
sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'));import local_mc_runtime as rt
out=args.output.resolve();classes=out/'classes';classes.mkdir(parents=True)
sources=sorted(ROOT.rglob('*.java'));assert len(sources)==39
source_hashes={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
rt.argfile(out/'compile.args',['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',classes,*sources])
run=subprocess.run([str(args.jdk/'bin/javac.exe'),'@'+str(out/'compile.args')],cwd=out,capture_output=True,text=True,encoding='utf-8',errors='replace')
(out/'compile.log').write_text(run.stdout+run.stderr,encoding='utf-8');assert run.returncode==0,run.stderr
test=subprocess.run([str(args.jdk/'bin/java.exe'),'-cp',str(classes)+os.pathsep+cp,'net.muxigame.musiccompat.qa.entry042.Entry042FixtureTest'],cwd=out,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
(out/'fixture-tests.log').write_text(test.stdout+test.stderr,encoding='utf-8');assert test.returncode==0,test.stdout+test.stderr
protocol_passed=False
protocol_reason='backend_build_not_provided'
if args.backend_build is not None:
    backend=json.loads((args.backend_build/'receipt.json').read_text(encoding='utf-8'))
    deps=Path(backend['pythonDeps']).resolve()
    if not deps.is_dir():raise ValueError('backend receipt dependency directory missing')
    env=dict(os.environ);env['PYTHONPATH']=str(deps)+os.pathsep+str(WORKSPACE/'muxi-auth')+os.pathsep+str(ROOT)
    env['PYTHONDONTWRITEBYTECODE']='1'
    protocol=subprocess.run([sys.executable,'-B',str(ROOT/'backend4_java_test.py'),str(out),str(args.product_build),str(args.jdk)],env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
    (out/'actual-backend-java-protocol.log').write_text(protocol.stdout+protocol.stderr,encoding='utf-8')
    if protocol.returncode:raise RuntimeError(protocol.stdout+protocol.stderr)
    protocol_passed=True;protocol_reason='passed_with_historical_scope_fixture_not_native_acceptance'
checks=json.loads(next(line for line in reversed(test.stdout.splitlines()) if line.startswith('{')))
mixins={'required':True,'minVersion':'0.8','compatibilityLevel':'JAVA_21','package':'net.muxigame.musiccompat.qa.entry042.mixin','mixins':['Entry042NetworkMixin','Entry042ClickPacketMixin','Entry042DeliveryMixin','Entry042LifecycleResolverMixin','Entry042LifecycleCallbackMixin','Entry042LifecycleDispatchMixin'],'client':['Entry042CoreMixin','Entry042DnsMixin','Entry042LoginNetworkMixin','Entry042ProgressMixin','Entry042PrivatePlaybackMixin','Entry042LyricsMixin','Entry042AudioClockMixin','Entry042PlaybackNetworkMixin','Entry042PlaybackDeliveryMixin','Entry042LyricPendingMixin','Entry042LifecycleNoteMixin','Entry042BurnReplyMixin'],'injectors':{'defaultRequire':1}}
metadata='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_music_entry042_qa"\nversion="0.1.1-candidate11-probe-only"\ndisplayName="Music candidate11 COMPILE PROBE ONLY"\ndescription="Compile/protocol probe only; frozen runtime scope is NOT candidate11 compatible. Never install or publish."\n[[mixins]]\nconfig="muxi_entry042_qa.mixins.json"\n[[dependencies.muxi_music_entry042_qa]]\nmodId="muxi_music_compat"\ntype="required"\nversionRange="[0.4.3-burn-flow-candidate.11]"\nordering="AFTER"\nside="BOTH"\n'
helper=out/'muxi-music-entry042-qa-candidate11-compile-probe-only.jar'
with zipfile.ZipFile(helper,'w',zipfile.ZIP_DEFLATED) as archive:
 def put(name,data):
  z=zipfile.ZipInfo(name,(2026,10,13,0,0,0));z.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(z,data)
 put('META-INF/neoforge.mods.toml',metadata);put('muxi_entry042_qa.mixins.json',json.dumps(mixins))
 for source in sorted(classes.rglob('*.class')):
  if not source.stem.startswith(('Entry042FixtureTest','Entry042BackendProtocolTest')):put(source.relative_to(classes).as_posix(),source.read_bytes())
receipt={'jar':str(helper),'sha256':hashlib.sha256(helper.read_bytes()).hexdigest(),'productSha256':product['sha256'],'sourceRoot':str(ROOT),'compiledSources':len(sources),'sourceSha256':source_hashes,'mcStarted':False,'ossRequests':0,'actualPlatformCalls':0,'runtimeMixinVerified':False,'productionLoginVerified':False,'fixtureAccountAndReceipt':True,'normalExitVerified':False,'compileOnly':True,'fixtureTests':checks,'actualJavaBackendCryptoProtocolPassed':protocol_passed,'protocolStatus':protocol_reason,'nativeReady':False,'runtimeScopeProductSha256':'9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216','productVersion':product['version'],'warning':'Compile/protocol probe only. Frozen scope and runtime runners are not migrated.','backendIdentityFixture':True,'backendServiceActual':protocol_passed,'runtimeSourcesCopied':False}
assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in source_hashes.items()),'qa_source_changed_during_build'
if any(sha(module/relative)!=digest for relative,digest in actual.items()) or sha(jar)!=args.expected_product_sha256:raise RuntimeError('product_changed_during_qa_build')
(out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in receipt.items() if k!='sourceSha256'},indent=2))
