from __future__ import annotations

import textwrap
from pathlib import Path

from testing.components.services.readme_documentation_navigation_service import (
    ReadmeDocumentationNavigationService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_980_readme_navigation_points_to_maintained_documentation_hubs() -> None:
    service = ReadmeDocumentationNavigationService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)

    jobs_observation = service.observation_for_target(service.JOBS_REFERENCE_TARGET)
    installation_observation = service.observation_for_target(service.INSTALLATION_TARGET)
    configuration_observation = service.observation_for_target(service.CONFIGURATION_TARGET)
    integrations_observation = service.observation_for_target(service.INTEGRATIONS_TARGET)
    mcp_observation = service.observation_for_target(service.MCP_TOOLS_TARGET)
    workflow_observation = service.observation_for_target(service.AI_TEAMMATE_TARGET)
    skill_observation = service.observation_for_target(service.SKILL_GUIDE_TARGET)

    assert "Jobs + agents" in jobs_observation.line_text
    assert "Installation and upgrade" in installation_observation.line_text
    assert "Configuration overview" in configuration_observation.line_text
    assert "Integration setup guides" in integrations_observation.line_text
    assert "MCP tools reference" in mcp_observation.line_text
    assert workflow_observation.text == "GitHub Actions teammate workflow"
    assert "AI assistant skills" in skill_observation.line_text


def test_dmc_980_service_accepts_current_navigation_pattern_in_synthetic_readme(
    tmp_path: Path,
) -> None:
    _write_readme(
        tmp_path,
        """
        # DMTools

        ## Primary usage paths

        | Usage path | What you do with it | Start here |
        |---|---|---|
        | CLI + MCP tools | Execute direct tool calls and integration operations from the terminal | [MCP tools reference](dmtools-ai-docs/references/mcp-tools/README.md) |
        | Jobs + agents | Run orchestrated workflows such as Teammate, reporting, and test generation | [Jobs reference](dmtools-ai-docs/references/jobs/README.md) |
        | GitHub workflow automation | Run DMTools in CI/CD for ticket processing and teammate flows | [GitHub Actions teammate workflow](dmtools-ai-docs/references/workflows/github-actions-teammate.md) |
        | AI assistant skills | Install project-level DMTools skills for agent-driven usage | [Skill guide](dmtools-ai-docs/SKILL.md) |

        ## Documentation map

        | Topic | Link |
        |---|---|
        | Jobs and workflow orchestration | [references/jobs/README.md](dmtools-ai-docs/references/jobs/README.md) |
        | Installation and upgrade | [references/installation/README.md](dmtools-ai-docs/references/installation/README.md) |
        | Configuration overview | [references/configuration/README.md](dmtools-ai-docs/references/configuration/README.md) |
        | Integration setup guides | [references/configuration/integrations/](dmtools-ai-docs/references/configuration/integrations/) |

        ## Quick start
        """
    )
    _create_required_doc_targets(tmp_path)

    service = ReadmeDocumentationNavigationService(tmp_path)

    assert service.validate() == []


def test_dmc_980_service_rejects_external_or_wrong_workflow_navigation_targets(
    tmp_path: Path,
) -> None:
    _write_readme(
        tmp_path,
        """
        # DMTools

        ## Primary usage paths

        | Usage path | What you do with it | Start here |
        |---|---|---|
        | CLI + MCP tools | Execute direct tool calls and integration operations from the terminal | [MCP tools reference](dmtools-ai-docs/references/mcp-tools/README.md) |
        | GitHub workflow automation | Run DMTools in CI/CD for ticket processing and teammate flows | [Workflow file](.github/workflows/teammate.yml) |
        | AI assistant skills | Install project-level DMTools skills for agent-driven usage | [Skill guide](https://docs.example.com/skills) |

        ## Documentation map

        | Topic | Link |
        |---|---|
        | Installation and upgrade | [references/installation/README.md](dmtools-ai-docs/references/installation/README.md) |
        | Configuration overview | [references/configuration/README.md](dmtools-ai-docs/references/configuration/README.md) |
        | Integration setup guides | [references/configuration/integrations/](dmtools-ai-docs/references/configuration/integrations/) |

        ## Quick start
        """
    )
    _create_required_doc_targets(tmp_path)
    workflow_file = tmp_path / ".github/workflows/teammate.yml"
    workflow_file.parent.mkdir(parents=True, exist_ok=True)
    workflow_file.write_text("name: teammate\n", encoding="utf-8")

    service = ReadmeDocumentationNavigationService(tmp_path)

    failures = service.validate()

    assert [failure.step for failure in failures] == [2, 3, 4]
    assert failures[0].summary == (
        "README navigation is missing one or more required maintained documentation links."
    )
    assert service.AI_TEAMMATE_TARGET in failures[0].actual
    assert failures[1].summary == (
        "README navigation contains invalid or non-maintained link targets."
    )
    assert "https://docs.example.com/skills" in failures[1].actual
    assert failures[2].summary == (
        "README does not point the AI teammate workflow navigation entry to the maintained teammate guide."
    )
    assert ".github/workflows/teammate.yml" in failures[2].actual


def test_dmc_980_service_rejects_missing_jobs_workflow_navigation_hub(
    tmp_path: Path,
) -> None:
    _write_readme(
        tmp_path,
        """
        # DMTools

        ## Primary usage paths

        | Usage path | What you do with it | Start here |
        |---|---|---|
        | CLI + MCP tools | Execute direct tool calls and integration operations from the terminal | [MCP tools reference](dmtools-ai-docs/references/mcp-tools/README.md) |
        | GitHub workflow automation | Run DMTools in CI/CD for ticket processing and teammate flows | [GitHub Actions teammate workflow](dmtools-ai-docs/references/workflows/github-actions-teammate.md) |
        | AI assistant skills | Install project-level DMTools skills for agent-driven usage | [Skill guide](dmtools-ai-docs/SKILL.md) |

        ## Documentation map

        | Topic | Link |
        |---|---|
        | Installation and upgrade | [references/installation/README.md](dmtools-ai-docs/references/installation/README.md) |
        | Configuration overview | [references/configuration/README.md](dmtools-ai-docs/references/configuration/README.md) |
        | Integration setup guides | [references/configuration/integrations/](dmtools-ai-docs/references/configuration/integrations/) |

        ## Quick start
        """
    )
    _create_required_doc_targets(tmp_path)

    service = ReadmeDocumentationNavigationService(tmp_path)

    failures = service.validate()

    assert [failure.step for failure in failures] == [2]
    assert failures[0].summary == (
        "README navigation is missing one or more required maintained documentation links."
    )
    assert service.JOBS_REFERENCE_TARGET in failures[0].actual


def _write_readme(root: Path, content: str) -> None:
    (root / "README.md").write_text(
        textwrap.dedent(content).strip() + "\n",
        encoding="utf-8",
    )


def _create_required_doc_targets(root: Path) -> None:
    for relative_path in (
        "dmtools-ai-docs/references/jobs/README.md",
        "dmtools-ai-docs/references/installation/README.md",
        "dmtools-ai-docs/references/configuration/README.md",
        "dmtools-ai-docs/references/workflows/github-actions-teammate.md",
        "dmtools-ai-docs/references/mcp-tools/README.md",
        "dmtools-ai-docs/SKILL.md",
    ):
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("# Placeholder\n", encoding="utf-8")

    integrations_dir = root / "dmtools-ai-docs/references/configuration/integrations"
    integrations_dir.mkdir(parents=True, exist_ok=True)
