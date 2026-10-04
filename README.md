# Потеряшки

Курсовая работа по дисциплине «Информационные системы»: поиск и возврат потерянных вещей в Санкт-Петербурге. Подписка, проверка пользователей, модерация, справочник метрополитена и аукционы. [Задание](TASK.md).

| Материалы | PDF | Исходный текст |
| --- | --- | --- |
| Этап 1 | [Отчёт](docs/part1/report.pdf) | [Markdown](docs/part1/report.md) |
| Этапы 1–2 | [Общий отчёт](docs/part1-2/report.pdf) | [Markdown](docs/part1-2/report.md) |
| Этап 3 | [Отчёт](docs/part3/report.pdf) | [Markdown](docs/part3/report.md) |
| Этапы 1–3 | [Общий отчёт](docs/part1-3/report.pdf) | [Markdown](docs/part1-3/report.md) |

Приложение и средства сопровождения написаны на **Java 17**. Backend: Spring Boot 3.5.16, Spring Data JPA/Hibernate, JdbcTemplate, Jakarta Validation, BCrypt, Lombok. PostgreSQL хранит предметную модель; критичные переходы выполняются функциями PL/pgSQL. Сборка — Gradle Wrapper 8.14.3. Для будущих веб-страниц выбран Spring MVC с Thymeleaf.

Первый этап содержит описание предметной области, требования, 16 прецедентов и архитектуру. Второй — 27 таблиц, 41 внешний ключ, связь пользователей и ролей M:N, ограничения, триггеры, индексы и функции. Третий — 27 JPA-сущностей, вызовы 11 функций и процедур PostgreSQL, семь предметных сервисов, транзакции и внутренние уведомления. [Backend](backend), [SQL](database), [модели](docs/part2/model), [диаграммы классов](docs/part3/uml).

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
| db create/seed/test/drop/explain/catalog | Запуск канонических SQL-скриптов через psql |
| verify | Все 45 тестов приложения на изолированном PostgreSQL через SSH |
| verify-db | 50 проверок целостности, конкуренция и цикл установки/удаления |
| entities | Генерация JPA-сущностей из каталога PostgreSQL |
| models | Словарь, две Mermaid-модели и шесть ER-схем |
| diagrams | Пересборка PNG/SVG из PlantUML средствами Java |
| reports | PDF для этапов 1, 1–2, 3 и 1–3 |
| audit | Проверка текста, границ страниц и закладок PDF |
| deploy | Установка только курсовой на helios, проверка SHA-256 и запуск demo |

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

JVM приложения ограничена 64–256 МиБ. Hibernate работает в validate; HTTP-порт не открывается. Демонстрация вызывает сервисы, выполняет поиск PL/pgSQL и читает контакты метро. Первичная установка: команды db create, затем db seed; на helios она уже выполнена. Повторное создание останавливается. db drop удаляет только перечисленные lf_* после проверки маркера, без CASCADE. SQL create_database.sql/drop_database.sql предназначен для отдельной локальной БД, не для общей studs.

Проверены 45 тестов приложения, 7 тестов Java-инструментов и 50 условий целостности PostgreSQL; проверки конкуренции используют два настоящих соединения. SQL и PL/pgSQL сохраняются согласно заданию. Python, Node.js и Microsoft Word для актуальной сборки не требуются. Gradle Wrapper содержит штатные загрузчики для Windows и Unix.

Группу и преподавателя нужно заполнить в report_data.json и пересобрать PDF. UI, REST-контроллеры, выпуск JWT, SMTP и двоичный MinIO-адаптер относятся к последующим этапам.
