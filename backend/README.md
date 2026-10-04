# Backend «Потеряшки»

Java 17, Spring Boot 3.5.16, Spring Data JPA/Hibernate, JdbcTemplate, Jakarta Validation, BCrypt, Lombok. PostgreSQL — модель второго этапа. Gradle Wrapper 8.14.3.

Третий этап реализует сервисы и слой хранения. HTTP-сервер и frontend здесь не запускаются. Классы сервисов вызываются через Spring-контекст; actor — доверенный идентификатор текущего пользователя, который будущий контроллер получит из проверенной сессии.

```powershell
$env:JAVA_HOME='C:\Users\minec\.jdks\corretto-17.0.14'
.\gradlew.bat test bootJar
```

Обычная команда test проверяет локальные правила именования; интеграционные тесты требуют PostgreSQL и RUN_DB_TESTS=true. Полный воспроизводимый прогон из корня проекта:

```powershell
.venv\Scripts\python.exe scripts\verify_stage3.py `
  --java-home 'C:\Users\minec\.jdks\corretto-17.0.14'
```

Для скрипта нужен paramiko из requirements-remote.txt. SSH-пароль вводится скрыто. Скрипт создаёт и удаляет только собственный одноразовый набор lf3check_*, использует SSH-туннель и проверяет сохранность исходных объектов и последовательностей. Существующий набор с таким именем автоматически не удаляется.

Переменные приложения:

| Имя | Назначение |
| --- | --- |
| DB_URL | JDBC URL; по умолчанию pg/studs, currentSchema=s465826 |
| DB_USER | PostgreSQL role; по умолчанию s465826 |
| DB_PASSWORD | Пароль PostgreSQL; launcher может взять его из .pgpass |
| DB_PREFIX | lf_ для курсовой; lf3check_ только для одноразового тестирования |
| APP_DEMO | true для безопасной демонстрации чтения через сервисы |
| RUN_DB_TESTS | true включает тесты PostgreSQL; никогда не включать их на рабочем lf_* |

JAR: build/libs/poteryashki.jar. На helios:

```sh
cd ~/poteryashki-course
python3.11 scripts/run_backend.py --demo
```

Launcher использует память JVM 64–256 МиБ. Работа демонстрации не требует HTTP-порта, Docker или фонового процесса. Hibernate запускается в validate, без создания таблиц или доступа к истории миграций другой лабораторной.

Слой хранения:

- 27 неизменяемых JPA-проекций таблиц, включая составной ключ UserRoleKey.
- UserRepository и ListingRepository для чтения; SqlStore для параметризованного CRUD.
- DatabaseFunctions вызывает все 11 прикладных функций/процедур второго этапа.
- Общая транзакция JPA/JDBC, локальный автор аудита, очистка JPA-кэша после записи.

Сервисы: AccountService, SubscriptionService, ListingService, ReturnService, AuctionService, AdministrationService, OutboxService. Основные операции покрыты реальными транзакциями PostgreSQL. Внешние адаптеры JWT/HTTP, SMTP и двоичного MinIO-хранилища добавляются на следующем этапе. attachImage сохраняет метаданные уже проверенного объекта; payDemo выполняет учебную оплату владельца заказа без настоящего списания.

Для тестовых аккаунтов из seed.sql используется открытый демонстрационный пароль DemoCourse2026!. Это не пароль SSH или базы данных.
