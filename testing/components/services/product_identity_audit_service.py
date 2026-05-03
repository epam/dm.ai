from __future__ import annotations

from dataclasses import dataclass
import re
from pathlib import Path


@dataclass(frozen=True)
class ProductIdentityAudit:
    label: str
    path: Path
    location: str
    line_number: int
    text: str
    issues: tuple[str, ...]


class ProductIdentityAuditService:
    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.root_readme_path = repository_root / "README.md"
        self.docs_readme_path = repository_root / "dmtools-ai-docs/README.md"
        self.skill_path = repository_root / "dmtools-ai-docs/SKILL.md"

    def audit(self) -> list[ProductIdentityAudit]:
        return [
            self._audit_lead_paragraph(
                self.root_readme_path,
                label="root README lead paragraph",
            ),
            self._audit_lead_paragraph(
                self.docs_readme_path,
                label="documentation hub lead paragraph",
            ),
            self._audit_lead_paragraph(
                self.skill_path,
                label="skill guide lead paragraph",
            ),
        ]

    @staticmethod
    def format_findings(audits: list[ProductIdentityAudit]) -> str:
        lines = [
            "DMC-981 expects README.md, dmtools-ai-docs/README.md, and dmtools-ai-docs/SKILL.md "
            "to describe DMTools consistently as an orchestrator with enterprise dark-factory "
            "positioning and without conflicting toolkit wording."
        ]
        for audit in audits:
            if not audit.issues:
                continue
            issue_list = ", ".join(audit.issues)
            lines.append(
                f"- {audit.label} ({audit.path.as_posix()}:L{audit.line_number}, {audit.location}): "
                f"{issue_list}"
            )
            lines.append(f"  Observed text: {audit.text}")
        return "\n".join(lines)

    def _audit_lead_paragraph(self, path: Path, label: str) -> ProductIdentityAudit:
        line_number, text = self._extract_lead_paragraph(path)
        normalized = self._normalize(text)

        issues = []
        if re.search(r"\borchestrator\b", normalized) is None:
            issues.append("missing orchestrator wording")
        if not self._mentions_enterprise_dark_factory(normalized):
            issues.append("missing enterprise dark-factory positioning")
        if re.search(r"\btoolkit\b", normalized) is not None:
            issues.append("contains conflicting toolkit wording")

        return ProductIdentityAudit(
            label=label,
            path=path,
            location="first paragraph after the H1 heading",
            line_number=line_number,
            text=text,
            issues=tuple(issues),
        )

    @staticmethod
    def _extract_lead_paragraph(path: Path) -> tuple[int, str]:
        lines = path.read_text(encoding="utf-8").splitlines()
        content_start_index = ProductIdentityAuditService._content_start_index(lines)

        heading_index = next(
            (
                index
                for index in range(content_start_index, len(lines))
                if lines[index].strip().startswith("# ")
            ),
            None,
        )
        if heading_index is None:
            raise AssertionError(f"Could not find an H1 heading in {path.as_posix()}")

        paragraph_start_index = next(
            (
                index
                for index in range(heading_index + 1, len(lines))
                if lines[index].strip() and not lines[index].strip().startswith("#")
            ),
            None,
        )
        if paragraph_start_index is None:
            raise AssertionError(
                f"Could not find a lead paragraph after the H1 heading in {path.as_posix()}"
            )

        paragraph_lines: list[str] = []
        for index in range(paragraph_start_index, len(lines)):
            stripped_line = lines[index].strip()
            if not stripped_line or stripped_line.startswith("#") or stripped_line.startswith("```"):
                break
            paragraph_lines.append(stripped_line)

        if not paragraph_lines:
            raise AssertionError(
                f"Could not read a lead paragraph after the H1 heading in {path.as_posix()}"
            )

        return paragraph_start_index + 1, " ".join(paragraph_lines)

    @staticmethod
    def _content_start_index(lines: list[str]) -> int:
        if not lines or lines[0].strip() != "---":
            return 0

        closing_index = next(
            (index for index in range(1, len(lines)) if lines[index].strip() == "---"),
            None,
        )
        if closing_index is None:
            raise AssertionError("Encountered an unterminated YAML frontmatter block.")
        return closing_index + 1

    @staticmethod
    def _mentions_enterprise_dark_factory(normalized: str) -> bool:
        return (
            re.search(r"\benterprise\b", normalized) is not None
            and re.search(r"\bdark(?:-|\s)?fact(?:ory|ories)\b", normalized) is not None
        )

    @staticmethod
    def _normalize(text: str) -> str:
        return re.sub(r"\s+", " ", text).strip().lower()
