from __future__ import annotations

import csv
import itertools
import json
import math
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from openpyxl import Workbook, load_workbook
import numpy as np
from scipy import stats

from .models import AnalysisArtifact, AnalysisRequest


@dataclass
class Table:
    columns: list[str]
    rows: list[dict[str, Any]]


class TableReader(Protocol):
    format: str

    def read(self, path: Path) -> Table: ...


class TableWriter(Protocol):
    format: str
    extension: str
    media_type: str

    def write(self, table: Table, path: Path) -> None: ...


class TabularFormatRegistry:
    def __init__(self, readers: list[TableReader], writers: list[TableWriter]) -> None:
        self._readers = {item.format: item for item in readers}
        self._writers = {item.format: item for item in writers}

    def reader(self, format_name: str) -> TableReader:
        try:
            return self._readers[format_name.upper()]
        except KeyError as failure:
            raise ValueError(f"unsupported tabular input format: {format_name}") from failure

    def writer(self, format_name: str) -> TableWriter:
        try:
            return self._writers[format_name.upper()]
        except KeyError as failure:
            raise ValueError(f"unsupported tabular output format: {format_name}") from failure


class CsvTableAdapter:
    format = "CSV"
    extension = "csv"
    media_type = "text/csv"

    def read(self, path: Path) -> Table:
        with path.open("r", encoding="utf-8-sig", newline="") as source:
            reader = csv.DictReader(source)
            columns = _validate_columns(reader.fieldnames)
            return Table(columns, [dict(row) for row in reader])

    def write(self, table: Table, path: Path) -> None:
        with path.open("w", encoding="utf-8-sig", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=table.columns)
            writer.writeheader()
            writer.writerows(table.rows)


class XlsxTableAdapter:
    format = "XLSX"
    extension = "xlsx"
    media_type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    def read(self, path: Path) -> Table:
        workbook = load_workbook(path, read_only=True, data_only=True)
        try:
            sheet = workbook.active
            values = sheet.iter_rows(values_only=True)
            header = next(values, None)
            columns = _validate_columns([str(value).strip() if value is not None else "" for value in header or []])
            rows = [dict(zip(columns, row)) for row in values]
            return Table(columns, rows)
        finally:
            workbook.close()

    def write(self, table: Table, path: Path) -> None:
        workbook = Workbook(write_only=True)
        sheet = workbook.create_sheet("Cleaned Data")
        sheet.append(table.columns)
        for row in table.rows:
            sheet.append([row.get(column) for column in table.columns])
        workbook.save(path)


