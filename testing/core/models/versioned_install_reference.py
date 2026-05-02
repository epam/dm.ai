from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class VersionedInstallReference:
    path: Path
    line_number: int
    line: str
    url: str
    url_version: str
    flag_version: str | None = None
    owner: str | None = None
    repo: str | None = None

    @property
    def location(self) -> str:
        return f"{self.path}:{self.line_number}"
