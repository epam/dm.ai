from __future__ import annotations

from pathlib import Path

from testing.components.services.cli_packaging_workflow_metadata_service import (
    CliPackagingWorkflowMetadataService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_999_package_cli_workflow_metadata_remains_cli_first() -> None:
    service = CliPackagingWorkflowMetadataService(REPOSITORY_ROOT)

    failures = service.validate()
    observation = service.observation()

    assert not failures, service.format_failures(failures)
    assert observation.workflow_relative_path == service.WORKFLOW_RELATIVE_PATH
    assert observation.workflow_name == service.REQUIRED_WORKFLOW_NAME
    assert observation.artifact_output_description == service.REQUIRED_ARTIFACT_OUTPUT_DESCRIPTION
    assert observation.artifact_output_value == service.REQUIRED_ARTIFACT_OUTPUT_VALUE
    assert observation.step_names == service.REQUIRED_STEP_NAMES
    assert observation.surfaced_messages == service.REQUIRED_SURFACED_MESSAGES
    assert observation.packaged_artifact_commands == service.REQUIRED_PACKAGED_ARTIFACT_COMMANDS


def test_dmc_999_service_accepts_cli_packaging_workflow_fixture(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    workflow_path = repository_root / CliPackagingWorkflowMetadataService.WORKFLOW_RELATIVE_PATH
    workflow_path.parent.mkdir(parents=True)
    workflow_path.write_text(
        "\n".join(
            [
                "name: Package CLI (Reusable)",
                "",
                "on:",
                "  workflow_call:",
                "    outputs:",
                "      artifact_name:",
                "        description: 'Name of the uploaded artifact'",
                "        value: cli-package",
                "",
                "jobs:",
                "  package:",
                "    steps:",
                "      - name: Download Build Artifacts",
                "      - name: Prepare CLI Package",
                "        run: |",
                '          echo "ERROR: CLI JAR not found in build artifacts"',
                '          echo "Available files:"',
                "          cp build-artifacts/dmtools-cli.jar cli-release/dmtools-v${{ inputs.version }}-all.jar",
                "          cp build-artifacts/install.sh cli-release/",
                "          cp build-artifacts/install cli-release/ 2>/dev/null || true",
                "          cp build-artifacts/install.bat cli-release/ 2>/dev/null || true",
                "          cp build-artifacts/install.ps1 cli-release/ 2>/dev/null || true",
                "          cp build-artifacts/dmtools.sh cli-release/",
                '          echo "CLI package prepared:"',
                "      - name: Upload CLI Package",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    service = CliPackagingWorkflowMetadataService(repository_root)

    assert service.validate() == []


def test_dmc_999_service_reports_legacy_server_or_api_only_wording(tmp_path: Path) -> None:
    repository_root = tmp_path / "repo"
    workflow_path = repository_root / CliPackagingWorkflowMetadataService.WORKFLOW_RELATIVE_PATH
    workflow_path.parent.mkdir(parents=True)
    workflow_path.write_text(
        "\n".join(
            [
                "name: Package server bundle",
                "",
                "on:",
                "  workflow_call:",
                "    outputs:",
                "      artifact_name:",
                "        description: 'Name of the uploaded server artifact'",
                "        value: api-only-package",
                "",
                "jobs:",
                "  package:",
                "    steps:",
                "      - name: Download Build Artifacts",
                "      - name: Prepare API-only Package",
                "        run: |",
                '          echo "API-only package prepared for the server bundle"',
                "      - name: Upload CLI Package",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    service = CliPackagingWorkflowMetadataService(repository_root)

    failures = service.validate()

    assert [failure.step for failure in failures] == [1, 2, 3]
    assert failures[0].summary == (
        "The reusable packaging workflow metadata no longer presents the uploaded bundle as the CLI package flow."
    )
    assert "api-only-package" in failures[0].actual
    assert "'server' in 'Package server bundle'" in failures[2].actual
    assert "'api-only'" in failures[2].actual
