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
    README_SECTION_HEADING = "Deprecated compatibility shims"

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self.jobs_reference_path = repository_root / "dmtools-ai-docs/references/jobs/README.md"

    def audit(self) -> list[MigrationGuidanceAudit]:
        return [
            self._audit_root_readme_section(),
            self._audit_jobs_reference_notice(),
        ]

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
        section = self._extract_named_section(self.readme_path, self.README_SECTION_HEADING)
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
            label="root README deprecated compatibility shims section",
            path=self.readme_path,
            location=self.README_SECTION_HEADING,
            content=section,
            missing_requirements=missing_requirements,
        )

    def _audit_jobs_reference_notice(self) -> MigrationGuidanceAudit:
        notice = self._extract_first_blockquote(self.jobs_reference_path)
        normalized_notice = self._normalize(notice)
        missing_requirements = tuple(
            requirement
            for requirement, token in {
                "CodeGenerator mention": "`codegenerator`",
                "unsupported workflow wording": "no longer a supported development workflow",
                "compatibility shim wording": "compatibility shim",
                "deprecation warning wording": "logs a deprecation warning",
                "no-generation wording": "performs no generation",
                "alternative points to Teammate": "`teammate`-driven development flows",
                "version target v1.8.0": "v1.8.0",
            }.items()
            if token not in normalized_notice
        )
        return MigrationGuidanceAudit(
            label="jobs reference deprecation notice",
            path=self.jobs_reference_path,
            location="leading blockquote notice",
            content=notice,
            missing_requirements=missing_requirements,
        )

    @staticmethod
    def _extract_named_section(path: Path, heading: str) -> str:
        lines = path.read_text(encoding="utf-8").splitlines()
        heading_pattern = re.compile(rf"^(?P<hashes>#+)\s+{re.escape(heading)}\s*$")

        for index, line in enumerate(lines):
            match = heading_pattern.match(line.strip())
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
            return "\n".join(collected_lines).strip()

        raise AssertionError(f"Could not find heading '{heading}' in {path.as_posix()}")

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
