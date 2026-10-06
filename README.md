# REST API Системы управления сотрудниками (Микросервисы)

> Распределённый бэкенд на **Spring Boot 4.1.1 / Spring Cloud 2025.1.3 / Java 21 LTS**, реализующий микросервисную архитектуру с Service Discovery, Config Server, API Gateway, реактивным стеком (Reactor + R2DBC / Reactor + JPA) и межсервисным взаимодействием (Feign Client + Circuit Breaker).
> 
> **Лабораторная работа №2** по дисциплине «Высокопроизводительные системы».

---

##  Содержание
1. [Архитектура системы](#-архитектура-системы)
2. [Обзор модулей и сервисов](#-обзор-модулей-и-сервисов)
3. [Реализация требований ТЗ Лабораторной №2](#-реализация-требований-тз-лабораторной-2)
4. [Интерактивная документация (Swagger UI)](#-интерактивная-документация-swagger-ui)
5. [Быстрый старт и запуск](#-быстрый-старт-и-запуск)
6. [Декомпозиция задач и TODO](#-декомпозиция-задач-и-todo)

---

##  Архитектура системы

```
                              [ Клиент / Frontend / Swagger UI ]
                                              │ :8080
                                              ▼
                                   ┌──────────────────────┐
                                   │   Gateway Service    │
                                   │(Spring Cloud Gateway)│
                                   └──────────┬───────────┘
                                              │ (lb:// routes)
               ┌──────────────────────────────┼──────────────────────────────┐
               │ :8081                        │ :8082                        │ :8083
               ▼                              ▼                              ▼
    ┌──────────────────────┐       ┌──────────────────────┐       ┌──────────────────────┐
    │   Employee Service   │◄──────┤  Attendance Service  │       │   Schedule Service   │
    │ (Spring Data JPA)    │ Feign │   (Reactor + R2DBC)  │       │ (Reactor + JPA + CB) │
    └──────────┬───────────┘  + CB └──────────┬───────────┘       └──────────┬───────────┘
               │                              │                              │
               ▼                              ▼                              ▼
    [ PostgreSQL (JPA) ]           [ PostgreSQL (R2DBC) ]         [ PostgreSQL (JPA) ]

               ▲                              ▲                              ▲
               └──────────────────────────────┼──────────────────────────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      │                                               │
             ┌─────────────────┐                             ┌─────────────────┐
             │Discovery Service│                             │ Config Service  │
             │ (Eureka Server) │                             │ (Config Server) │
             │      :8761      │                             │      :8888      │
             └─────────────────┘                             └─────────────────┘
```

---

##  Обзор модулей и сервисов

| Модуль | Порт | Стек / Технологии | Назначение |
| :--- | :---: | :--- | :--- |
| **`common-dto`** | — | Jackson, Jakarta Validation, Swagger Annotations | Общая библиотека моделей, Request/Response DTO, Enums (`RoleCode`, `EmployeeStatus`, `RequestType` и т.д.) и пагинации `SliceResponse`. |
| **`discovery-service`** | `8761` | Netflix Eureka Server, Spring Boot Actuator | Service Discovery: единый реестр адресов и инстансов микросервисов. |
| **`config-service`** | `8888` | Spring Cloud Config Server, Native Profile | Централизованное хранилище конфигурационных файлов (`config-repo/`) для всех сервисов. |
| **`gateway-service`** | `8080` | Spring Cloud Gateway (MVC), Springdoc OpenAPI | Единая точка входа в систему, маршрутизация запросов к микросервисам по Eureka-именам (`lb://`), проброс CORS и агрегация Swagger UI. |
| **`employee-service`** | `8081` | Spring Web, Spring Data JPA, PostgreSQL, Flyway | Управление персоналом: компании, филиалы сети, справочник должностей, сотрудники, назначения и учетные записи пользователей. |
| **`attendance-service`** | `8082` | **Spring WebFlux, Reactor, Spring Data R2DBC, PostgreSQL R2DBC** | Учет фактического рабочего времени: фиксация прихода (`Check-In`) и ухода (`Check-Out`), бесконечная реактивная лента посещений (`Mono`/`Flux`). |
| **`schedule-service`** | `8083` | **Spring WebFlux, Reactor, Spring Data JPA, OpenFeign, Resilience4j** | Управление расписаниями и сменами: реактивные мосты для тяжелых JPA-запросов (`Schedulers.boundedElastic()`), вызовы `employee-service` через Feign Client с Circuit Breaker и fallback-заглушкой. |

---

##  Реализация требований ТЗ Лабораторной №2

1. **Регистрация в Eureka:**
   - Все микросервисы (`employee-service`, `attendance-service`, `schedule-service`, `gateway-service`, `config-service`) подключают `spring-cloud-starter-netflix-eureka-client` и регистрируются в `discovery-service` (`:8761`).
2. **Конфигурация через Config Server:**
   - Настроен `config-service` (`:8888`), читающий конфигурации из каталога [`config-repo`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/config-repo). Все сервисы подключают `spring-cloud-starter-config`.
3. **Единая точка входа через Spring Gateway:**
   - Сервис `gateway-service` слушает порт `8080` и маршрутизирует:
     - `/api/companies/**`, `/api/branches/**`, `/api/positions/**`, `/api/employees/**`, `/api/users/**` $\to$ `lb://employee-service`
     - `/api/attendance/**` $\to$ `lb://attendance-service`
     - `/api/schedules/**`, `/api/shifts/**`, `/api/absences/**`, `/api/requests/**` $\to$ `lb://schedule-service`
4. **Межсервисное взаимодействие (Feign Client):**
   - В `schedule-service` реализован [`EmployeeClient`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/schedule-service/src/main/java/portal/schedule/client/EmployeeClient.java) с аннотацией `@FeignClient(name = "employee-service")` для проверки данных сотрудника при планировании смен.
5. **Отказоустойчивость (Circuit Breaker):**
   - Интегрирован `Resilience4j` с fallback-классом [`EmployeeClientFallback`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/schedule-service/src/main/java/portal/schedule/client/EmployeeClientFallback.java). При недоступности сервиса сотрудников запрос не блокирует поток и возвращает резервный ответ.
6. **Микросервис на Reactor + R2DBC:**
   - Реализован [`attendance-service`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/attendance-service) с реактивным неблокирующим драйвером PostgreSQL (`r2dbc-postgresql`), реактивными репозиториями `R2dbcRepository` и контроллерами на `Mono`/`Flux`.
7. **Микросервис на Reactor + Spring Data JPA/JDBC:**
   - Реализован [`schedule-service`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/schedule-service) с реактивным сервисом [`ReactiveScheduleBridgeService`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/schedule-service/src/main/java/portal/schedule/service/ReactiveScheduleBridgeService.java), где синхронные операции JPA изолированы в специальном пуле потоков:
     ```java
     Mono.fromCallable(() -> shiftRepository.findById(id))
         .subscribeOn(Schedulers.boundedElastic());
     ```

---

##  Интерактивная документация (Swagger UI)

Благодаря агрегации в Gateway, вся документация API доступна в одном окне:
* **Единый Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* В верхнем правом выпадающем списке доступно переключение между спецификациями:
  * **Employee Service** (`/v3/api-docs/employee-service`)
  * **Attendance Service (R2DBC)** (`/v3/api-docs/attendance-service`)
  * **Schedule Service (WebFlux + JPA)** (`/v3/api-docs/schedule-service`)

---

##  Быстрый старт и запуск

### Вариант 1: Сборка и локальная компиляция
```bash
# Собрать все модули проекта
./mvnw clean compile
```

### Вариант 2: Запуск всей системы в Docker Compose
```bash
docker compose up --build
```
*Поднимутся:* PostgreSQL, Eureka Server (`:8761`), Config Server (`:8888`), Gateway (`:8080`) и 3 бизнес-сервиса (`:8081`, `:8082`, `:8083`).

---

## 📋Декомпозиция задач и TODO

Система подготовлена для командной работы и разделена по зонам ответственности:

###  Сделано (Архитектура и сервисы):
- [x] Декомпозиция монолита на модули Maven multi-module.
- [x] Выделение общего модуля контрактов `common-dto`.
- [x] Реализация Service Discovery (`discovery-service` на Netflix Eureka).
- [x] Реализация Centralized Configuration (`config-service` + `config-repo`).
- [x] Настройка API Gateway (`gateway-service`) с CORS и роутингом `lb://`.
- [x] Выделение сервиса `employee-service` (Spring Data JPA + Flyway).
- [x] Реализация сервиса `attendance-service` на **Reactor + R2DBC**.
- [x] Реализация сервиса `schedule-service` на **Reactor + JPA + Feign Client + Circuit Breaker**.
- [x] Агрегация Swagger UI на Gateway по всем микросервисам.
- [x] Мультистейдж `Dockerfile` и оркестрация в `compose.yaml`.

---

###  TODO для Лёши (Тестирование):
- [ ] **Тесты для `common-dto`:**
  - [ ] Сериализация / десериализация DTO через Jackson (проверка кастомных дат и Enums).
- [ ] **Тесты для `employee-service`:**
  - [ ] Модульные тесты сервисов (`EmployeeServiceTest`, `BranchServiceTest`).
  - [ ] Интеграционные тесты с Testcontainers PostgreSQL (`@SpringBootTest`).
- [ ] **Тесты для реактивного `attendance-service`:**
  - [ ] Тестирование реактивных потоков `Mono`/`Flux` с использованием `StepVerifier`.
  - [ ] Интеграционные тесты реактивного репозитория R2DBC.
- [ ] **Тесты для `schedule-service`:**
  - [ ] Тестирование `ReactiveScheduleBridgeService` с моками `EmployeeClient`.
  - [ ] Тестирование сценариев Circuit Breaker: имитация падения `employee-service` и проверка срабатывания `EmployeeClientFallback`.
  - [ ] Тестирование транзакционных операций создания и отклонения отпусков/смен.

---

###  TODO для Насти (Интеграция):
- [ ] **Сквозное ручное/E2E тестирование через Gateway:**
  - [ ] Тыкай на ошибки и всё что можно
  более локальные 
  - [ ] Выполнить запрос в `schedule-service` и убедиться, что Circuit Breaker корректно отдает fallback-ответ без зависания шлюза.
  - [ ] Убедиться, что фронтенд из папки [`frontend/`](file:///Users/devianted/Desktop/Учёба/Впст/lab2/frontend) работает
  - [ ] Описать диаграмму декомпозиции сервисов.
  - [ ] потыкать Swagger UI и логи Circuit Breaker.
