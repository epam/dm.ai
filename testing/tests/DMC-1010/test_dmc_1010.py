from __future__ import annotations

from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _read(relative_path: str) -> str:
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


def test_dmc_1010_standalone_workflows_read_shadowjar_from_root_build_libs() -> None:
    build_gradle_text = _read("dmtools-core/build.gradle")

    assert 'destinationDirectory.set(file("${project.rootDir}/build/libs"))' in build_gradle_text

    for workflow_path in (
        ".github/workflows/standalone-release.yml",
        ".github/workflows/standalone-release-auto.yml",
    ):
        workflow_text = _read(workflow_path)

        assert 'JAR_PATH=$(ls build/libs/dmtools-v*-all.jar | head -n 1)' in workflow_text
        assert 'dmtools-core/build/libs/dmtools-v*-all.jar' not in workflow_text
