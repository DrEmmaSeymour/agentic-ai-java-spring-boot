# Agentic AI Java Spring Boot Task API

A task management REST API built with **Java 25** and **Spring Boot 4**. It was developed with an
**agentic AI workflow**: an AI coding agent planned, generated, implemented, tested, and verified the
code, while a human developer reviewed and approved each step.

The API is small on purpose. It shows a clean controller–service architecture, request validation,
standard error responses, thread-safe in-memory storage, and a layered test suite.

It also includes an **AI task agent** (`POST /agent/chat`). The agent takes natural-language requests
such as *"I finished the quarterly report, mark it done"* and carries them out through **LLM tool
calling**: Claude decides which tools to call, and the tools run the existing `TaskService` code.

---

## Contents

- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [REST API](#rest-api)
- [AI task agent](#ai-task-agent)
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
- Natural-language task management with Claude, using real tool calling through Spring AI
- Safeguards on what the agent can change: limited tools, a per-request write limit, a tool-call limit,
  and an audit list of every change
- Fast unit tests, web-layer slice tests, and agent integration tests against a fake Anthropic API,
  so no API key and no paid calls are needed

## Technology stack

| Concern        | Technology                                         |
|----------------|----------------------------------------------------|
| Language       | Java 25                                            |
| Framework      | Spring Boot 4.1 (Spring MVC)                       |
| Build          | Maven (via the included Maven Wrapper)             |
| Validation     | Jakarta Bean Validation (Hibernate Validator)      |
| AI integration | Spring AI 2.0.2 (`spring-ai-starter-model-anthropic`), built on the official Anthropic Java SDK |
| LLM            | Claude Haiku 5.5 by default (configurable, e.g. Claude Opus 5.5) |
| Testing        | JUnit 5, Spring MockMvc, Mockito, AssertJ, JDK `HttpServer` as a fake Anthropic API |
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

The AI agent is a second entry point into the same `TaskService`. See
[Agent architecture](#agent-architecture).

### Project layout

```
src/main/java/com/example/taskapi/
├── TaskApiApplication.java          # Spring Boot entry point
├── agent/
│   ├── AgentController.java         # POST /agent/chat
│   ├── TaskAgentService.java        # Runs the request through Claude; maps provider errors
│   ├── TaskTools.java               # @Tool methods → TaskService, with per-request limits
│   ├── AgentConfiguration.java      # ChatClient bean + system prompt
│   ├── AgentProperties.java         # task-agent.* settings (model, effort, limits)
│   ├── AgentChatRequest.java        # Request body + validation
│   ├── AgentChatResponse.java       # Reply + list of changes made
│   ├── AgentAction.java             # One recorded change
│   └── AgentUnavailableException.java  # Provider failure → 503
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
├── agent/
│   ├── TaskToolsTest.java           # Tool unit tests (validation, limits, delegation)
│   ├── TaskAgentServiceTest.java    # Provider-error translation (mocked ChatClient)
│   ├── AgentControllerTest.java     # @WebMvcTest slice tests
│   ├── TaskAgentIntegrationTest.java  # Full tool-calling loop against a fake Anthropic API
│   ├── FakeAnthropicServer.java     # Scripted Messages API stub (JDK HttpServer)
│   └── TaskAgentLiveSmokeTest.java  # Optional real-API test, off by default
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

## AI task agent

`POST /agent/chat` accepts a natural-language message. Claude, called through Spring AI, chooses
which tools to call. The tools are ordinary Java methods that call the existing `TaskService`, so the
agent follows exactly the same business rules and validation as the REST API.

There is **no keyword matching or scripted intent detection**: the model reads the tool definitions
(names, descriptions and JSON schemas) and makes real `tool_use` calls.

### Agent architecture

```
POST /agent/chat {"message": "..."}
        │
        ▼
AgentController ── @Valid (not blank, ≤ 2000 chars)
        │
        ▼
TaskAgentService ── creates a new TaskTools for this request (its own limits and change log)
        │
        ▼
Spring AI ChatClient ── system prompt + tool definitions + model/effort options
        │
        ▼
AnthropicChatModel ── official Anthropic Java SDK ──► Claude (Messages API)
        ▲                                               │
        │            tool-calling loop                  │ tool_use: create_task {...}
        │◄──────────────────────────────────────────────┘
        ▼
TaskTools ── list_tasks · create_task · update_task_status
        │      (validation, write limit, tool-call limit, change log)
        ▼
TaskService ── existing business logic and storage
```

Spring AI runs the loop. When Claude replies with a `tool_use` block, Spring AI invokes the matching
`@Tool` method, sends the result back as a `tool_result`, and repeats until Claude gives a final
answer. The response contains Claude's reply and an `actions` list of every change made.

| Tool                 | What it does                                  | Delegates to                |
|----------------------|-----------------------------------------------|-----------------------------|
| `list_tasks`         | Lists tasks, optionally filtered by status    | `TaskService.findAll()`     |
| `create_task`        | Creates one task (starts as `TODO`)           | `TaskService.create(...)`   |
| `update_task_status` | Changes one task's status by exact id         | `TaskService.updateStatus(...)` |

### Configuration

The API key is read **only** from the `ANTHROPIC_API_KEY` environment variable. It is never written to
a file in this repository.

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # Windows PowerShell: $env:ANTHROPIC_API_KEY = "sk-ant-..."
./mvnw spring-boot:run
```

All agent settings are in `application.properties` and can be overridden with environment variables:

| Property                               | Environment variable                    | Default            |
|----------------------------------------|-----------------------------------------|--------------------|
| `task-agent.model`                     | `TASK_AGENT_MODEL`                      | `claude-haiku-5-5` |
| `task-agent.effort`                    | `TASK_AGENT_EFFORT`                     | `low`              |
| `task-agent.max-tokens`                | `TASK_AGENT_MAX_TOKENS`                 | `16000`            |
| `task-agent.max-writes-per-request`    | `TASK_AGENT_MAX_WRITES_PER_REQUEST`     | `3`                |
| `task-agent.max-tool-calls-per-request`| `TASK_AGENT_MAX_TOOL_CALLS_PER_REQUEST` | `10`               |

To switch to the more capable Claude Opus 5.5:

```bash
export TASK_AGENT_MODEL=claude-opus-5-5
```

**Cost.** The agent uses the paid Anthropic API, billed per token. At the time of writing:

| Model | Input / output price per 1M tokens | Typical chat request* |
|---|---|---|
| Claude Haiku 5.5 (default) | $0.10 / $0.50 | well under $0.001 |
| Claude Opus 5.5 | $4 / $20 | roughly $0.02–$0.05 |

\*Estimate: 2–3 model round trips, a few thousand input tokens and up to ~1,500 output tokens. Check
[Anthropic's pricing page](https://www.anthropic.com/pricing) for current rates. The automated test
suite makes **no** paid calls.

### Example requests

```bash
# Create
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"message": "Add a task to prepare the quarterly report, due Friday"}'

# Query
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"message": "What do I still have to do?"}'

# Update
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"message": "I have started the quarterly report"}'
```

Example response:

```json
{
  "reply": "I've marked \"Prepare the quarterly report\" as IN_PROGRESS.",
  "actions": [
    {
      "type": "STATUS_UPDATED",
      "taskId": "4c7ee2cc-6106-4bb4-909e-4ff9caf3b373",
      "title": "Prepare the quarterly report",
      "status": "IN_PROGRESS"
    }
  ]
}
```

The `reply` text is written by the model, so its wording varies. The `actions` list is produced by the
application, and it is the reliable record of what changed.

### Safeguards

| Risk | Safeguard |
|---|---|
| Destructive or unexpected operations | Least privilege: there is no delete tool and no way to edit titles. The agent can only list, create, and change status. |
| Bypassing business rules | Tools call `TaskService` and validate input with the same Jakarta constraints (`CreateTaskRequest`, `UpdateStatusRequest`) as the REST API. |
| Bulk or runaway changes | A per-request **write limit** (default 3). Extra writes are refused and the model is told they were not made. |
| Runaway tool loops and cost | A per-request **tool-call limit** (default 10). |
| Changing the wrong task | Updates need an exact task id. Unknown or malformed ids are rejected. The system prompt tells the model to look up ids first, never guess, and ask when a request is ambiguous. |
| Prompt injection through task content | The system prompt marks task titles and descriptions as data that must never be followed as instructions, and the tools above limit what an injected instruction could do anyway. |
| Silent changes | Every change is returned in `actions`. |
| Provider failures | A missing or invalid key, rate limiting, or an outage returns `503` with a `ProblemDetail` body and no internal details. If the model declines a request (a refusal), the API returns a safe reply instead of an error. |
| Leaked secrets | The key comes only from the environment, and `.env` files are ignored by Git. |

### Limitations

- **No conversation memory.** Each request is independent. If the agent asks a clarifying question,
  the next message must include the details again.
- **Model output is non-deterministic.** Wording varies between runs, and the model can still
  misunderstand a request. The safeguards limit the impact but are not a guarantee.
- **The safeguards apply per request.** A client sending many requests can still make many changes.
  Rate limiting and authentication belong at the API-gateway level and are out of scope here.
- **No refusal fallback.** Anthropic offers a server-side fallback to another model when a request is
  refused, but Spring AI 2.0.2 cannot send that setting. Refusals return a fixed safe reply instead.
- **No authentication.** The endpoint is open, like the rest of this demo API. Don't expose it publicly
  with a funded API key.
- **In-memory storage.** Tasks are lost on restart.

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
| Agent tools         | `TaskToolsTest`            | Plain unit tests: delegation to `TaskService`, validation, unknown and malformed ids, the write limit, the tool-call limit, and per-request isolation. |
| Agent errors        | `TaskAgentServiceTest`     | Mocked `ChatClient`. Checks that provider failures (missing key, network error) become `503`, and that application bugs are not hidden. |
| Agent web layer     | `AgentControllerTest`      | `@WebMvcTest` slice: reply and actions JSON, `400` for blank or too-long messages, `503` mapping. |
| Agent end to end    | `TaskAgentIntegrationTest` | `@SpringBootTest` with the real Spring AI client, Anthropic SDK and tool-calling loop, pointed at `FakeAnthropicServer`. Scripted responses cover create, list-then-update, the write limit, invalid tool input, refusal, `401` and `529`. It also checks the exact JSON sent to the API: model, effort, tool definitions, no sampling parameters, and tool results. |
| Live API (optional) | `TaskAgentLiveSmokeTest`   | One real request to Claude. **Skipped by default.** |

Run the full suite. It needs **no API key** and makes **no paid calls**:

```bash
./mvnw test
```

### Mocking the AI

`FakeAnthropicServer` is a small stub built on the JDK's `HttpServer`. It implements just enough of the
Anthropic Messages API to return scripted responses, such as a `tool_use` turn followed by a final
answer, and it records every request. Pointing `spring.ai.anthropic.base-url` at it means the tests
exercise all of the real code (Spring AI, the official SDK and the tool loop) without a network call,
and they are deterministic.

### Optional live smoke test

To check the real integration end to end, run one paid request (a fraction of a cent on Haiku 5.5).
**Both** variables are required, so having a key in your environment never triggers a paid call by
accident:

```bash
ANTHROPIC_API_KEY=sk-ant-... TASK_AGENT_LIVE_TEST=true ./mvnw test -Dtest=TaskAgentLiveSmokeTest
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
7. **Extend with an AI agent.** Before writing any code, the agent checked compatibility rather than
   assuming it. From Maven Central it confirmed that Spring AI 2.0.2 was the latest stable release, and
   that its Anthropic module is compiled against Spring Boot 4.1.2. It then read Spring AI's source
   code to confirm how options, sampling parameters and tool errors are handled. The plan, including
   provider, cost and safeguards, was approved before implementation, and a run of the real app without
   an API key was used to check the error handling.

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
- **Tools are a thin layer.** `TaskTools` holds no business logic. It adapts the model's input to
  `TaskService` and enforces agent-specific limits, so the REST API and the agent cannot drift apart.
- **One `TaskTools` instance per request.** Limits and the change log are per request, without shared
  mutable state or thread-local tricks.
- **Explicit model options.** Spring AI uses per-request options as-is, so every request sets the
  model, max tokens and effort from `task-agent.*`. That way there is one source of truth for which
  model is called, and the integration test asserts it.

## Possible extensions

- Persistence with Spring Data JPA, plus Testcontainers for integration tests
- `GET /tasks/{id}`, `DELETE /tasks/{id}`, and full `PUT` updates
- Pagination and filtering by status
- OpenAPI/Swagger documentation with `springdoc-openapi`
- A GitHub Actions CI workflow and a code coverage report with JaCoCo
- A container image built with `./mvnw spring-boot:build-image`
- Conversation memory for the agent (Spring AI `ChatMemory`) keyed by a conversation id
- A confirmation step that requires human approval before the agent applies changes
- Authentication and per-user rate limiting in front of `/agent/chat`

## License

Released under the [MIT License](LICENSE).
