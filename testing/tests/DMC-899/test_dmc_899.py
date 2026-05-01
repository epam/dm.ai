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


def test_dmc_899_service_accepts_valid_table_of_contents_and_badge_links(
    tmp_path: Path,
) -> None:
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

            ## Table of Contents
            - [Installation](#Installation)

            ## Installation
            Use the latest release page:
            [![Latest Release](https://img.shields.io/github/v/release/epam/dm.ai?label=latest%20version)](https://github.com/epam/dm.ai/releases/latest)
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    service = ReadmeInstallationEntryPointsService(tmp_path)

    assert service.validate() == []


def test_dmc_899_service_requires_real_table_of_contents_block(tmp_path: Path) -> None:
    readme_path = tmp_path / "README.md"
    readme_path.write_text(
        textwrap.dedent(
            """
            # DMTools
            Delivery Management Tools

            > Quick install
            >
            > ```bash
            > curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
            > ```
            >
            > Windows: https://github.com/epam/dm.ai/releases/latest/download/install.ps1

            ## Quick Links
            - [Installation](#Installation)

            ## Installation
            Use the latest release page:
            <https://github.com/epam/dm.ai/releases/latest>
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    service = ReadmeInstallationEntryPointsService(tmp_path)

    failures = service.validate()

    assert [failure.step for failure in failures] == [4]
    assert failures[0].summary == (
        "README is missing a Table of Contents section for Installation discovery."
    )
