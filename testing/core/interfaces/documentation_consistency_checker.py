from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path
from typing import Iterable

from testing.core.models.job_reference import JobReference


class DocumentationConsistencyChecker(ABC):
    @property
    @abstractmethod
    def canonical_paths(self) -> tuple[Path, Path]:
        pass

    @property
    @abstractmethod
    def secondary_paths(self) -> tuple[Path, Path]:
        pass

    @abstractmethod
    def canonical_reference_tables(self) -> tuple[list[JobReference], list[JobReference]]:
        pass

    @abstractmethod
    def reference_by_name(self) -> dict[str, JobReference]:
        pass

    @abstractmethod
    def valid_job_names(self) -> set[str]:
        pass

    @abstractmethod
    def invalid_job_names(self, path: Path, valid_names: set[str]) -> list[str]:
        pass

    @abstractmethod
    def inconsistent_secondary_summaries(
        self,
        path: Path,
        references_by_name: dict[str, JobReference],
    ) -> list[str]:
        pass

    @staticmethod
    @abstractmethod
    def format_table_mismatch(
        left_label: str,
        left_table: Iterable[JobReference],
        right_label: str,
        right_table: Iterable[JobReference],
    ) -> str:
        pass

    @staticmethod
    @abstractmethod
    def format_invalid_name_findings(findings: dict[str, list[str]]) -> str:
        pass

    @staticmethod
    @abstractmethod
    def format_summary_findings(findings: dict[str, list[str]]) -> str:
        pass

