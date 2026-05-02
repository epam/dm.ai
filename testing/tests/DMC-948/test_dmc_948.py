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
EXPECTED_ENDPOINTS = tuple(f"/dmtools/{skill}" for skill in SELECTED_SKILLS)
FAILED_COMMAND_TEST_WARNING = "Installation completed but dmtools command test failed"
SUCCESSFUL_COMMAND_TEST_MESSAGE = "DMTools CLI installed successfully!"
MISSING_METADATA_ASSERTION = "AssertionError"


def build_service() -> InstallerMetadataService:
    return create_installer_metadata_service(
        repository_root=REPOSITORY_ROOT,
        installer_url=str(CONFIG["installer_url"]),
    )


def test_dmc_948_selective_install_completes_without_post_install_dmtools_warning() -> None:
    service = build_service()
    run = service.run_selective_install(
        SELECTED_SKILLS,
        use_default_home_install_dir=True,
    )
    combined_output = run.execution.combined_output

    assert run.execution.returncode == 0, service.format_execution_failure(run)
    assert "Installing DMTools CLI..." in run.execution.stdout, (
        "The installer should show its normal startup banner to the user.\n"
        f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert "Effective skills: jira,confluence (source: env)" in run.execution.stdout, (
        "The installer did not report the selected skills in its user-visible output.\n"
        f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert SUCCESSFUL_COMMAND_TEST_MESSAGE in run.execution.stdout, (
        "The installer did not report a successful post-install dmtools command validation.\n"
        f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert FAILED_COMMAND_TEST_WARNING not in combined_output, (
        "The post-install dmtools command validation still reported a user-visible failure.\n"
        f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert MISSING_METADATA_ASSERTION not in combined_output, (
        "The installer surfaced an assertion failure during post-install validation.\n"
        f"stdout:\n{run.execution.stdout}\n\nstderr:\n{run.execution.stderr}"
    )

    assert run.installed_skills_path.exists(), service.format_missing_artifact(
        run,
        run.installed_skills_path,
        "The selective install should finish with installed-skills.json available for the "
        "post-install dmtools command test.",
    )
    assert run.endpoints_path.exists(), service.format_missing_artifact(
        run,
        run.endpoints_path,
        "The selective install should finish with endpoints.json available for the "
        "post-install dmtools command test.",
    )

    installed_skills_payload = service.read_json(run.installed_skills_path)
    assert service.payload_contains_skills(
        installed_skills_payload,
        SELECTED_SKILLS,
    ), service.format_unexpected_payload(
        run.installed_skills_path,
        installed_skills_payload,
        "installed-skills.json does not expose the selected skill list after the "
        "post-install validation completes.",
    )
    assert service.payload_contains_version(installed_skills_payload), (
        service.format_unexpected_payload(
            run.installed_skills_path,
            installed_skills_payload,
            "installed-skills.json does not expose installer or artifact version "
            "information after the post-install validation completes.",
        )
    )

    endpoints_payload = service.read_json(run.endpoints_path)
    discovered_endpoint_paths = service.endpoint_paths(endpoints_payload)
    missing_endpoints = [
        endpoint for endpoint in EXPECTED_ENDPOINTS if endpoint not in discovered_endpoint_paths
    ]
    assert not missing_endpoints, service.format_unexpected_payload(
        run.endpoints_path,
        endpoints_payload,
        "endpoints.json is missing one or more REST-friendly skill endpoints after the "
        "post-install validation completes: " + ", ".join(missing_endpoints),
    )
