from __future__ import annotations

from pathlib import Path
from typing import Iterable

from testing.core.models.job_reference import JobReference
from testing.core.utils.markdown_job_reference_parser import (
    extract_backticked_job_like_identifiers,
    extract_job_reference_table,
    extract_json_name_values,
)


class DocumentationConsistencyService:
    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.teammate_configs_path = repository_root / "dmtools-ai-docs/references/agents/teammate-configs.md"
        self.jobs_readme_path = repository_root / "dmtools-ai-docs/references/jobs/README.md"
        self.javascript_agents_path = repository_root / "dmtools-ai-docs/references/agents/javascript-agents.md"
        self.cli_integration_path = repository_root / "dmtools-ai-docs/references/agents/cli-integration.md"

    @property
    def canonical_paths(self) -> tuple[Path, Path]:
        return self.teammate_configs_path, self.jobs_readme_path

    @property
    def secondary_paths(self) -> tuple[Path, Path]:
        return self.javascript_agents_path, self.cli_integration_path

    def load_job_reference_table(self, path: Path) -> list[JobReference]:
        return extract_job_reference_table(path)

    def canonical_reference_tables(self) -> tuple[list[JobReference], list[JobReference]]:
        return (
            self.load_job_reference_table(self.teammate_configs_path),
            self.load_job_reference_table(self.jobs_readme_path),
        )

    def valid_job_names(self) -> set[str]:
        valid_names: set[str] = set()
        for path in self.canonical_paths:
            for reference in self.load_job_reference_table(path):
                valid_names.add(reference.job)
                valid_names.update(reference.accepted_names)
        return valid_names

    def invalid_json_name_values(self, path: Path, valid_names: set[str]) -> list[str]:
        return sorted({name for name in extract_json_name_values(path) if name not in valid_names})

    def suspicious_job_identifiers(self, path: Path, valid_names: set[str]) -> list[str]:
        return sorted(
            {
                token
                for token in extract_backticked_job_like_identifiers(path)
                if token not in valid_names
            }
        )

    @staticmethod
    def format_table_mismatch(
        left_label: str,
        left_table: Iterable[JobReference],
        right_label: str,
        right_table: Iterable[JobReference],
    ) -> str:
        left_lines = [f"{index + 1}. {reference}" for index, reference in enumerate(left_table)]
        right_lines = [f"{index + 1}. {reference}" for index, reference in enumerate(right_table)]
        return (
            f"Job reference tables differ between {left_label} and {right_label}.\n"
            f"{left_label}:\n" + "\n".join(left_lines) + "\n\n"
            f"{right_label}:\n" + "\n".join(right_lines)
        )

    @staticmethod
    def format_invalid_name_findings(findings: dict[str, list[str]]) -> str:
        lines = ["Found non-canonical job names in secondary documentation examples:"]
        for document_name, names in sorted(findings.items()):
            lines.append(f"- {document_name}: {', '.join(names)}")
        return "\n".join(lines)

