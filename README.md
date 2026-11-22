# EDB — Experiment Data Bridge

<!--
CI badge: replace <owner>/<repo> with the real repository slug, then uncomment.

[![CI](https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg)](https://github.com/<owner>/<repo>/actions/workflows/ci.yml)
-->

EDB is a self-hosted platform for running reproducible data-processing work over
experiment data. It queues and schedules tasks, tracks every uploaded data file
with provenance, executes versioned workflow definitions, produces reports, and
lets an LLM agent **propose** workflows that a human must explicitly approve
before anything runs.

## Project status

Read this before depending on the project.

| Area | State |
|---|---|
| `core` (tasks, scheduler, workflow IR, data files, reports, plugins) | Implemented, covered by the active test suite |
| `web` (Ktor + JWT + SSE API surface) | Implemented, documented in [develop.md](develop.md) |
| `cli` | Implemented |
| `agent` (planning, proposal approval) | Implemented, fake provider plus an OpenAI-compatible provider |
| `web-ui` | **Functional, but demo-grade visually.** No design system yet, hand-written CSS, thin coverage (2 unit tests) |
| `desktop` (Tauri 2 shell) | Early. Not part of the Gradle build; no installers or updates |
| Optional integrations | Docker workers, Python worker, R worker — all optional, off by default |

Known gaps, tracked rather than hidden:

- 46 test sources are disabled (`.disabled` suffix), including duplicates left
  behind by an interrupted refactor. See [CHANGELOG.md](CHANGELOG.md).
- The project version lives in more than one place; `packaging/ci/check-repo.mjs`
  guards consistency in CI, but the duplication itself is still there.
- `web/src/main/kotlin/.../Application.kt` reports a hard-coded version string
  from `/api/v1/health`.
- `web/src/main/resources/application.yml` still names the pre-rename module
  `com.experiment.bridge.web.ApplicationKt`. It is dead configuration (the server
  is started with `embeddedServer`, not `EngineMain`), but it is misleading.
- `THIRD-PARTY-LICENSES.md` has unresolved entries; see [NOTICE](NOTICE).
- `./gradlew build` and `./gradlew test` currently fail because the `web`
  module's **test** sources do not compile (API drift after the Docker worker
  rebuild feature landed). `./gradlew packageDemoV1` is unaffected — it never
  compiles test sources. Details in [CHANGELOG.md](CHANGELOG.md).

## Architecture

```text
web (Ktor app, routes, lifecycle, DI)
├── agent (controlled workflow planner)
│   └── core
│       └── plugin-api
└── core (domain model, services, persistence, Task/DataFile/Workflow)
    └── plugin-api (small, stable third-party SPI)

cli (standalone HTTP client) ── core, plugin-api
third-party plugin.jar ──────── plugin-api only
```

Dependency rules, the full HTTP contract-evolution policy and the Plugin
Contract v1 specification live in [develop.md](develop.md). That document is the
authoritative guide for contributors; this README only summarises.

| Module | Kotlin files | ~LOC |
|---|---|---|
| `core` | 481 | 24,700 |
| `web` | 153 | 9,100 |
| `cli` | 59 | 6,300 |
| `agent` | 58 | 4,600 |
| `plugin-api` | 6 | 155 |
| `web-ui` (TypeScript) | 53 | 2,500 |

## Requirements

- JDK 21
- MySQL 8 (the application runs Flyway migrations on startup)
- Node.js 20+ and npm (web console)
- Docker is optional and only needed for containerised analysis workers
- Windows is currently required for the packaging task (PowerShell + `start.bat`)

> **Windows path caveat — verified, not theoretical.** Gradle 8.13 writes
> test-worker argument files in the platform ANSI code page (GBK on a zh-CN
> system) while the JDK reads `@argfile` contents as UTF-8. If the checkout path
> contains non-ASCII characters, every test task fails with
> `ClassNotFoundException` for the project's own test classes even though the
> class files exist on disk — compilation succeeds and only the test worker
> breaks. Check out and build from an ASCII-only path (for example `D:\edb`), or
> enable Windows *Use Unicode UTF-8 for worldwide language support*.

## Quick start (from source)

```bash
git clone <your-fork-url> edb
cd edb
```

1. Create an empty MySQL database and a user allowed to run migrations.

