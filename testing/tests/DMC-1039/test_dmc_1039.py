from __future__ import annotations

import re
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.deprecated_workflow_run_audit_service_factory import (  # noqa: E402
    create_deprecated_workflow_run_audit_service,
)
from testing.core.interfaces.deprecated_workflow_run_audit_service import (  # noqa: E402
    DeprecatedWorkflowRunAuditService,
)
from testing.core.models.deprecated_workflow_run_audit import DeprecatedWorkflowRunAudit  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
TIMESTAMP_PREFIX_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T[^ ]+\s+")


def _normalize_visible_text(value: str) -> str:
    return " ".join(value.lower().split())


def _normalized_config_list(key: str) -> tuple[str, ...]:
    values = CONFIG.get(key, [])
    if not isinstance(values, list):
        raise TypeError(f"Expected {key!r} to be a list in {TEST_DIRECTORY / 'config.yaml'}.")
    return tuple(_normalize_visible_text(str(value)) for value in values)


REQUIRED_SUCCESSFUL_STEPS = tuple(str(value) for value in CONFIG.get("required_successful_steps", []))
REQUIRED_LOG_FRAGMENTS = _normalized_config_list("required_log_fragments")
FORBIDDEN_LOG_FRAGMENTS = _normalized_config_list("forbidden_log_fragments")
REQUIRED_SUMMARY_MARKERS = _normalized_config_list("required_summary_markers")


def build_service() -> DeprecatedWorkflowRunAuditService:
    return create_deprecated_workflow_run_audit_service(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=str(CONFIG["workflow_name"]),
        release_job_name=str(CONFIG["workflow_job_name"]),
        release_tag="",
        dispatch_timeout_seconds=int(str(CONFIG["dispatch_timeout_seconds"])),
        completion_timeout_seconds=int(str(CONFIG["completion_timeout_seconds"])),
        poll_interval_seconds=int(str(CONFIG["poll_interval_seconds"])),
        required_notice_markers=(),
        forbidden_strings=(),
        require_step_summary=str(CONFIG["require_step_summary"]).lower() == "true",
    )


def _preview_text(value: str, *, limit: int = 500) -> str:
    compact = " ".join(value.split())
    if not compact:
        return "no visible text"
    if len(compact) <= limit:
        return compact
    return compact[: limit - 3] + "..."


def _normalize_log_line(line: str) -> str:
    without_ansi = ANSI_PATTERN.sub("", line)
    return TIMESTAMP_PREFIX_PATTERN.sub("", without_ansi).strip()


def _visible_runtime_log_text(raw_log_text: str) -> str:
    visible_lines: list[str] = []
    within_command_group = False
    for raw_line in raw_log_text.splitlines():
        line = _normalize_log_line(raw_line)
        if line.startswith("##[group]Run "):
            within_command_group = True
            continue
        if within_command_group:
            if line.startswith("##[endgroup]"):
                within_command_group = False
            continue
        visible_lines.append(raw_line)
    return "\n".join(visible_lines)


def _append_audit_failures(
    failures: list[str],
    *,
    audit: DeprecatedWorkflowRunAudit,
    service: DeprecatedWorkflowRunAuditService,
) -> None:
    if audit.failures:
        failures.append(service.format_failures(audit.failures))


def test_dmc_1039_live_auto_standalone_workflow_captures_shadow_jar_metadata_without_missing_file_error() -> None:
    service = build_service()
    audit = service.audit()

    failures: list[str] = []
    _append_audit_failures(failures, audit=audit, service=service)

    if audit.workflow_run is None:
        failures.append("Expected a workflow run observation for the dispatched standalone workflow.")
    elif audit.workflow_run.conclusion != "success":
        failures.append(
            "The live Auto Standalone Release workflow should conclude successfully after dispatch. "
            f"Observed status={audit.workflow_run.status!r}, conclusion={audit.workflow_run.conclusion!r}, "
            f"url={audit.workflow_run.html_url}."
        )

    if audit.release_job is None:
        failures.append("Expected the completed workflow run to expose the auto-standalone-release job.")
        raw_job_log = ""
        step_conclusions: dict[str, str] = {}
        step_summary = ""
    else:
        raw_job_log = audit.release_job.raw_log_text
        step_conclusions = dict(audit.release_job.step_conclusions)
        step_summary = audit.release_job.step_summary_markdown

        if audit.release_job.conclusion != "success":
            failures.append(
                "The visible auto-standalone-release job should finish successfully for users monitoring "
                "the Actions UI. "
                f"Observed conclusion={audit.release_job.conclusion!r}, url={audit.release_job.html_url}. "
                f"Visible log excerpt: {_preview_text(raw_job_log)}"
            )

        for step_name in REQUIRED_SUCCESSFUL_STEPS:
            conclusion = step_conclusions.get(step_name)
            if conclusion is None:
                failures.append(
                    f"Expected the Actions job to expose the visible step {step_name!r}, "
                    f"but only observed {list(step_conclusions.keys()) or ['none']}."
                )
                continue
            if conclusion != "success":
                failures.append(
                    f"Expected the visible step {step_name!r} to conclude with 'success', got "
                    f"{conclusion!r}. Step conclusions: {step_conclusions}. "
                    f"Visible log excerpt: {_preview_text(raw_job_log)}"
                )

    visible_job_log = _visible_runtime_log_text(raw_job_log)
    normalized_log = _normalize_visible_text(visible_job_log)
    for fragment in REQUIRED_LOG_FRAGMENTS:
        if fragment not in normalized_log:
            failures.append(
                f"Expected the metadata-capture run log to reference {fragment!r}, but it was absent. "
                f"Visible log excerpt: {_preview_text(visible_job_log)}"
            )

    for fragment in FORBIDDEN_LOG_FRAGMENTS:
        if fragment in normalized_log:
            failures.append(
                f"Observed forbidden missing-artifact marker {fragment!r} in the live run log. "
                f"Visible log excerpt: {_preview_text(visible_job_log)}"
            )

    normalized_summary = _normalize_visible_text(step_summary)
    for marker in REQUIRED_SUMMARY_MARKERS:
        if marker not in normalized_summary:
            failures.append(
                f"Expected the user-visible Step Summary to include {marker!r}. "
                f"Observed summary: {_preview_text(step_summary)}"
            )

    if audit.release is None:
        failures.append("Expected the workflow to publish a release after metadata capture succeeded.")
    else:
        if not audit.release.html_url:
            failures.append("Expected the workflow to publish a discoverable GitHub release URL.")
        if not audit.release.body.strip():
            failures.append("Expected the published release body to contain release notes.")

    assert not failures, "\n\n".join(failures)
