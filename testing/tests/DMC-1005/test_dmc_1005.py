from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _workflow_text(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1005_manual_standalone_workflow_avoids_removed_projects_and_unconditional_test_reporter() -> None:
    workflow_text = _workflow_text(".github/workflows/standalone-release.yml")

    assert ":dmtools-automation:test" not in workflow_text
    assert ":dmtools-server:bootJar" not in workflow_text
    assert "dmtools-server/" not in workflow_text
    assert "dorny/test-reporter@v1" not in workflow_text


def test_dmc_1005_auto_standalone_workflow_targets_existing_modules_and_publishes_summary() -> None:
    workflow_text = _workflow_text(".github/workflows/standalone-release-auto.yml")

    assert ":dmtools-automation:test" not in workflow_text
    assert ":dmtools-server:bootJar" not in workflow_text
    assert "dmtools-server/" not in workflow_text
    assert "$GITHUB_STEP_SUMMARY" in workflow_text
    assert "Deprecated/internal-only packaging workflow" in workflow_text
