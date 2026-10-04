# Потеряшки

Курсовая работа по дисциплине «Информационные системы»: поиск и возврат потерянных вещей в Санкт-Петербурге. Подписка, проверка пользователей, модерация, справочник метрополитена и аукционы. [Задание](TASK.md).

| Материалы | PDF | Исходный текст |
| --- | --- | --- |
| Этап 1 | [Отчёт](docs/part1/report.pdf) | [Markdown](docs/part1/report.md) |
| Этапы 1–2 | [Общий отчёт](docs/part1-2/report.pdf) | [Markdown](docs/part1-2/report.md) |
| Этап 3 | [Отчёт](docs/part3/report.pdf) | [Markdown](docs/part3/report.md) |
| Этапы 1–3 | [Общий отчёт](docs/part1-3/report.pdf) | [Markdown](docs/part1-3/report.md) |
| Этап 4 | [Отчёт](docs/part4/report.pdf) | [Markdown](docs/part4/report.md) |
| Этапы 1–4 | [Итоговый отчёт](docs/part1-4/report.pdf) | [Markdown](docs/part1-4/report.md) |
| Презентация | [PDF](docs/part4/presentation/presentation.pdf) | [HTML](docs/part4/presentation/presentation.html), [сценарий защиты](docs/part4/presentation/defense.md) |

Приложение и средства сопровождения написаны на **Java 17**. Spring Boot 3.5.16, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA/Hibernate, JdbcTemplate, Jakarta Validation, BCrypt, Lombok. Веб-страницы — HTML/CSS без прикладного JavaScript. PostgreSQL хранит предметную модель; критичные переходы выполняются функциями PL/pgSQL. Сборка — Gradle Wrapper 8.14.3.

Первый этап содержит описание предметной области, требования, 16 прецедентов и архитектуру. Второй — 27 таблиц, 42 внешних ключа, связь пользователей и ролей M:N, ограничения, триггеры, индексы и функции. Третий — 27 JPA-сущностей, вызовы 12 функций и процедур PostgreSQL, семь предметных сервисов, транзакции и внутренние уведомления. [Backend](backend), [SQL](database), [модели](docs/part2/model), [диаграммы классов](docs/part3/uml).

Сборка из корня проекта в Windows:

```powershell
$env:JAVA_HOME='C:\Users\minec\.jdks\corretto-17.0.14'
.\backend\gradlew.bat -p backend test bootJar :tooling:test :tooling:toolJar
& "$env:JAVA_HOME\bin\java.exe" -jar tooling/build/libs/course-tools.jar help
```

Для другой машины указывается её путь к JDK 17 или новее. Результат: backend/build/libs/poteryashki.jar и tooling/build/libs/course-tools.jar. В Linux/FreeBSD сборка выполняется командой ./backend/gradlew -p backend test bootJar :tooling:test :tooling:toolJar.

[Java-модуль tooling](tooling) выполняет задачи прежних скриптов:

| Команда | Назначение |
| --- | --- |
| launch --demo | Запуск приложения; пароль БД из окружения или .pgpass |
| launch | Веб-приложение в текущем терминале, 127.0.0.1:18081 |
| web start/stop/status | Фоновый сервер курсовой с проверкой принадлежности PID |
| db create/seed/test/drop/explain/catalog | Запуск канонических SQL-скриптов через psql |
| verify | Тесты сервисов и веб-страниц на изолированном PostgreSQL через SSH |
| verify-web | Сценарии в Microsoft Edge через Playwright for Java; снимки desktop и mobile |
| verify-db | 50 проверок целостности, конкуренция и цикл установки/удаления |
| entities | Генерация JPA-сущностей из каталога PostgreSQL |
| models | Словарь, две Mermaid-модели и шесть ER-схем |
| diagrams | Пересборка PNG/SVG из PlantUML средствами Java |
| reports | PDF для этапов 1, 1–2, 3, 1–3, 4 и 1–4 |
| presentation | 13 слайдов в HTML и PDF из общего Markdown |
| demo-assets | Учебные изображения и документы для начального набора |
| audit | Проверка текста, границ страниц и закладок PDF |
| deploy | Установка курсовой на helios, проверка SHA-256, demo и запуск веб-сервера |

Например, пересборка материалов:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xmx512m -jar tooling/build/libs/course-tools.jar models
& "$env:JAVA_HOME\bin\java.exe" -Xmx512m -jar tooling/build/libs/course-tools.jar diagrams
& "$env:JAVA_HOME\bin\java.exe" -Xmx512m -jar tooling/build/libs/course-tools.jar reports
& "$env:JAVA_HOME\bin\java.exe" -Xmx512m -jar tooling/build/libs/course-tools.jar audit
```

Данные титульного листа находятся в [report_data.json](docs/report_data.json). Оформление основано на шаблоне D:\ITMO\ITMOlabs\opd\sem2\lab6\opd6.tex: поля 30/15/20/20 мм, Times New Roman 14, таблицы и подписи меньшим кеглем. Для PDF нужны times.ttf, timesbd.ttf, timesi.ttf, timesbi.ttf и consola.ttf; каталог задаётся COURSE_FONTS, по умолчанию C:/Windows/Fonts. DOCX первых двух этапов сохранены как архив предыдущей редакции; актуальные материалы — PDF и Markdown. Новые Word-документы не создаются.

Проверка на helios запускается с машины разработчика:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xmx256m -jar tooling/build/libs/course-tools.jar verify
& "$env:JAVA_HOME\bin\java.exe" -Xmx256m -jar tooling/build/libs/course-tools.jar verify-db
& "$env:JAVA_HOME\bin\java.exe" -Xmx256m -jar tooling/build/libs/course-tools.jar deploy
```

