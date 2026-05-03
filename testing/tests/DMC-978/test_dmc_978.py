from pathlib import Path

from testing.components.services.readme_product_positioning_service import (
    ReadmeProductPositioningService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_INSTALL_COMMAND = (
    "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash"
)
EXPECTED_AUDIENCES = ("platform", "delivery", "qa", "engineering")


def test_dmc_978_readme_opening_positions_dmtools_as_dark_factory_orchestrator() -> None:
    service = ReadmeProductPositioningService(REPOSITORY_ROOT)

    hero_markdown = service.hero_markdown()
    hero_text = service.hero_visible_text()
    hero_normalized = service.normalized_text(hero_text)

    assert hero_markdown.startswith("# DMTools"), (
        "README.md must open with the DMTools product heading so the first screen is "
        "clearly branded for readers landing on the repository."
    )
    assert EXPECTED_INSTALL_COMMAND in hero_markdown, (
        "The README hero should keep the primary CLI install command visible so the "
        "opening remains CLI-first for new users."
    )
    assert "dark factory orchestrator" in hero_normalized, (
        "The README hero must frame DMTools as an orchestrator for enterprise dark "
        "factories using visible user-facing language.\n"
        f"Observed hero text: {hero_text}"
    )
    assert "self hosted and enterprise environments" in hero_normalized, (
        "The opening must explicitly state that DMTools is intended for self-hosted "
        "and enterprise environments.\n"
        f"Observed hero text: {hero_text}"
    )

    what_is_for_text = service.section_visible_text("## What DMTools is for")
    what_is_for_normalized = service.normalized_text(what_is_for_text)

    missing_audiences = [
        audience for audience in EXPECTED_AUDIENCES if audience not in what_is_for_normalized
    ]
    assert not missing_audiences, (
        "The opening README section must explain who DMTools is for, including platform, "
        "delivery, QA, and engineering teams. "
        f"Missing audience terms: {', '.join(missing_audiences)}.\n"
        f"Observed section text: {what_is_for_text}"
    )
    assert "mcp tools" in what_is_for_normalized, (
        "The opening README section must describe the MCP connector/tooling path.\n"
        f"Observed section text: {what_is_for_text}"
    )

    usage_rows = {
        row.cells[0]: row for row in service.markdown_table_rows("## Primary usage paths") if row.cells
    }
    assert "CLI + MCP tools" in usage_rows, (
        "README.md must list 'CLI + MCP tools' as a primary usage path so the product "
        "is presented as CLI-first."
    )
    assert "Jobs + agents" in usage_rows, (
        "README.md must list 'Jobs + agents' as a primary usage path for orchestrated workflows."
    )

    cli_row_text = " ".join(usage_rows["CLI + MCP tools"].cells)
    jobs_row_text = " ".join(usage_rows["Jobs + agents"].cells)

    assert "terminal" in service.normalized_text(cli_row_text), (
        "The CLI + MCP tools usage path should explicitly tell users that this path is "
        "run from the terminal.\n"
        f"Observed row: {cli_row_text}"
    )
    assert "teammate" in service.normalized_text(jobs_row_text), (
        "The jobs workflow row must call out teammate workflows in user-facing text.\n"
        f"Observed row: {jobs_row_text}"
    )

    assert service.opening_stops_before("## What DMTools is for"), (
        "The 'What DMTools is for' section should appear in the README opening before "
        "the broader documentation map."
    )
    assert service.opening_stops_before("## Primary usage paths"), (
        "The 'Primary usage paths' section should appear in the README opening before "
        "the broader documentation map."
    )
