from __future__ import annotations

import json
import textwrap
from pathlib import Path

from testing.components.services.github_issue_template_positioning_service import (
    GitHubIssueTemplatePositioningService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_988_issue_templates_match_discoverability_metadata_and_orchestrator_narrative() -> None:
    service = GitHubIssueTemplatePositioningService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)

    feature_observation = service.observation_for_label(service.FEATURE_TEMPLATE_LABEL)
    bug_observation = service.observation_for_label(service.BUG_TEMPLATE_LABEL)

    assert feature_observation.about == service.expected_about_for_label(
        service.FEATURE_TEMPLATE_LABEL
    )
    assert bug_observation.about == service.expected_about_for_label(
        service.BUG_TEMPLATE_LABEL
    )
    assert feature_observation.prompt_headings[:3] == (
        "What workflow are you trying to improve?",
        "What outcome do you want?",
        "Describe the proposed solution",
    )
    assert bug_observation.prompt_headings[:3] == (
        "What broke",
        "To Reproduce",
        "Expected behavior",
    )
    assert "dmtools" in feature_observation.opening_prompt_body_text.casefold()
    assert "workflow" in feature_observation.opening_prompt_signal_hits
    assert "automation" in feature_observation.opening_prompt_signal_hits
    assert "cli" in feature_observation.opening_prompt_signal_hits
    assert "mcp" in feature_observation.opening_prompt_signal_hits
    assert "github" in feature_observation.opening_prompt_signal_hits
    assert "dmtools" in bug_observation.opening_prompt_body_text.casefold()
    assert "workflow" in bug_observation.opening_prompt_signal_hits
    assert "surface" in bug_observation.opening_prompt_signal_hits


