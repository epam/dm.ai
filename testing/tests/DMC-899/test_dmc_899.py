from pathlib import Path

from testing.components.services.readme_installation_entry_points_service import (
    ReadmeInstallationEntryPointsService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_899_readme_installation_entry_points_use_latest_epam_release_links() -> None:
    service = ReadmeInstallationEntryPointsService(REPOSITORY_ROOT)

    failures = service.validate()

    assert not failures, service.format_failures(failures)
