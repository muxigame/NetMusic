"""Run existing native assertions after explicit current product/QA/run pin validation."""
from pathlib import Path
import argparse, importlib, json, sys
from current_scope import verify, sha

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--inputs',type=Path,required=True)
    ap.add_argument('--expected-product-sha256',required=True);ap.add_argument('--expected-qa-sha256',required=True);ap.add_argument('--run-id',required=True)
    ap.add_argument('--suite',choices=['matrix','burn','lyrics','lifecycle','network','legacy','playback'],required=True)
    ap.add_argument('extra',nargs=argparse.REMAINDER);a=ap.parse_args()
    verify(a.instance_root,a.expected_product_sha256,a.expected_qa_sha256,a.run_id)
    lock=json.loads(a.inputs.read_text(encoding='utf-8'))
    if Path(lock['instanceRoot']).resolve()!=a.instance_root.resolve():raise ValueError('input instance mismatch')
    if lock.get('scope')!='unified-current-v1' or lock['candidateSha256']!=a.expected_product_sha256 or lock['qaSha256']!=a.expected_qa_sha256:raise ValueError('input scope mismatch')
    for p,h in lock['sourceSha256'].items():
        if sha(Path(p))!=h:raise ValueError('source changed since preflight: '+p)
    for row in lock['mods']:
        if sha(Path(row['path']))!=row['sha256']:raise ValueError('input JAR changed')
    names={'matrix':'run_matrix1438','burn':'run_burn10','lyrics':'run_lyrics4','lifecycle':'run_lifecycle10','network':'run_network9','legacy':'run_legacy_current','playback':'run_playback_current'}
    # These modules share the same validated Driver; each historical module keeps
    # its default pin when invoked directly. This process installs one exact scope.
    driver=importlib.import_module('run_matrix1438');driver.PRODUCT=a.expected_product_sha256
    target=importlib.import_module(names[a.suite])
    for name,module in list(sys.modules.items()):
        if name.startswith('run_') and hasattr(module,'PRODUCT'):module.PRODUCT=a.expected_product_sha256
    extra=a.extra[1:] if a.extra[:1]==['--'] else a.extra
    if any(x.startswith(('--instance-root','--frozen-inputs','--prior-root')) for x in extra):raise ValueError('scope arguments cannot be overridden')
    sys.argv=[names[a.suite],'--instance-root',str(a.instance_root),'--frozen-inputs',str(a.inputs),*extra]
    target.main()
if __name__=='__main__':main()
