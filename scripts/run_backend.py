"""Launch the coursework JAR with credentials from environment or pgpass."""
from pathlib import Path
import argparse,os,subprocess,sys
from urllib.parse import urlsplit

ROOT=Path(__file__).resolve().parents[1]

def pgpass_fields(line):
    result=[];part='';escaped=False
    for c in line:
        if escaped: part+=c;escaped=False
        elif c=='\\': escaped=True
        elif c==':': result.append(part);part=''
        else: part+=c
    result.append(part);return result

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--demo',action='store_true')
    parser.add_argument('--jar',type=Path,default=ROOT/'backend/build/libs/poteryashki.jar')
    args=parser.parse_args()
    if not args.jar.is_file(): parser.error('Build the JAR before launching')
    env=os.environ.copy()
    env.setdefault('DB_URL','jdbc:postgresql://pg:5432/studs?currentSchema=s465826')
    env.setdefault('DB_USER',os.environ.get('USER',os.environ.get('USERNAME','s465826')))
    if not env.get('DB_PASSWORD'):
        url=urlsplit(env['DB_URL'].removeprefix('jdbc:'))
        values=(url.hostname,str(url.port or 5432),url.path.lstrip('/'),env['DB_USER'])
        file=Path(os.environ.get('PGPASSFILE',str(Path.home()/'.pgpass')))
        if file.is_file():
            for line in file.read_text(encoding='utf-8').splitlines():
                if not line or line.startswith('#'):continue
                fields=pgpass_fields(line)
                if len(fields)==5 and all(f=='*' or f==v for f,v in zip(fields[:4],values)):
                    env['DB_PASSWORD']=fields[4];break
        if not env.get('DB_PASSWORD'):parser.error('Set DB_PASSWORD or provide a matching pgpass entry')
    command=['java','-Xms64m','-Xmx256m','-Dfile.encoding=UTF-8','-jar',str(args.jar.resolve())]
    if args.demo:command.append('--app.demo=true')
    return subprocess.call(command,cwd=ROOT,env=env)

if __name__=='__main__':sys.exit(main())
