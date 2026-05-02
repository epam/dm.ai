from __future__ import annotations

import sys
from pathlib import Path

import pytest


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.installer_full_install_audit_service import (  # noqa: E402
    InstallerFullInstallAuditService,
)


@pytest.mark.parametrize(
    ("scenario_name", "extra_env", "expected_source"),
    [
        pytest.param("missing-selection", {}, "default", id="missing-selection"),
        pytest.param("empty-selection", {"DMTOOLS_SKILLS": ""}, None, id="empty-selection"),
        pytest.param("all-selection", {"DMTOOLS_SKILLS": "all"}, "env", id="all-selection"),
    ],
)
def test_dmc_926_missing_or_empty_skill_selection_installs_all_skills_by_default(
    installer_full_install_audit_service: InstallerFullInstallAuditService,
    scenario_name: str,
    extra_env: dict[str, str | None],
    expected_source: str | None,
) -> None:
    observation = installer_full_install_audit_service.observe(extra_env=extra_env)

    assert observation.execution.returncode == 0, (
        f"Installer run failed for {scenario_name}.\n"
        f"stdout:\n{observation.stdout}\n\nstderr:\n{observation.stderr}"
    )
    assert not observation.stderr, (
        f"Expected no stderr output for {scenario_name}, got:\n{observation.stderr}"
    )
    assert "Installing all skills" in observation.stdout, (
        "Installer did not announce the full-install fallback for "
        f"{scenario_name}.\nstdout:\n{observation.stdout}"
    )
    assert (
        f"Effective skills: {InstallerFullInstallAuditService.ALL_SKILLS}" in observation.stdout
    ), (
        "Installer did not show the canonical all-skills selection for "
        f"{scenario_name}.\nstdout:\n{observation.stdout}"
    )
    if expected_source is not None:
        assert f"Installing all skills (source: {expected_source})" in observation.stdout, (
            "Installer did not report the expected selection source for "
            f"{scenario_name}.\nstdout:\n{observation.stdout}"
        )
        assert (
            f"Effective skills: {InstallerFullInstallAuditService.ALL_SKILLS} "
            f"(source: {expected_source})" in observation.stdout
        ), (
            "Installer did not report the expected source alongside the canonical all-skills "
            f"selection for {scenario_name}.\nstdout:\n{observation.stdout}"
        )
    assert "Configured installer-managed skills at" in observation.stdout, (
        "Installer did not report the persisted skill configuration path.\n"
        f"stdout:\n{observation.stdout}"
    )

    assert observation.install_root_exists, "Expected install root to exist after installation."
    assert observation.bin_dir_exists, "Expected bin directory to exist after installation."
    assert observation.jar_exists, "Expected installer to create a non-empty dmtools.jar."
    assert observation.script_exists, "Expected installer to create the dmtools launcher script."
    assert observation.script_is_executable, (
        "Expected installer-created dmtools launcher script to be executable."
    )

    assert (
        observation.installer_env.get("DMTOOLS_SKILLS")
        == InstallerFullInstallAuditService.ALL_SKILLS
    ), (
        "Persisted DMTOOLS_SKILLS did not contain the canonical full install set.\n"
        f"expected: {InstallerFullInstallAuditService.ALL_SKILLS}\n"
        f"actual: {observation.installer_env.get('DMTOOLS_SKILLS')}"
    )
    assert (
        observation.installer_env.get("DMTOOLS_INTEGRATIONS")
        == InstallerFullInstallAuditService.ALL_INTEGRATIONS
    ), (
        "Persisted DMTOOLS_INTEGRATIONS did not contain the canonical full install set.\n"
        f"expected: {InstallerFullInstallAuditService.ALL_INTEGRATIONS}\n"
        f"actual: {observation.installer_env.get('DMTOOLS_INTEGRATIONS')}"
    )
