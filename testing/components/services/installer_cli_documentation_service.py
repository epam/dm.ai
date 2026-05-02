from __future__ import annotations

import re
from pathlib import Path


class InstallerCliDocumentationService:
    SECTION_HEADING = "Install Only the Skills You Need"

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.installation_readme_path = (
            repository_root / "dmtools-ai-docs/references/installation/README.md"
        )

    def audit_installation_readme(self) -> list[str]:
        if not self.installation_readme_path.exists():
            return [
                "Missing installation guide: "
                f"{self.installation_readme_path.relative_to(self.repository_root)}"
            ]

        sections = self._extract_named_sections(
            self.installation_readme_path,
            self.SECTION_HEADING,
        )
        if not sections:
            return [
                f"Missing '{self.SECTION_HEADING}' section in "
                f"{self.installation_readme_path.relative_to(self.repository_root)}."
            ]

        section = sections[0]
        normalized_section = self._normalize(section)

        findings: list[str] = []
        if not self._has_primary_skill_flag(section, normalized_section):
            findings.append(
                "The installer usage section does not document `--skill <name>` as the "
                "primary or canonical skill-selection form."
            )
        if not self._has_skills_alias(section, normalized_section):
            findings.append(
                "The installer usage section does not document `--skills=<name,name>` "
                "as an allowed alias for multi-skill selection."
            )
        if "--all-skills" not in section:
            findings.append(
                "The installer usage section does not mention the `--all-skills` flag."
            )
        if "--skip-unknown" not in section:
            findings.append(
                "The installer usage section does not mention the `--skip-unknown` flag."
            )
        if not self._has_invalid_skill_behavior(section, normalized_section):
            findings.append(
                "The installer usage section does not explicitly describe invalid-skill "
                "behavior: non-zero exit with invalid names listed, and warnings when "
                "`--skip-unknown` is used."
            )
        return findings

    def format_findings(self, findings: list[str]) -> str:
        lines = [
            "Expected the installation guide to describe the canonical installer "
            "selection flags and invalid-skill behavior.",
            f"Checked file: {self.installation_readme_path.relative_to(self.repository_root)}",
            "",
            "Missing or incomplete expectations:",
            *[f"- {finding}" for finding in findings],
            "",
            "Observed 'Install Only the Skills You Need' section:",
            self._indented_section(self._extract_named_sections(
                self.installation_readme_path,
                self.SECTION_HEADING,
            )[0]),
        ]
        return "\n".join(lines)

    @staticmethod
    def _extract_named_sections(path: Path, heading: str) -> list[str]:
        lines = path.read_text(encoding="utf-8").splitlines()
        sections: list[str] = []
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
            sections.append("\n".join(collected_lines).strip())

        return sections

    @staticmethod
    def _normalize(content: str) -> str:
        return re.sub(r"\s+", " ", content).lower()

    @staticmethod
    def _has_primary_skill_flag(section: str, normalized_section: str) -> bool:
        mentions_flag = bool(
            re.search(r"--skill(?:\s+<name>|(?:=|\s+)[a-z0-9-]+)", section)
        )
        mentions_primary = any(
            token in normalized_section
            for token in ("primary", "canonical", "preferred", "recommended")
        )
        return mentions_flag and mentions_primary

    @staticmethod
    def _has_skills_alias(section: str, normalized_section: str) -> bool:
        mentions_flag = bool(
            re.search(r"--skills=(?:<name,name>|[a-z0-9-]+(?:,[a-z0-9-]+)+)", section)
        )
        mentions_alias = any(
            token in normalized_section
            for token in (
                "alias",
                "also supported",
                "still supported",
                "allowed alias",
            )
        )
        return mentions_flag and mentions_alias

    @staticmethod
    def _has_invalid_skill_behavior(section: str, normalized_section: str) -> bool:
        mentions_invalid_names = (
            "invalid names" in normalized_section
            or "invalid name" in normalized_section
            or "unknown skill names" in normalized_section
        )
        mentions_non_zero_exit = any(
            token in normalized_section
            for token in ("non-zero exit", "nonzero exit", "exit 1", "status 1")
        )
        mentions_warning_downgrade = "--skip-unknown" in section and any(
            token in normalized_section
            for token in ("warning", "warnings", "warn instead", "downgrade")
        )
        return (
            mentions_invalid_names
            and mentions_non_zero_exit
            and mentions_warning_downgrade
        )

    @staticmethod
    def _indented_section(section: str) -> str:
        return "\n".join(f"  {line}" for line in section.splitlines())
