from pathlib import Path
import sys,json,tempfile,threading,subprocess,uuid
from backend4 import Backend,serve,PRODUCT
from owned_audio4 import data
qa=Path(sys.argv[1]);product=Path(sys.argv[2]);jdk=Path(sys.argv[3])
rows=[x.strip(chr(34)) for x in (qa/"compile.args").read_text(encoding="utf-8").splitlines()];cp=str(qa/"classes")+";"+rows[rows.index("-classpath")+1].replace(chr(92)*2,chr(92))
with tempfile.TemporaryDirectory() as folder:
    root=Path(folder)
    for role in ['host','guest','server','coordinator']:(root/role).mkdir()
    marker={'runId':uuid.uuid4().hex,'instanceRoot':str(root),'clientNames':{'host':'10000','guest':'10001'},'inputArtifacts':[{'sha256':PRODUCT}]};(root/'local-mc-owner.json').write_text(json.dumps(marker));(root/'entry042-owned-cc0.wav').write_bytes(data())
    backend=Backend(root,marker);server=serve(backend,0);thread=threading.Thread(target=server.serve_forever);thread.start()
    try:
        result=subprocess.run([str(jdk/'bin/java.exe'),'-cp',cp,'net.muxigame.musiccompat.qa.entry042.Entry042BackendProtocolTest',str(root),str(server.server_port)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=35)
        print(result.stdout);print(result.stderr);assert result.returncode==0
    finally:server.shutdown();thread.join(5);server.server_close();assert not thread.is_alive()
print(json.dumps({'backendNormalShutdown':True,'ossRequests':0,'mcStarted':False}))
