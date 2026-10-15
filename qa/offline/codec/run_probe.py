
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
    import math, struct, wave, zipfile
    build, output, ffmpeg, java, javac, cpfile, cp, net, module_cp = inputs(
        'Temporary offline fixed-JAR MP3/WAV SPI proof; never starts Minecraft.')
    output.mkdir(parents=True, exist_ok=True)
    wav, mp3 = output / 'self-authored-0.5s.wav', output / 'self-authored-0.5s.mp3'
    rate, frames = 44100, 22050
    pcm = b''.join(struct.pack('<hh', int(8000 * math.sin(2 * math.pi * 440 * n / rate)),
                              int(6000 * math.sin(2 * math.pi * 660 * n / rate)))
                   for n in range(frames))
    with wave.open(str(wav), 'wb') as stream:
        stream.setparams((2, 2, rate, frames, 'NONE', 'not compressed'))
        stream.writeframes(pcm)
    encode = subprocess.run(
        [str(ffmpeg), '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
         '-protocol_whitelist', 'file,pipe', '-i', str(wav), '-map', '0:a:0',
         '-map_metadata', '-1', '-c:a', 'libmp3lame', '-b:a', '128k',
         '-t', '0.5', '-f', 'mp3', str(mp3)],
        cwd=output, capture_output=True, text=True, encoding='utf8',
        errors='replace', timeout=20)
    save_run(output, 'fixture-encode', encode)
    if encode.returncode:
        raise RuntimeError('fixture_encode_failed')
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    compile_args = output / 'probe-compile.args'
    argfile(compile_args, ['--release', '21', '-encoding', 'UTF-8', '-proc:none',
                          '-classpath', cp, '-d', classes, SOURCE / 'FixedMp3SpiProbe.java'])
    compile_run = subprocess.run([str(javac), '@' + str(compile_args)], cwd=output,
                                 capture_output=True, text=True, encoding='utf8',
                                 errors='replace', timeout=30)
    save_run(output, 'compile', compile_run)
    if compile_run.returncode:
        raise RuntimeError('probe_compile_failed')
    tail = ['FixedMp3SpiProbe', str(wav), str(mp3)]
    def run_mode(name, flags, classpath):
        run = subprocess.run([str(java), '-Djava.awt.headless=true', *flags,
                              '-cp', str(classes) + os.pathsep + classpath, *tail],
                             cwd=output, capture_output=True, text=True,
                             encoding='utf8', errors='replace', timeout=25)
        save_run(output, name, run)
        result = reported(run)
        result['javaExitCode'] = run.returncode
        (output / (name + '.json')).write_text(json.dumps(result, indent=2), encoding='utf8')
        return run, result
    ordinary, result = run_mode('ordinary-classpath', [], cp)
    run_mode('classpath-without-netmusic-control', [], module_cp)
    module, baseline = run_mode('netmusic-automatic-module-control',
                                ['--module-path', str(net), '--add-modules', 'netmusic'],
                                module_cp)
    fixtures = {p.name: {'bytes': p.stat().st_size, 'sha256': sha(p)} for p in (wav, mp3)}
    baseline.update(netMusicJarSha256=NET_PIN, ffmpegSha256=sha(ffmpeg), fixtures=fixtures)
    (output / 'netmusic-automatic-module-control.json').write_text(
        json.dumps(baseline, indent=2), encoding='utf8')
    result.update(fixtureLicence='CC0-1.0; self-authored synthetic sine waves',
                  fixtureSeconds=0.5, fixtureFrames=frames, fixtureSampleRate=rate,
                  fixtures=fixtures, netMusicJarSha256=NET_PIN,
                  ffmpegSha256=sha(ffmpeg), productClasspathSha256=sha(cpfile),
                  probeSourceSha256=sha(SOURCE / 'FixedMp3SpiProbe.java'),
                  productBuild=str(build), productionSourceModified=False,
                  registeredTestSuite=False, automaticModuleJavaExitCode=module.returncode,
                  automaticModuleDecodedActualPcm=baseline.get('spiDecodedToActualPcm', False))
    with zipfile.ZipFile(net) as archive:
        result['spiRegistrations'] = {
            name: archive.read(name).decode()
            for name in ['META-INF/services/javax.sound.sampled.spi.AudioFileReader',
                         'META-INF/services/javax.sound.sampled.spi.FormatConversionProvider']}
    (output / 'receipt.json').write_text(json.dumps(result, indent=2), encoding='utf8')
    licence = json.loads((SOURCE / 'LICENSE.json').read_text(encoding='utf8'))
    licence['fixtures'] = fixtures
    (output / 'fixture-license.json').write_text(json.dumps(licence, indent=2), encoding='utf8')
    summary = {
        'mp3ActualSpiDecodedToPcm': result.get('mp3SpiDecodedToActualPcm', False),
        'wavAndMp3ActualAutomaticModuleDecodedToPcm': result['automaticModuleDecodedActualPcm'],
        'ordinaryClasspathJavaExitCode': ordinary.returncode,
        'automaticModuleJavaExitCode': module.returncode,
        'originalJarUnmodified': True, 'boundedZeroRetryUsed': True,
        'fixtureLicence': result['fixtureLicence'], 'fixtureSeconds': 0.5,
        'fixtures': fixtures, 'netMusicJarSha256': NET_PIN,
        'mcStarted': False, 'openAlAccepted': False, 'networkCalls': 0,
        'productionSourceModified': False, 'registeredTestSuite': False}
    (output / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf8')
    print(json.dumps(summary, indent=2))
    return module.returncode

if __name__ == '__main__':
    raise SystemExit(main())
