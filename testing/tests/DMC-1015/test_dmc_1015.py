from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _read(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1015_standalone_workflows_use_immutable_safe_release_flow_and_tolerate_expected_test_failures() -> None:
    for workflow_path in (
        ".github/workflows/standalone-release.yml",
        ".github/workflows/standalone-release-auto.yml",
    ):
        workflow_text = _read(workflow_path)

        assert "continue-on-error: true" in workflow_text
        assert "DMTOOLS_FORCE_COMPATIBILITY_TEST_FAILURE" in workflow_text
        assert "--continue" in workflow_text
        assert "gh release create" in workflow_text
        assert "softprops/action-gh-release@v2" not in workflow_text
