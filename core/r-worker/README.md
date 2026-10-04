# EDB R Analysis Worker

Reference non-Docker Plumber worker for ExternalHttpAnalysisHandler protocol version 1.

## Requirements

- R 4.3 or newer
- `plumber`
- `jsonlite`

Install dependencies in an isolated project library and start the worker:

```r
dir.create(".r-library", showWarnings = FALSE)
install.packages(c("plumber", "jsonlite"), lib = ".r-library")
```

If a `.r-library` directory exists beside `run.R`, the worker and smoke test add it
to `.libPaths()` automatically. This keeps development dependencies local to the worker.

```powershell
$env:EDB_WORKER_BEARER_TOKEN = "replace-me"
$env:EDB_R_WORKER_PORT = "8001"
Rscript run.R
```

The worker exposes `GET /health` and `POST /analyze`. It accepts the same multipart manifest and source parts as the Python reference worker and returns `X-EDB-File-Name` plus optional `X-EDB-Metadata-Base64` headers.

Build and run the Docker image independently with:

```powershell
docker compose up --build r-worker
```

For Kotlin-managed mode, use image `edb/r-worker:dev`, worker kind `r-http`,
container name `edb-r-worker`, and container port `8001` in `task.analysis.docker-worker`.
The complete configuration fragment is available in `application-managed.example.yml`;
use the same bearer token for the managed container, analysis handler, and report generator.
To manage Python and R simultaneously, use `application-fleet.example.yml`. Fleet member
IDs, container names, and published endpoints must be unique.

Only registered operation IDs can run. The initial `r-statistical-summary` operation accepts a CSV source and returns a JSON artifact; request parameters are data, never executable R code.

Run the direct handler smoke test with:

```powershell
Rscript tests/smoke.R
```

The Kotlin integration test is opt-in and expects a running worker:

```powershell
$env:EDB_R_WORKER_INTEGRATION = "true"
$env:EDB_R_WORKER_URL = "http://127.0.0.1:8001/analyze"
$env:EDB_R_WORKER_TOKEN = "replace-me"
..\gradlew.bat :core:test --tests "me.moiclathy.edb.core.datafile.analysis.RHttpAnalysisWorkerIntegrationTest"
```

For reproducible deployment, initialize `renv` in this directory and commit the generated `renv.lock` after selecting the production R version.