SSH-пароль запрашивается скрыто в терминале; для автоматического запуска используется временная переменная COURSE_SSH_PASSWORD. Ключ сервера проверяется по ~/.ssh/known_hosts. Адрес по умолчанию helios.cs.ifmo.ru:2222, пользователь s465826. Настройки переопределяются COURSE_SSH_HOST, COURSE_SSH_PORT, COURSE_SSH_USER и DB_SCHEMA. Проверки отказываются использовать уже существующие lfcheck_* и lf3check_*; создают только собственный набор, затем проверяют сохранность исходных объектов и значений последовательностей. [Протоколы](docs/part3/validation).

На helios курсовая находится в /home/studs/s465826/poteryashki-course. PostgreSQL 18.3: база studs, схема s465826, объекты lf_. Отдельную БД или схему этот аккаунт создавать не может. Таблицы и процессы другой лабораторной не изменяются. Запуск после установки:

```sh
cd ~/poteryashki-course
java -Xmx256m -jar tooling/build/libs/course-tools.jar launch --demo
java -Xmx256m -jar tooling/build/libs/course-tools.jar db test
java -Xmx256m -jar tooling/build/libs/course-tools.jar db explain
```

JVM приложения ограничена 64–256 МиБ. Hibernate работает в validate. Команда launch --demo вызывает сервисы и завершает процесс без HTTP-порта; обычный запуск открывает веб-интерфейс. Первичная установка: команды db create, затем db seed; на helios она уже выполнена. Повторное создание останавливается. db drop удаляет только перечисленные lf_* после проверки маркера, без CASCADE. SQL create_database.sql/drop_database.sql предназначен для отдельной локальной БД, не для общей studs.

Проверены 113 тестов приложения, 8 тестов Java-инструментов и 50 условий целостности PostgreSQL; проверки конкуренции используют два настоящих соединения. SQL и PL/pgSQL сохраняются согласно заданию. Python, Node.js и Microsoft Word для актуальной сборки не требуются. Gradle Wrapper содержит штатные загрузчики для Windows и Unix.

Группу и преподавателя нужно заполнить в report_data.json и пересобрать PDF.

Для работы с веб-интерфейсом на helios:

```text
cd ~/poteryashki-course
java -Xmx256m -jar tooling/build/libs/course-tools.jar web start
```

В отдельном локальном терминале:

```text
ssh -p 2222 -L 18081:127.0.0.1:18081 s465826@helios.cs.ifmo.ru
```

Открыть http://127.0.0.1:18081. Начальные аккаунты: finder@example.invalid, owner@example.invalid, buyer@example.invalid, moderator@example.invalid, admin@example.invalid. Учебный пароль: DemoCourse2026! Начальные подписки действуют 30 суток с заполнения базы, аукцион — сутки; новый учебный заказ оформляется в кабинете. Запуск не пересоздаёт данные.

Четвёртый этап содержит каталог и редактор с фотографиями, проверку профиля, подписку, заявки и диалоги, два подтверждения передачи, аукционы, справочник метро, рабочие страницы сотрудников и аудит. JWT передаются в HttpOnly cookie, формы защищены CSRF. [Материалы и протоколы](docs/part4).

[Результаты повторной проверки](docs/part4/validation/review.md): передача победителю аукциона, рассмотрение жалоб, уведомления, фильтр статуса, ограничения каталога и обработка повреждённых фотографий.

Переменные окружения: DB_URL, DB_USER, DB_PASSWORD, DB_PREFIX; APP_PORT, STORAGE_ROOT; JWT_SECRET (Base64, минимум 32 случайных байта), COOKIE_SECURE; MAIL_MODE, MAIL_HOST, MAIL_PORT, MAIL_USER, MAIL_PASSWORD, MAIL_FROM, PUBLIC_URL; DEMO_PAYMENTS и JOBS_ENABLED. Пароли и секреты не помещать в репозиторий. По умолчанию сервер слушает loopback; публичное размещение требует HTTPS и COOKIE_SECURE=true.

MAIL_MODE=demo показывает письма только авторизованному получателю в /account/mail и хранит их до перезапуска. Для восстановления без доступа к аккаунту нужен настроенный SMTP (MAIL_MODE=smtp, STARTTLS). DEMO_PAYMENTS=true включает учебную оплату без списания средств. MinIO и реальный платёжный провайдер не подключены; файлы хранятся в закрытом каталоге STORAGE_ROOT.

Для браузерной проверки сначала собрать bootJar и toolJar, затем запустить `verify-web` с машины разработчика. Нужен установленный Microsoft Edge; браузерная зависимость включена только в тесты. При обычной сборке тесты PostgreSQL пропускаются без RUN_DB_TESTS=true; полную проверку выполняет команда verify. Python и отдельная frontend-сборка не нужны. Playwright использует свой штатный драйвер только при браузерных тестах.
