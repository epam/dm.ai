from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class DocumentationRow:
    job: str
    accepted_names: tuple[str, ...]
    example_path: Path


class DocumentationAudit(ABC):
    @abstractmethod
    def get_row_for_canonical_name(self, canonical_name: str) -> DocumentationRow:
        raise NotImplementedError

    @abstractmethod
    def read_job_name_from_example(self, example_path: Path) -> str:
        raise NotImplementedError

    @abstractmethod
    def find_inexact_name_field_mentions(
        self,
        accepted_names: dict[str, str],
        exact_name_spellings: set[str],
    ) -> list[str]:
        raise NotImplementedError

    @abstractmethod
    def find_deprecated_mentions(self, identifiers: list[str]) -> list[str]:
        raise NotImplementedError
