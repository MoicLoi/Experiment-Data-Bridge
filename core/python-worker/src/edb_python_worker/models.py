from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class SourceManifest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    part_name: str = Field(alias="partName", min_length=1)
    data_file_id: int | None = Field(default=None, alias="dataFileId", gt=0)
    file_name: str = Field(alias="fileName", min_length=1)
    format: str = Field(min_length=1)
    media_type: str | None = Field(default=None, alias="mediaType")
    size_bytes: int | None = Field(default=None, alias="sizeBytes", ge=0)
    sha256: str | None = None

    @field_validator("sha256")
    @classmethod
    def validate_sha256(cls, value: str | None) -> str | None:
        if value is not None and (len(value) != 64 or any(c not in "0123456789abcdefABCDEF" for c in value)):
            raise ValueError("sha256 must contain 64 hexadecimal characters")
        return value


class AnalysisManifest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: int = Field(alias="schemaVersion")
    handler_id: str = Field(alias="handlerId", min_length=1)
    analysis_type: str = Field(alias="analysisType", min_length=1)
    parameters: dict[str, str] = Field(default_factory=dict)
    sources: list[SourceManifest] = Field(min_length=1)

    @field_validator("schema_version")
    @classmethod
    def supported_version(cls, value: int) -> int:
        if value != 1:
            raise ValueError(f"unsupported schemaVersion: {value}")
        return value


@dataclass(frozen=True)
class LocalSource:
    manifest: SourceManifest
    path: Path


@dataclass(frozen=True)
class AnalysisRequest:
    manifest: AnalysisManifest
    sources: list[LocalSource]


@dataclass(frozen=True)
class AnalysisArtifact:
    path: Path
    file_name: str
    media_type: str
    format: str
    metadata: dict[str, Any] = field(default_factory=dict)
