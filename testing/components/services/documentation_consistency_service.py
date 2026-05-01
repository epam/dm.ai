from __future__ import annotations

import re
from pathlib import Path
from typing import Iterable

from testing.core.interfaces.documentation_consistency_checker import (
    DocumentationConsistencyChecker,
)
from testing.core.models.job_reference import JobReference
from testing.core.utils.markdown_job_reference_parser import (
    extract_identifier_tokens,
    extract_job_reference_table,
    extract_markdown_paragraphs,
)

NON_JOB_IDENTIFIERS = frozenset({"InstructionProcessor"})
SUMMARY_STOP_WORDS = frozenset(
    {
        "a",
        "an",
        "and",
        "as",
        "for",
        "from",
        "in",
        "into",
        "it",
        "of",
        "on",
        "or",
        "the",
        "to",
        "with",
    }
)
SHORT_ALLOWED_KEYWORDS = frozenset({"ai", "js", "kb", "qa"})
DESCRIPTION_VERBS = frozenset(
    {
        "allows",
        "builds",
        "calculates",
        "creates",
        "enables",
        "executes",
        "generates",
        "integrates",
        "orchestrates",
        "produces",
        "renders",
        "runs",
        "supports",
        "uses",
        "works",
    }
)
TEXT_TOKEN_PATTERN = re.compile(r"[A-Za-z0-9]+")


class DocumentationConsistencyService(DocumentationConsistencyChecker):
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

    def reference_by_name(self) -> dict[str, JobReference]:
        references: dict[str, JobReference] = {}
        for path in self.canonical_paths:
            for reference in self.load_job_reference_table(path):
                for name in reference.all_names:
                    references[name] = reference
        return references

    def valid_job_names(self) -> set[str]:
        return set(self.reference_by_name())

    def invalid_job_names(self, path: Path, valid_names: set[str]) -> list[str]:
        job_name_suffixes = self._job_name_suffixes(valid_names)
        return sorted(
            {
                token
                for token in extract_identifier_tokens(path)
                if token not in valid_names
                and token not in NON_JOB_IDENTIFIERS
                and self._looks_like_job_identifier(token, job_name_suffixes)
            }
        )

    def inconsistent_secondary_summaries(
        self,
        path: Path,
        references_by_name: dict[str, JobReference],
    ) -> list[str]:
        findings: list[str] = []
        paragraphs = extract_markdown_paragraphs(path)
        canonical_references = {reference.job: reference for reference in references_by_name.values()}

        for reference in canonical_references.values():
            matching_paragraphs = [
                paragraph
                for paragraph in paragraphs
                if self._is_descriptive_job_paragraph(paragraph.text, reference.all_names)
            ]
            if not matching_paragraphs:
                continue

            best_overlap = 0
            best_excerpt = ""
            for paragraph in matching_paragraphs:
                overlap = len(reference.summary_keywords & self._text_keywords(paragraph.text))
                if overlap > best_overlap:
                    best_overlap = overlap
                    best_excerpt = f"L{paragraph.start_line}: {paragraph.text}"

            required_overlap = self._required_summary_overlap(reference)
            if best_overlap < required_overlap:
                findings.append(
                    f"{reference.job} summary drift: expected '{reference.summary}' "
                    f"(best prose match {best_overlap}/{required_overlap}: {best_excerpt or 'no matching prose found'})"
                )

        return findings

    @staticmethod
    def _job_name_suffixes(valid_names: set[str]) -> tuple[str, ...]:
        suffixes = {_last_camel_case_segment(name) for name in valid_names}
        suffixes.add("Processor")
        return tuple(sorted(suffixes, key=len, reverse=True))

    @staticmethod
    def _looks_like_job_identifier(token: str, suffixes: tuple[str, ...]) -> bool:
        if token in {"Teammate", "JSRunner"}:
            return True
        return token.endswith(suffixes) and len(_camel_case_segments(token)) > 1

    @staticmethod
    def _required_summary_overlap(reference: JobReference) -> int:
        return 1 if len(reference.summary_keywords) == 1 else 2

    @staticmethod
    def _text_keywords(text: str) -> frozenset[str]:
        keywords: set[str] = set()
        for token in TEXT_TOKEN_PATTERN.findall(text.lower()):
            if token in SUMMARY_STOP_WORDS:
                continue
            if len(token) >= 4 or token in SHORT_ALLOWED_KEYWORDS:
                keywords.add(token)
        return frozenset(keywords)

    @staticmethod
    def _is_descriptive_job_paragraph(text: str, names: tuple[str, ...]) -> bool:
        if not any(_mentions_name(text, name) for name in names):
            return False
        return bool(DocumentationConsistencyService._text_keywords(text) & DESCRIPTION_VERBS)

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
        lines = ["Found non-canonical job names in secondary documentation:"]
        for document_name, names in sorted(findings.items()):
            lines.append(f"- {document_name}: {', '.join(names)}")
        return "\n".join(lines)

    @staticmethod
    def format_summary_findings(findings: dict[str, list[str]]) -> str:
        lines = ["Found secondary-document summary inconsistencies:"]
        for document_name, mismatches in sorted(findings.items()):
            lines.append(f"- {document_name}:")
            lines.extend(f"  - {mismatch}" for mismatch in mismatches)
        return "\n".join(lines)


def _last_camel_case_segment(name: str) -> str:
    segments = _camel_case_segments(name)
    return segments[-1] if segments else name


def _mentions_name(text: str, name: str) -> bool:
    return re.search(rf"\b{re.escape(name)}\b", text) is not None


def _camel_case_segments(name: str) -> list[str]:
    return re.findall(r"[A-Z][a-z0-9]*", name)
