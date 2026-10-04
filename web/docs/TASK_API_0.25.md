# Task API 0.25

Task 0.25 provides authenticated task submission and observation APIs plus an
administrator-only control plane. The canonical machine-readable contract is
[`src/main/resources/openapi/task-0.25.yaml`](../src/main/resources/openapi/task-0.25.yaml).

## Authentication

All Task 0.25 endpoints require `Authorization: Bearer <JWT>`. Project task
operations additionally enforce project access. Routes under `/api/v1/admin`
require the JWT `role` claim to be `ADMIN`.

## Task operations

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/tasks/pipeline` | Submit a registered pipeline task |
| GET | `/api/v1/tasks/{taskId}` | Read current task state |
| POST | `/api/v1/tasks/{taskId}/cancel` | Request cooperative cancellation |
| GET | `/api/v1/tasks/{taskId}/timeline` | Read the ordered audit timeline |
| GET | `/api/v1/projects/{projectId}/tasks` | List tasks by project/status/page |
| GET | `/api/v1/projects/{projectId}/tasks/events` | Receive lifecycle events over SSE |

SSE emits `id`, `event`, and JSON `data` fields. Clients should reconnect and
use the timeline endpoint to recover any events missed while disconnected.

## Administrator operations

| Area | Paths |
|---|---|
| Scheduler | `/api/v1/admin/task-scheduler`, `/start`, `/stop`, `/workers` |
| Metrics | `/api/v1/admin/task-metrics` |
| Manual retry | `/api/v1/admin/tasks/{taskId}/requeue` |
| Retention | `/api/v1/admin/task-retention`, `/estimate`, `/cleanup`, `/last-result` |

Manual retry accepts only `FAILED` and `TIMED_OUT` tasks. Manual retention
cleanup returns `409 Conflict` when another cleanup is already running.

## Documentation endpoints

- OpenAPI: `/api/v1/openapi/task-0.25.yaml`
- Human-readable index: `/api/v1/docs/task-0.25`
