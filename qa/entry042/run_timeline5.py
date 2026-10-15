"""Reuse the ten original cases in a separately frozen .5 lab; never starts MC."""
from pathlib import Path
import argparse,json,traceback
from run_matrix1438 import Driver,read
from run_lyrics4 import Dual
from run_supplement3 import ready,seed_reload
from run_supplement4 import pause_or_rejoin
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--instance-root',type=Path,required=True);ap.add_argument('--frozen-inputs',type=Path,required=True);ap.add_argument('--prior-root',type=Path,required=True);ap.add_argument('--execute',action='store_true');a=ap.parse_args();d=Driver(a.instance_root,a.frozen_inputs);dual=Dual(d);results=[]
 if not a.execute:print(json.dumps({'verifiedOnly':True,'mcStarted':False}));return
 def attempt(label,callback):
  try:callback();row={'case':label,'passed':True}
  except Exception as error:row={'case':label,'passed':False,'reason':str(error)};dual.record('FAILED-timeline5',dual.pair(active=False) or [],scenario=label,reason=str(error),traceback=traceback.format_exc())
  results.append(row);(d.base/'entry042-timeline-results.json').write_text(json.dumps({'runId':d.run,'cases':results,'ossRequests':0},indent=2),encoding='utf-8');print(json.dumps(row),flush=True)
 try:
  ready(d,True);d.wait(lambda:(read(d.base/'status-guest.json') or {}).get('connected') and (read(d.base/'entry042-client-status-guest.json') or {}).get('fixtureCookiesInstalled'),'guest ready',600);seed_reload(d,a.prior_root,a.frozen_inputs)
  d.send('audio-enable-owned');dual.send('audio-enable-owned');d.send('pause-focus-false');dual.send('pause-focus-false');d.send('lyrics-on');dual.send('lyrics-on');d.send('audio-resume');dual.send('audio-resume')
  groups={}
  for case in dual.inventory():groups.setdefault(dual.origin(case['reference'])['platform'],[]).append(case)
  assert all(groups.get(p) for p in ['netease','qq','bilibili','none'])
  # Same writable original discs, actual product rewrite and isolated backend; no extra CDs.
  groups['netease'][0]=dual.rewrite(groups['netease'][0],'netease','https://music.163.com/song?id=1868207326','candidate5-netease-original-translation')
  groups['bilibili'][0]=dual.rewrite(groups['bilibili'][0],'bilibili','https://www.bilibili.com/video/BV1Q5411x7aE?p=2','candidate5-bili-second-P')
  for p in ['netease','qq','bilibili','none']:attempt(p,lambda p=p:dual.play(groups[p][0],p))
  for platform,value,label,source in [('qq_music','003ENTRY042abc','qq-songmid','qq'),('bilibili','https://www.bilibili.com/video/BV0000000000?p=2','bilibili-without-subtitles','bilibili'),('netease','https://music.163.com/song?id=12345','netease-no-translation','netease')]:
   def variant(p=platform,v=value,l=label,s=source):case=dual.rewrite(groups[s][0],p,v,l);groups[s][0]=case;dual.play(case,l,site=1)
   attempt(label,variant)
  case=groups['netease'][0];d.send('lyrics-off');dual.send('lyrics-off');attempt('original-lyric-switch-off',lambda:dual.play(case,'original-lyric-switch-off',seconds=15,force_no_lyrics=True));d.send('lyrics-on');dual.send('lyrics-on')
  attempt('actual-pause-resume',lambda:pause_or_rejoin(d,dual,case,False));attempt('actual-rejoin',lambda:pause_or_rejoin(d,dual,case,True))
 finally:
  if not (d.root/'run-result.json').exists():
   dual.send('finish');d.send('close-menu');d.send('audit-discs');d.send('stop-server-owned');d.wait(lambda:any(x.get('event')=='server-stopping-after-product-return' and x.get('cases') and all(v['total']==1 and v['inventory']==1 for v in x['cases']) for x in (read(d.base/'entry042-disc-lifecycle.json') or {}).get('history',[])),'normal stop preserves every original disc',30);d.record('inventory','none','candidate5-normal-stop',d.current_case())
 print(json.dumps({'timelineChecksFinished':True,'failedCases':sum(not x['passed'] for x in results),'longMenuCasesRepeated':False}),flush=True)
if __name__=='__main__':main()
