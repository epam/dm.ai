from __future__ import annotations

import re
from pathlib import Path


class InstallerCliDocumentationService:
    SECTION_HEADING = "Install Only the Skills You Need"
    PRIMARY_SKILL_PATTERN = re.compile(r"--skill\s+[a-z0-9-]+\b")
    ALIAS_SKILLS_PATTERN = re.compile(r"--skills=[a-z0-9-]+(?:,[a-z0-9-]+)+\b")
    ALL_SKILLS_PATTERN = re.compile(r"--all-skills\b")
    SKIP_UNKNOWN_PATTERN = re.compile(r"--skip-unknown\b")
    INVALID_SKILL_TERMS = ("unknown skill", "unknown skills", "invalid skill", "invalid skills")
    NON_ZERO_EXIT_TERMS = (
        "non-zero exit",
        "non zero exit",
        "exit non-zero",
        "exit 1",
        "exits 1",
        "exit code 1",
        "non-zero status",
    )
    INVALID_NAME_LISTING_TERMS = (
        "list invalid names",
        "lists invalid names",
        "invalid names listed",
        "invalid names are listed",
        "list the invalid names",
        "lists the invalid names",
    )
    WARNING_TERMS = ("warning", "warnings", "warn", "warns")

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

        if not self.PRIMARY_SKILL_PATTERN.search(section):
            findings.append(
                "The installer usage section does not document `--skill <name>` as "
                "the primary skill-selection form with a copy-pasteable example."
            )
        if not self.ALIAS_SKILLS_PATTERN.search(section):
            findings.append(
                "The installer usage section does not document "
                "`--skills=<name,name>` as an allowed alias with a copy-pasteable "
                "example."
            )
        if not self.ALL_SKILLS_PATTERN.search(section):
            findings.append(
                "The installer usage section does not document the `--all-skills` "
                "flag."
            )
        if not self.SKIP_UNKNOWN_PATTERN.search(section):
            findings.append(
                "The installer usage section does not document the "
                "`--skip-unknown` flag."
            )
        if not self._documents_invalid_skill_failure(normalized_section):
            findings.append(
                "The installer usage section does not explain that unknown skill "
                "names cause a non-zero exit and list the invalid names."
            )
        if not self._documents_skip_unknown_warning_behavior(normalized_section):
            findings.append(
                "The installer usage section does not explain that `--skip-unknown` "
                "downgrades invalid skill names to warnings."
            )
        return findings

    def format_findings(self, findings: list[str]) -> str:
        observed_section = self._observed_section()
        lines = [
            "Expected the installation guide to document the DMC-915 installer "
            "selection flags and invalid-skill behavior.",
            f"Checked file: {self.installation_readme_path.relative_to(self.repository_root)}",
            "",
            "Missing or incomplete expectations:",
            *[f"- {finding}" for finding in findings],
            "",
            "Observed 'Install Only the Skills You Need' section:",
            self._indented_section(observed_section) if observed_section else "  (section missing)",
        ]
        return "\n".join(lines)

    def _observed_section(self) -> str | None:
        if not self.installation_readme_path.exists():
            return None
        sections = self._extract_named_sections(
            self.installation_readme_path,
            self.SECTION_HEADING,
        )
        if not sections:
            return None
        return sections[0]

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
            in_code_block = False
            for candidate_line in lines[index + 1 :]:
                stripped_candidate = candidate_line.strip()
                if stripped_candidate.startswith("```"):
                    in_code_block = not in_code_block
                if not in_code_block and stripped_candidate.startswith("#"):
                    next_heading_match = re.match(r"^(?P<hashes>#+)\s+", stripped_candidate)
                    if next_heading_match and len(next_heading_match.group("hashes")) <= level:
                        break
                collected_lines.append(candidate_line)
            sections.append("\n".join(collected_lines).strip())

        return sections

    @staticmethod
    def _normalize(content: str) -> str:
        return re.sub(r"\s+", " ", content).lower()

    @classmethod
    def _documents_invalid_skill_failure(cls, normalized_section: str) -> bool:
        mentions_invalid_skills = any(
            token in normalized_section for token in cls.INVALID_SKILL_TERMS
        )
        mentions_non_zero_exit = any(
            token in normalized_section for token in cls.NON_ZERO_EXIT_TERMS
        )
        mentions_invalid_name_listing = any(
            token in normalized_section for token in cls.INVALID_NAME_LISTING_TERMS
        )
        return (
            mentions_invalid_skills
            and mentions_non_zero_exit
            and mentions_invalid_name_listing
        )

    @classmethod
    def _documents_skip_unknown_warning_behavior(cls, normalized_section: str) -> bool:
        mentions_skip_unknown = "--skip-unknown" in normalized_section
        mentions_invalid_skills = any(
            token in normalized_section for token in cls.INVALID_SKILL_TERMS
        )
        mentions_warnings = any(
            token in normalized_section for token in cls.WARNING_TERMS
        )
        return mentions_skip_unknown and mentions_invalid_skills and mentions_warnings

    @staticmethod
    def _indented_section(section: str) -> str:
        return "\n".join(f"  {line}" for line in section.splitlines())
