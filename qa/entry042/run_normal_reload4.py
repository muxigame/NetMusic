"""One bounded normal reload; no repeated long menus or known failed pause/rejoin."""
from pathlib import Path
import argparse,json,time,traceback
from run_matrix1438 import Driver,read
from run_lyrics4 import Dual
from run_supplement3 import ready,seed_reload
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--prior-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args();d=Driver(a.instance_root,a.frozen_inputs);dual=Dual(d);results=[]
 if not a.execute:print(json.dumps({'verifiedOnly':True,'mcStarted':False}));return
 def attempt(label,callback):
  try:callback();row={'case':label,'passed':True}
  except Exception as error:row={'case':label,'passed':False,'reason':str(error)};dual.record('FAILED-normal-reload',dual.pair(active=False) or [],scenario=label,reason=str(error),traceback=traceback.format_exc())
  results.append(row);(d.base/'entry042-normal-reload-results.json').write_text(json.dumps({'runId':d.run,'cases':results,'knownPauseAndRejoinFailuresNotRetested':True,'ossRequests':0},indent=2),encoding='utf-8');print(json.dumps(row),flush=True)
 try:
  ready(d,True);d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') and (read(d.base/'entry042-client-status-guest.json') or {}).get('fixtureCookiesInstalled'),'guest ready',600);seed_reload(d,a.prior_root,a.frozen_inputs)
  d.send('audio-enable-owned');dual.send('audio-enable-owned');d.send('pause-focus-false');dual.send('pause-focus-false');d.send('lyrics-on');dual.send('lyrics-on');d.send('audio-resume');dual.send('audio-resume')
  groups={}
  for case in dual.inventory():groups.setdefault(dual.origin(case['reference'])['platform'],[]).append(case)
  assert all(groups.get(p) for p in ['netease','qq','bilibili','none'])
  for platform in ['netease','qq','bilibili','none']:attempt('persisted-'+platform,lambda p=platform:dual.play(groups[p][0],'persisted-'+p,seconds=15))
  case=dual.rewrite(groups['netease'][0],'netease','https://music.163.com/song?id=1868207326','normal-reload-netease-original-translation');attempt('netease-original-with-translation',lambda:dual.play(case,'netease-original-with-translation'))
  case=dual.rewrite(groups['bilibili'][0],'bilibili','https://www.bilibili.com/video/BV1Q5411x7aE?p=2','normal-reload-bili-second-P');attempt('bili-second-P-original-with-translation',lambda:dual.play(case,'bili-second-P-original-with-translation',site=1))
 finally:
  if not (d.root/'run-result.json').exists():
   dual.send('finish');d.send('close-menu');d.send('audit-discs');d.send('stop-server-owned');d.wait(lambda:any(x.get('event')=='server-stopping-after-product-return' and x.get('cases') and all(v['total']==1 and v['inventory']==1 for v in x['cases']) for x in (read(d.base/'entry042-disc-lifecycle.json') or {}).get('history',[])),'normal reloaded stop preserves every original disc',30);d.record('inventory','none','normal-reload-final-stop',d.current_case())
 print(json.dumps({'normalReloadChecksFinished':True,'failedCases':sum(not x['passed'] for x in results)}),flush=True)
if __name__=='__main__':main()