2. Create the runtime configuration. `loadConfig()` reads
   `config/application.yml` relative to the working directory and falls back to
   the packaged default:

   ```bash
   mkdir -p config
   cp web/src/main/resources/application.yml config/application.yml
   ```

   Then set the database credentials, a real JWT secret and the initial admin
   account. Never commit this file — it is gitignored for that reason.

3. Start the server:

   ```bash
   ./gradlew :web:run          # Windows: gradlew.bat :web:run
   ```

   The API listens on `http://127.0.0.1:25560` and exposes `/api/v1/health`.

4. Run the web console against it:

   ```bash
   cd web-ui
   npm ci
   npm run dev                 # Vite dev server, proxies /api to 127.0.0.1:25560
   ```

   For a production bundle use `npm run build`; the server serves the resulting
   `web-ui/dist` as static assets.

## Building and packaging

| Command | Purpose |
|---|---|
| `./gradlew build` | Compile everything and run all checks, including tests |
| `./gradlew :plugin-api:test :core:compileKotlin :agent:compileKotlin :web:compileKotlin :cli:compileKotlin` | The core compile check documented in [develop.md](develop.md) |
| `npm run typecheck` (in `web-ui/`) | Type-check the console |
| `./gradlew packageDemoV1` | Build the verified demo distribution into `release/edb-demo-v1` |

`release/` is **not** committed: it contains runtime credentials, logs and
uploaded data. Publish it as a CI artifact or a release attachment instead.

## Testing

- Backend: `./gradlew test` (JUnit 5; H2 and mockk for unit-level tests).
- Console: `npm run test` (Vitest).
- Optional integrations (Docker workers, Python/R workers) are covered by
  scripts under `packaging/demo-v1/` and need Docker or a reachable worker.
- [frontend-manual-test.md](frontend-manual-test.md) is the end-to-end manual
  verification script for a release package.

## Documentation map

| File | Contents |
|---|---|
| [develop.md](develop.md) | Modules, HTTP API, contract evolution, Plugin Contract v1, release checklist |
| [frontend-manual-test.md](frontend-manual-test.md) | Manual UI/API verification for a packaged release |
| [packaging/demo-v1/DEMO.md](packaging/demo-v1/DEMO.md) | How the demo package is started and demonstrated |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution workflow and repository rules |
| [NOTICE](NOTICE) / [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md) | Licensing |

## License

Apache License 2.0 — see [LICENSE](LICENSE). Third-party obligations are recorded
in [NOTICE](NOTICE); note in particular the MySQL Connector/J FOSS Exception,
which is what makes this dependency combination redistributable.

---

## 中文说明

EDB（Experiment Data Bridge）是一个自托管的实验数据处理平台：负责任务排队与
调度、数据文件与溯源管理、版本化 Workflow 执行、报表生成，并允许 LLM Agent
**提出**工作流方案，由人工审批后才会执行。

**成熟度**：后端各模块已实现并有测试覆盖；Web 控制台功能可用但视觉上仍是
Demo 级（没有设计系统，CSS 手写，前端单测只有 2 个）；Tauri 桌面壳处于早期
阶段，未进入 Gradle 构建。项目已知缺口（46 个被禁用的测试、版本号多处重复、
`/api/v1/health` 硬编码版本字符串等）记录在 [CHANGELOG.md](CHANGELOG.md) 与
本文件上方的 *Project status* 中。

**本地运行**：

1. 准备 JDK 21 与 MySQL 8，创建空数据库。
2. `mkdir config` 并把 `web/src/main/resources/application.yml` 复制为
   `config/application.yml`，填写数据库账号、JWT secret 与初始管理员。该文件
   已被 gitignore，请不要提交。
3. `gradlew.bat :web:run` 启动服务（`http://127.0.0.1:25560`）。
4. `cd web-ui` → `npm ci` → `npm run dev` 启动前端开发服务器。

**打包**：`gradlew.bat packageDemoV1` 产出 `release/edb-demo-v1`（含 fat JAR、
CLI 发行包、SHA256 清单）。`release/` 目录含真实凭据与运行数据，已被
gitignore，请通过 CI 产物或 Release 附件分发，不要提交进仓库。

**模块与接口文档**见 [develop.md](develop.md)；插件 SPI 见其第 6 节。
