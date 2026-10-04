"""Render the complete overview with a pinned PlantUML and embedded Smetana."""
from pathlib import Path
import hashlib,os,shutil,subprocess,urllib.request
ROOT=Path(__file__).resolve().parents[1]
VERSION='1.2025.10'
SHA256='4a01ea09b317180fb8e7eef712dfdca725409d2ee1919e4b5adfe9d8362b6fe5'

def render(name):
    tool=ROOT/'.venv/plantuml.jar';tool.parent.mkdir(exist_ok=True)
    if not tool.exists():
        data=urllib.request.urlopen(f'https://github.com/plantuml/plantuml/releases/download/v{VERSION}/plantuml-{VERSION}.jar',timeout=60).read()
        if hashlib.sha256(data).hexdigest()!=SHA256:raise RuntimeError('PlantUML checksum mismatch')
        tool.write_bytes(data)
    if hashlib.sha256(tool.read_bytes()).hexdigest()!=SHA256:raise RuntimeError('PlantUML checksum mismatch')
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if 'JAVA_HOME' in os.environ else shutil.which('java')
    if java is None:raise RuntimeError('Set JAVA_HOME to JDK 17')
    if os.name=='nt' and not str(java).endswith('.exe'):java=str(java)+'.exe'
    source=ROOT/'docs/part3/uml'/(name+'.puml')
    for format in ['png','svg']:
        subprocess.run([str(java),'-Xmx256m','-jar',str(tool),'-charset','UTF-8','-t'+format,str(source)],check=True)

if __name__=='__main__':render('01_architecture')
