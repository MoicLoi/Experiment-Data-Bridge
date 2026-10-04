from __future__ import annotations

import base64
import io
import json

import h5py
import numpy as np
from fastapi.testclient import TestClient
from openpyxl import Workbook, load_workbook
from scipy.io import savemat

from edb_python_worker.app import create_app


def test_health_lists_reference_operations() -> None:
    with TestClient(create_app()) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["protocolVersion"] == 1
    assert response.json()["operations"] == [
        "hdf5-summary",
        "hdf5-to-mat",
        "mat-summary",
        "mat-to-hdf5",
        "tabular-clean",
        "tabular-statistics",
    ]


def test_tabular_statistics_runs_common_animal_study_analyses() -> None:
    source = io.BytesIO()
    workbook = Workbook()
    sheet = workbook.active
    sheet.append(["动物品种", "品系", "性别", "体重_g", "收缩压_mmHg", "白细胞计数_10_9_L", "健康状态"])
    sheet.append(["大鼠", "SD", "雄", 350.5, 125, 8.4, "健康"])
    sheet.append(["大鼠", "SD", "雌", 280.2, 120, 7.9, "健康"])
    sheet.append(["大鼠", "Wistar", "雄", 320.0, 122, 9.0, "健康"])
    sheet.append(["大鼠", "Wistar", "雌", 265.4, 118, 14.2, "患病(发热)"])
    workbook.save(source)
    spec = {
        "schemaVersion": 1,
        "identifierColumns": ["sample_id"],
        "analyses": [
            {"name": "大鼠雌雄体重 Welch t 检验", "method": "welch_t_test", "filters": {"动物品种": "大鼠"}, "groupColumn": "性别", "groupA": "雄", "groupB": "雌", "valueColumn": "体重_g"},
            {"name": "SD 与 Wistar 白细胞 Mann-Whitney U 检验", "method": "mann_whitney_u", "filters": {"动物品种": "大鼠"}, "groupColumn": "品系", "groupA": "SD", "groupB": "Wistar", "valueColumn": "白细胞计数_10_9_L"},
            {"name": "体重与收缩压 Spearman 相关", "method": "spearman", "filters": {"动物品种": "大鼠"}, "xColumn": "体重_g", "yColumn": "收缩压_mmHg"},
        ],
    }
    manifest = _manifest("XLSX", "animals.xlsx", {"operation": "tabular-statistics", "spec": json.dumps(spec, ensure_ascii=False)})

    with TestClient(create_app()) as client:
        response = client.post(
            "/analyze",
            files={
                "manifest": (None, json.dumps(manifest, ensure_ascii=False), "application/json"),
                "source-0": ("animals.xlsx", source.getvalue(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            },
        )

    assert response.status_code == 200
    results = response.json()["results"]
    assert results[0]["nA"] == 2 and results[0]["nB"] == 2
    assert results[0]["meanA"] == 335.25 and results[0]["meanB"] == 272.8
    assert results[1]["statistic"] == 0.0
    assert results[2]["n"] == 4
    assert results[2]["pValue"] == 1 / 12
    assert results[2]["pValueMethod"] == "exact-permutation"


def test_tabular_clean_uses_json_spec_and_returns_xlsx() -> None:
    source = io.BytesIO()
    workbook = Workbook()
    sheet = workbook.active
    sheet.append(["sample_id", "category", "value"])
    sheet.append([" A-1 ", "Control", 10])
    sheet.append(["A-1", "CONTROL", 10])
    sheet.append(["B-2", " Treatment ", None])
    sheet.append(["C-3", "Treatment", -2])
    sheet.append(["D-4", "Treatment", 25])
    workbook.save(source)
    spec = {
        "schemaVersion": 1,
        "output": {"format": "XLSX", "fileName": "cleaned.xlsx"},
        "rules": [
            {"type": "trimStrings"},
            {"type": "normalizeCase", "columns": ["category"], "mode": "lower"},
            {"type": "dropRowsWithMissing", "columns": ["value"]},
            {"type": "removeDuplicates", "columns": ["sample_id"]},
            {"type": "numericRange", "column": "value", "min": 0, "max": 100},
        ],
    }
    manifest = _manifest("XLSX", "observations.xlsx", {"operation": "tabular-clean", "spec": json.dumps(spec)})

    with TestClient(create_app()) as client:
        response = client.post(
            "/analyze",
            files={
                "manifest": (None, json.dumps(manifest), "application/json"),
                "source-0": ("observations.xlsx", source.getvalue(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            },
        )

    assert response.status_code == 200
    assert response.headers["x-edb-format"] == "XLSX"
    assert response.headers["x-edb-file-name"] == "cleaned.xlsx"
    result = load_workbook(io.BytesIO(response.content), read_only=True, data_only=True)
    try:
        assert list(result.active.values) == [
            ("sample_id", "category", "value"),
            ("A-1", "control", 10),
            ("D-4", "treatment", 25),
        ]
    finally:
        result.close()
    metadata = json.loads(_decode_header(response.headers["x-edb-metadata-base64"]))
    assert metadata["inputRows"] == 5
    assert metadata["outputRows"] == 2
    assert metadata["rules"][2]["removedRowIdentifiers"] == ['sample_id="B-2"']
    assert metadata["rules"][4]["removedRowIdentifiers"] == ['sample_id="C-3"']
    assert metadata["rules"][0]["removedRowIdentifiers"] == ['sample_id=" A-1 "']
    assert metadata["rules"][3]["removedRowIdentifiers"] == ['sample_id="A-1"']


def test_tabular_clean_rejects_unknown_rule() -> None:
    source = b"id,value\n1,10\n"
    spec = {"schemaVersion": 1, "rules": [{"type": "executePython"}]}
    manifest = _manifest("CSV", "data.csv", {"operation": "tabular-clean", "spec": json.dumps(spec)})

    with TestClient(create_app()) as client:
        response = client.post(
            "/analyze",
            files={
                "manifest": (None, json.dumps(manifest), "application/json"),
                "source-0": ("data.csv", source, "text/csv"),
            },
        )

    assert response.status_code == 400
    assert "unsupported cleaning rule" in response.json()["detail"]


def test_hdf5_summary_uses_edb_multipart_and_response_contract(tmp_path) -> None:
    source = tmp_path / "observations.h5"
    with h5py.File(source, "w") as handle:
        handle.create_dataset("pose/x", data=np.array([1.0, 2.5, 4.0]))
    manifest = _manifest("HDF5", "observations.h5", {"operation": "hdf5-summary"})

    with TestClient(create_app()) as client, source.open("rb") as content:
        response = client.post(
            "/analyze",
            files={
                "manifest": (None, json.dumps(manifest), "application/json"),
                "source-0": ("observations.h5", content, "application/x-hdf5"),
            },
        )

    assert response.status_code == 200
    assert response.headers["x-edb-file-name"] == "hdf5-summary.json"
    assert response.json()["sources"][0]["datasets"]["pose/x"] == {
        "shape": [3],
        "dtype": "float64",
        "size": 3,
        "min": 1.0,
        "max": 4.0,
    }
    metadata = json.loads(_decode_header(response.headers["x-edb-metadata-base64"]))
    assert metadata == {"operation": "hdf5-summary", "sourceCount": 1, "datasetCount": 1}


def test_mat_to_hdf5_conversion_returns_real_hdf5() -> None:
    source = io.BytesIO()
    savemat(source, {"positions": np.array([[1, 2], [3, 4]])})
    manifest = _manifest("MAT", "positions.mat", {"operation": "mat-to-hdf5"})

    with TestClient(create_app()) as client:
        response = client.post(
            "/analyze",
            files={
                "manifest": (None, json.dumps(manifest), "application/json"),
                "source-0": ("positions.mat", source.getvalue(), "application/x-matlab-data"),
            },
        )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/x-hdf5")
    with h5py.File(io.BytesIO(response.content), "r") as handle:
        np.testing.assert_array_equal(handle["positions"][()], np.array([[1, 2], [3, 4]]))


def test_optional_bearer_authentication(monkeypatch) -> None:
    monkeypatch.setenv("EDB_WORKER_BEARER_TOKEN", "secret")
    with TestClient(create_app()) as client:
        assert client.get("/health").status_code == 200
        unauthorized = client.post("/analyze")
        authorized = client.post("/analyze", headers={"Authorization": "Bearer secret"})

    assert unauthorized.status_code == 401
    assert authorized.status_code == 400


def _manifest(format_name: str, file_name: str, parameters: dict[str, str]) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "handlerId": "python-http",
        "analysisType": "python",
        "parameters": parameters,
        "sources": [
            {
                "partName": "source-0",
                "dataFileId": 42,
                "fileName": file_name,
                "format": format_name,
                "mediaType": "application/octet-stream",
            }
        ],
    }


def _decode_header(value: str) -> str:
    padded = value + "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(padded).decode("utf-8")
