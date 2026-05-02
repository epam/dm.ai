from __future__ import annotations

import sys
from pathlib import Path
from typing import Mapping, Sequence


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.installer_metadata_service_factory import (  # noqa: E402
    create_installer_metadata_service,
)
from testing.components.services.installer_metadata_service import (  # noqa: E402
    InstallerMetadataService,
)
from testing.core.models.process_execution_result import ProcessExecutionResult  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
SELECTED_SKILLS = tuple(str(skill) for skill in CONFIG["selected_skills"])
EXPECTED_ENDPOINTS = tuple(f"/dmtools/{skill}" for skill in SELECTED_SKILLS)


def build_service() -> InstallerMetadataService:
    return create_installer_metadata_service(
        repository_root=REPOSITORY_ROOT,
        installer_url=str(CONFIG["installer_url"]),
    )


def test_dmc_930_selective_install_generates_machine_readable_metadata_files() -> None:
    service = build_service()
    run = service.run_selective_install(SELECTED_SKILLS)
    visible_output = service.normalized_stdout(run.execution)

    assert run.execution.returncode == 0, service.format_execution_failure(run)
    assert service.output_reports_selected_skills(run.execution, SELECTED_SKILLS, "env"), (
        "The installer did not report the selected skills in its user-visible output.\n"
        f"stdout:\n{visible_output}\n\nstderr:\n{run.execution.stderr}"
    )
    assert "DMTools CLI installation completed!" in visible_output, (
        "The installer did not show the user-facing completion message after the selective "
        f"install.\nstdout:\n{visible_output}\n\nstderr:\n{run.execution.stderr}"
    )
    assert run.installer_env_path.exists(), service.format_missing_artifact(
        run,
        run.installer_env_path,
        "The selective install should persist its runtime skill selection state.",
    )
    assert run.installed_skills_path.exists(), service.format_missing_artifact(
        run,
        run.installed_skills_path,
        "The installer should create installed-skills.json with the selected skill list "
        "and version metadata.",
    )
    assert run.endpoints_path.exists(), service.format_missing_artifact(
        run,
        run.endpoints_path,
        "The installer should create endpoints.json with REST-friendly endpoint discovery entries.",
    )

    installed_skills_payload = service.read_json(run.installed_skills_path)
    assert service.payload_contains_skills(
        installed_skills_payload,
        SELECTED_SKILLS,
    ), service.format_unexpected_payload(
        run.installed_skills_path,
        installed_skills_payload,
        "installed-skills.json does not expose the selected skill list.",
    )
    assert service.payload_contains_version(installed_skills_payload), (
        service.format_unexpected_payload(
            run.installed_skills_path,
            installed_skills_payload,
            "installed-skills.json does not expose installer or artifact version information.",
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
        "endpoints.json is missing one or more REST-friendly skill endpoints: "
        + ", ".join(missing_endpoints),
    )


class _UnusedRunner:
    def run(
        self,
        args: Sequence[str],
        cwd: Path,
        env: Mapping[str, str] | None = None,
        trace_network: bool = False,
    ) -> ProcessExecutionResult:
        del args, cwd, env, trace_network
        raise AssertionError("The process runner is not used by this helper-only test.")


def test_dmc_930_payload_contains_skills_reads_explicit_skill_collections() -> None:
    service = InstallerMetadataService(REPOSITORY_ROOT, runner=_UnusedRunner())
    payload = {
        "version": "v1.7.179",
        "skills": [{"name": "jira"}, {"id": "confluence"}],
    }

    assert service.payload_contains_skills(payload, SELECTED_SKILLS)


def test_dmc_930_payload_contains_skills_ignores_unrelated_nested_name_and_id_fields() -> None:
    service = InstallerMetadataService(REPOSITORY_ROOT, runner=_UnusedRunner())
    payload = {
        "version": "v1.7.179",
        "metadata": {"name": "jira", "id": "confluence"},
        "artifacts": [{"name": "dmtools.jar"}, {"id": "download"}],
    }

    assert not service.payload_contains_skills(payload, SELECTED_SKILLS)


def test_dmc_930_output_reports_selected_skills_ignores_ansi_and_spacing() -> None:
    service = InstallerMetadataService(REPOSITORY_ROOT, runner=_UnusedRunner())
    execution = ProcessExecutionResult(
        args=("bash",),
        cwd=REPOSITORY_ROOT,
        returncode=0,
        stdout=(
            "\x1b[0;32m🚀 Installing DMTools CLI...\x1b[0m\n"
            "\x1b[0;32mEffective skills: jira, confluence (source: env)\x1b[0m\n"
        ),
        stderr="",
    )

    assert service.output_reports_selected_skills(execution, SELECTED_SKILLS, "env")
