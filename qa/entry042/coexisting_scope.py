"""Permit explicitly named owned loopback peers, including visible clients; never controls them."""
from pathlib import Path
import json
import re
import shlex
from current_scope import WORKSPACE,RUNS,child,sha,run_path

def launch_properties(text):
    values={}
    for argument in shlex.split(text, posix=True):
        if not argument.startswith('-Dqa.local.'):
            continue
        key,separator,value=argument[2:].partition('=')
        if not separator or key in values:
            raise ValueError('missing or duplicate peer launch property')
        values[key]=value
    return values

def verify_peers(roots, processes, ports):
    actual={row['pid']:row for row in processes}
    if len(actual)!=len(processes):raise ValueError('duplicate live PID')
    admitted=set();peers=[];reserve=0
    for requested in roots:
        root=run_path(requested);marker_path=root/'local-mc-owner.json'
        marker=json.loads(marker_path.read_text(encoding='utf-8'))
        if marker.get('schema')!=1 or Path(marker['instanceRoot']).resolve()!=root or Path(marker['projectRoot']).resolve()!=(WORKSPACE/'better-mc-remake').resolve():raise ValueError('peer owner mismatch')
        if type(marker.get('hidden')) is not bool or marker.get('offlineLoopback') is not True or marker.get('productionMutation') is not False:raise ValueError('peer must be owned offline loopback')
        if not re.fullmatch('[0-9a-f]{32}',marker.get('runId','')):raise ValueError('peer runId invalid')
        port=marker.get('serverPort')
        if type(port) is not int or not 1024<=port<=65535 or port in ports:raise ValueError('peer port conflict')
        if (root/'run-result.json').exists():raise ValueError('completed peer is not a live coexistence grant')
        roles=marker.get('roles',[]);owned=marker.get('processes',{})
        if not roles or set(roles)!=set(owned) or len(set(roles))!=len(roles):raise ValueError('peer roles/processes incomplete')
        executable=child(Path(marker['runtime']['javaHome'])/'bin/java.exe',WORKSPACE/'test-environment/toolchains')
        verified=[];rss=0
        for role in roles:
            if not re.fullmatch('server|host|guest|client[0-9]{2,}',role):raise ValueError('peer role invalid')
            entry=owned[role];pid=entry.get('pid');live=actual.get(pid)
            if live is None or pid in admitted:raise ValueError('peer PID missing or duplicated')
            created=entry.get('processCreateTime')
            if type(created) not in (int,float) or abs(live['createTime']-created)>.001:raise ValueError('peer PID generation mismatch')
            role_root=child(root/role,root)
            if Path(entry['root']).resolve()!=role_root or Path(live['cwd']).resolve()!=role_root:raise ValueError('peer process cwd mismatch')
            command=live['cmdline'];args=role_root/'launch.args'
            if len(command)!=2 or Path(command[0]).resolve()!=executable or command[1]!='@'+str(args):raise ValueError('peer actual command mismatch')
            properties=launch_properties(args.read_text(encoding='utf-8'))
            if Path(properties.get('qa.local.root','')).resolve()!=root or properties.get('qa.local.runId')!=marker['runId'] or properties.get('qa.local.role')!=role:raise ValueError('peer launch scope mismatch')
            if role!='server' and properties.get('qa.local.hidden')!=str(marker['hidden']).lower():raise ValueError('peer visibility does not match owner')
            admitted.add(pid);rss+=live['rssMiB']
            verified.append({'role':role,'pid':pid,'createTime':created,'cmdline':command,'cwd':str(role_root),'launchArgsSha256':sha(args)})
        properties=(root/'server/server.properties').read_text(encoding='utf-8').splitlines()
        if not {'server-ip=127.0.0.1','online-mode=false','server-port='+str(port)}<=set(properties):raise ValueError('peer server loopback properties mismatch')
        budget=marker['resources'].get('reservedPhysicalMiB')
        if type(budget) is not int or budget<1:raise ValueError('peer memory budget missing')
        reserve+=max(0,budget-rss)
        ports.add(port)
        peers.append({'instanceRoot':str(root),'runId':marker['runId'],'serverPort':port,'ownerSha256':sha(marker_path),'processes':verified,'reservedPhysicalMiB':budget})
    if admitted!=set(actual):raise ValueError('unregistered Java process remains')
    return peers,reserve
