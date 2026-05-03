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
class NavigationLinkObservation:
    section_heading: str
    text: str
    target: str
    line_number: int
    line_text: str

    def display(self) -> str:
        return (
            f"{self.section_heading} line {self.line_number}: "
            f"[{self.text}]({self.target}) in {self.line_text.strip()}"
        )


@dataclass(frozen=True)
class NavigationSection:
    heading: str
    start_line: int
    body: str


class ReadmeDocumentationNavigationService:
    PRIMARY_USAGE_HEADING = "## Primary usage paths"
    DOCUMENTATION_MAP_HEADING = "## Documentation map"
    QUICK_START_HEADING = "## Quick start"

    JOBS_REFERENCE_TARGET = "dmtools-ai-docs/references/jobs/README.md"
    INSTALLATION_TARGET = "dmtools-ai-docs/references/installation/README.md"
    CONFIGURATION_TARGET = "dmtools-ai-docs/references/configuration/README.md"
    INTEGRATIONS_TARGET = "dmtools-ai-docs/references/configuration/integrations/"
    MCP_TOOLS_TARGET = "dmtools-ai-docs/references/mcp-tools/README.md"
    AI_TEAMMATE_TARGET = "dmtools-ai-docs/references/workflows/github-actions-teammate.md"
    SKILL_GUIDE_TARGET = "dmtools-ai-docs/SKILL.md"

    ALLOWED_TARGET_PREFIXES = ("dmtools-ai-docs/", ".github/workflows/")

    REQUIRED_TARGETS = (
        JOBS_REFERENCE_TARGET,
        INSTALLATION_TARGET,
        CONFIGURATION_TARGET,
        INTEGRATIONS_TARGET,
        MCP_TOOLS_TARGET,
        AI_TEAMMATE_TARGET,
        SKILL_GUIDE_TARGET,
    )

    REQUIRED_LINE_FRAGMENTS = {
        JOBS_REFERENCE_TARGET: (
            "Jobs reference",
            "Jobs + agents",
            "Jobs and workflow orchestration",
        ),
        INSTALLATION_TARGET: ("Installation and upgrade",),
        CONFIGURATION_TARGET: ("Configuration overview",),
        INTEGRATIONS_TARGET: ("Integration setup guides",),
        MCP_TOOLS_TARGET: ("MCP tools reference", "CLI + MCP tools"),
        AI_TEAMMATE_TARGET: ("GitHub workflow guidance", "GitHub workflow automation"),
        SKILL_GUIDE_TARGET: ("AI assistant skills", "AI skill packages"),
    }

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self.readme_text = self.readme_path.read_text(encoding="utf-8")
        self.lines = self.readme_text.splitlines()
        self.cross_link_service = DocumentationCrossLinkService(repository_root)

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        primary_usage = self._load_section(self.PRIMARY_USAGE_HEADING)
        documentation_map = self._load_section(self.DOCUMENTATION_MAP_HEADING)
        quick_start_line = self._heading_line(self.QUICK_START_HEADING)

        missing_sections = [
            heading
            for heading, section in (
                (self.PRIMARY_USAGE_HEADING, primary_usage),
                (self.DOCUMENTATION_MAP_HEADING, documentation_map),
            )
            if section is None
        ]
        if missing_sections:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="README is missing the maintained documentation navigation section(s).",
                    expected=(
                        "README should expose both 'Primary usage paths' and 'Documentation map' "
                        "before Quick start."
                    ),
                    actual="Missing headings: " + ", ".join(missing_sections),
                )
            )

        out_of_order_sections = [
            section.heading
            for section in (primary_usage, documentation_map)
            if section is not None
            and quick_start_line is not None
            and section.start_line > quick_start_line
        ]
        if out_of_order_sections:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="README navigation sections appear after Quick start instead of guiding discovery first.",
                    expected="Navigation sections should appear before '## Quick start'.",
                    actual=(
                        f"'## Quick start' starts on line {quick_start_line}, but these sections "
                        f"start later: {', '.join(out_of_order_sections)}"
                    ),
                )
            )

        observations = self.navigation_links()
        observations_by_target = self._observations_by_target(observations)

        missing_targets = [
            target for target in self.REQUIRED_TARGETS if target not in observations_by_target
        ]
        if missing_targets:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="README navigation is missing one or more required maintained documentation links.",
                    expected=(
                        "Navigation should include Jobs/agents workflows, Installation, "
                        "Configuration, Integrations, MCP tools, AI teammate workflow, "
                        "and AI skill package links."
                    ),
                    actual="Missing targets: " + ", ".join(missing_targets),
                )
            )

        mislabeled_targets = []
        for target, required_fragments in self.REQUIRED_LINE_FRAGMENTS.items():
            matching_observations = observations_by_target.get(target, [])
            if not matching_observations:
                continue
            if not any(
                any(fragment in observation.line_text for fragment in required_fragments)
                for observation in matching_observations
            ):
                observed_lines = "; ".join(
                    observation.line_text.strip() for observation in matching_observations
                )
                mislabeled_targets.append(f"{target}: {observed_lines}")
        if mislabeled_targets:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="README navigation links are present but not exposed with the expected user-facing labels.",
                    expected=(
                        "Each maintained link should appear on a navigation row whose visible text "
                        "matches the requested topic."
                    ),
                    actual="; ".join(mislabeled_targets),
                )
            )

        invalid_targets = []
        for observation in observations:
            if not observation.target.startswith(self.ALLOWED_TARGET_PREFIXES):
                invalid_targets.append(
                    f"{observation.display()} resolves outside maintained docs roots."
                )
                continue

            resolved_path = (self.repository_root / observation.target).resolve()
            if not resolved_path.exists():
                invalid_targets.append(
                    f"{observation.display()} resolves to missing path "
                    f"{resolved_path.relative_to(self.repository_root)}."
                )
        if invalid_targets:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="README navigation contains invalid or non-maintained link targets.",
                    expected=(
                        "Every navigation link should stay inside dmtools-ai-docs/ or "
                        ".github/workflows/ and resolve to an existing repository path."
                    ),
                    actual=" | ".join(invalid_targets),
                )
            )

        workflow_observations = [
            observation
            for observation in observations
            if "workflow" in observation.line_text.lower()
            or "teammate" in observation.target.lower()
        ]
        if self.AI_TEAMMATE_TARGET not in observations_by_target:
            actual_targets = (
                ", ".join(observation.target for observation in workflow_observations) or "none"
            )
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="README does not point the AI teammate workflow navigation entry to the maintained teammate guide.",
                    expected=self.AI_TEAMMATE_TARGET,
                    actual=f"Workflow-related targets found: {actual_targets}",
                )
            )

        return failures

    def navigation_links(self) -> list[NavigationLinkObservation]:
        observations: list[NavigationLinkObservation] = []
        for heading in (self.PRIMARY_USAGE_HEADING, self.DOCUMENTATION_MAP_HEADING):
            section = self._load_section(heading)
            if section is None:
                continue

            for link in self.cross_link_service.parse_markdown_links(
                section.body,
                start_line=section.start_line,
            ):
                observations.append(
                    NavigationLinkObservation(
                        section_heading=heading,
                        text=link.text,
                        target=link.target,
                        line_number=link.line_number,
                        line_text=self.lines[link.line_number - 1],
                    )
                )
        return observations

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def observation_for_target(self, target: str) -> NavigationLinkObservation:
        observations = self._observations_by_target(self.navigation_links()).get(target, [])
        if not observations:
            raise AssertionError(f"Navigation link target {target!r} was not found in README.md.")
        return observations[0]

    def _load_section(self, heading: str) -> NavigationSection | None:
        try:
            start_line, body = self.cross_link_service.section_content(self.readme_path, heading)
        except ValueError:
            return None
        return NavigationSection(heading=heading, start_line=start_line, body=body)

    def _heading_line(self, heading: str) -> int | None:
        for line_number, line in enumerate(self.lines, start=1):
            if line.strip() == heading:
                return line_number
        return None

    @staticmethod
    def _observations_by_target(
        observations: list[NavigationLinkObservation],
    ) -> dict[str, list[NavigationLinkObservation]]:
        by_target: dict[str, list[NavigationLinkObservation]] = {}
        for observation in observations:
            by_target.setdefault(observation.target, []).append(observation)
        return by_target
