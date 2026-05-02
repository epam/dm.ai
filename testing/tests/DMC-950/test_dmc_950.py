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
from testing.components.services.installer_script_service import (  # noqa: E402
    InstallerScriptService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
REQUESTED_SKILLS = tuple(str(skill) for skill in CONFIG["requested_skills"])
EXPECTED_VALID_SKILLS = tuple(str(skill) for skill in CONFIG["expected_valid_skills"])
EXPECTED_INVALID_SKILLS = tuple(str(skill) for skill in CONFIG["expected_invalid_skills"])
EXPECTED_ENDPOINTS = tuple(f"/dmtools/{skill}" for skill in EXPECTED_VALID_SKILLS)
UNEXPECTED_ENDPOINTS = tuple(f"/dmtools/{skill}" for skill in EXPECTED_INVALID_SKILLS)


def build_service() -> InstallerMetadataService:
    return create_installer_metadata_service(
        repository_root=REPOSITORY_ROOT,
        installer_url=str(CONFIG["installer_url"]),
    )


def test_dmc_950_selective_install_ignores_unknown_skills_without_corrupting_metadata() -> None:
    service = build_service()
    run = service.run_selective_install(REQUESTED_SKILLS)
    visible_stdout = InstallerScriptService.strip_ansi(run.execution.stdout)

    assert run.execution.returncode == 0, service.format_execution_failure(run)
    assert "Warning: Skipping unknown skills: non_existent_skill_xyz" in visible_stdout, (
        "A user should see which requested skill name was rejected while the install continues.\n"
        f"stdout:\n{visible_stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert "Effective skills: jira (source: env)" in visible_stdout, (
        "The installer should visibly confirm that only the valid skill remains selected.\n"
        f"stdout:\n{visible_stdout}\n\nstderr:\n{run.execution.stderr}"
    )
    assert run.installer_env_path.exists(), service.format_missing_artifact(
        run,
        run.installer_env_path,
        "The selective install should persist its filtered runtime skill selection state.",
    )
    installer_env = run.installer_env_path.read_text(encoding="utf-8")
    assert 'DMTOOLS_SKILLS="jira"' in installer_env, (
        "The persisted installer-managed environment should keep only the valid requested skill.\n"
        f"{installer_env}"
    )
    assert "non_existent_skill_xyz" not in installer_env, (
        "Unknown skills must not leak into the installer-managed environment file.\n"
        f"{installer_env}"
    )
    assert run.installed_skills_path.exists(), service.format_missing_artifact(
        run,
        run.installed_skills_path,
        "The installer should create installed-skills.json for the surviving valid skill.",
    )
    assert run.endpoints_path.exists(), service.format_missing_artifact(
        run,
        run.endpoints_path,
        "The installer should create endpoints.json for the surviving valid skill.",
    )

    installed_skills_payload = service.read_json(run.installed_skills_path)
    declared_skills = service.declared_skills(installed_skills_payload)
    assert declared_skills == {skill.lower() for skill in EXPECTED_VALID_SKILLS}, (
        service.format_unexpected_payload(
            run.installed_skills_path,
            installed_skills_payload,
            "installed-skills.json should contain only the valid requested skill.",
        )
    )
    assert service.payload_contains_version(installed_skills_payload), (
        service.format_unexpected_payload(
            run.installed_skills_path,
            installed_skills_payload,
            "installed-skills.json should include version metadata for downstream consumers.",
        )
    )

    endpoints_payload = service.read_json(run.endpoints_path)
    discovered_endpoint_paths = service.endpoint_paths(endpoints_payload)
    assert discovered_endpoint_paths == set(EXPECTED_ENDPOINTS), service.format_unexpected_payload(
        run.endpoints_path,
        endpoints_payload,
        "endpoints.json should contain only the endpoint paths for the valid requested skills.",
    )
    for unexpected_endpoint in UNEXPECTED_ENDPOINTS:
        assert unexpected_endpoint not in discovered_endpoint_paths, service.format_unexpected_payload(
            run.endpoints_path,
            endpoints_payload,
            "endpoints.json must exclude endpoints for unknown requested skills.",
        )
