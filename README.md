# CleaningApp Backend

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-4169E1?logo=postgresql&logoColor=white)
![Liquibase](https://img.shields.io/badge/Liquibase-Migrations-2962FF?logo=liquibase&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth-FFCA28?logo=firebase&logoColor=black)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

Backend-сервис для **CleaningApp** — мобильного приложения для геймификации бытовых задач в совместном жилом пространстве.

Сервис позволяет пользователям создавать домохозяйства, присоединяться к ним по инвайт-коду, создавать и выполнять бытовые задачи за внутреннюю валюту, покупать привилегии, смотреть историю операций, ленту активности и лидерборд участников.

## Основные возможности

- регистрация пользователя после проверки Firebase ID Token;
- получение, обновление и удаление профиля;
- синхронизация email из Firebase Authentication;
- создание, обновление, просмотр и удаление домохозяйств;
- присоединение к домохозяйству по invite code;
- выход из домохозяйства и удаление участника;
- создание, редактирование, удаление, бронирование, освобождение и завершение задач;
- начисление баланса за выполнение задач;
- создание, редактирование, удаление и покупка привилегий;
- история личных транзакций в рамках домохозяйства;
- лента активности домохозяйства;
- лидерборд участников по заработанным монетам;
- централизованная обработка ошибок;
- миграции схемы базы данных через Liquibase;
- Docker-сборка для деплоя.

## Стек

| Компонент | Технология |
| --- | --- |
| Язык | Kotlin 2.2.21 |
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.2 |
| API | Spring Web MVC, REST |
| Security | Spring Security, Firebase Admin SDK |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| Migrations | Liquibase |
| Validation | Jakarta Validation |
| API docs | Springdoc OpenAPI |
| Build | Gradle Kotlin DSL |
| Tests | JUnit 5, Spring Boot Test, Spring Security Test |
| Deploy | Docker, Render-compatible configuration |

## Архитектура

Проект построен по слоистой архитектуре. Контроллеры отвечают только за HTTP-контракт, бизнес-логика находится в сервисах, доступ к данным вынесен в репозитории, а наружу возвращаются DTO, а не JPA-сущности.

```text
src/main/kotlin/com/cleaningapp/backend
├── activity        # лента событий домохозяйства
├── config          # конфигурация приложения и Firebase
├── exception       # доменные исключения и GlobalExceptionHandler
├── household       # домохозяйства
├── leaderboard     # рейтинг участников
├── privilege       # привилегии
├── security        # Firebase auth filter, auth endpoints, ping
├── task            # задачи
├── transaction     # транзакции и баланс
├── user            # пользователи
└── userhousehold   # участие пользователя в домохозяйстве
```

Типовой модуль содержит:

```text
Controller -> Service interface -> ServiceImpl -> Repository -> Entity
                                      |
                                      -> DTO / Mapper
```

Такое разделение нужно, чтобы не смешивать HTTP-логику, бизнес-правила и работу с базой данных.

## Доменная модель

| Сущность | Назначение |
| --- | --- |
| `User` | пользователь приложения, связанный с Firebase UID |
| `Household` | домохозяйство с названием, invite code и статусом активности |
| `UserHousehold` | связь пользователя и домохозяйства, хранит баланс участника |
| `Task` | бытовая задача с наградой, автором, бронью и статусом выполнения |
| `Privilege` | платная привилегия внутри домохозяйства |
| `Transaction` | запись о начислении, покупке или сбросе баланса |
| `Activity` | событие в ленте активности домохозяйства |
| `Leaderboard` | агрегированный рейтинг участников |

## Бизнес-правила

- пользователь может состоять максимум в 3 активных домохозяйствах;
- домохозяйство может иметь максимум 6 активных участников;
- награда за задачу ограничена диапазоном от 5 до 100;
- стоимость привилегии ограничена диапазоном от 5 до 500;
- баланс участника не может становиться отрицательным;
- задачу можно выполнить только один раз;
- выполнить задачу может только пользователь, который ее забронировал;
- забронированную или выполненную задачу нельзя редактировать как свободную;
- привилегию можно купить только один раз;
- при выходе пользователя из домохозяйства его активные брони освобождаются, а баланс сбрасывается;
- при удалении последнего участника домохозяйство удаляется через общий сценарий удаления;
- лидерборд строится по заработанным монетам, а не по текущему балансу.

## Аутентификация

Backend не хранит пароль пользователя. Аутентификация построена через Firebase Authentication.

Клиент отправляет Firebase ID Token в каждом защищенном запросе:

```http
Authorization: Bearer <firebase_id_token>
```

`FirebaseAuthFilter` проверяет токен через Firebase Admin SDK. Для регистрации достаточно валидного Firebase UID из токена. Для остальных запросов дополнительно проверяется наличие активного пользователя в базе данных.

## API

Базовый префикс API:

```text
/api
```

### Auth

| Method | Endpoint | Описание |
| --- | --- | --- |
| `POST` | `/api/auth/register` | регистрация пользователя после Firebase-аутентификации |

### User

| Method | Endpoint | Описание |
| --- | --- | --- |
| `GET` | `/api/users/me` | получить текущий профиль |
| `PUT` | `/api/users/me` | обновить профиль |
| `PUT` | `/api/users/me/email/sync` | синхронизировать email из Firebase |
| `DELETE` | `/api/users/me` | удалить текущего пользователя |

### Household

| Method | Endpoint | Описание |
| --- | --- | --- |
| `POST` | `/api/households` | создать домохозяйство |
| `GET` | `/api/households/{householdId}` | получить домохозяйство |
| `PUT` | `/api/households/{householdId}` | обновить домохозяйство |
| `DELETE` | `/api/households/{householdId}` | удалить домохозяйство |
| `POST` | `/api/households/join` | присоединиться по invite code |
| `DELETE` | `/api/households/{householdId}/leave` | выйти из домохозяйства |
| `GET` | `/api/households/myHouseholds` | получить свои домохозяйства |
| `GET` | `/api/households/{householdId}/members` | получить участников |
| `DELETE` | `/api/households/{householdId}/members/{userToRemoveId}` | удалить участника |

### Task

| Method | Endpoint | Описание |
| --- | --- | --- |
| `POST` | `/api/households/{householdId}/tasks` | создать задачу |
| `GET` | `/api/households/{householdId}/tasks?filter=ALL` | получить задачи домохозяйства |
| `GET` | `/api/tasks/{taskId}` | получить задачу |
| `PUT` | `/api/tasks/{taskId}` | обновить задачу |
| `DELETE` | `/api/tasks/{taskId}` | удалить задачу |
| `POST` | `/api/tasks/{taskId}/assign` | забронировать задачу |
| `POST` | `/api/tasks/{taskId}/unassign` | снять бронь |
| `POST` | `/api/tasks/{taskId}/complete` | завершить задачу |

Фильтры задач:

```text
ALL, FREE, MY, COMPLETED
```

### Privilege

| Method | Endpoint | Описание |
| --- | --- | --- |
| `POST` | `/api/households/{householdId}/privileges` | создать привилегию |
| `GET` | `/api/households/{householdId}/privileges?filter=ALL` | получить привилегии |
| `GET` | `/api/privileges/{privilegeId}` | получить привилегию |
| `PUT` | `/api/privileges/{privilegeId}` | обновить привилегию |
| `DELETE` | `/api/privileges/{privilegeId}` | удалить привилегию |
| `POST` | `/api/privileges/{privilegeId}/buy` | купить привилегию |

Фильтры привилегий:

```text
ALL, AVAILABLE, MY
```

### Transaction

| Method | Endpoint | Описание |
| --- | --- | --- |
| `GET` | `/api/households/{householdId}/transactions/my` | получить личные транзакции участника |

### Activity

| Method | Endpoint | Описание |
| --- | --- | --- |
| `GET` | `/api/households/{householdId}/activity` | получить ленту активности |

Параметры:

```text
activityType = HOUSEHOLD_CREATED | USER_JOINED | USER_LEFT | USER_REMOVED | TASK_CREATED | TASK_ASSIGNED | TASK_UNASSIGNED | TASK_COMPLETED | PRIVILEGE_CREATED | PRIVILEGE_BOUGHT
actorScope = ALL | MY
```

### Leaderboard

| Method | Endpoint | Описание |
| --- | --- | --- |
| `GET` | `/api/households/{householdId}/leaderboard` | получить рейтинг участников |

### Ping

| Method | Endpoint | Описание |
| --- | --- | --- |
| `GET` | `/api/ping` | проверка доступности сервиса без авторизации |

## Конфигурация

В проекте используются профили:

```text
dev
prod
test
```

Основной конфиг находится в `src/main/resources/application.yaml`, профильные настройки — в `application-dev.yaml`, `application-prod.yaml` и `application-test.yaml`.

Необходимые переменные окружения:

| Переменная | Назначение |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | активный профиль, например `dev` |
| `DB_URL` | JDBC URL PostgreSQL |
| `DB_USERNAME` | имя пользователя БД |
| `DB_PASSWORD` | пароль пользователя БД |
| `FIREBASE_ADMIN_KEY_PATH` | путь к Firebase Admin SDK service account key |
| `PORT` | порт приложения, по умолчанию `8080` |

Пример для локальной разработки:

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://localhost:5432/cleaningapp_dev
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export FIREBASE_ADMIN_KEY_PATH=file:/absolute/path/to/adminsdk-service-account-key.json
```

В репозитории есть шаблоны `.env.dev.example`, `.env.prod.example` и `.env.test.example`. Секреты, реальные пароли и Firebase service account key нельзя коммитить в репозиторий.

## Запуск локально

### 1. Подготовить окружение

Потребуется:

- JDK 21;
- PostgreSQL;
- Firebase project с включенным Authentication;
- Firebase Admin SDK service account key.

Создать базу данных:

```bash
createdb cleaningapp_dev
```

### 2. Склонировать проект

```bash
git clone https://github.com/alfoll/cleaning-app-backend.git
cd cleaning-app-backend
```

### 3. Указать переменные окружения

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://localhost:5432/cleaningapp_dev
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export FIREBASE_ADMIN_KEY_PATH=file:/absolute/path/to/adminsdk-service-account-key.json
```

### 4. Запустить приложение

```bash
./gradlew bootRun
```

После запуска сервис будет доступен по адресу:

```text
http://localhost:8080
```

## Запуск в Docker

Собрать образ:

```bash
docker build -t cleaning-app-backend .
```

Запустить контейнер:

```bash
docker run --env-file .env -p 8080:8080 cleaning-app-backend
```

Пример `.env`:

```env
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://host.docker.internal:5432/cleaningapp_dev
DB_USERNAME=postgres
DB_PASSWORD=postgres
FIREBASE_ADMIN_KEY_PATH=file:/app/firebase-adminsdk.json
PORT=8080
```

Если Firebase key передается как файл, его нужно примонтировать в контейнер отдельно.

## Миграции базы данных

Liquibase включен в конфигурации приложения. Master changelog:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

Изменения схемы хранятся в:

```text
src/main/resources/db/changelog/changes
```

При старте приложения Liquibase применяет миграции автоматически. Hibernate работает в режиме `ddl-auto: validate`, поэтому схема должна соответствовать миграциям.

## Тесты

Запуск всех тестов:

```bash
./gradlew test
```

Тестовая структура включает проверки для основных модулей:

```text
src/test/kotlin/com/cleaningapp/backend
├── activity
├── base
├── config
├── contract
├── household
├── leaderboard
├── privilege
├── security
├── task
├── transaction
├── user
└── userhousehold
```

## API документация

В проект подключен Springdoc OpenAPI. После запуска приложения документация обычно доступна по адресам:

```text
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/v3/api-docs
```

## Связь с Android-клиентом

Android-клиент должен использовать тот же Firebase project, что и backend. Последовательность работы такая:

1. пользователь проходит Firebase Authentication на Android;
2. клиент получает Firebase ID Token;
3. клиент отправляет запросы на backend с заголовком `Authorization: Bearer <token>`;
4. backend проверяет токен и связывает запрос с пользователем по Firebase UID.

Репозиторий Android-клиента:

```text
https://github.com/alfoll/cleaning-app-android
```

## Безопасность

Не коммитить:

- Firebase Admin SDK JSON;
- `.env` с реальными паролями;
- production JDBC URL с доступами;
- ключи и токены Firebase.

Для публичного репозитория оставлять только `.env.*.example` с шаблонными значениями.
