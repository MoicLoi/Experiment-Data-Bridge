from __future__ import annotations

import base64
import hmac
import json
import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import ValidationError
from starlette.background import BackgroundTask
from starlette.datastructures import UploadFile

from .handlers import OperationRegistry, default_registry, resolve_operation
from .models import AnalysisManifest, AnalysisRequest, LocalSource

PROTOCOL_VERSION = 1
FILE_NAME_HEADER = "X-EDB-File-Name"
METADATA_HEADER = "X-EDB-Metadata-Base64"
FORMAT_HEADER = "X-EDB-Format"
COPY_CHUNK_SIZE = 1024 * 1024


def create_app(registry: OperationRegistry | None = None) -> FastAPI:
    operations = registry or default_registry()
    app = FastAPI(title="EDB Python Analysis Worker", version="0.1.0")

    @app.get("/health")
    async def health() -> dict[str, object]:
        return {
            "status": "ok",
            "protocolVersion": PROTOCOL_VERSION,
            "operations": operations.ids(),
        }

    @app.post("/analyze")
    async def analyze(request: Request) -> FileResponse:
        _authorize(request)
        temporary = tempfile.TemporaryDirectory(prefix="edb-worker-")
        try:
            form = await request.form()
            raw_manifest = form.get("manifest")
            if not isinstance(raw_manifest, str):
                raise HTTPException(400, "multipart field 'manifest' must contain JSON text")
            try:
                manifest = AnalysisManifest.model_validate_json(raw_manifest)
            except ValidationError as failure:
                raise HTTPException(400, f"invalid analysis manifest: {failure}") from failure
            if len({source.part_name for source in manifest.sources}) != len(manifest.sources):
                raise HTTPException(400, "manifest source partName values must be unique")

            max_bytes = int(os.getenv("EDB_WORKER_MAX_UPLOAD_BYTES", str(10 * 1024**3)))
            total_bytes = 0
            local_sources: list[LocalSource] = []
            expected_parts = {source.part_name for source in manifest.sources}
            supplied_parts = {key for key, value in form.multi_items() if isinstance(value, UploadFile)}
            if supplied_parts != expected_parts:
                raise HTTPException(400, f"source parts do not match manifest: expected {sorted(expected_parts)}")

            for index, source in enumerate(manifest.sources):
                upload = form.get(source.part_name)
                if not isinstance(upload, UploadFile):
                    raise HTTPException(400, f"missing source file part: {source.part_name}")
                suffix = Path(source.file_name).suffix[:20]
                target = Path(temporary.name) / f"source-{index}{suffix}"
                with target.open("wb") as output:
                    while chunk := await upload.read(COPY_CHUNK_SIZE):
                        total_bytes += len(chunk)
                        if total_bytes > max_bytes:
                            raise HTTPException(413, "analysis upload exceeds EDB_WORKER_MAX_UPLOAD_BYTES")
                        output.write(chunk)
                await upload.close()
                local_sources.append(LocalSource(source, target))

            analysis_request = AnalysisRequest(manifest, local_sources)
            operation_id = resolve_operation(analysis_request)
            artifact = operations.require(operation_id).run(analysis_request)
            artifact.metadata.setdefault("workerVersion", os.getenv("EDB_WORKER_VERSION", "0.1.0"))
            artifact.metadata.setdefault("imageVersion", os.getenv("EDB_WORKER_IMAGE_VERSION", "unknown"))
            if not artifact.path.is_file() or not artifact.path.resolve().is_relative_to(Path(temporary.name).resolve()):
                raise RuntimeError("worker operation returned an invalid artifact path")
            metadata_json = json.dumps(artifact.metadata, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
            metadata = base64.urlsafe_b64encode(metadata_json).decode("ascii").rstrip("=")
            return FileResponse(
                artifact.path,
                media_type=artifact.media_type,
                filename=artifact.file_name,
                headers={FILE_NAME_HEADER: artifact.file_name, METADATA_HEADER: metadata, FORMAT_HEADER: artifact.format},
                background=BackgroundTask(temporary.cleanup),
            )
        except HTTPException:
            temporary.cleanup()
            raise
        except ValueError as failure:
            temporary.cleanup()
            raise HTTPException(400, str(failure)) from failure
        except Exception:
            temporary.cleanup()
            raise

    return app


def _authorize(request: Request) -> None:
    expected = os.getenv("EDB_WORKER_BEARER_TOKEN")
    if not expected:
        return
    authorization = request.headers.get("Authorization", "")
    supplied = authorization.removeprefix("Bearer ") if authorization.startswith("Bearer ") else ""
    if not hmac.compare_digest(supplied, expected):
        raise HTTPException(401, "invalid worker bearer token")


app = create_app()
