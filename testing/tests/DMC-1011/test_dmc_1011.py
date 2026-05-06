from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _workflow_text(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1011_deprecated_standalone_workflows_attach_assets_during_release_creation() -> None:
    for workflow_path in (
        ".github/workflows/standalone-release.yml",
        ".github/workflows/standalone-release-auto.yml",
    ):
        workflow_text = _workflow_text(workflow_path)

        assert "uses: softprops/action-gh-release@v2" in workflow_text
        assert "actions/create-release@v1" not in workflow_text
        assert "actions/upload-release-asset@v1" not in workflow_text
        assert "files: |" in workflow_text
        assert "${{ steps.compatibility_artifact.outputs.jar_path }}" in workflow_text
