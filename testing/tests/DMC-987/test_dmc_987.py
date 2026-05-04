from __future__ import annotations

from pathlib import Path

from testing.components.services.repository_discoverability_playbook_service import (
    RepositoryDiscoverabilityPlaybookService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_987_maintainer_playbook_entry_points_and_sections_are_kept_in_sync() -> None:
    service = RepositoryDiscoverabilityPlaybookService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)

    readme_observation = service.readme_playbook_link()
    contributing_observation = service.contributing_playbook_link()
    manual_section = service.playbook_section(service.MANUAL_SETTINGS_HEADING)
    repo_backed_section = service.playbook_section(service.REPO_BACKED_SECTION_HEADING)
    checklist_section = service.playbook_section(service.MAINTAINER_CHECKLIST_HEADING)

    assert readme_observation is not None
    assert readme_observation.section_heading == service.README_SECTION_HEADING
    assert readme_observation.text == "references/workflows/github-repository-discoverability-playbook.md"
    assert "GitHub repository discoverability" in readme_observation.line_text

    assert contributing_observation is not None
    assert contributing_observation.section_heading == service.CONTRIBUTING_SECTION_HEADING
    assert "canonical playbook" in contributing_observation.line_text

    assert manual_section is not None
    assert "About description and topics" in manual_section.body
    assert "Social preview" in manual_section.body

    assert repo_backed_section is not None
    assert "README.md" in repo_backed_section.body
    assert ".github/ISSUE_TEMPLATE/*" in repo_backed_section.body
    assert ".github/workflows/release.yml" in repo_backed_section.body

    assert checklist_section is not None
    assert "1. Update `github-repository-discoverability.json`" in checklist_section.body
    assert "5. Apply any required About, topics, homepage, and social-preview changes" in (
        checklist_section.body
    )


def test_dmc_987_service_accepts_a_playbook_with_split_manual_and_repo_backed_guidance(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    workflow_dir = repository_root / "dmtools-ai-docs/references/workflows"
    workflow_dir.mkdir(parents=True)

    (repository_root / "README.md").write_text(
        "# DMTools\n\n"
        "## Documentation map\n\n"
        "| Topic | Link |\n"
        "|---|---|\n"
        "| GitHub repository discoverability | "
        "[references/workflows/github-repository-discoverability-playbook.md]"
        "(dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md) |\n"
        "\n"
        "## Quick start\n",
        encoding="utf-8",
    )
    (repository_root / "CONTRIBUTING.md").write_text(
        "# Contributing to DMTools\n\n"
        "## Maintainer discoverability checklist\n\n"
        "Use the canonical playbook in "
        "[github-repository-discoverability-playbook.md]"
        "(dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md).\n",
        encoding="utf-8",
    )
    (workflow_dir / "github-repository-discoverability-playbook.md").write_text(
        "# GitHub Repository Discoverability Playbook\n\n"
        "## Manual GitHub settings\n\n"
        "1. Update About description and topics in the GitHub UI.\n"
        "2. Refresh the homepage target if needed.\n"
        "3. Replace the social preview hero card when positioning changes.\n\n"
        "## Repo-backed files that must stay aligned\n\n"
        "- `README.md`\n"
        "- `.github/ISSUE_TEMPLATE/bug.yml`\n"
        "- `.github/workflows/release.yml`\n\n"
        "## Maintainer checklist\n\n"
        "1. Review the canonical metadata.\n"
        "2. Keep README and contributor links pointing to this playbook.\n"
        "3. Update issue templates.\n"
        "4. Refresh release copy.\n"
        "5. Apply About, topics, homepage, and social preview updates in the GitHub UI.\n",
        encoding="utf-8",
    )

    service = RepositoryDiscoverabilityPlaybookService(repository_root)

    assert service.validate() == []


def test_dmc_987_service_reports_missing_manual_or_repo_backed_sections(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    workflow_dir = repository_root / "dmtools-ai-docs/references/workflows"
    workflow_dir.mkdir(parents=True)

    (repository_root / "README.md").write_text(
        "# DMTools\n\n"
        "## Documentation map\n\n"
        "| Topic | Link |\n"
        "|---|---|\n"
        "| GitHub repository discoverability | "
        "[references/workflows/github-repository-discoverability-playbook.md]"
        "(dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md) |\n"
        "\n"
        "## Quick start\n",
        encoding="utf-8",
    )
    (repository_root / "CONTRIBUTING.md").write_text(
        "# Contributing to DMTools\n\n"
        "## Maintainer discoverability checklist\n\n"
        "Use the canonical playbook in "
        "[github-repository-discoverability-playbook.md]"
        "(dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md).\n",
        encoding="utf-8",
    )
    (workflow_dir / "github-repository-discoverability-playbook.md").write_text(
        "# GitHub Repository Discoverability Playbook\n\n"
        "## Manual GitHub settings\n\n"
        "1. Update the homepage target when needed.\n\n"
        "## Maintainer checklist\n\n"
        "1. Review metadata.\n"
        "2. Update links.\n",
        encoding="utf-8",
    )

    service = RepositoryDiscoverabilityPlaybookService(repository_root)

    failures = service.validate()

    assert [failure.step for failure in failures] == [3, 4, 5]
    assert failures[0].summary == (
        "The playbook manual-settings section does not cover the required GitHub UI surfaces."
    )
    assert "about" in failures[0].actual
    assert "topics" in failures[0].actual
    assert "social preview" in failures[0].actual
    assert failures[1].summary == (
        "The playbook is missing the repo-backed updates section."
    )
    assert service.REPO_BACKED_SECTION_HEADING in failures[1].actual
    assert failures[2].summary == (
        "The maintainer checklist is not repeatable enough for metadata refreshes."
    )

