# EDB Demo v1 — 0.25.0-demo.1

This package demonstrates the EDB Task, DataFile, Workflow, Agent and administration foundations.

## Build from IntelliJ IDEA

Select the shared `Package Demo V1` run configuration and click **Run**, or execute
`distribution > packageDemoV1` from the Gradle tool window. The verified output is
created at `release/edb-demo-v1`.

For a repeatable browser verification sequence, follow
`frontend-manual-test.md` in the package root.

## Prerequisites

- Windows PowerShell 5.1 or PowerShell 7
- Java 21 available as `java`
- MySQL 8 with an empty database and a user allowed to run Flyway migrations
- Docker Desktop is optional. The default Demo config does not manage Docker workers.

## Start

Run `start.bat` once. The first run creates `eula.txt` and
`config/application.yml`, then exits without connecting to the database. Set
`eula=true`, replace all `CHANGE_ME` values, and run `start.bat` again.

The launcher prints startup phases, records stdout/stderr under `logs/`, opens the
web console after the health check, and reports sustained high CPU or slow health
responses as a possible overload. Use `start.bat -NoBrowser` on headless systems.
When launched by double-click, the console remains open after initialization,
failure, or a normal shutdown and closes only after the user presses a key.
Automation should invoke `start.ps1` directly instead of `start.bat`.

Environment-based startup from older packages is no longer required. Do not save
real credentials in source control or redistribute the generated configuration.

Legacy environment variable example:

```powershell
$env:EDB_DB_HOST='127.0.0.1'
$env:EDB_DB_PORT='3306'
$env:EDB_DB_NAME='edb'
$env:EDB_DB_USER='edb'
$env:EDB_DB_PASSWORD='your-mysql-password'
$env:EDB_ADMIN_USER='admin'
$env:EDB_ADMIN_PASSWORD='choose-a-demo-password'
.\start.ps1
```

Open <http://127.0.0.1:25560>. The first startup runs Flyway and creates the initial administrator.

Run the smoke test:

```powershell
.\smoke-test.ps1
```

Stop the service:

```powershell
.\stop.ps1
```

## Suggested demonstration

1. Sign in and create a project.
2. Upload `examples/data/experiment.csv` in DataFile Workbench.
3. Submit CSV → JSON or CSV → XLSX conversion.
4. Generate the `experiment-summary` report.
5. Open Workflow Editor, inspect the Step Catalog, compose and validate Workflow IR.
6. Create an Agent session using the built-in Fake Provider, inspect its Proposal and approval boundary.
7. Show live tasks, audit timeline and administration metrics.

## Security and limitations

- `config/application.yml` is generated at startup and contains runtime credentials. Never redistribute it.
- The package contains no API keys. Real model profiles require separate configuration and environment secrets.
- JS workflow nodes and browser-side workflow execution are intentionally unsupported.
- Step port data typing is reserved for Step Catalog v2.
- Docker, Python and R workers remain optional integrations.
