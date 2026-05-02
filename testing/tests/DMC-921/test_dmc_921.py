from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_BACKLINK_TEXT = "Back to README installation"
EXPECTED_BACKLINK_TARGET = "../../../README.md#installation"
EXPECTED_DESTINATION_HEADING = "### Installation"
EXPECTED_INSTALL_COMMAND = (
    "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash"
)


def test_dmc_921_installation_guide_backlink_returns_to_root_installation() -> None:
    service = DocumentationCrossLinkService(REPOSITORY_ROOT)
    source_path = service.installation_guide_path
    source_text = source_path.read_text(encoding="utf-8")
    source_lines = source_text.splitlines()
    links = service.parse_markdown_links(source_text)

    matching_links = [link for link in links if link.text == EXPECTED_BACKLINK_TEXT]
    assert matching_links, (
        f"{service.relative_path(source_path)} must show a visible "
        f"{EXPECTED_BACKLINK_TEXT!r} link so readers can return to the root installation flow."
    )
    assert len(matching_links) == 1, (
        f"Expected exactly one visible {EXPECTED_BACKLINK_TEXT!r} link in "
        f"{service.relative_path(source_path)}, found {len(matching_links)}."
    )

    backlink = matching_links[0]
    assert backlink.target == EXPECTED_BACKLINK_TARGET, (
        f"{service.relative_path(source_path)}:{backlink.line_number} must point to "
        f"{EXPECTED_BACKLINK_TARGET!r}, found {backlink.target!r}."
    )

    resolved_path, fragment = service.resolve_target(source_path, backlink.target)
    assert resolved_path == service.readme_path.resolve(), service.format_invalid_target(
        source_path,
        backlink,
        resolved_path,
        fragment,
    )
    assert fragment == "installation", (
        f"{service.relative_path(source_path)}:{backlink.line_number} must navigate to the "
        f"README installation anchor, found #{fragment or '<missing>'}."
    )
    assert fragment in service.anchors_for(resolved_path), service.format_invalid_target(
        source_path,
        backlink,
        resolved_path,
        fragment,
    )

    quick_install_line_number = next(
        index + 1
        for index, line in enumerate(source_lines)
        if line.strip() == "## 🚀 Quick Installation"
    )
    assert backlink.line_number < quick_install_line_number, (
        f"{service.relative_path(source_path)} must place the visible backlink before the "
        f"quick installation content so readers can find it immediately after opening the page. "
        f"Backlink line: {backlink.line_number}, quick installation line: {quick_install_line_number}."
    )

    _, installation_section = service.section_content(service.readme_path, EXPECTED_DESTINATION_HEADING)
    assert EXPECTED_INSTALL_COMMAND in installation_section, (
        "Following the backlink should return the reader to the root README installation section "
        "with the primary install command visible."
    )
