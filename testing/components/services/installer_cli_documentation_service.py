from __future__ import annotations

import re
from pathlib import Path


class InstallerCliDocumentationService:
    SECTION_HEADING = "Install Only the Skills You Need"
    SINGLE_SKILL_PATTERN = re.compile(r"--skills\s+[a-z0-9-]+\b")
    MULTI_SKILL_PATTERN = re.compile(r"--skills\s+[a-z0-9-]+(?:,[a-z0-9-]+)+\b")
    UNSUPPORTED_FLAG_PATTERNS = {
        "`--skill <name>`": re.compile(r"--skill(?=[\s`=]|$)"),
        "`--skills=<name,name>`": re.compile(r"--skills="),
        "`--all-skills`": re.compile(r"--all-skills"),
        "`--skip-unknown`": re.compile(r"--skip-unknown"),
    }

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
        if not self._has_supported_skill_examples(section):
            findings.append(
                "The installer usage section does not show the supported "
                "`--skills <name[,name]>` syntax with copy-pasteable examples for "
                "focused skill installs."
            )
        if "DMTOOLS_SKILLS" not in section:
            findings.append(
                "The installer usage section does not mention the supported "
                "`DMTOOLS_SKILLS` environment variable for non-interactive skill "
                "selection."
            )
        findings.extend(self._unsupported_flag_findings(section))
        if self._has_unsupported_invalid_skill_behavior(normalized_section):
            findings.append(
                "The installer usage section describes unsupported invalid-skill "
                "handling. The current installer exits with `Unsupported skill package: "
                "<name>` and does not support warning downgrades or `--skip-unknown`."
            )
        return findings

    def format_findings(self, findings: list[str]) -> str:
        observed_section = self._observed_section()
        lines = [
            "Expected the installation guide to match the supported installer skill "
            "selection syntax and avoid unsupported flags or error-handling claims.",
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

    @staticmethod
    def _has_supported_skill_examples(section: str) -> bool:
        return bool(
            InstallerCliDocumentationService.SINGLE_SKILL_PATTERN.search(section)
            and InstallerCliDocumentationService.MULTI_SKILL_PATTERN.search(section)
        )

    @classmethod
    def _unsupported_flag_findings(cls, section: str) -> list[str]:
        findings: list[str] = []
        for label, pattern in cls.UNSUPPORTED_FLAG_PATTERNS.items():
            if pattern.search(section):
                findings.append(
                    "The installer usage section documents unsupported syntax "
                    f"{label}; the current installer supports `--skills <name[,name]>` "
                    "for focused installs instead."
                )
        return findings

    @staticmethod
    def _has_unsupported_invalid_skill_behavior(normalized_section: str) -> bool:
        mentions_warning_downgrade = any(
            token in normalized_section
            for token in ("warning", "warnings", "warn instead", "downgrade")
        )
        mentions_unknown_skills = any(
            token in normalized_section
            for token in ("unknown skill", "unknown skills", "invalid skill", "invalid skills")
        )
        return mentions_warning_downgrade and mentions_unknown_skills

    @staticmethod
    def _indented_section(section: str) -> str:
        return "\n".join(f"  {line}" for line in section.splitlines())
