"""Read-only native process snapshot; run with the existing base Python/psutil."""
import json
import psutil

if __name__=='__main__':
    rows=[]
    for process in psutil.process_iter(['pid','name']):
        if (process.info['name'] or '').lower() not in ('java.exe','javaw.exe'):
            continue
        rows.append({'pid':process.pid,'name':process.info['name'],'createTime':process.create_time(),
                     'cmdline':process.cmdline(),'cwd':process.cwd(),'rssMiB':process.memory_info().rss//1048576})
    print(json.dumps(rows))
