from pathlib import Path

from testing.components.services.readme_legacy_messaging_service import (
    ReadmeLegacyMessagingService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_979_readme_removes_legacy_web_app_messaging_and_leads_with_cli_first_paths() -> None:
    service = ReadmeLegacyMessagingService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)


def test_dmc_979_reports_legacy_keywords_and_non_cli_primary_entry_point(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    repository_root.mkdir()
    (repository_root / "README.md").write_text(
        "\n".join(
            [
                "# DMTools",
                "",
                "Legacy OAuth web application for a live application demo.",
                "",
                "## Primary usage paths",
                "",
                "| Usage path | What you do with it | Start here |",
                "|---|---|---|",
                "| Browser UI | Launch the hosted service and inspect Swagger UI | /swagger |",
                "| CLI + MCP tools | Execute direct tool calls and integration operations from the terminal | docs |",
            ]
        ),
        encoding="utf-8",
    )

    service = ReadmeLegacyMessagingService(repository_root)

    failures = service.validate()
    failure_message = service.format_failures(failures)

    assert [failure.step for failure in failures] == [1, 2]
    assert "'oauth' at README.md:3" in failure_message
    assert "'web application' at README.md:3" in failure_message
    assert "'live application' at README.md:3" in failure_message
    assert "'swagger ui' at README.md:9" in failure_message
    assert "Observed usage path order: Browser UI, CLI + MCP tools" in failure_message


def test_dmc_979_allows_legacy_terms_in_later_migration_context(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    repository_root.mkdir()
    (repository_root / "README.md").write_text(
        "\n".join(
            [
                "# DMTools",
                "",
                "CLI orchestration for enterprise delivery teams.",
                "",
                "## Primary usage paths",
                "",
                "| Usage path | What you do with it | Start here |",
                "|---|---|---|",
                "| CLI + MCP tools | Execute direct tool calls and integration operations from the terminal | docs |",
                "| Jobs + agents | Run reusable orchestration flows | agents |",
                "",
                "## Quick start",
                "",
                "Run DMTools from the terminal with MCP tools and jobs.",
                "",
                "## Upgrading from legacy installs",
                "",
                "Older migration notes may still reference OAuth, Swagger UI, or a live application.",
            ]
        ),
        encoding="utf-8",
    )

    service = ReadmeLegacyMessagingService(repository_root)

    failures = service.validate()

    assert not failures, service.format_failures(failures)