def test_dmc_988_service_accepts_dmtools_specific_issue_templates(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    _write_metadata(
        repository_root,
        bug_about="Report a bug in the DMTools CLI, MCP tools, jobs, integrations, or GitHub workflow automation",
        feature_about="Suggest an improvement for DMTools CLI workflows, MCP tools, jobs, integrations, AI assistant skills, or GitHub surfaces",
    )
    _write_file(
        repository_root / ".github/ISSUE_TEMPLATE/feature_request.md",
        """
        ---
        name: DMTools feature request
        about: Suggest an improvement for DMTools CLI workflows, MCP tools, jobs, integrations, AI assistant skills, or GitHub surfaces
        ---

        **What workflow are you trying to improve?**
        Describe the DMTools workflow and CLI/MCP surface that needs to change.

        **Describe the proposed solution**
        Explain the job, agent, integration, docs, or GitHub update you want.
        """,
    )
    _write_file(
        repository_root / ".github/ISSUE_TEMPLATE/bug_report.md",
        """
        ---
        name: DMTools bug report
        about: Report a bug in the DMTools CLI, MCP tools, jobs, integrations, or GitHub workflow automation
        ---

        **What broke**
        Describe the affected DMTools workflow, CLI command, or GitHub automation surface.

        **Affected surface**
        - CLI
        - MCP
        - Job or agent
        - Integration
        - Docs
        """,
    )

    service = GitHubIssueTemplatePositioningService(repository_root)

    assert service.validate() == []


def test_dmc_988_service_rejects_metadata_mismatch_and_generic_placeholders(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    _write_metadata(
        repository_root,
        bug_about="Report a bug in the DMTools CLI, MCP tools, jobs, integrations, or GitHub workflow automation",
        feature_about="Suggest an improvement for DMTools CLI workflows, MCP tools, jobs, integrations, AI assistant skills, or GitHub surfaces",
    )
    _write_file(
        repository_root / ".github/ISSUE_TEMPLATE/feature_request.md",
        """
        ---
        name: Feature request
        about: Suggest an improvement for your project
        ---

        **Describe the solution you'd like**
        A clear and concise description of what the problem is.
        """,
    )
    _write_file(
        repository_root / ".github/ISSUE_TEMPLATE/bug_report.md",
        """
        ---
        name: Bug report
        about: Report a bug for your project
        ---

        **Describe the bug**
        A clear and concise description of what the bug is.

        **Expected behavior**
        A clear and concise description of what you expected to happen.
        """,
    )

    service = GitHubIssueTemplatePositioningService(repository_root)

    failures = service.validate()

    assert [failure.step for failure in failures] == [3, 3, 3, 4, 3, 3, 3, 4]
    assert failures[0].summary == (
        "Feature request template front-matter is not synced with the metadata source."
    )
    assert "Suggest an improvement for DMTools CLI workflows" in failures[0].expected
    assert failures[1].summary == (
        "Feature request template opening prompt copy does not visibly describe DMTools."
    )
    assert failures[2].summary == (
        "Feature request template opening prompt copy is not orchestration focused."
    )
    assert failures[3].summary == (
        "Feature request template still contains generic GitHub template placeholders."
    )
    assert "Describe the solution you'd like" in failures[3].actual
    assert failures[4].summary == (
        "Bug report template front-matter is not synced with the metadata source."
    )
    assert failures[5].summary == (
        "Bug report template opening prompt copy does not visibly describe DMTools."
    )
    assert failures[6].summary == (
        "Bug report template opening prompt copy is not orchestration focused."
    )
    assert failures[7].summary == (
        "Bug report template still contains generic GitHub template placeholders."
    )
    assert "Describe the bug" in failures[7].actual


def test_dmc_988_service_does_not_let_later_sections_mask_generic_opening_prompts(
    tmp_path: Path,
) -> None:
    repository_root = tmp_path / "repo"
    _write_metadata(
        repository_root,
        bug_about="Report a bug in the DMTools CLI, MCP tools, jobs, integrations, or GitHub workflow automation",
        feature_about="Suggest an improvement for DMTools CLI workflows, MCP tools, jobs, integrations, AI assistant skills, or GitHub surfaces",
    )
    _write_file(
        repository_root / ".github/ISSUE_TEMPLATE/feature_request.md",
        """
        ---
        name: DMTools feature request
        about: Suggest an improvement for DMTools CLI workflows, MCP tools, jobs, integrations, AI assistant skills, or GitHub surfaces
        ---

        **What workflow are you trying to improve?**
        Describe what you want to change.

        **What outcome do you want?**
        Describe the result you want.

        **Describe the proposed solution**
        Share the approach you prefer.

        **Which DMTools surfaces are involved?**
        - CLI or wrapper
        - MCP tools or integrations
        - Jobs or agents
        - GitHub Actions or CI workflows
        - AI assistant skills or docs
        """,
    )
    _write_file(
        repository_root / ".github/ISSUE_TEMPLATE/bug_report.md",
        """
        ---
        name: DMTools bug report
        about: Report a bug in the DMTools CLI, MCP tools, jobs, integrations, or GitHub workflow automation
        ---

        **What broke**
        Describe the issue.

        **To Reproduce**
        1.
        2.
        3.

        **Expected behavior**
        Describe what you expected.

        **Affected surface**
        - CLI command or wrapper
        - MCP tool or integration
        - Job or agent configuration
        - GitHub Actions or CI workflow
        - Documentation or discoverability surface
        """,
    )

    service = GitHubIssueTemplatePositioningService(repository_root)

    feature_observation = service.observation_for_label(service.FEATURE_TEMPLATE_LABEL)
    bug_observation = service.observation_for_label(service.BUG_TEMPLATE_LABEL)
    failures = service.validate()
    failure_summaries = {failure.summary for failure in failures}

    assert "cli" in feature_observation.signal_hits
    assert "github" in bug_observation.signal_hits
    assert "dmtools" not in feature_observation.opening_prompt_body_text.casefold()
    assert "dmtools" not in bug_observation.opening_prompt_body_text.casefold()
    assert feature_observation.opening_prompt_signal_hits == ()
    assert bug_observation.opening_prompt_signal_hits == ()
    assert "Feature request template opening prompt copy does not visibly describe DMTools." in failure_summaries
    assert "Feature request template opening prompt copy is not orchestration focused." in failure_summaries
    assert "Bug report template opening prompt copy does not visibly describe DMTools." in failure_summaries
    assert "Bug report template opening prompt copy is not orchestration focused." in failure_summaries


def _write_metadata(repository_root: Path, *, bug_about: str, feature_about: str) -> None:
    metadata_path = (
        repository_root / "dmtools-core/src/main/resources/github-repository-discoverability.json"
    )
    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(
        json.dumps(
            {
                "repoBackedSurfaces": {
                    "bugReportAbout": bug_about,
                    "featureRequestAbout": feature_about,
                }
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def _write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(content).strip() + "\n", encoding="utf-8")
