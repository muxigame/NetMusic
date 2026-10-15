
from pathlib import Path
import argparse, hashlib, json, os, subprocess

SOURCE = Path(__file__).resolve().parent
NET_PIN = '731fba4b4d47256d5f44f8392b093b53c7d1c650b66111eda75b820f7f89a14b'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def inputs(description):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('--product-build', type=Path, required=True,
                        help='Existing product build containing compile.args and classes/')
    parser.add_argument('--output', type=Path, required=True,
                        help='Owned artifact directory; all generated files stay here')
    parser.add_argument('--ffmpeg', type=Path, required=True,
                        help='Existing offline ffmpeg executable; no download')
    parser.add_argument('--jdk', type=Path, required=True,
                        help='Existing JDK21 root containing bin/java and bin/javac')
    args = parser.parse_args()
    build, output = args.product_build.resolve(), args.output.resolve()
    ffmpeg, jdk = args.ffmpeg.resolve(), args.jdk.resolve()
    if output == SOURCE or SOURCE in output.parents:
        parser.error('--output must be outside the canonical codec source directory')
    if output == build or build in output.parents:
        parser.error('--output must be separate from the input product build')
    cpfile = build / 'compile.args'
    if not cpfile.is_file() or not ffmpeg.is_file():
        parser.error('existing compile.args and ffmpeg executable are required')
    suffix = '.exe' if os.name == 'nt' else ''
    java, javac = jdk / ('bin/java' + suffix), jdk / ('bin/javac' + suffix)
    if not java.is_file() or not javac.is_file():
        parser.error('JDK java/javac executables are required')
    lines = cpfile.read_text(encoding='utf8').splitlines()
    try:
        index = next(i for i, line in enumerate(lines)
                     if line.strip().strip('"') in ('-classpath', '--class-path', '-cp'))
        cp = lines[index + 1].strip().strip('"')
    except (StopIteration, IndexError):
        parser.error('product compile.args has no classpath')
    paths = [Path(p) for p in cp.split(os.pathsep)]
    nets = list(dict.fromkeys(p.resolve() for p in paths
                            if 'netmusic-1.5.2' in p.name and p.is_file()))
    if len(nets) != 1 or sha(nets[0]) != NET_PIN:
        parser.error('exact pinned NetMusic1.5.2 dependency is required')
    net = nets[0]
    module_cp = os.pathsep.join(str(p) for p in paths if p.resolve() != net)
    return build, output, ffmpeg, java, javac, cpfile, cp, net, module_cp

def argfile(path, values):
    text = []
    for value in values:
        value = str(value).replace('\\', '/')
        if any(c in value for c in ('"', '\r', '\n', '\0')):
            raise ValueError('unsafe_javac_argument')
        text.append('"' + value + '"')
    path.write_text('\n'.join(text), encoding='utf8')

def reported(run):
    payload = next((line for line in reversed(run.stdout.splitlines())
                    if line.startswith('{')), None)
    if payload is None:
        raise RuntimeError('probe_did_not_report_json')
    return json.loads(payload)

def save_run(output, name, run):
    (output / (name + '.log')).write_text(run.stdout + run.stderr, encoding='utf8')

def main():
    build, output, ffmpeg, java, javac, cpfile, cp, net, module_cp = inputs(
        'Read existing owned fixtures through actual compiled PrivatePcmStream; no MC.')
    product = build / 'classes'
    actual = product / 'net/muxigame/musiccompat/burn/PrivatePcmStream.class'
    if not actual.is_file():
        raise ValueError('actual_product_class_required')
    baseline_file = output / 'netmusic-automatic-module-control.json'
    baseline = json.loads(baseline_file.read_text(encoding='utf8'))
    if baseline.get('netMusicJarSha256') != NET_PIN or baseline.get('ffmpegSha256') != sha(ffmpeg):
        raise ValueError('matching_fixed_decoder_baseline_required')
    fixtures = [output / 'self-authored-0.5s.wav', output / 'self-authored-0.5s.mp3']
    for fixture in fixtures:
        if sha(fixture) != baseline['fixtures'][fixture.name]['sha256']:
            raise ValueError('owned_fixture_baseline_mismatch')
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    compile_args = output / 'actual-private-compile.args'
    argfile(compile_args, ['--release', '21', '-encoding', 'UTF-8', '-proc:none',
                          '-classpath', str(product) + os.pathsep + cp, '-d', classes,
                          SOURCE / 'ActualPrivatePcmProbe.java'])
    compile_run = subprocess.run([str(javac), '@' + str(compile_args)], cwd=output,
                                 capture_output=True, text=True, encoding='utf8',
                                 errors='replace', timeout=25)
    save_run(output, 'actual-private-compile', compile_run)
    if compile_run.returncode:
        raise RuntimeError('actual_probe_compile_failed')
    run = subprocess.run(
        [str(java), '-Djava.awt.headless=true', '--module-path', str(net),
         '--add-modules', 'netmusic', '-cp',
         str(product) + os.pathsep + str(classes) + os.pathsep + module_cp,
         'ActualPrivatePcmProbe', *map(str, fixtures)],
        cwd=output, capture_output=True, text=True, encoding='utf8',
        errors='replace', timeout=25)
    save_run(output, 'actual-private-decode', run)
    result = reported(run)
    if result['actualPrivatePcmClassSha256'] != sha(actual):
        raise ValueError('loaded_product_class_mismatch')
    if result['success']:
        for case in result['cases']:
            original = next(row for row in baseline['results']
                            if row['file'] == case['file']
                            and row['targetChannels'] == case['channels']
                            and row['entry'] == 'AudioStreamHandlerManager.handle(fileURL)')
            if case['frames'] != original['frames'] or case['pcmSha256'] != original['decodedSha256']:
                raise ValueError('product_changed_decoded_pcm_bytes')
        result['actualProductPcmMatchesOriginalDecoderBytes'] = True
    result.update(javaExitCode=run.returncode, netMusicJarSha256=NET_PIN,
                  ffmpegSha256=sha(ffmpeg), fixtureLicence='CC0-1.0; self-authored synthetic sine waves',
                  fixtureSeconds=0.5, temporaryProbe=True, registeredTestSuite=False,
                  productBuild=str(build), productClasspathSha256=sha(cpfile),
                  probeSourceSha256=sha(SOURCE / 'ActualPrivatePcmProbe.java'),
                  productSourceModifiedByThisProbe=False)
    (output / 'actual-private-receipt.json').write_text(json.dumps(result, indent=2), encoding='utf8')
    print(json.dumps(result, indent=2))
    return run.returncode

if __name__ == '__main__':
    raise SystemExit(main())
