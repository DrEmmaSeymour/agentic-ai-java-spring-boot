# Agentic AI Java Spring Boot Task API

A task management REST API built with **Java 25** and **Spring Boot 4**. It was developed with an
**agentic AI workflow**: an AI coding agent planned, generated, implemented, tested, and verified the
code, while a human developer reviewed and approved each step.

The API is small on purpose. It shows a clean controller–service architecture, request validation,
standard error responses, thread-safe in-memory storage, and a layered test suite.

---

## Contents

- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [REST API](#rest-api)
- [Error handling](#error-handling)
- [Testing strategy](#testing-strategy)
- [Agentic AI development workflow](#agentic-ai-development-workflow)
- [Design decisions](#design-decisions)
- [Possible extensions](#possible-extensions)
- [License](#license)

---

## Features

- Create, list, and update the status of tasks over HTTP/JSON
- Request validation with Jakarta Bean Validation
- Consistent [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail` error responses
- Thread-safe in-memory storage backed by `ConcurrentHashMap`
- Fast unit tests plus web-layer slice tests with MockMvc

## Technology stack

| Concern        | Technology                                         |
|----------------|----------------------------------------------------|
| Language       | Java 25                                            |
| Framework      | Spring Boot 4.1 (Spring MVC)                       |
| Build          | Maven (via the included Maven Wrapper)             |
| Validation     | Jakarta Bean Validation (Hibernate Validator)      |
| Testing        | JUnit 5, Spring MockMvc, Mockito, AssertJ          |
| Storage        | In-memory `ConcurrentHashMap`                      |

## Architecture

```
            HTTP request
                 │
                 ▼
┌──────────────────────────────────┐
│ TaskController  (@RestController)│  HTTP mapping, @Valid request bodies,
│                                  │  status codes and Location headers
└────────────────┬─────────────────┘
                 │ calls
                 ▼
┌──────────────────────────────────┐
│ TaskService     (@Service)       │  Business rules, ID/timestamp creation,
│                                  │  atomic status updates
└────────────────┬─────────────────┘
                 │ reads / writes
                 ▼
┌──────────────────────────────────┐
│ ConcurrentHashMap<UUID, Task>    │  Thread-safe in-memory store
└──────────────────────────────────┘

ApiExceptionHandler (@RestControllerAdvice) turns exceptions into ProblemDetail responses.
```

### Project layout

```
src/main/java/com/example/taskapi/
├── TaskApiApplication.java          # Spring Boot entry point
└── task/
    ├── Task.java                    # Immutable domain record
    ├── TaskStatus.java              # TODO | IN_PROGRESS | DONE
    ├── CreateTaskRequest.java       # POST body + validation rules
    ├── UpdateStatusRequest.java     # PATCH body + validation rules
    ├── TaskController.java          # REST endpoints
    ├── TaskService.java             # Business logic + storage
    ├── TaskNotFoundException.java   # Domain exception → 404
    └── ApiExceptionHandler.java     # Exception → ProblemDetail mapping

src/test/java/com/example/taskapi/
├── TaskApiApplicationTests.java     # Application context smoke test
└── task/
    ├── TaskServiceTest.java         # Pure unit tests (no Spring)
    └── TaskControllerTest.java      # @WebMvcTest slice tests
```

## Getting started

### Prerequisites

- **JDK 25** on your `PATH` (or set `JAVA_HOME`)
- No separate Maven install is needed, because the Maven Wrapper downloads the right version.

### Build and test

```bash
# macOS / Linux
./mvnw clean verify

# Windows
mvnw.cmd clean verify
```

### Run the application

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The API starts on **http://localhost:8080**.

## REST API

Base path: `/tasks`

| Method  | Path          | Description             | Success                                | Errors     |
|---------|---------------|-------------------------|----------------------------------------|------------|
| `POST`  | `/tasks`      | Create a task           | `201 Created` + `Location` header       | `400`      |
| `GET`   | `/tasks`      | List all tasks          | `200 OK` (sorted by creation time)      | —          |
| `PATCH` | `/tasks/{id}` | Update a task's status  | `200 OK`                                | `400`, `404` |

### Task model

```json
{
  "id": "4c7ee2cc-6106-4bb4-909e-4ff9caf3b373",
  "title": "Write API docs",
  "description": "Document the REST endpoints",
  "status": "TODO",
  "createdAt": "2026-01-01T10:00:00Z"
}
```

`status` is one of `TODO`, `IN_PROGRESS`, `DONE`. New tasks always start as `TODO`.

### Validation rules

| Field         | Rule                                  |
|---------------|---------------------------------------|
| `title`       | Required, not blank, max 100 chars    |
| `description` | Optional, max 500 chars               |
| `status`      | Required on PATCH, must be a valid enum value |

### Examples

**Create a task**

```bash
curl -i -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Write API docs", "description": "Document the REST endpoints"}'
```

```http
HTTP/1.1 201 Created
Location: /tasks/4c7ee2cc-6106-4bb4-909e-4ff9caf3b373
Content-Type: application/json

{"id":"4c7ee2cc-...","title":"Write API docs","description":"Document the REST endpoints","status":"TODO","createdAt":"..."}
```

**List tasks**

```bash
curl http://localhost:8080/tasks
```

**Update a task's status**

```bash
curl -X PATCH http://localhost:8080/tasks/4c7ee2cc-6106-4bb4-909e-4ff9caf3b373 \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}'
```

> **Windows PowerShell:** use `curl.exe` instead of `curl`, and escape the inner double quotes in JSON
> bodies, or save the body to a file and pass it with `-d "@body.json"`.

## Error handling

All errors use the standard `application/problem+json` format.

**Validation failure (`400`)**, which includes a per-field `errors` map:

```json
{
  "title": "Validation error",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/tasks",
  "errors": { "title": "title must not be blank" }
}
```

**Unknown task (`404`)**

```json
{
  "title": "Task not found",
  "status": 404,
  "detail": "Task not found: 00000000-0000-0000-0000-000000000000",
  "instance": "/tasks/00000000-0000-0000-0000-000000000000"
}
```

Malformed JSON, invalid enum values such as `"status": "FINISHED"`, and invalid UUIDs in the path all
return `400 Bad Request`. Spring's `ResponseEntityExceptionHandler` handles these, and
`ApiExceptionHandler` extends it.

## Testing strategy

The tests follow the testing pyramid, so that most coverage comes from the fastest tests.

| Layer               | Test class                 | Approach                                                                 |
|---------------------|----------------------------|--------------------------------------------------------------------------|
| Business logic      | `TaskServiceTest`          | Plain JUnit 5, no Spring context. A fixed `Clock` makes timestamps deterministic. Covers creation defaults, listing, status updates, not-found errors, and a 1,000-task concurrent-write test. |
| Web layer           | `TaskControllerTest`       | `@WebMvcTest` slice with `MockMvc` and a `@MockitoBean` service. Checks status codes, `Location` headers, JSON bodies, and validation and error responses. |
| Application wiring  | `TaskApiApplicationTests`  | `@SpringBootTest` smoke test that confirms the full context starts.      |

Run the full suite:

```bash
./mvnw test
```

### Mockito on modern JDKs

Since JDK 21, the JVM warns when an agent attaches itself to a running JVM, and future JDKs will block
this ([JEP 451](https://openjdk.org/jeps/451)). Mockito's inline mock maker normally does exactly that. The
`pom.xml` uses the configuration that Mockito documents instead:

1. `maven-dependency-plugin:properties` exposes the Mockito jar path as `${org.mockito:mockito-core:jar}`.
2. Surefire loads that jar with `-javaagent:` when the test JVM starts.

The test output is clean on Java 25 and will keep working on future JDKs.

## Agentic AI development workflow

This project was built with an AI coding agent, [Claude Code](https://claude.com/claude-code). A human
developer directed and reviewed the work. The workflow was a loop of explicit, checkable steps rather
than one large prompt:

1. **Inspect and plan.** The agent inspected the empty workspace and the installed JDK and Maven, then
   wrote an implementation plan covering the architecture, endpoints, error handling, and tests. No
   files were created until the developer approved the plan.
2. **Generate.** The agent created the project skeleton with Spring Initializr instead of writing build
   files from memory, so the dependency names matched the current Spring Boot release.
3. **Implement.** It wrote the domain records, the service, the controller, and the exception handler,
   following the approved controller–service design.
4. **Test.** It wrote unit tests and web slice tests, then ran the full Maven suite and read the
   Surefire reports.
5. **Verify end to end.** It started the application and called every endpoint with `curl`, covering
   both success and error paths, then shut the server down.
6. **Review and harden.** It audited the tracked files and Git history for personal data and secrets
   before publishing, cleaned up the build metadata, and removed tool warnings.

Principles followed throughout:

- **Human in the loop.** Each phase ended with the developer reviewing the result. Nothing was
  committed or pushed without explicit approval.
- **Verify, don't assume.** Every claim that something works is backed by a test run or a live request.
- **Small, reviewable changes.** Diffs were shown before each commit.
- **Transparent attribution.** Commits written with the agent carry a `Co-Authored-By` trailer.

## Design decisions

- **Immutable records.** `Task` and the request DTOs are Java records. A status change creates a new
  `Task` with `withStatus(...)` instead of mutating shared state.
- **Validation on DTOs, not the domain.** Rules about API input live on `CreateTaskRequest` and
  `UpdateStatusRequest`, so the domain model stays free of transport concerns.
- **Atomic updates.** `ConcurrentHashMap.computeIfPresent` performs the check-and-replace in one step,
  so concurrent PATCH requests cannot overwrite each other's changes.
- **Constructor injection.** Dependencies are explicit and final, and the service can be built in plain
  unit tests without Spring.
- **Injectable `Clock`.** Timestamps are deterministic in tests.
- **Standard errors.** `ProblemDetail` gives clients a single, standards-based error format.

## Possible extensions

- Persistence with Spring Data JPA, plus Testcontainers for integration tests
- `GET /tasks/{id}`, `DELETE /tasks/{id}`, and full `PUT` updates
- Pagination and filtering by status
- OpenAPI/Swagger documentation with `springdoc-openapi`
- A GitHub Actions CI workflow and a code coverage report with JaCoCo
- A container image built with `./mvnw spring-boot:build-image`

## License

Released under the [MIT License](LICENSE).
