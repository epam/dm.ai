from __future__ import annotations

import re
from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)
from testing.components.services.per_skill_catalog_service import (
    PerSkillCatalogService,
    SkillCatalogExpectation,
)


MARKDOWN_HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*)$")


class PerSkillPageAuditService:
    MANDATORY_SECTIONS = (
        "Overview",
        "Package / Artifact",
        "Installer / CLI example",
        "Endpoints / Config keys",
        "Minimal usage example",
        "Compatibility / Supported versions",
        "Security & Permissions",
        "Linkbacks",
        "Maintainer / Contact",
    )

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.docs_root = repository_root / "dmtools-ai-docs"
        self.per_skill_root = self.docs_root / "per-skill-packages"
        self.per_skill_index_path = self.per_skill_root / "README.md"
        self.installation_guide_path = (
            self.docs_root / "references" / "installation" / "README.md"
        )
        self.cross_link_service = DocumentationCrossLinkService(repository_root)
        self.skill_expectations = {
            expectation.skill_name: expectation
            for expectation in PerSkillCatalogService.EXPECTED_SKILLS
        }

    def audit(self) -> list[str]:
        findings: list[str] = []

        if not self.per_skill_root.exists():
            findings.append(
                "Expected per-skill catalogue directory "
                f"{self.relative_path(self.per_skill_root)} to exist, but it is missing."
            )
            return findings

        if not self.per_skill_index_path.exists():
            findings.append(
                "Expected per-skill index "
                f"{self.relative_path(self.per_skill_index_path)} to exist, but it is missing."
            )

        child_pages = self.child_pages()
        if not child_pages:
            findings.append(
                "Expected at least one skill child page under "
                f"{self.relative_path(self.per_skill_root)}, but none were found."
            )
            return findings

        for page_path in child_pages:
            findings.extend(self.audit_page(page_path))

        return findings

    def child_pages(self) -> list[Path]:
        return sorted(
            path
            for path in self.per_skill_root.glob("*.md")
            if path.name.lower() != "readme.md"
        )

    def audit_page(self, page_path: Path) -> list[str]:
        findings: list[str] = []
        content = page_path.read_text(encoding="utf-8")
        normalized_headings = {self.normalize_heading(heading) for heading in self.headings(page_path)}

        missing_sections = [
            section
            for section in self.MANDATORY_SECTIONS
            if self.normalize_heading(section) not in normalized_headings
        ]
        if missing_sections:
            findings.append(
                f"{self.relative_path(page_path)} is missing mandatory sections: "
                + ", ".join(missing_sections)
                + "."
            )

        expected_skill = self.expected_skill(page_path)
        if expected_skill is None:
            findings.append(
                f"{self.relative_path(page_path)} is not listed in the canonical per-skill "
                "catalogue mapping."
            )
        else:
            if expected_skill.java_package not in content:
                findings.append(
                    f"{self.relative_path(page_path)} does not include expected Java package "
                    f"identifier {expected_skill.java_package!r}."
                )

            if expected_skill.slash_command not in content:
                findings.append(
                    f"{self.relative_path(page_path)} does not include expected slash-command "
                    f"identifier {expected_skill.slash_command!r}."
                )

        links = self.cross_link_service.parse_markdown_links(content)
        if not self.has_link_to(page_path, links, self.installation_guide_path):
            findings.append(
                f"{self.relative_path(page_path)} does not link back to the central installation guide "
                f"{self.relative_path(self.installation_guide_path)}."
            )
        if not self.has_link_to(page_path, links, self.per_skill_index_path):
            findings.append(
                f"{self.relative_path(page_path)} does not link back to the per-skill index "
                f"{self.relative_path(self.per_skill_index_path)}."
            )

        return findings

    def headings(self, page_path: Path) -> list[str]:
        headings: list[str] = []
        for line in page_path.read_text(encoding="utf-8").splitlines():
            match = MARKDOWN_HEADING_PATTERN.match(line)
            if match:
                headings.append(match.group(2).strip())
        return headings

    @staticmethod
    def normalize_heading(heading: str) -> str:
        normalized = heading.strip().lower()
        normalized = normalized.replace("&", "and")
        normalized = re.sub(r"[^a-z0-9]+", " ", normalized)
        return re.sub(r"\s+", " ", normalized).strip()

    def expected_skill(self, page_path: Path) -> SkillCatalogExpectation | None:
        return self.skill_expectations.get(self.canonical_skill_name(page_path))

    def has_link_to(self, source_path: Path, links: list, expected_target: Path) -> bool:
        expected_resolved = expected_target.resolve()
        return any(
            self.cross_link_service.resolve_target(source_path, link.target)[0]
            == expected_resolved
            for link in links
        )

    @staticmethod
    def canonical_skill_name(page_path: Path) -> str:
        if page_path.stem.startswith("dmtools-"):
            return page_path.stem
        return f"dmtools-{page_path.stem}"

    def relative_path(self, path: Path) -> str:
        return path.relative_to(self.repository_root).as_posix()
