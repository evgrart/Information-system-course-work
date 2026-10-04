# Backend «Потеряшки»

Java 17, Spring Boot 3.5.16, Spring Data JPA/Hibernate, JdbcTemplate, Jakarta Validation, BCrypt, Lombok. PostgreSQL — модель второго этапа. Gradle Wrapper 8.14.3.

Третий этап реализует сервисы и слой хранения. Сервисы вызываются через Spring-контекст; actor — доверенный идентификатор, который будущий контроллер получит из проверенной сессии. HTTP-сервер на этом этапе не запускается.

Из каталога backend:

```powershell
$env:JAVA_HOME='C:\Users\minec\.jdks\corretto-17.0.14'
.\gradlew.bat test bootJar :tooling:test :tooling:toolJar
```

Обычная команда test выполняет локальный тест именования и пропускает интеграционные сценарии. Для всех 45 тестов с PostgreSQL из корня курсовой:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xmx256m -jar tooling/build/libs/course-tools.jar verify
```

Команда создаёт одноразовый набор lf3check_*, подключает PostgreSQL по SSH-туннелю и проверяет сохранность исходных объектов и последовательностей. SSH-пароль вводится скрыто; ключ сервера проверяется по known_hosts. Требуется предварительно собрать toolJar. [Средства сопровождения](../tooling).

| Переменная | Назначение |
| --- | --- |
| DB_URL | JDBC URL; по умолчанию pg/studs, currentSchema=s465826 |
| DB_USER | PostgreSQL role; по умолчанию s465826 |
| DB_PASSWORD | Пароль PostgreSQL; Java-launcher может прочитать .pgpass |
| DB_PREFIX | lf_ для курсовой; lf3check_ для одноразовой проверки |
| APP_DEMO | true для демонстрации чтения через сервисы |
| RUN_DB_TESTS | true включает тесты БД; допустимо только с lf3check_* |

JAR: build/libs/poteryashki.jar. На helios из каталога курсовой:

```sh
java -Xmx256m -jar tooling/build/libs/course-tools.jar launch --demo
```

Launcher передаёт пароль только в окружение дочернего процесса и ограничивает JVM приложения параметрами -Xms64m -Xmx256m. Hibernate работает в validate, без изменения таблиц и истории миграций другой лабораторной.

Слой хранения содержит 27 неизменяемых JPA-проекций с составным UserRoleKey, UserRepository и ListingRepository для чтения, параметризованный SqlStore и DatabaseFunctions с вызовами всех 11 прикладных функций/процедур второго этапа. JPA и JDBC используют одну транзакцию; автор аудита задаётся локально, кэш JPA очищается после записи.

Сервисы: AccountService, SubscriptionService, ListingService, ReturnService, AuctionService, AdministrationService, OutboxService. Внешние адаптеры HTTP/JWT, SMTP, MinIO и страницы Thymeleaf добавляются после третьего этапа. attachImage сохраняет метаданные проверенного объекта; payDemo выполняет учебную оплату владельца заказа без настоящего списания. Тестовые аккаунты seed.sql имеют открытый демонстрационный пароль DemoCourse2026!.
