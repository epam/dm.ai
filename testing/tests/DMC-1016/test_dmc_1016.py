from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _read(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1016_standalone_workflows_publish_immutable_safe_draft_releases() -> None:
    for workflow_path in (
        ".github/workflows/standalone-release.yml",
        ".github/workflows/standalone-release-auto.yml",
    ):
        workflow_text = _read(workflow_path)

        assert "gh release create" in workflow_text
        assert "--draft" in workflow_text
        assert "gh release edit" in workflow_text
        assert "--draft=false" in workflow_text
        assert "--prerelease" in workflow_text
