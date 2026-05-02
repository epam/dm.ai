from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class DocumentationFinding:
    category: str
    reason: str
    file_path: str
    line_number: int
    line_text: str
    section: str
