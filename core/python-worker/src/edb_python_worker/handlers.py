from __future__ import annotations

import json
import math
import re
from pathlib import Path
from typing import Protocol

import numpy as np

from .formats import Hdf5FormatAdapter, MatFormatAdapter
from .models import AnalysisArtifact, AnalysisRequest, LocalSource
from .tabular import default_statistics_operation, default_tabular_operation


class WorkerOperation(Protocol):
    id: str

    def run(self, request: AnalysisRequest) -> AnalysisArtifact: ...


class OperationRegistry:
    def __init__(self, operations: list[WorkerOperation] | None = None) -> None:
        self._operations: dict[str, WorkerOperation] = {}
        for operation in operations or []:
            self.register(operation)

    def register(self, operation: WorkerOperation) -> None:
        if not operation.id or operation.id in self._operations:
            raise ValueError(f"invalid or duplicate worker operation: {operation.id!r}")
        self._operations[operation.id] = operation

    def require(self, operation_id: str) -> WorkerOperation:
        try:
            return self._operations[operation_id]
        except KeyError as failure:
            raise ValueError(f"unsupported analysis operation: {operation_id}") from failure

    def ids(self) -> list[str]:
        return sorted(self._operations)


class SummaryOperation:
    def __init__(self, operation_id: str, source_format: str, adapter: object) -> None:
        self.id = operation_id
        self._source_format = source_format
        self._adapter = adapter

    def run(self, request: AnalysisRequest) -> AnalysisArtifact:
        sources = _matching_sources(request, self._source_format)
        summaries = []
        dataset_count = 0
        for source in sources:
            datasets = self._adapter.read(source.path)  # type: ignore[attr-defined]
            dataset_count += len(datasets)
            summaries.append(
                {
                    "dataFileId": source.manifest.data_file_id,
                    "fileName": source.manifest.file_name,
                    "format": source.manifest.format,
                    "datasets": {name: _array_summary(value) for name, value in sorted(datasets.items())},
                }
            )
        output = request.sources[0].path.parent / _output_name(request, f"{self._source_format.lower()}-summary.json")
        output.write_text(
            json.dumps({"schemaVersion": 1, "sources": summaries}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return AnalysisArtifact(
            path=output,
            file_name=output.name,
            media_type="application/json",
            format="JSON",
            metadata={"operation": self.id, "sourceCount": len(sources), "datasetCount": dataset_count},
        )


class ConversionOperation:
    def __init__(
        self,
        operation_id: str,
        source_format: str,
        extension: str,
        media_type: str,
        reader: object,
        writer: object,
    ) -> None:
        self.id = operation_id
        self._source_format = source_format
        self._extension = extension
        self._media_type = media_type
        self._reader = reader
        self._writer = writer

    def run(self, request: AnalysisRequest) -> AnalysisArtifact:
        sources = _matching_sources(request, self._source_format)
        if len(sources) != 1:
            raise ValueError(f"{self.id} requires exactly one {self._source_format} source")
        datasets = self._reader.read(sources[0].path)  # type: ignore[attr-defined]
        default_name = f"{Path(sources[0].manifest.file_name).stem}.{self._extension}"
        output = sources[0].path.parent / _output_name(request, default_name)
        self._writer.write(datasets, output)  # type: ignore[attr-defined]
        return AnalysisArtifact(
            path=output,
            file_name=output.name,
            media_type=self._media_type,
            format=self._extension.upper().replace("H5", "HDF5"),
            metadata={"operation": self.id, "datasetCount": len(datasets)},
        )


def default_registry() -> OperationRegistry:
    hdf5 = Hdf5FormatAdapter()
    mat = MatFormatAdapter()
    return OperationRegistry(
        [
            SummaryOperation("hdf5-summary", "HDF5", hdf5),
            SummaryOperation("mat-summary", "MAT", mat),
            ConversionOperation("hdf5-to-mat", "HDF5", "mat", "application/x-matlab-data", hdf5, mat),
            ConversionOperation("mat-to-hdf5", "MAT", "h5", "application/x-hdf5", mat, hdf5),
            default_tabular_operation(),
            default_statistics_operation(),
        ]
    )


def resolve_operation(request: AnalysisRequest) -> str:
    requested = request.manifest.parameters.get("operation", "auto-summary")
    if requested != "auto-summary":
        return requested
    formats = {source.manifest.format.upper() for source in request.sources}
    if formats == {"HDF5"}:
        return "hdf5-summary"
    if formats == {"MAT"}:
        return "mat-summary"
    raise ValueError("auto-summary requires all sources to be HDF5 or all sources to be MAT")


def _matching_sources(request: AnalysisRequest, expected: str) -> list[LocalSource]:
    sources = [source for source in request.sources if source.manifest.format.upper() == expected]
    if len(sources) != len(request.sources):
        raise ValueError(f"operation requires {expected} sources only")
    return sources


def _output_name(request: AnalysisRequest, default: str) -> str:
    requested = request.manifest.parameters.get("outputFileName", default)
    raw_name = Path(requested).name
    name = re.sub(r"[^A-Za-z0-9._-]", "_", raw_name)
    if not name or name in {".", ".."}:
        raise ValueError("outputFileName is invalid")
    return name


def _array_summary(value: np.ndarray) -> dict[str, object]:
    array = np.asarray(value)
    result: dict[str, object] = {
        "shape": list(array.shape),
        "dtype": str(array.dtype),
        "size": int(array.size),
    }
    if array.size and np.issubdtype(array.dtype, np.number):
        finite = array[np.isfinite(array)]
        if finite.size:
            minimum = finite.min()
            maximum = finite.max()
            result["min"] = _json_number(minimum)
            result["max"] = _json_number(maximum)
    return result


def _json_number(value: np.generic) -> int | float:
    converted = value.item()
    if isinstance(converted, complex):
        return float(abs(converted))
    if isinstance(converted, float) and not math.isfinite(converted):
        return 0.0
    return converted
