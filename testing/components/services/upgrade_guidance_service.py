from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


@dataclass(frozen=True)
class SectionAudit:
    path: Path
    heading: str
    content: str
    missing_requirements: tuple[str, ...]


class UpgradeGuidanceService:
    SECTION_HEADING = "Upgrading from legacy installs"

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.candidate_paths = (
            repository_root / "README.md",
            repository_root / "dmtools-ai-docs/references/installation/README.md",
            repository_root / "dmtools-ai-docs/references/installation/troubleshooting.md",
        )

    def audit_upgrade_guidance_sections(self) -> list[SectionAudit]:
        audits: list[SectionAudit] = []
        for path in self.candidate_paths:
            for section in self._extract_named_sections(path, self.SECTION_HEADING):
                normalized_section = self._normalize(section)
                missing_requirements = tuple(
                    label
                    for label, checker in self._requirement_checks().items()
                    if not checker(section, normalized_section)
                )
                audits.append(
                    SectionAudit(
                        path=path,
                        heading=self.SECTION_HEADING,
                        content=section.strip(),
                        missing_requirements=missing_requirements,
                    )
                )
        return audits

    @staticmethod
    def format_missing_requirements(audits: list[SectionAudit]) -> str:
        lines = [
            "Each visible 'Upgrading from legacy installs' section must include backup, replace, preserve/merge config, path update, verification, and rollback guidance."
        ]
        for audit in audits:
            if not audit.missing_requirements:
                continue
            lines.append(f"- {audit.path.as_posix()}: missing {', '.join(audit.missing_requirements)}")
            lines.append("  Section content:")
            for section_line in audit.content.splitlines():
                lines.append(f"    {section_line}")
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

    @classmethod
    def _requirement_checks(cls) -> dict[str, Callable[[str, str], bool]]:
        return {
            "backup of ~/.dmtools": cls._has_backup_step,
            "replace with EPAM release": cls._has_replace_step,
            "preserve or merge config": cls._has_preserve_step,
            "update system paths": cls._has_path_update_step,
            "verification commands": cls._has_verification_step,
            "rollback using backup copy": cls._has_rollback_step,
        }

    @staticmethod
    def _has_backup_step(section: str, normalized_section: str) -> bool:
        return "~/.dmtools" in section and any(token in normalized_section for token in ("backup", "back up", "copy"))

    @staticmethod
    def _has_replace_step(section: str, normalized_section: str) -> bool:
        mentions_epam_release = any(
            token in normalized_section
            for token in (
                "github.com/epam/dm.ai/releases",
                "epam release",
                "release asset",
                "latest/download/install",
            )
        )
        return mentions_epam_release and any(
            token in normalized_section for token in ("replace", "re-run", "rerun", "switch")
        )

    @staticmethod
    def _has_preserve_step(section: str, normalized_section: str) -> bool:
        return any(
            token in normalized_section for token in ("preserve", "merge", "keep", "retain")
        ) and any(
            token in normalized_section
            for token in ("config", "configuration", "dmtools.env", "dmtools-local.env")
        )

    @staticmethod
    def _has_path_update_step(section: str, normalized_section: str) -> bool:
        return any(
            token in normalized_section
            for token in (
                "~/.dmtools/bin",
                "path",
                "which dmtools",
                "get-command dmtools",
                "alias",
                "wrapper script",
                "shell profile",
            )
        )

    @staticmethod
    def _has_verification_step(section: str, normalized_section: str) -> bool:
        return "dmtools --version" in section and "dmtools list" in section

    @staticmethod
    def _has_rollback_step(section: str, normalized_section: str) -> bool:
        return any(token in normalized_section for token in ("rollback", "roll back", "restore")) and any(
            token in normalized_section for token in ("backup", "backup copy", "backup folder")
        )
