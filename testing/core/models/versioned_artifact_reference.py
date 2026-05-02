from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class VersionedArtifactReference:
    path: Path
    line_number: int
    line: str
    url: str
    owner: str | None = None
    repo: str | None = None
    path_version: str | None = None
    file_version: str | None = None

    @property
    def location(self) -> str:
        return f"{self.path}:{self.line_number}"
