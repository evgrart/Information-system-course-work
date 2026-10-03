"""Run Java integration tests against an isolated PostgreSQL fixture on helios.

Requires paramiko; SSH password is read interactively or from COURSE_SSH_PASSWORD.
Database credentials are held in memory and passed only through child environment.
"""
from pathlib import Path
import argparse,concurrent.futures,getpass,json,os,select,shlex,socketserver,subprocess,tempfile,threading
import paramiko
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
PREFIX='lf3check_'

def run():
    parser=argparse.ArgumentParser()
    parser.add_argument('--host',default='helios.cs.ifmo.ru')
    parser.add_argument('--user',default='s465826')
    parser.add_argument('--schema',default='s465826')
    parser.add_argument('--port',type=int,default=2222)
    parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME'))
    args=parser.parse_args()
    if not args.schema.replace('_','').isalnum(): parser.error('Invalid schema')
    out=ROOT/'docs/part3/validation';out.mkdir(parents=True,exist_ok=True)
    client=paramiko.SSHClient();client.load_system_host_keys()
    client.connect(args.host,port=args.port,username=args.user,
                   password=os.environ.pop('COURSE_SSH_PASSWORD',None) or getpass.getpass('SSH password: '),
                   look_for_keys=False,allow_agent=False,timeout=20)
    def remote(command,stdin=None):
        inp,stdout,stderr=client.exec_command(command,timeout=120)
        if stdin: inp.write(stdin);inp.flush()
        inp.channel.shutdown_write()
        text=stdout.read().decode('utf-8');error=stderr.read().decode('utf-8')
        if stdout.channel.recv_exit_status(): raise RuntimeError(error)
        return text
    def sql(text):
        return remote('psql -h pg -d studs -X -w -qAt -v ON_ERROR_STOP=1',f'SET search_path TO "{args.schema}",pg_catalog;\n'+text).strip()
    fingerprint=f"SELECT md5(string_agg(oid::text||':'||relname,'|' ORDER BY oid)) FROM pg_class WHERE relnamespace='{args.schema}'::regnamespace AND relname NOT LIKE 'lf3check\\_%' ESCAPE '\\';"
    before=sql(fingerprint)
    sequences=f"select jsonb_object_agg(sequencename,last_value)::text from pg_sequences where schemaname='{args.schema}' and sequencename not like 'lf3check\\_%' escape '\\';"
    sequences_before=sql(sequences)
    if sql(f"select count(*) from pg_class where relnamespace='{args.schema}'::regnamespace and relname like 'lf3check\\_%' escape '\\'")!='0':
        raise RuntimeError('Existing lf3check_* objects: automatic adoption or deletion is forbidden')
    folder=remote('mktemp -d /tmp/poteryashki-stage3-XXXXXX').strip()
    created=False;server=None
    try:
        with client.open_sftp() as sftp:
            for source in (ROOT/'database').glob('*.sql'):
                with sftp.file(folder+'/'+source.name,'w') as target:
                    text=source.read_text(encoding='utf-8').replace('lf\\_',PREFIX[:-1]+'\\_').replace('lf_',PREFIX)
                    target.write(text.encode('utf-8'))
        psql=f'psql -h pg -d studs -X -w -v ON_ERROR_STOP=1 -v schema={shlex.quote(args.schema)} -f '
        remote(psql+shlex.quote(folder+'/create.sql'));created=True
        remote(psql+shlex.quote(folder+'/seed.sql'))
        # Parse escaped pgpass fields without printing or saving their contents.
        credential_code="""import pathlib,json
def fields(line):
 result=[];part='';escaped=False
 for c in line:
  if escaped: part+=c;escaped=False
  elif c=='\\\\': escaped=True
  elif c==':': result.append(part);part=''
  else: part+=c
 result.append(part);return result
for line in (pathlib.Path.home()/'.pgpass').read_text().splitlines():
 if not line or line.startswith('#'): continue
 f=fields(line)
 if len(f)==5 and f[0] in ('pg','*') and f[1] in ('5432','*') and f[2] in ('studs','*') and f[3] in (USER,'*'):
  print(json.dumps(f[4]));break
else: raise RuntimeError('No matching pgpass entry')
""".replace('USER',repr(args.user))
        password=json.loads(remote('/usr/local/bin/python3.11 -',credential_code))
        transport=client.get_transport()
        class Handler(socketserver.BaseRequestHandler):
            def handle(self):
                channel=transport.open_channel('direct-tcpip',('pg',5432),self.request.getpeername())
                try:
                    while True:
                        readable,_,_=select.select([self.request,channel],[],[],1)
                        for source in readable:
                            data=source.recv(65536)
                            if not data: return
                            (channel if source is self.request else self.request).sendall(data)
                finally: channel.close()
        class Server(socketserver.ThreadingTCPServer): daemon_threads=True;allow_reuse_address=True
        server=Server(('127.0.0.1',0),Handler)
        threading.Thread(target=server.serve_forever,daemon=True).start()
        env=os.environ.copy();env.pop('DEBUG',None)
        env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{server.server_address[1]}/studs?currentSchema={args.schema}',
                   DB_USER=args.user,DB_PASSWORD=password,DB_PREFIX=PREFIX,RUN_DB_TESTS='true')
        if args.java_home: env['JAVA_HOME']=args.java_home
        gradle=ROOT/'backend'/('gradlew.bat' if os.name=='nt' else 'gradlew')
        print('Running service integration tests on isolated lf3check_* fixture',flush=True)
        result=subprocess.run([str(gradle),'clean','test','bootJar','--console=plain'],cwd=ROOT/'backend',env=env,
                              stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',timeout=600)
        (out/'integration.txt').write_text('\n'.join(line.rstrip() for line in result.stdout.splitlines())+'\n',encoding='utf-8')
        print('\n'.join(result.stdout.splitlines()[-12:]),flush=True)
        if result.returncode: raise RuntimeError('Gradle tests failed; see validation/integration.txt')
        summary=[]
        for file in sorted((ROOT/'backend/build/test-results/test').glob('TEST-*.xml')):
            attrs=ET.parse(file).getroot().attrib
            summary.append({key:attrs[key] for key in ['name','tests','skipped','failures','errors','timestamp','time']})
        if not summary or any(int(item[k]) for item in summary for k in ['skipped','failures','errors']):
            raise RuntimeError('Incomplete integration test run')
        (out/'tests.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    finally:
        if server: server.shutdown();server.server_close()
        if created:
            remote(psql+shlex.quote(folder+'/drop.sql'))
        # Enumerated files, never a recursive computed-path deletion.
        remote('rm -f '+ ' '.join(shlex.quote(folder+'/'+p.name) for p in (ROOT/'database').glob('*.sql')))
        remote('rmdir '+shlex.quote(folder))
        after=sql(fingerprint)
        sequences_after=sql(sequences)
        remains=sql(f"select count(*) from pg_class where relnamespace='{args.schema}'::regnamespace and relname like 'lf3check\\_%' escape '\\'")
        client.close()
        evidence=f'Existing objects before: {before}\nExisting objects after: {after}\nOriginal sequence values preserved: {sequences_before==sequences_after}\nRemaining fixture objects: {remains}\n'
        (out/'isolation.txt').write_text(evidence,encoding='utf-8')
        if before!=after or sequences_before!=sequences_after or remains!='0': raise RuntimeError('Isolation verification failed')
        print('PASS: original database objects preserved; isolated fixture removed',flush=True)

if __name__=='__main__': run()
