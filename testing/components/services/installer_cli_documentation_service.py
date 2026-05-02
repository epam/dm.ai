from __future__ import annotations

import re
from pathlib import Path

from testing.components.factories.installer_script_factory import create_installer_script
from testing.core.interfaces.installer_script import InstallerScript
from testing.core.models.process_execution_result import ProcessExecutionResult


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

    def __init__(
        self,
        repository_root: Path,
        installer_script: InstallerScript | None = None,
    ) -> None:
        self.repository_root = repository_root
        self.installation_readme_path = (
            repository_root / "dmtools-ai-docs/references/installation/README.md"
        )
        self.install_script_path = repository_root / "install.sh"
        self.installer_script = installer_script

    def audit_installation_readme(self) -> list[str]:
        findings: list[str] = []
        findings.extend(self._audit_documentation())
        findings.extend(self._audit_installer_runtime())
        return findings

    def format_findings(self, findings: list[str]) -> str:
        observed_section = self._observed_section()
        lines = [
            "Expected the installation guide to document the DMC-915 installer "
            "selection flags and invalid-skill behavior, and for the installer to "
            "actually support the documented contract.",
            f"Checked file: {self.installation_readme_path.relative_to(self.repository_root)}",
            "",
            "Missing or incomplete expectations:",
            *[f"- {finding}" for finding in findings],
            "",
            "Observed 'Install Only the Skills You Need' section:",
            self._indented_section(observed_section) if observed_section else "  (section missing)",
            "",
            "Observed installer behavior:",
            *self._format_runtime_observations(),
        ]
        return "\n".join(lines)

    def _audit_documentation(self) -> list[str]:
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

    def _audit_installer_runtime(self) -> list[str]:
        installer_script = self._installer_script()
        if installer_script is None:
            return [f"Missing installer script: {self.install_script_path.name}"]

        findings: list[str] = []

        single_skill_result = installer_script.run_main(args=("--skill", "jira"))
        if not self._command_succeeded(
            single_skill_result,
            "Effective skills: jira (source: cli)",
        ):
            findings.append(
                "The installer runtime does not accept `--skill <name>` as a working "
                "skill-selection command."
            )

        multi_skill_result = installer_script.run_main(args=("--skills=jira,github",))
        if not self._command_succeeded(
            multi_skill_result,
            "Effective skills: jira,github (source: cli)",
        ):
            findings.append(
                "The installer runtime does not accept `--skills=<name,name>` as a "
                "working alias."
            )

        all_skills_result = installer_script.run_main(args=("--all-skills",))
        if not self._command_succeeded(
            all_skills_result,
            "Installing all skills (source: cli)",
        ):
            findings.append(
                "The installer runtime does not accept the `--all-skills` flag."
            )

        mixed_selection_result = installer_script.run_main(args=("--skills=jira,unknown",))
        if not self._command_failed(
            mixed_selection_result,
            "Unknown skills: unknown",
        ):
            findings.append(
                "The installer runtime does not fail for mixed valid+invalid skill "
                "selections without `--skip-unknown` while listing invalid names."
            )

        skip_unknown_result = installer_script.run_main(
            args=("--skills=jira,unknown", "--skip-unknown")
        )
        if (
            mixed_selection_result.returncode == 0
            or not self._command_succeeded(
                skip_unknown_result,
                "Warning: Skipping unknown skills: unknown",
                "Effective skills: jira (source: cli)",
            )
        ):
            findings.append(
                "The installer runtime does not prove that `--skip-unknown` changes "
                "a mixed valid+invalid selection from a non-zero failure into a "
                "warning-backed success."
            )

        invalid_skill_result = installer_script.run_main(args=("--skill", "unknown"))
        if not self._command_failed(
            invalid_skill_result,
            "Unknown skills: unknown",
        ):
            findings.append(
                "The installer runtime does not fail with a non-zero exit while "
                "listing invalid skill names for unknown selections."
            )

        return findings

    def _format_runtime_observations(self) -> list[str]:
        installer_script = self._installer_script()
        if installer_script is None:
            return [f"  install.sh missing at {self.install_script_path}"]

        observations = [
            ("bash install.sh --skill jira", installer_script.run_main(args=("--skill", "jira"))),
            (
                "bash install.sh --skills=jira,github",
                installer_script.run_main(args=("--skills=jira,github",)),
            ),
            (
                "bash install.sh --all-skills",
                installer_script.run_main(args=("--all-skills",)),
            ),
            (
                "bash install.sh --skills=jira,unknown",
                installer_script.run_main(args=("--skills=jira,unknown",)),
            ),
            (
                "bash install.sh --skills=jira,unknown --skip-unknown",
                installer_script.run_main(args=("--skills=jira,unknown", "--skip-unknown")),
            ),
            (
                "bash install.sh --skill unknown",
                installer_script.run_main(args=("--skill", "unknown")),
            ),
        ]
        return [
            self._format_runtime_observation(command, result)
            for command, result in observations
        ]

    def _installer_script(self) -> InstallerScript | None:
        if self.installer_script is not None:
            return self.installer_script
        if not self.install_script_path.exists():
            return None
        self.installer_script = create_installer_script(self.repository_root)
        return self.installer_script

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
    def _command_succeeded(
        result: ProcessExecutionResult,
        *expected_fragments: str,
    ) -> bool:
        if result.returncode != 0:
            return False
        return all(fragment in result.combined_output for fragment in expected_fragments)

    @staticmethod
    def _command_failed(
        result: ProcessExecutionResult,
        *expected_fragments: str,
    ) -> bool:
        if result.returncode == 0:
            return False
        return all(fragment in result.combined_output for fragment in expected_fragments)

    @staticmethod
    def _format_runtime_observation(
        command: str,
        result: ProcessExecutionResult,
    ) -> str:
        summary = InstallerCliDocumentationService._single_line_output(result)
        return f"  {command} -> exit {result.returncode}; {summary}"

    @staticmethod
    def _single_line_output(result: ProcessExecutionResult) -> str:
        output = " | ".join(
            line.strip()
            for line in result.combined_output.splitlines()
            if line.strip()
        )
        return output if output else "(no output)"

    @staticmethod
    def _indented_section(section: str) -> str:
        return "\n".join(f"  {line}" for line in section.splitlines())
