# Потеряшки

Курсовая работа по дисциплине «Информационные системы»: поиск и возврат потерянных вещей в Санкт-Петербурге. Предусмотрены подписка, проверка пользователей, модерация объявлений, справочник метрополитена и аукционы. [Задание](TASK.md).

| Материалы | PDF | DOCX | Исходный текст |
| --- | --- | --- | --- |
| Этап 1 | [Отчёт](docs/part1/report.pdf) | [Отчёт](docs/part1/report.docx) | [Markdown](docs/part1/report.md) |
| Этапы 1–2 | [Общий отчёт](docs/part1-2/report.pdf) | [Общий отчёт](docs/part1-2/report.docx) | [Markdown](docs/part1-2/report.md) |
| Этап 3 | [Отчёт](docs/part3/report.pdf) | — | [Markdown](docs/part3/report.md) |
| Этапы 1–3 | [Общий отчёт](docs/part1-3/report.pdf) | — | [Markdown](docs/part1-3/report.md) |

Первый этап: предметная область, требования, 16 прецедентов и архитектура. Стек последующей реализации: Java 17, Spring Boot, Spring Security/JWT, Hibernate/JPA, PostgreSQL, MinIO; frontend — Next.js, TypeScript, Redux Toolkit, Tailwind CSS, Radix UI/shadcn. Сборка — Gradle Wrapper и pnpm. На helios планируется запуск JAR со статическим frontend; файловый сервис размещается отдельно.

Второй этап: 27 таблиц, 41 внешний ключ, связь пользователей и ролей M:N, ограничения, триггеры, индексы и функции PL/pgSQL. [Модели](docs/part2/model), [SQL](database), [текст этапа 2](docs/part2/report.md), [протоколы](docs/part2/validation).

Третий этап: [Java-приложение](backend), 27 JPA-сущностей, слой хранения с вызовами 11 функций и процедур PL/pgSQL, семь предметных сервисов, транзакции и внутренние уведомления. [Диаграммы классов](docs/part3/uml), [45 проверок Java-кода](docs/part3/validation). JAR проверен на helios 04.10.2026; запуск: `python3.11 scripts/run_backend.py --demo` из каталога курсовой. HTTP-порт не открывается.

Новые отчёты третьего этапа и этапов 1–3 представлены в PDF и Markdown. Word-документы первых двух этапов сохранены. Сборка нового отчёта: `.venv\Scripts\python.exe scripts\build_stage3_report.py`; нужен JDK 17 через JAVA_HOME, PlantUML скачивается с проверкой SHA-256. Полная проверка: `.venv\Scripts\python.exe scripts\verify_stage3.py --java-home <путь-к-JDK17>`; зависимости SSH-скрипта находятся в requirements-remote.txt. Данные титульного листа задаются в docs/report_data.json.

Установка на helios выполнена 03.10.2026: PostgreSQL 18.3, база studs, схема s465826, объекты с префиксом lf_. Каталог /home/studs/s465826/poteryashki-course. У аккаунта нет права создавать отдельную базу или схему. Объекты лабораторной не изменяются.

Показ второго этапа:

```sh
ssh -p 2222 s465826@helios.cs.ifmo.ru
cd ~/poteryashki-course
sh scripts/db.sh test
python3.11 scripts/validate_database.py --schema s465826
sh scripts/db.sh explain
psql -h pg -d studs -X
```

В psql список объектов: \dt s465826.lf_*. Поиск:

```sql
SET search_path TO s465826, pg_catalog;
SELECT * FROM lf_search_listings('сумка');
SELECT * FROM lf_public_listings WHERE metro_station IS NOT NULL;
```

Проверены 50 условий целостности; два соединения для резервирования, ставок и повторной оплаты; пакетное завершение аукциона; полный цикл создания и удаления проверочного набора lfcheck_*; планы на 10 000 временных объявлениях. Основные данные сохраняются. Identity-последовательности могут получить пропуски после ROLLBACK.

Первичная установка в доступную схему: sh scripts/db.sh create, затем sh scripts/db.sh seed. На helios эти команды уже выполнены. Повторное создание останавливается. Сброс собственной курсовой — sh scripts/db.sh drop; перечисляются только lf_*, проверяется маркер, CASCADE не используется. [Создание](database/create_database.sql) и [удаление](database/drop_database.sql) отдельной локальной БД не предназначены для общей базы studs.

Сборка отчётов в Windows:

```powershell
uv venv --python 3.12 .venv
uv pip install --python .venv\Scripts\python.exe -r requirements.txt
.venv\Scripts\python.exe scripts\build_models.py
.venv\Scripts\python.exe scripts\build_reports.py
powershell -File scripts\update_contents.ps1
```

Оформление и титульный лист основаны на шаблоне D:\ITMO\ITMOlabs\opd\sem2\lab6\opd6.tex: Университет ИТМО, факультет ПИиКТ, название работы и дисциплины, сведения об исполнителе справа, город и год. Поля 30/15/20/20 мм, Times New Roman 14; таблицы и подписи меньшим кеглем. Для сборки нужны шрифты Times New Roman, Arial и Consolas из Windows. Последняя команда обновляет оглавление DOCX через установленный Microsoft Word; PDF имеет собственное оглавление.

ФИО взято из шаблона. Текущую группу и преподавателя нужно заполнить в [report_data.json](docs/report_data.json) и пересобрать отчёты. Пароли SSH/БД не хранятся в репозитории. UI, REST API, SMTP и MinIO относятся к последующим этапам и пока не реализованы.
