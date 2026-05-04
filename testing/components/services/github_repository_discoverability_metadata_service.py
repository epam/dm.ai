from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


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
class DiscoverabilityMetadataObservation:
    metadata_relative_path: str
    playbook_relative_path: str
    short_description: str
    about_text: str
    topics: tuple[str, ...]


class GitHubRepositoryDiscoverabilityMetadataService:
    METADATA_RELATIVE_PATH = "dmtools-core/src/main/resources/github-repository-discoverability.json"
    PLAYBOOK_RELATIVE_PATH = (
        "dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"
    )
    REQUIRED_SHORT_DESCRIPTION_FRAGMENT = "enterprise dark-factory orchestrator"
    EXPECTED_SHORT_DESCRIPTION = (
        "Enterprise dark-factory orchestrator for automating delivery workflows across "
        "trackers, source control, documentation, design systems, AI providers, and CI/CD."
    )
    EXPECTED_ABOUT_TEXT = (
        "DMTools is the orchestration layer for enterprise dark factories: a self-hosted "
        "CLI with MCP tools, jobs, and AI agents for delivery workflows across Jira, "
        "GitHub, Azure DevOps, documentation, design systems, and CI/CD."
    )
    EXPECTED_TOPICS = (
        "dark-factory",
        "delivery-automation",
        "workflow-orchestration",
        "platform-engineering",
        "self-hosted",
        "enterprise-ai",
        "mcp",
        "ai-agents",
        "generative-ai",
        "workflow-automation",
    )
    REQUIRED_PRIMARY_TOPICS = ("dark-factory", "workflow-automation", "enterprise-ai")
    REQUIRED_SECONDARY_TOPICS = ("mcp", "ai-agents", "generative-ai")
    DISALLOWED_VENDOR_TOPICS = ("cursor", "claude", "codex", "jira", "azure-devops", "github")

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.metadata_path = repository_root / self.METADATA_RELATIVE_PATH
        self.playbook_path = repository_root / self.PLAYBOOK_RELATIVE_PATH

    def observation(self) -> DiscoverabilityMetadataObservation:
        payload = json.loads(self.metadata_path.read_text(encoding="utf-8"))
        topics = tuple(str(topic) for topic in payload.get("topics", []))
        return DiscoverabilityMetadataObservation(
            metadata_relative_path=self.METADATA_RELATIVE_PATH,
            playbook_relative_path=self.PLAYBOOK_RELATIVE_PATH,
            short_description=str(payload.get("shortDescription", "")),
            about_text=str(payload.get("aboutText", "")),
            topics=topics,
        )

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []
        observation = self.observation()
        playbook_text = self.playbook_path.read_text(encoding="utf-8")

        if (
            observation.metadata_relative_path not in playbook_text
            or "canonical metadata values are versioned in" not in playbook_text
        ):
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="The discoverability playbook does not point maintainers to the canonical repo-backed metadata source.",
                    expected=(
                        "The playbook should explicitly identify "
                        f"{self.METADATA_RELATIVE_PATH} as the versioned canonical metadata file."
                    ),
                    actual=(
                        f"Playbook {self.PLAYBOOK_RELATIVE_PATH} does not include the expected canonical-source wording "
                        f"for {self.METADATA_RELATIVE_PATH}."
                    ),
                )
            )

        if self.REQUIRED_SHORT_DESCRIPTION_FRAGMENT not in observation.short_description.lower():
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="The canonical metadata short description does not contain the approved dark-factory orchestrator wording.",
                    expected=(
                        "shortDescription should contain "
                        f"{self.REQUIRED_SHORT_DESCRIPTION_FRAGMENT!r}."
                    ),
                    actual=f"Observed shortDescription: {observation.short_description!r}",
                )
            )

        if observation.about_text != self.EXPECTED_ABOUT_TEXT:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="The canonical About text does not match the approved orchestration narrative.",
                    expected=self.EXPECTED_ABOUT_TEXT,
                    actual=observation.about_text or "<missing>",
                )
            )

        missing_topics = [
            topic
            for topic in (*self.REQUIRED_PRIMARY_TOPICS, *self.REQUIRED_SECONDARY_TOPICS)
            if topic not in observation.topics
        ]
        if missing_topics:
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="The canonical topic set is missing one or more required orchestration or AI-discovery terms.",
                    expected=(
                        "Topics should include primary terms "
                        f"{', '.join(self.REQUIRED_PRIMARY_TOPICS)} and secondary terms "
                        f"{', '.join(self.REQUIRED_SECONDARY_TOPICS)}."
                    ),
                    actual=(
                        f"Missing topics: {', '.join(missing_topics)}. "
                        f"Observed topics: {', '.join(observation.topics) or '<none>'}"
                    ),
                )
            )

        unexpected_vendor_topics = [
            topic for topic in self.DISALLOWED_VENDOR_TOPICS if topic in observation.topics
        ]
        if unexpected_vendor_topics:
            failures.append(
                ValidationFailure(
                    step=5,
                    summary="The canonical topic set includes vendor-specific keywords instead of staying orchestration-first.",
                    expected=(
                        "Canonical topics should omit vendor keywords such as "
                        + ", ".join(self.DISALLOWED_VENDOR_TOPICS)
                        + "."
                    ),
                    actual=(
                        f"Unexpected vendor topics in canonical topics: {', '.join(unexpected_vendor_topics)}. "
                        f"Observed topics: {', '.join(observation.topics)}"
                    ),
                )
            )

        playbook_alignment_failures = []
        if observation.short_description not in playbook_text:
            playbook_alignment_failures.append("short description")
        if observation.about_text not in playbook_text:
            playbook_alignment_failures.append("About text")
        if self._format_markdown_list(observation.topics) not in playbook_text:
            playbook_alignment_failures.append("topic list")
        if playbook_alignment_failures:
            failures.append(
                ValidationFailure(
                    step=6,
                    summary="The user-visible discoverability playbook is not aligned with the canonical metadata file.",
                    expected=(
                        f"{self.PLAYBOOK_RELATIVE_PATH} should show the same short description, About text, "
                        "and canonical topic list as the metadata JSON."
                    ),
                    actual="Mismatched playbook fields: " + ", ".join(playbook_alignment_failures),
                )
            )

        return failures

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        lines = [
            "DMC-986 expects the GitHub discoverability metadata source to be repo-backed, "
            "linked from the maintainer playbook, and populated with the approved orchestration "
            "wording and canonical topic strategy."
        ]
        for failure in failures:
            lines.append(f"- Step {failure.step}: {failure.summary}")
            lines.append(f"  Expected: {failure.expected}")
            lines.append(f"  Actual: {failure.actual}")
        return "\n".join(lines)

    @staticmethod
    def _format_markdown_list(values: tuple[str, ...]) -> str:
        return ", ".join(f"`{value}`" for value in values)
