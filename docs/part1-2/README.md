# Отчёт по этапам 1–2

Основной файл — [report.tex](report.tex), результат сборки — [report.pdf](report.pdf). Текст подключается из двух исходников:

- [Первый этап](../part1/content.tex): описание предметной области, требования, пять прецедентов и архитектура приложения.
- [Второй этап](../part2/content.tex): ER-модель, словарь 27 отношений, реализация PostgreSQL, ограничения, триггеры, функции, индексы и проверки.

Первая часть общая с отдельным отчётом этапа 1. Изменения в `docs/part1/content.tex` используются при сборке обоих PDF. Титульный лист и метаданные общего отчёта находятся в `report.tex`.

Сборка из корня проекта:

```powershell
& "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File docs\part1-2\build.ps1
```

Нужны XeLaTeX и шрифты Times New Roman, Arial, Consolas. Скрипт выполняет три прохода, проверяет журнал и заменяет PDF после успешной сборки. Промежуточные файлы находятся в `.latex-build`.

Без PowerShell, из каталога `docs/part1-2`:

```sh
latexmk -xelatex -interaction=nonstopmode -halt-on-error -outdir=.latex-build report.tex
```

Результат этой команды находится в `.latex-build/report.pdf`. Markdown сохранён как текстовая версия, DOCX — архивная версия. Java-команда `reports` не заменяет PDF отчётов, у которых есть основной исходник LaTeX.
