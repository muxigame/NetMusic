"""Source-only fork build; original artifact contributes ONLY third-party soundlibs."""
from pathlib import Path
import argparse, hashlib, json, os, re, struct, subprocess, sys, zipfile, io
ROOT=Path(__file__).resolve().parents[1]; WS=ROOT.parent
VERSION='1.5.2-muxi.1+mc1.21.1'
PREFIX='com/github/tartaricacid/netmusic/soundlibs/'
NAMES=('javazoom/jl','javazoom/spi','org/tritonus','org/jflac','net/sourceforge/jaad')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def constants(data, transform=lambda s:s):
    n=struct.unpack_from('>H',data,8)[0]; pos=10; result=bytearray(data[:10]); names={}; i=1
    sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while i<n:
        tag=data[pos]; pos+=1; result.append(tag)
        if tag==1:
            length=struct.unpack_from('>H',data,pos)[0]; pos+=2; raw=data[pos:pos+length]; pos+=length
            names[i]=raw; raw=transform(raw); result.extend(struct.pack('>H',len(raw))); result.extend(raw)
        else:
            size=sizes[tag]; result.extend(data[pos:pos+size]); pos+=size
            if tag in (5,6):i+=1
        i+=1
    return bytes(result)+data[pos:],pos,names

def relocate(data,reverse=False):
    def change(raw):
        for name in NAMES:
            for src,dst in [(name,PREFIX+name),(name.replace('/','.'),(PREFIX+name).replace('/','.'))]:
                if reverse: src,dst=dst,src
                raw=raw.replace(src.encode(),dst.encode())
        return raw
    return constants(data,change)[0]

