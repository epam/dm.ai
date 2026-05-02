from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.installer_metadata_service_factory import (  # noqa: E402
    create_installer_metadata_service,
)
from testing.components.services.installer_metadata_service import (  # noqa: E402
    InstallerMetadataService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
SELECTED_SKILLS = tuple(str(skill) for skill in CONFIG["selected_skills"])


def build_service() -> InstallerMetadataService:
    return create_installer_metadata_service(REPOSITORY_ROOT)


def test_dmc_949_selective_install_with_write_protected_directory_fails_without_false_success() -> None:
    service = build_service()
    run = service.run_local_selective_install_with_write_protected_install_dir(SELECTED_SKILLS)

    assert run.execution.returncode != 0, (
        "The installer must exit with a non-zero code when machine-readable metadata cannot "
        "be written inside a write-protected installation directory.\n"
        f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert "Installing DMTools CLI..." in run.execution.stdout, (
        "A user should still see the normal installer banner before the metadata write failure.\n"
        f"stdout:\n{run.execution.stdout}"
    )
    assert "Effective skills: jira (source: env)" in run.execution.stdout, (
        "The visible output should preserve the requested selective-skill announcement.\n"
        f"stdout:\n{run.execution.stdout}"
    )
    assert "Latest version: v0.0.0-test" in run.execution.stdout, (
        "The installer should reach the visible version-resolution step before the "
        "metadata write fails.\n"
        f"stdout:\n{run.execution.stdout}"
    )
    assert "installed-skills.json" in run.execution.stderr, (
        "The error output must identify the metadata file that could not be written.\n"
        f"stderr:\n{run.execution.stderr}"
    )
    assert "Permission denied" in run.execution.stderr, (
        "The error output must make the filesystem permission problem explicit.\n"
        f"stderr:\n{run.execution.stderr}"
    )
    assert not run.installed_skills_path.exists(), (
        "The installer must not leave behind a partial installed-skills.json artifact after "
        "the permission failure."
    )
    assert not run.endpoints_path.exists(), (
        "The installer must not create endpoints.json when metadata generation aborts."
    )
    assert "Generated machine-readable installer metadata" not in run.execution.stdout, (
        "The installer must not report metadata generation as successful after the "
        "permission failure.\n"
        f"stdout:\n{run.execution.stdout}"
    )
    assert service.post_metadata_step_marker not in run.execution.combined_output, (
        "The installer should stop immediately at the metadata write failure instead of "
        "continuing into later success-path steps.\n"
        f"output:\n{run.execution.combined_output}"
    )
