"""Run the registered music backend modules with explicit offline inputs."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

MODULES = [
    'tests.test_music_publication',
    'tests.test_music_upload',
    'tests.test_music_production',
    'tests.test_music_oss_transport',
    'tests.test_music_read_deadline',
    'tests.test_music_protocol',
    'tests.test_music_chain',
    'tests.test_music_release_policy',
    'tests.test_music_m4a',
    'tests.test_music_lyrics',
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps-dir', type=Path, action='append', required=True,
                        help='Existing offline dependency directory; repeat in import order.')
    parser.add_argument('--ffmpeg', type=Path, required=True,
                        help='Reviewed fixed FFmpeg executable used by every codec check.')
    parser.add_argument('--output-dir', type=Path, required=True,
                        help='Output outside source, or inside the ignored .local-validation directory.')
    args = parser.parse_args()
    netmusic = Path(__file__).resolve().parents[3]
    root = netmusic.parent / 'muxi-auth'
    if not (root / 'app/music_upload.py').is_file() or not (root / 'tests/test_music_publication.py').is_file():
        parser.error('sibling muxi-auth backend source is required')
    dependency_dirs = [path.resolve() for path in args.deps_dir]
    if any(not path.is_dir() for path in dependency_dirs):
        parser.error('every --deps-dir must be an existing directory')
    ffmpeg = args.ffmpeg.resolve()
    if not ffmpeg.is_file():
        parser.error('--ffmpeg must be an existing reviewed executable')
    output = args.output_dir.resolve()
    for repository in (root.resolve(), netmusic.resolve()):
        owned = (repository / '.local-validation').resolve()
        if (output == repository or repository in output.parents) and owned not in output.parents:
            parser.error('--output-dir inside either repository must be a child of .local-validation')
        if repository not in owned.parents:
            parser.error('.local-validation must not resolve outside the repository')
    output.mkdir(parents=True, exist_ok=True)
    paths = [*dependency_dirs, root / 'tests', root]
    env = dict(os.environ)
    env['PYTHONPATH'] = os.pathsep.join(map(str, paths))
    env['PYTHONDONTWRITEBYTECODE'] = '1'
    env['MUXI_MUSIC_QA_FFMPEG'] = str(ffmpeg)
    # Real M4A tests use imageio_ffmpeg; bind its resolver to the same executable.
    env['IMAGEIO_FFMPEG_EXE'] = str(ffmpeg)
    run = subprocess.run(
        [sys.executable, '-B', '-m', 'unittest', '-v', *MODULES],
        cwd=root, env=env, capture_output=True, text=True,
        encoding='utf-8', errors='replace', timeout=240,
    )
    combined = run.stdout + run.stderr
    log = output / 'backend-regression.log'
    log.write_text(combined, encoding='utf-8', newline='\n')
    count = re.search(r'Ran ([0-9]+) tests in ([0-9.]+)s', combined)
    skipped = re.search(r'\bskipped=([0-9]+)', combined)
    checks = int(count[1]) if count else None
    skipped_count = int(skipped[1]) if skipped else 0
    verified = run.returncode == 0 and checks is not None and checks >= 104 and skipped_count == 0
    receipt = {
        'exitCode': run.returncode, 'modules': MODULES, 'log': str(log),
        'checks': checks, 'skipped': skipped_count, 'verified': verified,
        'elapsedSeconds': float(count[2]) if count else None,
        'minimumChecks': 104, 'backendSourceRoot': str(root), 'ffmpeg': str(ffmpeg),
        'ffmpegSha256': hashlib.sha256(ffmpeg.read_bytes()).hexdigest(),
        'sourceSha256': {str(path.relative_to(root)).replace('\\', '/'):
            hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted([*root.glob('app/music_*.py'),
                *(root / 'tests' / (name.split('.')[-1] + '.py') for name in MODULES)])},
        'minecraftStarted': False, 'realOssCalls': 0, 'realAccountRead': False,
        'productionDeployed': False,
    }
    (output / 'backend-result.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt))
    return 0 if verified else (run.returncode or 1)


if __name__ == '__main__':
    sys.exit(main())