def public_fields(data,wanted):
    _,pos,names=constants(data); b=bytearray(data); pos+=6
    count=struct.unpack_from('>H',b,pos)[0];pos+=2+2*count
    count=struct.unpack_from('>H',b,pos)[0];pos+=2; seen=set()
    for _ in range(count):
        flags,name,desc,attrs=struct.unpack_from('>HHHH',b,pos)
        if names[name].decode() in wanted:
            struct.pack_into('>H',b,pos,(flags&~6)|1);seen.add(names[name].decode())
        pos+=8
        for _ in range(attrs): length=struct.unpack_from('>I',b,pos+2)[0];pos+=6+length
    assert seen==set(wanted),(seen,wanted)
    return bytes(b)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--better-login-jar',type=Path,required=True);ap.add_argument('--core-jar',type=Path,required=True)
    ap.add_argument('--jdk',type=Path,default=WS/'test-environment/toolchains/java-21')
    ap.add_argument('--game-dir',type=Path,default=WS/'test-environment/resources/client/game')
    ap.add_argument('--server-dir',type=Path,default=WS/'bmc5server')
    ap.add_argument('--extra-compile-jar',type=Path,action='append',default=[])
    a=ap.parse_args()
    for key in ('output','better_login_jar','core_jar','jdk','game_dir','server_dir'):setattr(a,key,getattr(a,key).resolve())
    a.extra_compile_jar=[p.resolve() for p in a.extra_compile_jar]
    out=a.output;assert not out.exists(),'new output required'
    out.mkdir(parents=True);deps=out/'compile-dependencies';deps.mkdir();classes=out/'classes';classes.mkdir()
    sys.path.insert(0,str(WS/'better-mc-remake/scripts'));import local_mc_runtime as rt
    mods=a.game_dir/'mods';original=next(mods.glob('*netmusic-1.5.2*'))
    assert sha(original)=='731fba4b4d47256d5f44f8392b093b53c7d1c650b66111eda75b820f7f89a14b'
    playlist=next(mods.glob('*net_music_list-4.3.jar'));cloth=next(mods.glob('cloth-config*'))
    runtime={p.resolve():sha(p) for p in [playlist,cloth,a.better_login_jar,a.core_jar]}
    compiledeps={original.resolve():sha(original)}
    sound=deps/'soundlibs-unrelocated.jar'; sound_entries={};sound_licenses={}
    with zipfile.ZipFile(original) as z,zipfile.ZipFile(sound,'w') as target:
        for name in z.namelist():
            if name.startswith(PREFIX) and not name.endswith('/'):
                assert any(name.startswith(PREFIX+n+'/') for n in NAMES),name
                data=z.read(name);sound_entries[name]=data
                target.writestr(name[len(PREFIX):],relocate(data,True) if name.endswith('.class') else data)
            elif any(x in name.lower() for x in ('license','notice','copying')):sound_licenses[name]=z.read(name)
    assert len(sound_entries)>100
    meta=rt.client_metadata(a.game_dir,'BatterMC5Remake');libs=rt.client_libraries(a.game_dir,'BatterMC5Remake',meta)
    cp=[Path(p) for p in rt.javac_classpath(a.server_dir,a.game_dir,'21.1.250',libs).split(os.pathsep)]
    at=deps/'minecraft-access-transformer-compile-only.jar'
    targets={'net/minecraft/client/sounds/SoundManager.class':['soundEngine'],'net/minecraft/client/sounds/SoundEngine.class':['instanceToChannel','tickingSounds']}
    with zipfile.ZipFile(at,'w') as z:
        for name,fields in targets.items():
            for p in cp:
                with zipfile.ZipFile(p) as src:
                    if name in src.namelist():z.writestr(name,public_fields(src.read(name),fields));break
            else:raise RuntimeError('MC class missing '+name)
    selected=[]
    for p in mods.glob('*.jar'):
        if any(x in p.name.lower() for x in ('touhoulittlemaid-','sophisticatedcore-','sophisticatedbackpacks-','sable-','create-','inventoryprofiles','inventory-profiles')):selected.append(p)
    selected+=a.extra_compile_jar
    for name,expected in {'annotations-24.1.0.jar':'27a770dc7ce50500918bb8c3c0660c98290630ec796b5e3cf6b90f403b3033c6','InventoryProfilesNext-neoforge-1.21-2.0.4.jar':'8dc1dfa4184ff46ae08b076345075a91217d31ce9ddffc04b73b7a4161695925'}.items():
        p=WS/'test-environment/cache/netmusic-build-dependencies'/name
        assert sha(p)==expected,'pinned compile dependency changed: '+name
        selected.append(p)
    def addjar(p):
        p=p.resolve();compiledeps[p]=sha(p);cp.append(p)
        with zipfile.ZipFile(p) as z:
            for name in z.namelist():
                if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    data=z.read(name);dest=deps/(hashlib.sha256(data).hexdigest()[:16]+'-'+Path(name).name)
                    if not dest.exists():dest.write_bytes(data);addjar(dest)
    for p in selected:addjar(p)
    loader=a.server_dir/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-universal.jar'
    with zipfile.ZipFile(loader) as z:
        extra=deps/'mixinextras.jar';extra.write_bytes(z.read('META-INF/jarjar/mixinextras-neoforge-0.5.3.jar'));addjar(extra)
    cp=[at,sound,*runtime,*cp];classpath=os.pathsep.join(map(str,cp))
    sources=sorted((ROOT/'src/main/java').rglob('*.java'))+sorted((ROOT/'src/test/java').rglob('*.java'))
    resource_files=[p for folder in ('src/main/resources','src/generated/resources') for p in (ROOT/folder).rglob('*') if p.is_file()]+[p for p in ROOT.iterdir() if p.is_file() and (p.name.startswith('LICENSE') or p.name.startswith('NOTICE'))]+[ROOT/'gradle.properties',ROOT/'build.gradle']
    resources={p.relative_to(ROOT).as_posix():sha(p) for p in resource_files}
    tracked=sources+resource_files+[Path(__file__)]
    hashes={p.relative_to(ROOT).as_posix():sha(p) for p in tracked if p.is_file()}
    rt.argfile(out/'compile.args',['--release','21','-encoding','UTF-8','-proc:none','-classpath',classpath,'-d',classes,*sources])
    def run(cmd,log):
        r=subprocess.run(cmd,cwd=out,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180);(out/log).write_text(r.stdout+r.stderr,encoding='utf-8')
        if r.returncode:raise RuntimeError(str(out/log)+'\n'+r.stderr[:12000])
        return r.stdout
    run([str(a.jdk/'bin/javac.exe'),'-J-Duser.language=en','-J-Duser.country=US','@'+str(out/'compile.args')],'compile.log')
    tests={}
    for p in sorted((ROOT/'src/test/java').rglob('*Test.java')):
        package=re.search(r'package\s+([\w.]+)',p.read_text(encoding='utf-8')).group(1)
        stdout=run([str(a.jdk/'bin/java.exe'),'-cp',str(classes)+os.pathsep+classpath,package+'.'+p.stem],p.stem+'.log')
        tests[p.stem]=json.loads(next(x for x in reversed(stdout.splitlines()) if x.startswith('{')))
    assert len(tests)==21, len(tests)
    props=dict(line.split('=',1) for line in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
    entries=dict(sound_entries)
    for p in [p for folder in ('src/main/resources','src/generated/resources') for p in (ROOT/folder).rglob('*')]:
        if p.is_file():
            data=p.read_bytes();name=p.relative_to(ROOT/('src/main/resources' if p.is_relative_to(ROOT/'src/main/resources') else 'src/generated/resources')).as_posix()
            if name=='META-INF/neoforge.mods.toml':data=re.sub(r'\$\{([^}]+)\}',lambda m:props[m[1]],data.decode()).encode()
            entries[name]=data
    for p in classes.rglob('*.class'):
        if not any(p.stem==n or p.stem.startswith(n+'$') for n in tests):entries[p.relative_to(classes).as_posix()]=relocate(p.read_bytes())
    for p in ROOT.glob('*'):
        if p.is_file() and (p.name.startswith('LICENSE') or p.name.startswith('NOTICE')):entries[p.name]=p.read_bytes()
    for name,data in sound_licenses.items():entries['META-INF/third-party/original-netmusic/'+name]=data
    assert b'modId="muxi_music_compat"' not in entries['META-INF/neoforge.mods.toml']
    jar=out/f'netmusic-{VERSION}.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
    assert all(sha(ROOT/name)==digest for name,digest in hashes.items()),'source_changed_during_build'
    record={'artifact':str(jar),'version':VERSION,'sha256':sha(jar),'bytes':jar.stat().st_size,'sourceRoot':str(ROOT),'sourceSha256':hashes,'resourceSha256':resources,'soundlibAllowlist':[PREFIX+n+'/' for n in NAMES],'soundlibEntrySha256':{n:hashlib.sha256(b).hexdigest() for n,b in sound_entries.items()},'compiledSources':len(sources),'tests':tests,'binaryDependencySha256':{str(p):h for p,h in runtime.items()},'runtimeDependencySha256':{str(p):h for p,h in runtime.items()},'compileOnlyDependencySha256':{str(p):h for p,h in compiledeps.items()},'originalBusinessClassesCopied':0,'thirdPartySoundlibEntries':len(sound_entries),'nativeMcAccepted':False,'computerNativeAccepted':False,'productionDeployed':False}
    (out/'artifact.json').write_text(json.dumps(record,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in record.items() if k in ('artifact','sha256','compiledSources','thirdPartySoundlibEntries')}))
if __name__=='__main__':main()
