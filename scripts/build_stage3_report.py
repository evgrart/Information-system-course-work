"""Build separate stage-three and combined stages 1–3 reports, PDF only."""
from pathlib import Path
import json
import build_report as report
from build_stage3_diagrams import overview,persistence,processes

ROOT=Path(__file__).resolve().parents[1]

def main():
    overview();persistence();processes()
    config=json.loads((ROOT/'docs/report_data.json').read_text(encoding='utf-8'))
    report.TITLE_FIELDS[5]=f"Выполнил студент группы {config['group']}\n{config['student']}"
    report.TITLE_FIELDS[6]=f"Проверил:\n{config['teacher']}"
    prior=(ROOT/'docs/part1-2/report.md').read_text(encoding='utf-8')
    third=(ROOT/'docs/part3/report.md').read_text(encoding='utf-8')
    # Existing 1–2 source and its diagrams remain unchanged.
    combined=prior.replace('Отчёт по этапам 1–2 курсовой работы:', 'Отчёт по этапам 1–3 курсовой работы:',1)
    combined+='\n'+'\n'.join(third.splitlines()[2:]).replace('(uml/','(../part3/uml/')+'\n'
    folder=ROOT/'docs/part1-3';folder.mkdir(exist_ok=True)
    (folder/'report.md').write_text(combined,encoding='utf-8')
    for name,label in [('part3','Этап 3'),('part1-3','Этапы 1–3')]:
        report.REPORT_DIR=ROOT/'docs'/name
        report.REPORT_TITLE='ИС «Потеряшки». '+label
        report.TITLE_FIELDS[4]=label
        items=list(report.blocks((report.REPORT_DIR/'report.md').read_text(encoding='utf-8')))
        start=next(i for i,(kind,val) in enumerate(items) if kind=='heading' and val[0]==2)
        report.build_pdf(items[start:])
        print(name+': PDF')

if __name__=='__main__':main()
