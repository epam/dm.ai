from __future__ import annotations

import re
from pathlib import Path

from testing.core.models.documentation_finding import DocumentationFinding

FORBIDDEN_REFERENCE_PATTERNS = (
    re.compile(r"IstiN/dmtools"),
    re.compile(r"IstiN/dmtools-cli"),
    re.compile(r"raw\.githubusercontent\.com"),
)
VERSION_PINNED_INSTALL_PATTERNS = (
    re.compile(r"releases/download/v\d+\.\d+\.\d+/install(?:\.sh|\.bat)?"),
    re.compile(r"\b(?:bash -s --|set DMTOOLS_VERSION=) v?\d+\.\d+\.\d+"),
    re.compile(r"Specific Version \(e\.g\., v\d+\.\d+\.\d+\)"),
)
MARKDOWN_HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")
ACTIVE_INSTALL_HEADING_HINTS = ("install", "installation", "setup", "upgrade", "quick start")
ACTIONABLE_INSTALL_LINE_HINTS = (
    "curl ",
    "wget ",
    "install.sh",
    "install.bat",
    "install | bash",
    "install | bash",
    "setup-dmtools.sh",
    "bootstrap",
    "re-run the installer",
)
NON_ACTIONABLE_INSTALL_LINE_HINTS = (
    "failed to connect to raw.githubusercontent.com",
    "supports raw urls",
)
PRIMARY_INSTALL_DOC_PATHS = frozenset(
    {
        "README.md",
        "dmtools-ai-docs/SKILL.md",
    }
)
PRIMARY_INSTALL_DOC_PREFIXES = ("dmtools-ai-docs/references/installation/",)


class LegacyInstallReferenceService:
    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.docs_root = repository_root / "dmtools-ai-docs"
        self.target_paths = (repository_root / "README.md", *sorted(self.docs_root.rglob("*.md")))

    def forbidden_legacy_reference_findings(self) -> list[DocumentationFinding]:
        return self._collect_findings(
            category="legacy-reference",
            reason="Legacy repository/reference is present in active guidance.",
            patterns=FORBIDDEN_REFERENCE_PATTERNS,
            allow_deprecated_legacy_section=True,
        )

    def version_pinned_install_findings(self) -> list[DocumentationFinding]:
        return self._collect_findings(
            category="version-pinned-install",
            reason="Version-pinned install guidance is present where the latest flow should be used.",
            patterns=VERSION_PINNED_INSTALL_PATTERNS,
            allow_deprecated_legacy_section=False,
        )

    def all_findings(self) -> list[DocumentationFinding]:
        return [
            *self.forbidden_legacy_reference_findings(),
            *self.version_pinned_install_findings(),
        ]

    def _collect_findings(
        self,
        *,
        category: str,
        reason: str,
        patterns: tuple[re.Pattern[str], ...],
        allow_deprecated_legacy_section: bool,
    ) -> list[DocumentationFinding]:
        findings: list[DocumentationFinding] = []
        for path in self.target_paths:
            findings.extend(
                self._scan_markdown_path(
                    path=path,
                    category=category,
                    reason=reason,
                    patterns=patterns,
                    allow_deprecated_legacy_section=allow_deprecated_legacy_section,
                )
            )
        return findings

    def _scan_markdown_path(
        self,
        *,
        path: Path,
        category: str,
        reason: str,
        patterns: tuple[re.Pattern[str], ...],
        allow_deprecated_legacy_section: bool,
    ) -> list[DocumentationFinding]:
        findings: list[DocumentationFinding] = []
        heading_stack: list[str] = []

        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            heading_match = MARKDOWN_HEADING_PATTERN.match(line)
            if heading_match:
                level = len(heading_match.group(1))
                heading_title = heading_match.group(2).strip()
                heading_stack = heading_stack[: level - 1]
                heading_stack.append(heading_title)

            if not self._is_active_install_guidance(path, heading_stack, line):
                continue

            if allow_deprecated_legacy_section and self._is_upgrading_from_legacy_section(heading_stack):
                continue

            if not any(pattern.search(line) for pattern in patterns):
                continue

            findings.append(
                DocumentationFinding(
                    category=category,
                    reason=reason,
                    file_path=str(path.relative_to(self.repository_root)),
                    line_number=line_number,
                    line_text=line.strip(),
                    section=" > ".join(heading_stack) if heading_stack else "(no heading)",
                )
            )

        return findings

    @staticmethod
    def _is_upgrading_from_legacy_section(heading_stack: list[str]) -> bool:
        return any(heading.casefold() == "upgrading from legacy installs" for heading in heading_stack)

    def _is_active_install_guidance(self, path: Path, heading_stack: list[str], line: str) -> bool:
        relative_path = path.relative_to(self.repository_root).as_posix()
        normalized_line = line.casefold()
        normalized_headings = [heading.casefold() for heading in heading_stack]

        if any(hint in normalized_line for hint in NON_ACTIONABLE_INSTALL_LINE_HINTS):
            return False

        if line.lstrip().startswith("curl:"):
            return False

        if not self._is_primary_install_doc(relative_path):
            return False

        active_heading = any(
            any(hint in heading for hint in ACTIVE_INSTALL_HEADING_HINTS)
            for heading in normalized_headings
        )
        actionable_line = any(hint in normalized_line for hint in ACTIONABLE_INSTALL_LINE_HINTS)

        return actionable_line or active_heading

    @staticmethod
    def _is_primary_install_doc(relative_path: str) -> bool:
        return relative_path in PRIMARY_INSTALL_DOC_PATHS or any(
            relative_path.startswith(prefix) for prefix in PRIMARY_INSTALL_DOC_PREFIXES
        )

    @staticmethod
    def format_findings(findings: list[DocumentationFinding]) -> str:
        lines = [
            "Active installation guidance still contains forbidden legacy references or version-pinned installer examples:"
        ]
        for finding in findings:
            lines.append(
                f"- [{finding.category}] {finding.reason} "
                f"({finding.file_path}:{finding.line_number}, section: {finding.section})"
            )
            lines.append(f"  {finding.line_text}")
        return "\n".join(lines)
