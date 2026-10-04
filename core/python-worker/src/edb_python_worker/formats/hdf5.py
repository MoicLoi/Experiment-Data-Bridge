from __future__ import annotations

from pathlib import Path
from typing import Mapping

import h5py
import numpy as np


class Hdf5FormatAdapter:
    """Reads and writes a flat mapping of HDF5 dataset paths to NumPy arrays."""

    def read(self, path: Path) -> dict[str, np.ndarray]:
        datasets: dict[str, np.ndarray] = {}
        with h5py.File(path, "r") as handle:
            def collect(name: str, value: h5py.Group | h5py.Dataset) -> None:
                if isinstance(value, h5py.Dataset):
                    datasets[name] = np.asarray(value[()])

            handle.visititems(collect)
        return datasets

    def write(self, datasets: Mapping[str, np.ndarray], path: Path) -> None:
        if not datasets:
            raise ValueError("HDF5 output requires at least one dataset")
        with h5py.File(path, "w") as handle:
            for raw_name, raw_value in datasets.items():
                name = raw_name.strip("/")
                if not name or any(segment in {"", ".", ".."} for segment in name.split("/")):
                    raise ValueError(f"invalid HDF5 dataset path: {raw_name!r}")
                value = np.asarray(raw_value)
                if value.dtype.kind == "O":
                    raise ValueError(f"object arrays cannot be written to HDF5: {name}")
                parent, _, leaf = name.rpartition("/")
                group = handle.require_group(parent) if parent else handle
                group.create_dataset(leaf, data=value)
