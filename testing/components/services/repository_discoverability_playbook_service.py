from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


@dataclass(frozen=True)
class ValidationFailure:
    step: int
    summary: str
    expected: str
    actual: str

    def format(self) -> str:
        return (
            f"Step {self.step}: {self.summary}\n"
            f"Expected: {self.expected}\n"
            f"Actual: {self.actual}"
        )


@dataclass(frozen=True)
class PlaybookLinkObservation:
    source_path: Path
    section_heading: str
    text: str
    target: str
    line_number: int
    line_text: str

    def display(self) -> str:
        return (
            f"{self.source_path.name} line {self.line_number}: "
            f"[{self.text}]({self.target}) in {self.line_text.strip()}"
        )


@dataclass(frozen=True)
class PlaybookSectionObservation:
    heading: str
    start_line: int
    body: str


class RepositoryDiscoverabilityPlaybookService:
    PLAYBOOK_RELATIVE_PATH = (
        "dmtools-ai-docs/references/workflows/"
        "github-repository-discoverability-playbook.md"
    )
    README_SECTION_HEADING = "## Documentation map"
    CONTRIBUTING_SECTION_HEADING = "## Maintainer discoverability checklist"
    MANUAL_SETTINGS_HEADING = "## Manual GitHub settings"
    REPO_BACKED_SECTION_HEADING = "## Repo-backed files that must stay aligned"
    MAINTAINER_CHECKLIST_HEADING = "## Maintainer checklist"

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self.contributing_path = repository_root / "CONTRIBUTING.md"
        self.playbook_path = repository_root / self.PLAYBOOK_RELATIVE_PATH
        self.cross_link_service = DocumentationCrossLinkService(repository_root)

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        readme_observation = self.readme_playbook_link()
        if readme_observation is None:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="README documentation map does not expose the maintainer playbook.",
                    expected=(
                        "README.md should link to the canonical repository discoverability "
                        "playbook from the documentation map."
                    ),
                    actual=(
                        f"No Markdown link to {self.PLAYBOOK_RELATIVE_PATH} was found under "
                        f"{self.README_SECTION_HEADING}."
                    ),
                )
            )

        contributing_observation = self.contributing_playbook_link()
        if contributing_observation is None:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="CONTRIBUTING does not point maintainers to the canonical playbook.",
                    expected=(
                        "CONTRIBUTING.md should direct maintainers to the discoverability "
                        "playbook from the maintainer checklist section."
                    ),
                    actual=(
                        f"No Markdown link to {self.PLAYBOOK_RELATIVE_PATH} was found under "
                        f"{self.CONTRIBUTING_SECTION_HEADING}."
                    ),
                )
            )

        manual_section = self.playbook_section(self.MANUAL_SETTINGS_HEADING)
        repo_backed_section = self.playbook_section(self.REPO_BACKED_SECTION_HEADING)
        checklist_section = self.playbook_section(self.MAINTAINER_CHECKLIST_HEADING)

        if manual_section is None:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="The playbook is missing the manual GitHub settings section.",
                    expected=(
                        "The playbook should include a dedicated section for GitHub UI-only "
                        "settings such as About metadata, homepage, and social preview."
                    ),
                    actual=f"Missing heading: {self.MANUAL_SETTINGS_HEADING}",
                )
            )
        else:
            missing_manual_terms = self._missing_terms(
                manual_section.body,
                ("about", "topics", "homepage", "social preview"),
            )
            if missing_manual_terms:
                failures.append(
                    ValidationFailure(
                        step=3,
                        summary="The playbook manual-settings section does not cover the required GitHub UI surfaces.",
                        expected=(
                            "Manual GitHub settings guidance should mention About metadata, "
                            "homepage, topics, and social preview so maintainers know which "
                            "changes must be applied in the GitHub UI."
                        ),
                        actual=(
                            f"{self.MANUAL_SETTINGS_HEADING} is missing: "
                            + ", ".join(missing_manual_terms)
                        ),
                    )
                )

        if repo_backed_section is None:
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="The playbook is missing the repo-backed updates section.",
                    expected=(
                        "The playbook should include a dedicated section for repository-managed "
                        "files such as README, templates, and release copy."
                    ),
                    actual=f"Missing heading: {self.REPO_BACKED_SECTION_HEADING}",
                )
            )
        else:
            missing_repo_backed_terms = self._missing_terms(
                repo_backed_section.body,
                ("README.md", ".github/ISSUE_TEMPLATE/", "release"),
            )
            if missing_repo_backed_terms:
                failures.append(
                    ValidationFailure(
                        step=4,
                        summary="The playbook repo-backed section does not cover the required versioned update surfaces.",
                        expected=(
                            "Repo-backed guidance should explicitly call out README, templates, "
                            "and release-facing copy so maintainers keep versioned assets aligned."
                        ),
                        actual=(
                            f"{self.REPO_BACKED_SECTION_HEADING} is missing: "
                            + ", ".join(missing_repo_backed_terms)
                        ),
                    )
                )

        if checklist_section is None:
            failures.append(
                ValidationFailure(
                    step=5,
                    summary="The playbook is missing the maintainer checklist section.",
                    expected=(
                        "The playbook should include a repeatable checklist for repository "
                        "metadata refreshes."
                    ),
                    actual=f"Missing heading: {self.MAINTAINER_CHECKLIST_HEADING}",
                )
            )
        else:
            checklist_items = self._numbered_items(checklist_section.body)
            if len(checklist_items) < 5:
                failures.append(
                    ValidationFailure(
                        step=5,
                        summary="The maintainer checklist is not repeatable enough for metadata refreshes.",
                        expected=(
                            "Maintainers should have a multi-step checklist that covers the "
                            "repo-backed and manual GitHub refresh workflow."
                        ),
                        actual=(
                            f"{self.MAINTAINER_CHECKLIST_HEADING} contains only "
                            f"{len(checklist_items)} numbered item(s): "
                            + "; ".join(checklist_items or ("none",))
                        ),
                    )
                )

        return failures

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def readme_playbook_link(self) -> PlaybookLinkObservation | None:
        return self._playbook_link_observation(self.readme_path, self.README_SECTION_HEADING)

    def contributing_playbook_link(self) -> PlaybookLinkObservation | None:
        return self._playbook_link_observation(
            self.contributing_path,
            self.CONTRIBUTING_SECTION_HEADING,
        )

    def playbook_section(self, heading: str) -> PlaybookSectionObservation | None:
        try:
            start_line, body = self.cross_link_service.section_content(self.playbook_path, heading)
        except ValueError:
            return None
        return PlaybookSectionObservation(heading=heading, start_line=start_line, body=body)

    def _playbook_link_observation(
        self,
        source_path: Path,
        section_heading: str,
    ) -> PlaybookLinkObservation | None:
        try:
            start_line, body = self.cross_link_service.section_content(source_path, section_heading)
        except ValueError:
            return None

        lines = source_path.read_text(encoding="utf-8").splitlines()
        for link in self.cross_link_service.parse_markdown_links(body, start_line=start_line):
            if link.target == self.PLAYBOOK_RELATIVE_PATH:
                return PlaybookLinkObservation(
                    source_path=source_path,
                    section_heading=section_heading,
                    text=link.text,
                    target=link.target,
                    line_number=link.line_number,
                    line_text=lines[link.line_number - 1],
                )
        return None

    @staticmethod
    def _missing_terms(content: str, required_terms: tuple[str, ...]) -> list[str]:
        normalized = content.lower()
        return [term for term in required_terms if term.lower() not in normalized]

    @staticmethod
    def _numbered_items(content: str) -> list[str]:
        items: list[str] = []
        for line in content.splitlines():
            stripped_line = line.strip()
            if stripped_line[:2].isdigit() and ". " in stripped_line:
                items.append(stripped_line)
            elif stripped_line and stripped_line[0].isdigit() and ". " in stripped_line:
                items.append(stripped_line)
        return items
