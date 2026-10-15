"""Compile only registered QA sources. No Minecraft launch or network call."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parent
WORKSPACE=ROOT.parents[3]
ap=argparse.ArgumentParser();ap.add_argument('--product-build',type=Path,required=True);ap.add_argument('--output',type=Path,required=True)
ap.add_argument('--backend-build',type=Path,required=True)
ap.add_argument('--jdk',type=Path,default=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1'))
args=ap.parse_args()
product=json.loads((args.product_build/'artifact.json').read_text());jar=Path(product['artifact'])
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='9df0608dadcf94a7d2229ee76e7e265e38ec8a29ea5384cef619e918aedb0216'
lines=(args.product_build/'compile.args').read_text(encoding='utf-8').splitlines();index=lines.index('"-classpath"');cp=lines[index+1].strip('"').replace('\\\\','\\')+os.pathsep+str(jar)
sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'));import local_mc_runtime as rt
out=args.output.resolve();classes=out/'classes';classes.mkdir(parents=True,exist_ok=True)
for stale in classes.rglob('*.class'): stale.unlink()
sources=sorted(ROOT.rglob('*.java'));assert len(sources)==39
source_hashes={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
rt.argfile(out/'compile.args',['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',classes,*sources])
run=subprocess.run([str(args.jdk/'bin/javac.exe'),'@'+str(out/'compile.args')],cwd=out,capture_output=True,text=True,encoding='utf-8',errors='replace')
(out/'compile.log').write_text(run.stdout+run.stderr,encoding='utf-8');assert run.returncode==0,run.stderr
test=subprocess.run([str(args.jdk/'bin/java.exe'),'-cp',str(classes)+os.pathsep+cp,'net.muxigame.musiccompat.qa.entry042.Entry042FixtureTest'],cwd=out,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
(out/'fixture-tests.log').write_text(test.stdout+test.stderr,encoding='utf-8');assert test.returncode==0,test.stdout+test.stderr
backend=json.loads((args.backend_build/'receipt.json').read_text(encoding='utf-8'));env=dict(os.environ);env['PYTHONPATH']=backend['pythonDeps']+os.pathsep+str(WORKSPACE/'muxi-auth')+os.pathsep+str(ROOT)
protocol=subprocess.run([sys.executable,str(ROOT/'backend4_java_test.py'),str(out),str(args.product_build),str(args.jdk)],env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
(out/'actual-backend-java-protocol.log').write_text(protocol.stdout+protocol.stderr,encoding='utf-8');assert protocol.returncode==0,protocol.stdout+protocol.stderr
checks=json.loads(next(line for line in reversed(test.stdout.splitlines()) if line.startswith('{')))
mixins={'required':True,'minVersion':'0.8','compatibilityLevel':'JAVA_21','package':'net.muxigame.musiccompat.qa.entry042.mixin','mixins':['Entry042NetworkMixin','Entry042ClickPacketMixin','Entry042DeliveryMixin','Entry042LifecycleResolverMixin','Entry042LifecycleCallbackMixin','Entry042LifecycleDispatchMixin'],'client':['Entry042CoreMixin','Entry042DnsMixin','Entry042LoginNetworkMixin','Entry042ProgressMixin','Entry042PrivatePlaybackMixin','Entry042LyricsMixin','Entry042AudioClockMixin','Entry042PlaybackNetworkMixin','Entry042PlaybackDeliveryMixin','Entry042LyricPendingMixin','Entry042LifecycleNoteMixin','Entry042BurnReplyMixin'],'injectors':{'defaultRequire':1}}
metadata='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_music_entry042_qa"\nversion="0.1.0-private-only"\ndisplayName="Music normal entry QA ONLY"\ndescription="Owned local fixture only. Never publish. No OSS request."\n[[mixins]]\nconfig="muxi_entry042_qa.mixins.json"\n[[dependencies.muxi_music_entry042_qa]]\nmodId="muxi_music_compat"\ntype="required"\nversionRange="[0.4.3-burn-flow-candidate.10]"\nordering="AFTER"\nside="BOTH"\n'
helper=out/'muxi-music-entry042-qa-0.1.0.jar'
with zipfile.ZipFile(helper,'w',zipfile.ZIP_DEFLATED) as archive:
 def put(name,data):
  z=zipfile.ZipInfo(name,(2026,10,13,0,0,0));z.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(z,data)
 put('META-INF/neoforge.mods.toml',metadata);put('muxi_entry042_qa.mixins.json',json.dumps(mixins))
 for source in sorted(classes.rglob('*.class')):
  if not source.stem.startswith(('Entry042FixtureTest','Entry042BackendProtocolTest')):put(source.relative_to(classes).as_posix(),source.read_bytes())
receipt={'jar':str(helper),'sha256':hashlib.sha256(helper.read_bytes()).hexdigest(),'productSha256':product['sha256'],'sourceRoot':str(ROOT),'compiledSources':len(sources),'sourceSha256':source_hashes,'mcStarted':False,'ossRequests':0,'actualPlatformCalls':0,'runtimeMixinVerified':False,'productionLoginVerified':False,'fixtureAccountAndReceipt':True,'normalExitVerified':False,'compileOnly':True,'fixtureTests':checks,'actualJavaBackendCryptoProtocolPassed':True,'backendIdentityFixture':True,'backendServiceActual':True,'runtimeSourcesCopied':False}
assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in source_hashes.items()),'qa_source_changed_during_build'
(out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in receipt.items() if k!='sourceSha256'},indent=2))
