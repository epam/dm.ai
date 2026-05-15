from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RecordedRequest:
    method: str
    path: str
    timestamp: float
