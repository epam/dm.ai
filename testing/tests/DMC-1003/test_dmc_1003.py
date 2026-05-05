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
FORBIDDEN_STRINGS = tuple(
    " ".join(str(value).lower().split()) for value in CONFIG["forbidden_strings"]
)
REQUIRED_NOTICE_MARKERS = tuple(
    " ".join(str(value).lower().split()) for value in CONFIG["required_notice_markers"]
)


def build_live_service() -> DeprecatedWorkflowOutputService:
    return DeprecatedWorkflowOutputService(
        workflow_paths=WORKFLOW_PATHS,
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        ref=str(CONFIG["ref"]),
    )


def _normalize_visible_text(value: str) -> str:
    return " ".join(value.lower().split())


def _preview(value: str, *, limit: int = 240) -> str:
    compact = " ".join(value.split())
    if not compact:
        return "no visible text"
    if len(compact) <= limit:
        return compact
    return compact[: limit - 3] + "..."


def test_dmc_1003_deprecated_workflow_outputs_remove_installer_scripts_and_public_header() -> None:
    service = build_live_service()

    surfaces = service.output_surfaces()

    assert surfaces, (
        "Expected the live standalone workflows on main to expose at least one visible release body "
        "or GITHUB_STEP_SUMMARY surface."
    )

    surface_labels = {(surface.workflow_path, surface.surface_name) for surface in surfaces}
    assert (".github/workflows/standalone-release.yml", "release body") in surface_labels
    assert (".github/workflows/standalone-release.yml", "step summary") in surface_labels
    assert (".github/workflows/standalone-release-auto.yml", "release body") in surface_labels

    missing_notice_failures: list[str] = []
    forbidden_string_failures: list[str] = []

    for surface in surfaces:
        normalized_content = _normalize_visible_text(surface.content)
        missing_markers = [
            marker for marker in REQUIRED_NOTICE_MARKERS if marker not in normalized_content
        ]
        if missing_markers:
            missing_notice_failures.append(
                f"- {surface.workflow_path} [{surface.surface_name}] is missing {missing_markers}. "
                f"Visible content: {_preview(surface.content)}"
            )

        for forbidden in FORBIDDEN_STRINGS:
            if forbidden in normalized_content:
                forbidden_string_failures.append(
                    f"- {surface.workflow_path} [{surface.surface_name}] still shows {forbidden!r}. "
                    f"Visible content: {_preview(surface.content)}"
                )

    assert not missing_notice_failures, (
        "Deprecated/internal-only positioning must remain visible in every published workflow surface:\n"
        + "\n".join(missing_notice_failures)
    )
    assert not forbidden_string_failures, (
        "Deprecated standalone/server workflow outputs still expose removed public installer strings:\n"
        + "\n".join(forbidden_string_failures)
    )
