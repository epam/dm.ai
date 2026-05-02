from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class MigrationGuidanceAudit:
    label: str
    path: Path
    location: str
    content: str
    missing_requirements: tuple[str, ...]


class CodeGeneratorMigrationGuidanceService:
    README_SECTION_HEADINGS = (
        "Deprecated compatibility shims",
        "Breaking changes",
        "Removed/Deprecated features",
    )

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self.jobs_reference_path = repository_root / "dmtools-ai-docs/references/jobs/README.md"

    def audit(self) -> list[MigrationGuidanceAudit]:
        return [
            self._audit_root_readme_section(),
            self._audit_jobs_reference_notice(),
        ]

    def jobs_reference_notice_appears_near_top(self) -> bool:
        lines = self.jobs_reference_path.read_text(encoding="utf-8").splitlines()
        non_empty_lines = [line.strip() for line in lines if line.strip()]
        first_blockquote_index = next(
            (index for index, line in enumerate(non_empty_lines) if line.startswith(">")),
            None,
        )

        return (
            len(non_empty_lines) >= 3
            and non_empty_lines[0].startswith("# ")
            and first_blockquote_index is not None
            and first_blockquote_index <= 2
        )

    @staticmethod
    def format_missing_requirements(audits: list[MigrationGuidanceAudit]) -> str:
        lines = [
            "DMC-910 expects visible CodeGenerator migration guidance in both the root README and the jobs reference."
        ]
        for audit in audits:
            if not audit.missing_requirements:
                continue
            lines.append(
                f"- {audit.label} ({audit.path.as_posix()} :: {audit.location}): "
                f"missing {', '.join(audit.missing_requirements)}"
            )
            lines.append("  Observed content:")
            for content_line in audit.content.splitlines():
                lines.append(f"    {content_line}")
        return "\n".join(lines)

    def _audit_root_readme_section(self) -> MigrationGuidanceAudit:
        location, section = self._extract_root_readme_section()
        normalized_section = self._normalize(section)
        missing_requirements = tuple(
            requirement
            for requirement, token in {
                "CodeGenerator bullet": "`codegenerator`",
                "deprecated wording": "deprecated",
                "compatibility shim wording": "compatibility shim",
                "one-release removal timeline": "one release",
                "warning/no-op behavior": "warning and return a no-op response",
                "version target v1.8.0": "v1.8.0",
            }.items()
            if token not in normalized_section
        )
        return MigrationGuidanceAudit(
            label="root README migration guidance section",
            path=self.readme_path,
            location=location,
            content=section,
            missing_requirements=missing_requirements,
        )

    def _audit_jobs_reference_notice(self) -> MigrationGuidanceAudit:
        notice = self._extract_first_blockquote(self.jobs_reference_path)
        normalized_notice = self._normalize(notice)
        placement_is_valid = self.jobs_reference_notice_appears_near_top()
        missing_requirements = tuple(
            requirement
            for requirement, is_missing in {
                "CodeGenerator mention": "`codegenerator`" not in normalized_notice,
                "unsupported workflow wording": "no longer a supported development workflow"
                not in normalized_notice,
                "compatibility shim wording": "compatibility shim" not in normalized_notice,
                "deprecation warning wording": "logs a deprecation warning" not in normalized_notice,
                "no-generation wording": "performs no generation" not in normalized_notice,
                "alternative points to Teammate": "`teammate`-driven development flows"
                not in normalized_notice,
                "version target v1.8.0": "v1.8.0" not in normalized_notice,
                "leading placement near top of page": not placement_is_valid,
            }.items()
            if is_missing
        )
        return MigrationGuidanceAudit(
            label="jobs reference deprecation notice",
            path=self.jobs_reference_path,
            location="blockquote notice near the top of the page",
            content=notice,
            missing_requirements=missing_requirements,
        )

    def _extract_root_readme_section(self) -> tuple[str, str]:
        allowed_headings = {
            self._normalize(heading) for heading in self.README_SECTION_HEADINGS
        }
        for heading, section in self._extract_markdown_sections(self.readme_path):
            normalized_heading = self._normalize(heading)
            normalized_section = self._normalize(section)
            if "`codegenerator`" not in normalized_section:
                continue
            if normalized_heading in allowed_headings or any(
                token in normalized_heading for token in ("deprecated", "breaking", "removed")
            ):
                return heading, section

        expected_headings = ", ".join(f"'{heading}'" for heading in self.README_SECTION_HEADINGS)
        raise AssertionError(
            "Could not find a README migration section for CodeGenerator under an equivalent "
            f"deprecated-feature heading ({expected_headings}) in {self.readme_path.as_posix()}"
        )

    @staticmethod
    def _extract_markdown_sections(path: Path) -> list[tuple[str, str]]:
        lines = path.read_text(encoding="utf-8").splitlines()
        sections: list[tuple[str, str]] = []

        for index, line in enumerate(lines):
            stripped_line = line.strip()
            match = re.match(r"^(?P<hashes>#+)\s+(?P<title>.+?)\s*$", stripped_line)
            if not match:
                continue

            level = len(match.group("hashes"))
            collected_lines = [line]
            for candidate_line in lines[index + 1 :]:
                stripped_candidate = candidate_line.strip()
                if stripped_candidate.startswith("#"):
                    next_heading_match = re.match(r"^(?P<hashes>#+)\s+", stripped_candidate)
                    if next_heading_match and len(next_heading_match.group("hashes")) <= level:
                        break
                collected_lines.append(candidate_line)
            sections.append((match.group("title"), "\n".join(collected_lines).strip()))

        return sections

    @staticmethod
    def _extract_first_blockquote(path: Path) -> str:
        lines = path.read_text(encoding="utf-8").splitlines()
        collected_lines: list[str] = []
        in_blockquote = False

        for line in lines:
            stripped_line = line.strip()
            if stripped_line.startswith(">"):
                in_blockquote = True
                collected_lines.append(stripped_line)
                continue
            if in_blockquote:
                break

        if not collected_lines:
            raise AssertionError(f"Could not find a blockquote notice in {path.as_posix()}")

        return "\n".join(collected_lines)

    @staticmethod
    def _normalize(content: str) -> str:
        return re.sub(r"\s+", " ", content).strip().lower()
