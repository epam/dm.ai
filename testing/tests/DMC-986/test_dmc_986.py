import json
import textwrap
from pathlib import Path

from testing.components.services.github_repository_discoverability_metadata_service import (
    GitHubRepositoryDiscoverabilityMetadataService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_986_canonical_discoverability_metadata_matches_playbook_and_ticket_expectations() -> None:
    service = GitHubRepositoryDiscoverabilityMetadataService(REPOSITORY_ROOT)

    failures = service.validate()
    observation = service.observation()

    assert not failures, service.format_failures(failures)
    assert observation.metadata_relative_path == service.METADATA_RELATIVE_PATH
    assert observation.playbook_relative_path == service.PLAYBOOK_RELATIVE_PATH
    assert observation.short_description == service.EXPECTED_SHORT_DESCRIPTION
    assert observation.about_text == service.EXPECTED_ABOUT_TEXT
    assert observation.topics == service.EXPECTED_TOPICS


def test_dmc_986_accepts_repo_backed_metadata_when_playbook_and_topics_are_aligned(
    tmp_path: Path,
) -> None:
    _write_repository_fixture(
        tmp_path,
        short_description=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_SHORT_DESCRIPTION,
        about_text=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_ABOUT_TEXT,
        topics=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_TOPICS,
        playbook_metadata_path=GitHubRepositoryDiscoverabilityMetadataService.METADATA_RELATIVE_PATH,
        playbook_short_description=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_SHORT_DESCRIPTION,
        playbook_about_text=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_ABOUT_TEXT,
        playbook_topics=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_TOPICS,
    )

    service = GitHubRepositoryDiscoverabilityMetadataService(tmp_path)

    assert service.validate() == []


def test_dmc_986_reports_playbook_drift_and_topic_regressions(tmp_path: Path) -> None:
    _write_repository_fixture(
        tmp_path,
        short_description=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_SHORT_DESCRIPTION,
        about_text="DMTools is a toolkit for point integrations and vendor-specific automation.",
        topics=("dark-factory", "enterprise-ai", "cursor", "github"),
        playbook_metadata_path="docs/discovery-metadata.json",
        playbook_short_description=GitHubRepositoryDiscoverabilityMetadataService.EXPECTED_SHORT_DESCRIPTION,
        playbook_about_text=(
            "DMTools is a CLI-first orchestration layer for enterprise dark factories."
        ),
        playbook_topics=("dark-factory", "workflow-automation", "enterprise-ai"),
    )

    service = GitHubRepositoryDiscoverabilityMetadataService(tmp_path)

    failures = service.validate()

    assert [failure.step for failure in failures] == [1, 3, 4, 5, 6]
    assert failures[0].actual.endswith(
        "dmtools-core/src/main/resources/github-repository-discoverability.json."
    )
    assert failures[1].actual == (
        "DMTools is a toolkit for point integrations and vendor-specific automation."
    )
    assert "workflow-automation" in failures[2].actual
    assert "mcp" in failures[2].actual
    assert "cursor" in failures[3].actual
    assert "github" in failures[3].actual
    assert failures[4].actual == (
        "Mismatched playbook fields: About text, topic list"
    )


def _write_repository_fixture(
    repository_root: Path,
    *,
    short_description: str,
    about_text: str,
    topics: tuple[str, ...],
    playbook_metadata_path: str,
    playbook_short_description: str,
    playbook_about_text: str,
    playbook_topics: tuple[str, ...],
) -> None:
    metadata_path = (
        repository_root
        / GitHubRepositoryDiscoverabilityMetadataService.METADATA_RELATIVE_PATH
    )
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(
        json.dumps(
            {
                "shortDescription": short_description,
                "aboutText": about_text,
                "topics": list(topics),
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    playbook_path = (
        repository_root
        / GitHubRepositoryDiscoverabilityMetadataService.PLAYBOOK_RELATIVE_PATH
    )
    playbook_path.parent.mkdir(parents=True, exist_ok=True)
    playbook_path.write_text(
        textwrap.dedent(
            f"""
            # GitHub Repository Discoverability Playbook

            Use this page as the maintainer source of truth for repository discoverability updates. The canonical metadata values are versioned in `{playbook_metadata_path}`, and `GitHubRepositoryDiscoverabilityTest` fails when repo-backed GitHub surfaces drift from those values.

            ## Canonical repository metadata

            | Surface | Canonical value |
            |---|---|
            | Short description | `{playbook_short_description}` |
            | About text | `{playbook_about_text}` |
            | GitHub topics | {_format_markdown_topics(playbook_topics)} |
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )


def _format_markdown_topics(topics: tuple[str, ...]) -> str:
    return ", ".join(f"`{topic}`" for topic in topics)
