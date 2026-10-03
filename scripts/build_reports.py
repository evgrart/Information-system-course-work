"""Сборка отчёта этапа 1 и общего отчёта этапов 1–2."""
from pathlib import Path
import json
import build_report as report

ROOT=Path(__file__).resolve().parents[1]
config=json.loads((ROOT/'docs/report_data.json').read_text(encoding='utf-8'))
report.TITLE_FIELDS[5]=f"Выполнил студент группы {config['group']}\n{config['student']}"
report.TITLE_FIELDS[6]=f"Проверил:\n{config['teacher']}"
report.make_diagrams()
first=(ROOT/'docs/part1/report.md').read_text(encoding='utf-8')
second=(ROOT/'docs/part2/report.md').read_text(encoding='utf-8')
dictionary=(ROOT/'docs/part2/model/dictionary.md').read_text(encoding='utf-8')
second=second.replace('<!-- DICTIONARY -->',dictionary)
second='\n'.join(second.splitlines()[2:])
combined=first.replace('Отчёт по первому этапу курсовой работы: анализ предметной области и проектирование системы.',
    'Отчёт по этапам 1–2 курсовой работы: анализ предметной области, проектирование системы и реализация модели данных.')
task='''Второй этап курсовой работы включает:

1. ER-модель не менее чем с 10 сущностями и отношением «многие ко многим».
2. Построение даталогической модели на основе ER-модели.
3. Реализацию модели в PostgreSQL.
4. Обеспечение целостности средствами DDL и триггеров.
5. Скрипты создания, удаления и заполнения тестовыми данными.
6. Функции и процедуры PL/pgSQL для критически важных запросов.
7. Индексы по сценариям первого этапа и обоснование их полезности.
8. Подготовку отчёта.

'''
combined=combined.replace('## 1. Предметная область',task+'## 1. Предметная область',1)
combined+='\n'+second+'\n'
combined=combined.replace('(uml/','(../part1/uml/').replace('(model/','(../part2/model/')
target=ROOT/'docs/part1-2';target.mkdir(exist_ok=True)
(target/'report.md').write_text(combined,encoding='utf-8')
for folder,title,label in [('part1','Отчёт по этапу 1','Этап 1'),('part1-2','Отчёт по этапам 1–2','Этапы 1–2')]:
    report.REPORT_DIR=ROOT/'docs'/folder
    report.REPORT_TITLE='ИС «Потеряшки». '+title
    report.TITLE_FIELDS[4]=label
    items=list(report.blocks((report.REPORT_DIR/'report.md').read_text(encoding='utf-8')))
    start=next(i for i,(kind,val) in enumerate(items) if kind=='heading' and val[0]==2)
    report.build_docx(items[start:])
    report.build_pdf(items[start:])
    print(folder+': PDF, DOCX')
