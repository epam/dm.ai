from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _read(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1013_standalone_workflows_publish_compatibility_jar_without_legacy_upload_step() -> None:
    for workflow_path in (
        ".github/workflows/standalone-release.yml",
        ".github/workflows/standalone-release-auto.yml",
    ):
        workflow_text = _read(workflow_path)

        assert "gh release create" in workflow_text
        assert "build/libs/dmtools-v*-all.jar" in workflow_text
        assert "actions/create-release@v1" not in workflow_text
        assert "actions/upload-release-asset@v1" not in workflow_text
        assert "softprops/action-gh-release@v2" not in workflow_text