class JsonTableAdapter:
    format = "JSON"
    extension = "json"
    media_type = "application/json"

    def read(self, path: Path) -> Table:
        value = json.loads(path.read_text(encoding="utf-8"))
        rows = value.get("rows") if isinstance(value, dict) else value
        if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
            raise ValueError("JSON table must be an array of objects or an object with a rows array")
        columns = list(dict.fromkeys(key for row in rows for key in row))
        return Table(_validate_columns(columns), rows)

    def write(self, table: Table, path: Path) -> None:
        path.write_text(
            json.dumps({"schemaVersion": 1, "columns": table.columns, "rows": table.rows}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


class CleaningRule(Protocol):
    type: str

    def apply(self, table: Table, config: dict[str, Any]) -> tuple[Table, int]: ...


class CleaningRuleRegistry:
    def __init__(self, rules: list[CleaningRule]) -> None:
        self._rules = {rule.type: rule for rule in rules}

    def apply(self, table: Table, configs: list[dict[str, Any]], identifier_columns: list[str] | None = None) -> tuple[Table, list[dict[str, Any]]]:
        results = []
        identifiers = identifier_columns or []
        unknown_identifiers = set(identifiers) - set(table.columns)
        if unknown_identifiers:
            raise ValueError(f"unknown row identifier columns: {sorted(unknown_identifiers)}")
        for config in configs:
            rule_type = config.get("type")
            if not isinstance(rule_type, str) or rule_type not in self._rules:
                raise ValueError(f"unsupported cleaning rule: {rule_type!r}")
            before = [dict(row) for row in table.rows]
            table, affected = self._rules[rule_type].apply(table, config)
            result: dict[str, Any] = {"type": rule_type, "affectedRows": affected}
            if identifiers and affected:
                before_ids = [_row_identifier(row, identifiers) for row in before]
                after_ids = [_row_identifier(row, identifiers) for row in table.rows]
                before_counts, after_counts = Counter(before_ids), Counter(after_ids)
                removed = [key for key, count in before_counts.items() for _ in range(max(0, count - after_counts[key]))]
                before_by_id = {_row_identifier(row, identifiers): row for row in before}
                after_by_id = {_row_identifier(row, identifiers): row for row in table.rows}
                modified = [key for key in before_by_id if key in after_by_id and before_by_id[key] != after_by_id[key]]
                result["identifierColumns"] = identifiers
                result["removedRowIdentifiers"] = removed[:1000]
                result["modifiedRowIdentifiers"] = modified[:1000]
                result["rowIdentifiersTruncated"] = len(removed) > 1000 or len(modified) > 1000
            results.append(result)
        return table, results


class TrimStringsRule:
    type = "trimStrings"

    def apply(self, table: Table, config: dict[str, Any]) -> tuple[Table, int]:
        columns = _columns(table, config)
        affected = 0
        for row in table.rows:
            changed = False
            for column in columns:
                value = row.get(column)
                if isinstance(value, str) and value != value.strip():
                    row[column] = value.strip()
                    changed = True
            affected += int(changed)
        return table, affected


class NormalizeCaseRule:
    type = "normalizeCase"

    def apply(self, table: Table, config: dict[str, Any]) -> tuple[Table, int]:
        columns = _columns(table, config)
        mode = config.get("mode", "lower")
        transforms = {"lower": str.lower, "upper": str.upper, "title": str.title}
        if mode not in transforms:
            raise ValueError("normalizeCase mode must be lower, upper, or title")
        affected = 0
        for row in table.rows:
            changed = False
            for column in columns:
                value = row.get(column)
                if isinstance(value, str):
                    normalized = transforms[mode](value)
                    if normalized != value:
                        row[column] = normalized
                        changed = True
            affected += int(changed)
        return table, affected


class DropMissingRule:
    type = "dropRowsWithMissing"

    def apply(self, table: Table, config: dict[str, Any]) -> tuple[Table, int]:
        columns = _columns(table, config)
        before = len(table.rows)
        table.rows = [row for row in table.rows if not any(_missing(row.get(column)) for column in columns)]
        return table, before - len(table.rows)


class RemoveDuplicatesRule:
    type = "removeDuplicates"

    def apply(self, table: Table, config: dict[str, Any]) -> tuple[Table, int]:
        columns = _columns(table, config)
        seen: set[tuple[str, ...]] = set()
        kept = []
        for row in table.rows:
            key = tuple(json.dumps(row.get(column), sort_keys=True, default=str) for column in columns)
            if key not in seen:
                seen.add(key)
                kept.append(row)
        affected = len(table.rows) - len(kept)
        table.rows = kept
        return table, affected


class NumericRangeRule:
    type = "numericRange"

    def apply(self, table: Table, config: dict[str, Any]) -> tuple[Table, int]:
        column = config.get("column")
        if column not in table.columns:
            raise ValueError(f"unknown cleaning column: {column!r}")
        minimum, maximum = config.get("min"), config.get("max")
        if minimum is None and maximum is None:
            raise ValueError("numericRange requires min or max")

        def accepted(row: dict[str, Any]) -> bool:
            value = row.get(column)
            if _missing(value):
                return True
            try:
                number = float(value)
            except (TypeError, ValueError) as failure:
                raise ValueError(f"numericRange column {column!r} contains a non-numeric value") from failure
            return (minimum is None or number >= float(minimum)) and (maximum is None or number <= float(maximum))

        before = len(table.rows)
        table.rows = [row for row in table.rows if accepted(row)]
        return table, before - len(table.rows)


class TabularCleanOperation:
    id = "tabular-clean"

    def __init__(self, formats: TabularFormatRegistry, rules: CleaningRuleRegistry) -> None:
        self._formats = formats
        self._rules = rules

    def run(self, request: AnalysisRequest) -> AnalysisArtifact:
        if len(request.sources) != 1:
            raise ValueError("tabular-clean requires exactly one source")
        raw_spec = request.manifest.parameters.get("spec")
        if not raw_spec:
            raise ValueError("tabular-clean requires a JSON 'spec' parameter")
        try:
            spec = json.loads(raw_spec)
        except json.JSONDecodeError as failure:
            raise ValueError(f"tabular-clean spec is invalid JSON: {failure.msg}") from failure
        if not isinstance(spec, dict) or spec.get("schemaVersion") != 1:
            raise ValueError("tabular-clean spec schemaVersion must be 1")
        rules = spec.get("rules")
        if not isinstance(rules, list) or any(not isinstance(rule, dict) for rule in rules):
            raise ValueError("tabular-clean spec rules must be an array of objects")

        source = request.sources[0]
        table = self._formats.reader(source.manifest.format).read(source.path)
        input_rows = len(table.rows)
        identifier_columns = spec.get("identifierColumns", [])
        if not isinstance(identifier_columns, list) or any(not isinstance(item, str) for item in identifier_columns):
            raise ValueError("tabular-clean identifierColumns must be a string array")
        table, rule_results = self._rules.apply(table, rules, identifier_columns)
        output_config = spec.get("output", {})
        if not isinstance(output_config, dict):
            raise ValueError("tabular-clean output must be an object")
        output_format = str(output_config.get("format", "JSON")).upper()
        writer = self._formats.writer(output_format)
        requested_name = output_config.get("fileName", f"cleaned.{writer.extension}")
        file_name = Path(str(requested_name)).name
        if not file_name.lower().endswith(f".{writer.extension}"):
            raise ValueError(f"tabular-clean output file must end with .{writer.extension}")
        output = source.path.parent / file_name
        writer.write(table, output)
        return AnalysisArtifact(
            path=output,
            file_name=file_name,
            media_type=writer.media_type,
            format=output_format,
            metadata={
                "operation": self.id,
                "inputRows": input_rows,
                "outputRows": len(table.rows),
                "rules": rule_results,
            },
        )


class TabularStatisticsOperation:
    id = "tabular-statistics"

    def __init__(self, formats: TabularFormatRegistry) -> None:
        self._formats = formats

    def run(self, request: AnalysisRequest) -> AnalysisArtifact:
        if len(request.sources) != 1:
            raise ValueError("tabular-statistics requires exactly one source")
        raw_spec = request.manifest.parameters.get("spec")
        if not raw_spec:
            raise ValueError("tabular-statistics requires a JSON 'spec' parameter")
        try:
            spec = json.loads(raw_spec)
        except json.JSONDecodeError as failure:
            raise ValueError(f"tabular-statistics spec is invalid JSON: {failure.msg}") from failure
        analyses = spec.get("analyses") if isinstance(spec, dict) and spec.get("schemaVersion") == 1 else None
        if not isinstance(analyses, list) or not analyses or any(not isinstance(item, dict) for item in analyses):
            raise ValueError("tabular-statistics analyses must be a non-empty array of objects")
        source = request.sources[0]
        table = self._formats.reader(source.manifest.format).read(source.path)
        results = [self._analyze(table, item) for item in analyses]
        output = source.path.parent / Path(request.manifest.parameters.get("outputFileName", f"{Path(source.manifest.file_name).stem}-statistics.json")).name
        output.write_text(json.dumps({"schemaVersion": 1, "source": source.manifest.file_name, "results": results}, ensure_ascii=False, indent=2), encoding="utf-8")
        return AnalysisArtifact(output, output.name, "application/json", "JSON", {
            "operation": self.id, "analysisCount": len(results), "inputRows": len(table.rows),
            "outputRows": len(table.rows), "steps": results,
        })

    def _analyze(self, table: Table, spec: dict[str, Any]) -> dict[str, Any]:
        method = str(spec.get("method", ""))
        name = str(spec.get("name", method))
        rows = _filtered_rows(table.rows, spec.get("filters", {}))
        if method in {"welch_t_test", "mann_whitney_u"}:
            group_column, value_column = _required_column(table, spec, "groupColumn"), _required_column(table, spec, "valueColumn")
            group_a, group_b = spec.get("groupA"), spec.get("groupB")
            a = _numeric([row.get(value_column) for row in rows if row.get(group_column) == group_a], value_column)
            b = _numeric([row.get(value_column) for row in rows if row.get(group_column) == group_b], value_column)
            if not a or not b:
                raise ValueError(f"{method} requires non-empty groups")
            if method == "welch_t_test":
                result = stats.ttest_ind(a, b, equal_var=False)
                return {"name": name, "method": method, "groupA": group_a, "groupB": group_b, "nA": len(a), "nB": len(b), "meanA": float(np.mean(a)), "meanB": float(np.mean(b)), "statistic": float(result.statistic), "pValue": float(result.pvalue)}
            result = stats.mannwhitneyu(a, b, alternative=str(spec.get("alternative", "two-sided")), method="auto")
            return {"name": name, "method": method, "groupA": group_a, "groupB": group_b, "nA": len(a), "nB": len(b), "medianA": float(np.median(a)), "medianB": float(np.median(b)), "statistic": float(result.statistic), "pValue": float(result.pvalue)}
        if method == "fisher_exact":
            row_column, column_column = _required_column(table, spec, "rowColumn"), _required_column(table, spec, "columnColumn")
            row_values, column_values = spec.get("rowValues"), spec.get("columnValues")
            if not isinstance(row_values, list) or len(row_values) != 2 or not isinstance(column_values, list) or len(column_values) != 2:
                raise ValueError("fisher_exact requires two rowValues and two columnValues")
            matrix = [[sum(row.get(row_column) == rv and row.get(column_column) == cv for row in rows) for cv in column_values] for rv in row_values]
            result = stats.fisher_exact(matrix, alternative=str(spec.get("alternative", "two-sided")))
            odds_ratio = float(result.statistic)
            return {"name": name, "method": method, "rowValues": row_values, "columnValues": column_values, "table": matrix, "oddsRatio": odds_ratio if math.isfinite(odds_ratio) else None, "pValue": float(result.pvalue)}
        if method == "spearman":
            x_column, y_column = _required_column(table, spec, "xColumn"), _required_column(table, spec, "yColumn")
            pairs = [(_number(row.get(x_column)), _number(row.get(y_column))) for row in rows]
            pairs = [(x, y) for x, y in pairs if x is not None and y is not None]
            if len(pairs) < 2:
                raise ValueError("spearman requires at least two complete pairs")
            x_values, y_values = [x for x, _ in pairs], [y for _, y in pairs]
            result = stats.spearmanr(x_values, y_values)
            p_value, p_method = float(result.pvalue), "asymptotic"
            if len(pairs) <= 9 and len(set(x_values)) == len(pairs) and len(set(y_values)) == len(pairs):
                x_ranks = stats.rankdata(x_values) - (len(pairs) + 1) / 2
                y_ranks = stats.rankdata(y_values) - (len(pairs) + 1) / 2
                denominator = float(np.sqrt(np.dot(x_ranks, x_ranks) * np.dot(y_ranks, y_ranks)))
                threshold = abs(float(result.statistic)) - 1e-12
                extreme = sum(abs(float(np.dot(x_ranks, permutation)) / denominator) >= threshold for permutation in itertools.permutations(y_ranks))
                p_value, p_method = extreme / math.factorial(len(pairs)), "exact-permutation"
            return {"name": name, "method": method, "n": len(pairs), "statistic": float(result.statistic), "pValue": p_value, "pValueMethod": p_method}
        raise ValueError(f"unsupported statistical method: {method!r}")


def default_tabular_operation() -> TabularCleanOperation:
    adapters = [CsvTableAdapter(), XlsxTableAdapter(), JsonTableAdapter()]
    return TabularCleanOperation(
        TabularFormatRegistry(adapters, adapters),
        CleaningRuleRegistry([TrimStringsRule(), NormalizeCaseRule(), DropMissingRule(), RemoveDuplicatesRule(), NumericRangeRule()]),
    )


def default_statistics_operation() -> TabularStatisticsOperation:
    adapters = [CsvTableAdapter(), XlsxTableAdapter(), JsonTableAdapter()]
    return TabularStatisticsOperation(TabularFormatRegistry(adapters, adapters))


def _validate_columns(columns: list[str] | None) -> list[str]:
    if not columns or any(not column for column in columns) or len(set(columns)) != len(columns):
        raise ValueError("tabular input must have non-empty unique column names")
    return list(columns)


def _columns(table: Table, config: dict[str, Any]) -> list[str]:
    columns = config.get("columns", table.columns)
    if not isinstance(columns, list) or not columns or any(not isinstance(item, str) for item in columns):
        raise ValueError("cleaning rule columns must be a non-empty string array")
    unknown = set(columns) - set(table.columns)
    if unknown:
        raise ValueError(f"unknown cleaning columns: {sorted(unknown)}")
    return columns


def _missing(value: Any) -> bool:
    return value is None or (isinstance(value, str) and not value.strip())


def _row_identifier(row: dict[str, Any], columns: list[str]) -> str:
    return "|".join(f"{column}={json.dumps(row.get(column), ensure_ascii=False, sort_keys=True, default=str)}" for column in columns)


def _filtered_rows(rows: list[dict[str, Any]], filters: Any) -> list[dict[str, Any]]:
    if not isinstance(filters, dict):
        raise ValueError("analysis filters must be an object")
    return [row for row in rows if all(row.get(column) == value for column, value in filters.items())]


def _required_column(table: Table, spec: dict[str, Any], key: str) -> str:
    value = spec.get(key)
    if not isinstance(value, str) or value not in table.columns:
        raise ValueError(f"{key} must name an input column")
    return value


def _number(value: Any) -> float | None:
    if _missing(value):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError) as failure:
        raise ValueError(f"non-numeric value encountered: {value!r}") from failure
    return number if math.isfinite(number) else None


def _numeric(values: list[Any], column: str) -> list[float]:
    try:
        return [number for value in values if (number := _number(value)) is not None]
    except ValueError as failure:
        raise ValueError(f"column {column!r} contains a non-numeric value") from failure
