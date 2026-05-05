from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.deprecated_workflow_output_service import (  # noqa: E402
    DeprecatedWorkflowOutputService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
WORKFLOW_PATHS = tuple(str(path) for path in CONFIG["workflow_paths"])


def build_live_service() -> DeprecatedWorkflowOutputService:
    return DeprecatedWorkflowOutputService(
        workflow_paths=WORKFLOW_PATHS,
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        ref=str(CONFIG["ref"]),
    )


def test_dmc_997_live_deprecated_workflow_outputs_are_internal_only() -> None:
    service = build_live_service()

    findings = service.audit()

    assert not findings, service.format_findings(findings)


def test_dmc_997_service_ignores_internal_flutter_fetch_logic_when_it_is_not_published() -> None:
    service = DeprecatedWorkflowOutputService(
        workflow_paths=(".github/workflows/package-standalone.yml",),
        workflow_text_by_path={
            ".github/workflows/package-standalone.yml": """
name: Package Standalone (Reusable)
jobs:
  package:
    steps:
      - name: Download Flutter SPA Release
        run: |
          curl -s https://api.github.com/repos/IstiN/dmtools-flutter/releases/latest
      - name: Upload artifact
        uses: actions/upload-artifact@v4
""".strip()
        },
    )

    assert service.output_surfaces() == []
    assert service.audit() == []


def test_dmc_997_service_accepts_a_deprecated_internal_only_release_body_without_install_guidance() -> None:
    service = DeprecatedWorkflowOutputService(
        workflow_paths=(".github/workflows/standalone-release-auto.yml",),
        workflow_text_by_path={
            ".github/workflows/standalone-release-auto.yml": """
jobs:
  release:
    steps:
      - name: Create Release
        with:
          body: |
            # Internal packaging note

            > **Deprecated / internal-only workflow.**
            > Standalone artifacts remain available only for compatibility testing.

            Do not use this workflow output as product installation guidance.
""".strip()
        },
    )

    assert service.audit() == []


def test_dmc_997_service_reports_customer_facing_install_guidance_and_swagger_mentions() -> None:
    service = DeprecatedWorkflowOutputService(
        workflow_paths=(".github/workflows/standalone-release.yml",),
        workflow_text_by_path={
            ".github/workflows/standalone-release.yml": """
jobs:
  release:
    steps:
      - name: Create Release
        with:
          body: |
            > **Deprecated / internal-only workflow.**
            ## Supported public install paths
            curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
            Do not copy Swagger guidance from legacy standalone docs.
      - name: Summarize release
        run: |
          echo "**Positioning:** Deprecated/internal-only packaging workflow" >> $GITHUB_STEP_SUMMARY
          echo "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash" >> $GITHUB_STEP_SUMMARY
""".strip()
        },
    )

    findings = service.audit()

    assert len(findings) == 4
    assert any("customer-facing install heading" in finding.summary for finding in findings)
    assert any("customer-facing install command" in finding.summary for finding in findings)
    assert any("Swagger guidance reference" in finding.summary for finding in findings)
