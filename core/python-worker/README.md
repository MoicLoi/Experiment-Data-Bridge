# EDB Python Analysis Worker

Reference FastAPI implementation of the EDB Python HTTP analysis protocol. It runs independently
from the Kotlin service and is intentionally not coupled to Docker lifecycle management.

## Operations

- `hdf5-summary`: JSON inventory of HDF5 datasets, shapes, dtypes and numeric ranges.
- `mat-summary`: JSON inventory of MATLAB variables.
- `hdf5-to-mat`: HDF5 datasets to a compressed MATLAB level-5 file.
- `mat-to-hdf5`: MATLAB variables to HDF5 datasets.
- `tabular-clean`: JSON-configured CSV/XLSX/JSON cleaning with pluggable rules and output formats.
- `auto-summary`: request alias that selects the summary operation from the source format.

## Run locally

```bash
python -m venv .venv
.venv/Scripts/pip install -e ".[test]"
.venv/Scripts/edb-python-worker
```

Linux/macOS activation paths can be used instead of `.venv/Scripts`. The default endpoint is
`http://127.0.0.1:8000/analyze`; health information is available at `/health`.

Environment variables:

- `EDB_WORKER_HOST` and `EDB_WORKER_PORT`
- `EDB_WORKER_BEARER_TOKEN` to require `Authorization: Bearer ...`
- `EDB_WORKER_MAX_UPLOAD_BYTES` (default 10 GiB)

Configure the Kotlin service with the same endpoint and token. Select an operation through the
analysis parameter map, for example `{"operation":"hdf5-summary"}`.

Tabular cleaning uses `operation=tabular-clean` and a `spec` parameter containing JSON:

```json
{
  "schemaVersion": 1,
  "output": {"format": "XLSX", "fileName": "cleaned.xlsx"},
  "rules": [
    {"type": "trimStrings"},
    {"type": "normalizeCase", "columns": ["category"], "mode": "lower"},
    {"type": "dropRowsWithMissing", "columns": ["value"]},
    {"type": "removeDuplicates", "columns": ["sample_id"]},
    {"type": "numericRange", "column": "value", "min": 0, "max": 100}
  ]
}
```

The initial rule catalog contains `trimStrings`, `normalizeCase`, `dropRowsWithMissing`,
`removeDuplicates`, and `numericRange`. Readers, writers, and rules are independent registries;
new algorithms can be registered without changing the HTTP protocol. Output formats are JSON,
CSV, and XLSX. Arbitrary code execution is deliberately not supported.

## Run with Docker Compose

```bash
cp .env.example .env
docker compose up --build
docker compose ps
```

The development service publishes port `8000` by default. Its root file system is read-only,
Linux capabilities are dropped, and request files use a bounded in-memory `/var/tmp/edb-worker`
tmpfs. The container runs as UID/GID `10001`, includes an image-level `/health` check, and handles
Docker `SIGTERM` through Uvicorn's graceful shutdown.

For the Kotlin application, set the Python HTTP endpoint to
`http://127.0.0.1:8000/analyze` and use the same bearer token as `.env`. Leaving
`EDB_WORKER_BEARER_TOKEN` empty disables authentication and should only be used on an isolated
development machine.

## Protocol

`POST /analyze` consumes `multipart/form-data`:

- `manifest`: strict schema-versioned JSON text.
- `source-0`, `source-1`, ...: file parts named by each manifest `partName`.

A successful response streams the artifact body and sets:

- `X-EDB-File-Name`
- `Content-Type`
- `X-EDB-Metadata-Base64` containing Base64URL-encoded JSON metadata
- `X-EDB-Format` containing the DataFile format name for dynamic artifacts

Validation failures return `400`, authentication failures return `401`, oversized uploads return
`413`, and unexpected worker failures return `500`. The Kotlin client maps these statuses into the
existing structured Task error and retry policy.

## Initial adapter limits

The reference MAT adapter and HDF5-to-MAT/MAT-to-HDF5 operations load dataset arrays into Worker
memory. This is suitable for the initial interoperability layer, but very large scientific files
should use a dedicated chunked operation with explicit memory and dataset-size policies. Object
arrays and MATLAB structs are intentionally rejected by HDF5 writing instead of being converted
implicitly.
