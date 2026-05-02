from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_INSTALLATION_FRAGMENT = "installation"
EXPECTED_BACKLINK_TEXT = "Back to README installation"


def test_dmc_922_root_readme_exposes_installation_anchor_for_deep_links() -> None:
    service = DocumentationCrossLinkService(REPOSITORY_ROOT)

    readme_anchors = service.anchors_for(service.readme_path)
    assert EXPECTED_INSTALLATION_FRAGMENT in readme_anchors, (
        "README.md must expose a GitHub-compatible #installation anchor so "
        "installation guide backlinks can target the Installation section. "
        f"Found anchors: {sorted(readme_anchors)}"
    )

    findings: list[str] = []
    for guide_path in service.detailed_guides:
        backlinks = service.links_pointing_to(
            guide_path,
            service.readme_path.resolve(),
            EXPECTED_INSTALLATION_FRAGMENT,
        )
        if not backlinks:
            findings.append(service.format_missing_backlink(guide_path, []))
            continue

        if not any(link.text == EXPECTED_BACKLINK_TEXT for link in backlinks):
            findings.append(
                f"{service.relative_path(guide_path)} links to README.md#installation, "
                f"but the visible link text is not {EXPECTED_BACKLINK_TEXT!r}. "
                f"Found: {[link.text for link in backlinks]}"
            )

    assert not findings, "\n".join(findings)
