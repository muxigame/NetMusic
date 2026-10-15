"""Build a hash-bound native QA JAR; historical compile probe is not overwritten."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, sys, zipfile, tomllib
from current_scope import WORKSPACE, sha, digest
ROOT=Path(__file__).resolve().parent

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--product-build',type=Path,required=True)
    ap.add_argument('--expected-product-sha256',required=True)
    ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--jdk',type=Path,default=WORKSPACE/'test-environment/toolchains/java-21')
    a=ap.parse_args();expected=digest(a.expected_product_sha256)
    product=json.loads((a.product_build/'artifact.json').read_text(encoding='utf-8'))
    jar=Path(product['artifact']).resolve();out=a.output.resolve();jdk=a.jdk.resolve()
    if out.exists():raise ValueError('new output required')
    if sha(jar)!=expected or product['sha256']!=expected or jar.stat().st_size!=product['bytes']:raise ValueError('product pin mismatch')
    module=ROOT.parents[1]
    if Path(product['sourceRoot']).resolve()!=module:raise ValueError('registered source required')
    actual={p.relative_to(module).as_posix():sha(p) for folder in ('src/main/java','src/test/java') for p in (module/folder).rglob('*.java')}
    if actual!={p:h for p,h in product['sourceSha256'].items() if p.endswith('.java')}:raise ValueError('product Java source changed')
    if any(sha(module/p)!=h for p,h in product['sourceSha256'].items()):raise ValueError('product source/resource changed')
    for p,h in {**product['binaryDependencySha256'],**product.get('compileOnlyDependencySha256',{})}.items():
        if sha(Path(p))!=h:raise ValueError('dependency changed')
    with zipfile.ZipFile(jar) as z:
        mods=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods']
        if not any(m['modId']=='netmusic' and m['version']==product['version'] for m in mods):raise ValueError('metadata mismatch')
    lines=(a.product_build/'compile.args').read_text(encoding='utf-8').splitlines()
    cp=str(jar)+os.pathsep+lines[lines.index('"-classpath"')+1].strip('"').replace('\\\\','\\')
    sys.path.insert(0,str(WORKSPACE/'better-mc-remake/scripts'));import local_mc_runtime as rt
    classes=out/'classes';classes.mkdir(parents=True)
    sources=sorted(ROOT.rglob('*.java'));pins={p.relative_to(ROOT).as_posix():sha(p) for p in sources}
    rt.argfile(out/'compile.args',['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',classes,*sources])
    for label,command in [('compile',[str(jdk/'bin/javac.exe'),'@'+str(out/'compile.args')]),('fixture-tests',[str(jdk/'bin/java.exe'),'-cp',str(classes)+os.pathsep+cp,'net.muxigame.musiccompat.qa.entry042.Entry042FixtureTest'])]:
        run=subprocess.run(command,cwd=out,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=120)
        (out/(label+'.log')).write_text(run.stdout+run.stderr,encoding='utf-8')
        if run.returncode:raise RuntimeError(label+': '+run.stderr[-3000:])
    # Same reviewed mixin set as the historical candidate helper, without importing its CLI.
    import ast
    tree=ast.parse((ROOT/'build_fixture_candidate.py').read_text(encoding='utf-8-sig'))
    mixins=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='mixins' for t in n.targets))
    mixins['client'].append('Entry042ConstructedSoundMixin')
    metadata='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_music_entry042_qa"\nversion="0.2.0-current-native"\ndisplayName="Owned music native QA"\n[[mixins]]\nconfig="muxi_entry042_qa.mixins.json"\n[[dependencies.muxi_music_entry042_qa]]\nmodId="netmusic"\ntype="required"\nversionRange="['+product['version']+']"\nordering="AFTER"\nside="BOTH"\n'
    helper=out/'muxi-music-entry042-current-native-qa.jar'
    with zipfile.ZipFile(helper,'w',zipfile.ZIP_DEFLATED) as z:
        def put(name,data):
            info=zipfile.ZipInfo(name,(2026,10,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
        put('META-INF/neoforge.mods.toml',metadata);put('muxi_entry042_qa.mixins.json',json.dumps(mixins))
        put('entry042-current.properties','scope=unified-current-v1\nproductSha256='+expected+'\n')
        for p in sorted(classes.rglob('*.class')):
            if not p.stem.startswith(('Entry042FixtureTest','Entry042BackendProtocolTest')):put(p.relative_to(classes).as_posix(),p.read_bytes())
    if any(sha(ROOT/p)!=h for p,h in pins.items()):raise ValueError('QA changed while building')
    result={'jar':str(helper),'sha256':sha(helper),'productSha256':expected,'productVersion':product['version'],'sourceSha256':pins,'nativeReady':True,'compileOnly':False,'runtimeMixinVerified':False,'mcStarted':False,'realAccounts':False,'ossRequests':0}
    (out/'receipt.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
if __name__=='__main__':main()
