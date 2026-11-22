# EDB Demo v1 — {{VERSION}}

A self-hosted platform for reproducible processing of experiment data: task
queue and scheduler, data files with provenance, versioned workflow definitions,
reports, a plugin SPI, and an LLM agent that *proposes* workflows a human must
approve before they run.

This is a **demo-grade** build. See *Known limitations* before using it for
anything that matters.

## Download

| Artifact | Purpose |
|---|---|
| `{{ARCHIVE}}` | Complete Windows package: server fat JAR, CLI, web console, config templates, example data |
| `SHA256SUMS.txt` | Checksums for the archive and for the server JAR on its own |

Verify before running:

```powershell
Get-FileHash .\{{ARCHIVE}} -Algorithm SHA256
# compare with the matching line in SHA256SUMS.txt
```

## Requirements

- Windows 10/11 with PowerShell 5.1 or PowerShell 7
- Java 21 (`java -version` must work)
- MySQL 8 with an empty database and a user allowed to run Flyway migrations

Docker is optional and only needed for containerised analysis workers.

## First start

1. Unpack the archive. Everything sits under a single `edb-demo-v1` folder —
   that folder is the package root.
2. Run `start.bat` **once**. It creates `eula.txt` and
   `config/application.yml`, then exits without touching the database.
3. Set `eula=true` and replace every `CHANGE_ME` / `__PLACEHOLDER__` value in
   `config/application.yml` (database credentials, JWT secret, initial admin).
4. Run `start.bat` again. The launcher waits for the health check and opens
   <http://127.0.0.1:25560>.

Logs live under `logs/` (`server.out.log`, `server.err.log`, `debug.log`,
`launcher.log`). Stop the service with `stop.ps1`.

The package ships `config/application.yml.template` only — **no credentials**.
Never redistribute a `config/application.yml` you have filled in: it contains
your database password and JWT secret.

## Verify the install

```powershell
.\smoke-test.ps1
```

The end-to-end manual checklist is `frontend-manual-test.md` inside the package,
and the architecture/API reference is `develop.md`.

## Known limitations

- The web console is functional but visually demo-grade: no design system, thin
  frontend test coverage.
- The `web` module's **test** sources do not currently compile (API drift after
  the Docker worker rebuild feature landed), so `./gradlew build` and
  `./gradlew test` fail. 46 more test sources are disabled. The packaging task
  does not compile test sources, which is why this archive still builds.
- The Tauri desktop shell is early and is not included in this archive.
- Docker, Python and R analysis workers are optional integrations and off by
  default.
- JS workflow nodes and browser-side workflow execution are intentionally
  unsupported.

## Building from source

```bash
./gradlew :web:run            # start the server
cd web-ui && npm ci && npm run dev
./gradlew packageDemoV1       # reproduce this package
```

**Windows path caveat:** Gradle 8.13 writes test-worker argument files in the
platform ANSI code page while the JDK reads them as UTF-8, so a project checked
out under a path containing non-ASCII characters fails its tests with
`ClassNotFoundException`. Build from an ASCII-only path (for example `D:\edb`)
or enable the Windows *Use Unicode UTF-8 for worldwide language support* option.

## Licence

Apache-2.0 — see `LICENSE` in the source repository. Third-party obligations are
recorded in `NOTICE`; the generated inventory is `THIRD-PARTY-LICENSES.md`. Note
in particular that MySQL Connector/J is GPLv2 with the Universal FOSS Exception,
which is what makes this dependency combination redistributable.
