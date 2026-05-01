import textwrap
from pathlib import Path

from testing.components.services.readme_installation_entry_points_service import (
    ReadmeInstallationEntryPointsService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_899_readme_installation_entry_points_use_latest_epam_release_links() -> None:
    service = ReadmeInstallationEntryPointsService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)


def test_dmc_899_service_accepts_semantic_markdown_variants(tmp_path: Path) -> None:
    readme_path = tmp_path / "README.md"
    readme_path.write_text(
        textwrap.dedent(
            """
            # DMTools
            Delivery Management Tools

            [![Latest Release](https://img.shields.io/github/v/release/epam/dm.ai?label=latest%20version)](https://github.com/epam/dm.ai/releases/latest)

            > Quick install
            >
            > ```bash
            > curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
            > ```
            >
            > Windows: https://github.com/epam/dm.ai/releases/latest/download/install.ps1

            ## Quick Links
            - [Open Installation section](#Installation)

            ## Installation
            Use the latest release page:
            <https://github.com/epam/dm.ai/releases/latest>
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    service = ReadmeInstallationEntryPointsService(tmp_path)

    assert service.validate() == []
