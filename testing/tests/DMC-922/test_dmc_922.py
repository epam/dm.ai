from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_INSTALLATION_FRAGMENT = "installation"


def test_dmc_922_root_readme_exposes_installation_anchor_for_deep_links() -> None:
    service = DocumentationCrossLinkService(REPOSITORY_ROOT)

    readme_anchors = service.anchors_for(service.readme_path)
    assert EXPECTED_INSTALLATION_FRAGMENT in readme_anchors, (
        "README.md must expose a GitHub-compatible #installation anchor for "
        "deep links to the Installation section. "
        f"Found anchors: {sorted(readme_anchors)}"
    )
