"""Prepare data/commands only. Runtime remains gated by the parent's MC window."""
from pathlib import Path
import argparse,hashlib,json,datetime
ROOT=Path(__file__).resolve().parent;WORKSPACE=ROOT.parents[3]
ap=argparse.ArgumentParser();ap.add_argument('--product-build',type=Path,required=True);ap.add_argument('--qa-build',type=Path,required=True);ap.add_argument('--binary-inputs',type=Path,required=True);ap.add_argument('--output',type=Path,required=True)
args=ap.parse_args();product=json.loads((args.product_build/'artifact.json').read_text());qa=json.loads((args.qa_build/'receipt.json').read_text());inputs=json.loads(args.binary_inputs.read_text())
assert product['sha256']=='023738e78b595afbd2c57847f005256c360f47de63e5cc3d26fa2852c10e3199' and qa['productSha256']==product['sha256']
assert qa['fixtureTests']['scopeAndFixtureChecks']>=21
mods=[Path(product['artifact']),Path(qa['jar']),Path(inputs['core'])]
pins={mods[0]:product['sha256'],mods[1]:qa['sha256'],mods[2]:'46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59'}
for item in inputs['runtimeDependencies']:p=Path(item['path']);mods.append(p);pins[p]=item['sha256']
for path,digest in product['binaryDependencySha256'].items():
 p=Path(path)
 if p==Path(inputs['core']):continue
 mods.append(p);pins[p]=digest
cloth=next((WORKSPACE/'test-environment/resources/client/game/mods').glob('cloth-config-15.0.140*'));mods.append(cloth);pins[cloth]=hashlib.sha256(cloth.read_bytes()).hexdigest()
for p,h in pins.items():assert hashlib.sha256(p.read_bytes()).hexdigest()==h
fixture=WORKSPACE/'muxi-auth/tests/music_oss_qa/muxi-generated-cc0.wav';assert hashlib.sha256(fixture.read_bytes()).hexdigest()=='436c5777f4c35a4de3d0678ad272ec116b7018f8fb975e9d2638c2b7c09a9985'
# Choose the final private root and loopback port at window grant, not now.
command=['python',str(WORKSPACE/'better-mc-remake/scripts/local_mc_debug.py'),'run','--project-root',str(WORKSPACE/'better-mc-remake'),'--instance-root','<NEW_OWNED_ROOT_UNDER_BMC_BUILD_LOCAL_MC_DEBUG>','--server-runtime',str(WORKSPACE/'bmc5server'),'--client-game',str(WORKSPACE/'test-environment/resources/client/game'),'--java-home',r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1','--port','<CHECKED_FREE_LOOPBACK_PORT>','--clients','1','--client-name','10000','--visible','--accept-eula','--mode','hold','--hold-seconds','900','--approved-heap-budget-mb','4096','--server-memory-mb','1536','--client-memory-mb','2304','--jvm-property','muxi.music.entry042.qa=true']
for p in dict.fromkeys(mods):command+=['--mod',str(p)]
steps=[
 {'cmd':'open-computer'}, {'cmd':'lock-matrix','wait':'native server lock/click/handler/cancel/close/duplicate-return disc count invariant'},
 {'cmd':'open-burner'}, {'cmd':'lock-matrix','wait':'same lock matrix on original burner'},
 {'cmd':'open-computer','wait':'fresh profile confirmation false'},
 {'cmd':'readonly'}, {'cmd':'clear-local-login'}, {'cmd':'configure','platform':'qq_music','value':'https://music.163.com/song?id=1868207326'}, {'cmd':'craft','wait':'actual BetterLogin LoginScreen automatically opened with no audio job'},
 {'cmd':'login-return','wait':'same Computer menu, full URL preserved, confirmation false'},
 {'cmd':'login'}, {'cmd':'login-success-fixture','wait':'original LoginSuccessScreen; NOT actual platform authentication'}, {'cmd':'login-confirm','wait':'same Computer menu and full URL'},
 {'cmd':'permission','wait':'explicit QA click of confirmation; owned local file stores only boolean'}, {'cmd':'craft','wait':'actual output title/artist from Netease detail fixture'},
 {'cmd':'open-computer','wait':'confirmation remembered from explicit prior choice'}, {'cmd':'unconfirm','wait':'saved false'},
 {'cmd':'open-computer','wait':'false remembered'}, {'cmd':'configure','platform':'bilibili','value':'https://www.bilibili.com/video/BV1Q5411x7aE?p=2'}, {'cmd':'permission'}, {'cmd':'craft','wait':'Bili second-part title/author from fixture and actual CD'},
 {'cmd':'open-computer'}, {'cmd':'configure','platform':'qq_music','value':'12345'}, {'cmd':'craft','wait':'QQ names from fixture; confirmation remembered'},
 {'cmd':'open-computer'}, {'cmd':'pause-focus-true'}, {'cmd':'pick','manual':'owned native x close, same menu/no job'}, {'cmd':'pick','manual':'select own CC0 WAV through actual Windows picker; filename/unknown-author fallback'},
 {'cmd':'open-burner'}, {'cmd':'pick','manual':'original CDBurner native x close'},
 {'cmd':'pause-focus-false'}, {'cmd':'finish','wait':'normal game/server0 exits, own port closed/world lock released'}]
plan={'preparedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'reservedInputWindowMinutes':15,'mcStarted':False,'ossRequests':0,'actualPlatformCalls':0,'productionLoginVerified':False,'localAccountPlatformAndReceiptFixtures':True,'currentBudgetUnchanged':'63/64','sourceRoot':str(ROOT),'productSha256':product['sha256'],'qaSha256':qa['sha256'],'requiresParentWindowGrant':True,'startCommandTemplate':command,'steps':steps,'ownedAudioSource':str(fixture),'ownedAudioSharedDestination':'<INSTANCE_ROOT>/entry042-owned-cc0.wav','markerGuard':'existing local-mc-owner.json runId/inputArtifact product hash/host or server user.dir','runtimeSourceCopies':False,'productionCandidateChanged':False,'noTaskRegistration':True,'notes':['Copy only the self-generated fixture audio to the new shared root after the original runner creates its ownership marker; never copy source/config/account data.','Run through the already coordinated Session2 DesktopQA entry; do not register or change task identity/actions.','File commands invoke actual widgets but are not physical mouse clicks; record native picker OS actions separately.','Fixture HTTP success is not real upload, platform account, backend audio decoding or cryptographic receipt acceptance.','A timeout/forced kill is failure, never normal-exit evidence.']}
out=args.output.resolve();out.mkdir(parents=True,exist_ok=True);(out/'prepared-run.json').write_text(json.dumps(plan,indent=2),encoding='utf-8');print(json.dumps({k:plan[k] for k in ['reservedInputWindowMinutes','sourceRoot','productSha256','qaSha256','mcStarted','ossRequests','currentBudgetUnchanged','requiresParentWindowGrant','runtimeSourceCopies']},indent=2))
