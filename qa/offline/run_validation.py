"""Run current fork driver predicates and sibling Auth offline regression; no Minecraft."""
from pathlib import Path
import argparse
import json
import subprocess
import sys
import sysconfig

ROOT = Path(__file__).resolve().parents[2]
QA = Path(__file__).resolve().parent

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output-dir', type=Path, required=True)
    p.add_argument('--ffmpeg', type=Path, required=True)
    p.add_argument('--deps-dir', type=Path, action='append')
    args = p.parse_args()
    output = args.output_dir.resolve()
    owned = (ROOT / '.local-validation').resolve()
    if ROOT not in owned.parents or owned not in output.parents or output.exists():
        p.error('--output-dir must be a new child of netmusic/.local-validation')
    if not args.ffmpeg.is_file():
        p.error('--ffmpeg must be an existing reviewed executable')
    output.mkdir(parents=True)
    records = []
    def run(name, argv):
        command = [sys.executable, '-B', *map(str, argv)]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=300)
        log = output / (name + '.log')
        log.write_text(result.stdout + result.stderr, encoding='utf-8')
        records.append({'name': name, 'exitCode': result.returncode, 'command': command,
                        'log': log.name})
        (output / 'commands.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
        if result.returncode:
            raise RuntimeError(f'{name} failed: {log}')
    for name, script in [('burn', 'burn/driver_test.py'), ('playback', 'playback/short_driver_test.py'),
                         ('lifecycle', 'lifecycle/test_lifecycle_predicates.py')]:
        run(name, [QA / script])
    backend = [QA / 'backend/run_tests.py', '--ffmpeg', args.ffmpeg.resolve(),
               '--output-dir', output / 'backend']
    for deps in args.deps_dir or [Path(sysconfig.get_paths()['purelib'])]:
        backend += ['--deps-dir', deps.resolve()]
    run('backend', backend)
    receipt = json.loads((output / 'backend/backend-result.json').read_text(encoding='utf-8'))
    if not receipt['verified']:
        raise RuntimeError('backend evidence incomplete')
    report = {'driverAndBackendVerified': True, 'backendChecks': receipt['checks'],
              'sourceRoot': str(ROOT), 'backendSourceRoot': receipt['backendSourceRoot'],
              'commands': records, 'minecraftStarted': False, 'nativeAccepted': False,
              'productBuildVerified': False, 'historicalCodecRun': False,
              'productionDeployed': False}
    (output / 'result.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
