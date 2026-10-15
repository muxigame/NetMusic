"""Current native fixture boundary; historical pins remain unchanged elsewhere."""
from pathlib import Path
import hashlib, json, re
WORKSPACE = Path(__file__).resolve().parents[3]
RUNS = WORKSPACE / 'test-environment/runs'

def runs_root(path):
    """One canonical run directly beneath legacy runs or a numbered client slot."""
    lexical = Path(path).absolute()
    path = lexical.resolve()
    if lexical != path:
        raise ValueError('run aliases and traversal are not allowed')
    environment = RUNS.parent.absolute()
    if environment.resolve() != environment:
        raise ValueError('environment alias is not allowed')
    try:
        parts = path.relative_to(environment).parts
    except ValueError:
        raise ValueError('outside unified environment') from None
    valid = len(parts) == 2 and parts[0] == 'runs'
    valid |= len(parts) == 3 and re.fullmatch(r'client-[1-9][0-9]*', parts[0]) is not None and parts[1] == 'runs'
    if not valid or not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', parts[-1]):
        raise ValueError('canonical direct run path required')
    return path.parent

def run_path(path):
    runs_root(path)
    return Path(path).resolve()

def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def child(path, base):
    path, base = Path(path).resolve(), Path(base).absolute()
    if base.resolve() != base or base not in path.parents:
        raise ValueError('canonical boundary required: ' + str(base))
    return path

def digest(value):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{64}', value):
        raise ValueError('explicit SHA-256 required')
    return value

def verify(root, expected_product, expected_qa, expected_run=None):
    root = run_path(root)
    owner = json.loads((root/'local-mc-owner.json').read_text(encoding='utf-8'))
    scope = json.loads((root/'entry042-current-scope.json').read_text(encoding='utf-8'))
    if owner.get('hidden') is not True or owner.get('offlineLoopback') is not True or owner.get('productionMutation') is not False:
        raise ValueError('offline owner required')
    run = owner.get('runId', '')
    if not re.fullmatch('[0-9a-f]{32}', run) or expected_run is not None and run != expected_run:
        raise ValueError('runId mismatch')
    if Path(owner['instanceRoot']).resolve() != root or owner['clientNames'] != {'host':'10000','guest':'10001'}:
        raise ValueError('two-client owner mismatch')
    expected = {'schema':1, 'instanceRoot':str(root), 'runsRoot':str(runs_root(root)), 'runId':run,
                'productSha256':digest(expected_product), 'qaSha256':digest(expected_qa)}
    if scope != expected:
        raise ValueError('current scope mismatch')
    pins = {row['sha256'] for row in owner['inputArtifacts']}
    if not {expected_product, expected_qa} <= pins:
        raise ValueError('owner artifact mismatch')
    return owner
