from __future__ import annotations

import re
from pathlib import Path
from typing import Mapping

import numpy as np
from scipy.io import loadmat, savemat


class MatFormatAdapter:
    """Reads and writes MATLAB level-5 MAT variables as NumPy arrays."""

    def read(self, path: Path) -> dict[str, np.ndarray]:
        return {
            name: np.asarray(value)
            for name, value in loadmat(path).items()
            if not name.startswith("__")
        }

    def write(self, datasets: Mapping[str, np.ndarray], path: Path) -> None:
        if not datasets:
            raise ValueError("MAT output requires at least one variable")
        normalized: dict[str, np.ndarray] = {}
        for name, value in datasets.items():
            variable = re.sub(r"[^A-Za-z0-9_]", "__", name.strip("/"))
            if not variable or not variable[0].isalpha():
                variable = f"data_{variable}"
            if variable in normalized:
                raise ValueError(f"MAT variable name collision after normalization: {name!r}")
            normalized[variable] = np.asarray(value)
        savemat(path, normalized, do_compression=True, long_field_names=True)
