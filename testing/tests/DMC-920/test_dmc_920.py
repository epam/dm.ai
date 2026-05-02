from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_920_troubleshooting_backlink_returns_to_root_readme_installation() -> None:
    service = DocumentationCrossLinkService(REPOSITORY_ROOT)
    markdown = service.troubleshooting_guide_path.read_text(encoding="utf-8")
    readme_links = [
        link
        for link in service.parse_markdown_links(markdown)
        if service.resolve_target(service.troubleshooting_guide_path, link.target)[0]
        == service.readme_path.resolve()
    ]
    backlinks = [
        link
        for link in readme_links
        if service.resolve_target(service.troubleshooting_guide_path, link.target)[1]
        == "installation"
    ]

    assert backlinks, service.format_missing_backlink(
        service.troubleshooting_guide_path,
        readme_links,
    )

    backlink = backlinks[0]
    resolved_path, fragment = service.resolve_target(
        service.troubleshooting_guide_path,
        backlink.target,
    )

    assert backlink.text == "Back to README installation", (
        "The troubleshooting guide should expose a clear user-facing backlink label. "
        f"Found {backlink.text!r}."
    )
    assert resolved_path == service.readme_path.resolve(), service.format_invalid_target(
        service.troubleshooting_guide_path,
        backlink,
        resolved_path,
        fragment,
    )
    assert fragment == "installation", service.format_invalid_target(
        service.troubleshooting_guide_path,
        backlink,
        resolved_path,
        fragment,
    )
    assert fragment in service.anchors_for(resolved_path), service.format_invalid_target(
        service.troubleshooting_guide_path,
        backlink,
        resolved_path,
        fragment,
    )

    service.section_content(resolved_path, "### Installation")

    first_section_line = next(
        index
        for index, line in enumerate(markdown.splitlines(), start=1)
        if line.startswith("## ")
    )
    assert backlink.line_number < first_section_line, (
        "The backlink should appear before the troubleshooting sections so users can "
        "return to the README installation instructions immediately. "
        f"Found backlink on line {backlink.line_number} and first section on line "
        f"{first_section_line}."
    )
