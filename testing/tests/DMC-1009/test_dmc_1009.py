from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.deprecated_workflow_run_audit_service_factory import (  # noqa: E402
    create_deprecated_workflow_run_audit_service,
)
from testing.components.factories.github_actions_release_client_factory import (  # noqa: E402
    create_github_actions_release_client,
)
from testing.core.interfaces.deprecated_workflow_run_audit_service import (  # noqa: E402
    DeprecatedWorkflowRunAuditService,
)
from testing.core.interfaces.github_actions_release_client import (  # noqa: E402
    GitHubActionsReleaseClient,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
STABLE_RELEASE_TAG_PATTERN = re.compile(r"^v\d+\.\d+\.\d+$")


@dataclass(frozen=True)
class WorkflowScenario:
    workflow_file: str
    workflow_name: str
    release_job_name: str
    require_step_summary: bool
    dispatch_inputs: dict[str, object] | None
    release_tag: str


@dataclass(frozen=True)
class WorkflowValidationObservation:
    workflow_file: str
    workflow_name: str
    workflow_run_url: str
    workflow_conclusion: str
    release_job_url: str
    release_job_conclusion: str
    release_url: str
    release_tag: str
    step_conclusions: dict[str, str]
    step_summary_excerpt: str
    release_body_excerpt: str
    dmtools_core_test_command_present: bool
    observed_failure_markers: list[str]
    missing_failure_markers: list[str]
    failures: list[str]


def _config_list(key: str) -> tuple[str, ...]:
    values = CONFIG.get(key, [])
    if not isinstance(values, list):
        raise TypeError(f"Expected {key!r} to be a list in {TEST_DIRECTORY / 'config.yaml'}.")
    return tuple(str(value) for value in values)


REQUIRED_SUCCESSFUL_STEPS = _config_list("required_successful_steps")
FAILURE_MARKERS = _config_list("failure_markers")
USER_VISIBLE_RELEASE_MARKERS = tuple(
    " ".join(marker.lower().split()) for marker in _config_list("user_visible_release_markers")
)
USER_VISIBLE_SUMMARY_MARKERS = tuple(
    " ".join(marker.lower().split()) for marker in _config_list("user_visible_summary_markers")
)


def build_github_client() -> GitHubActionsReleaseClient:
    return create_github_actions_release_client(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def _timestamped_release_tag(now: datetime | None = None) -> str:
    current = now or datetime.now(timezone.utc)
    return (
        f"v{current.year}.{current.month:02d}{current.day:02d}."
        f"{current.hour:02d}{current.minute:02d}{current.second:02d}-standalone"
    )


def _latest_stable_release_tag(client: GitHubActionsReleaseClient) -> str:
    for release in client.list_releases(per_page=20):
        tag_name = str(release.get("tag_name", ""))
        if STABLE_RELEASE_TAG_PATTERN.match(tag_name):
            return tag_name
    raise AssertionError("Expected at least one stable vX.Y.Z release to exist for standalone dispatch.")


def _build_service(
    *,
    workflow_file: str,
    workflow_name: str,
    release_job_name: str,
    release_tag: str,
    require_step_summary: bool,
    dispatch_inputs: dict[str, object] | None = None,
) -> DeprecatedWorkflowRunAuditService:
    return create_deprecated_workflow_run_audit_service(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=workflow_file,
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=workflow_name,
        release_job_name=release_job_name,
        release_tag=release_tag,
        dispatch_timeout_seconds=int(str(CONFIG["dispatch_timeout_seconds"])),
        completion_timeout_seconds=int(str(CONFIG["completion_timeout_seconds"])),
        poll_interval_seconds=int(str(CONFIG["poll_interval_seconds"])),
        required_notice_markers=(),
        forbidden_strings=(),
        require_step_summary=require_step_summary,
        dispatch_inputs=dispatch_inputs,
    )


def _normalize_visible_text(value: str) -> str:
    return " ".join(value.lower().split())


def _preview_text(value: str, *, limit: int = 300) -> str:
    collapsed = " ".join(value.split())
    if len(collapsed) <= limit:
        return collapsed
    return f"{collapsed[:limit]}..."


def _scenario_definitions(client: GitHubActionsReleaseClient) -> tuple[WorkflowScenario, ...]:
    standalone_release_tag = _timestamped_release_tag()
    fatjar_release_tag = _latest_stable_release_tag(client)
    return (
        WorkflowScenario(
            workflow_file=str(CONFIG["standalone_workflow_file"]),
            workflow_name=str(CONFIG["standalone_workflow_name"]),
            release_job_name=str(CONFIG["standalone_release_job_name"]),
            require_step_summary=str(CONFIG["standalone_require_step_summary"]).lower() == "true",
            dispatch_inputs={
                "flutter_release_tag": "latest",
                "release_tag": standalone_release_tag,
                "fatjar_release_tag": fatjar_release_tag,
                "prerelease": True,
            },
            release_tag=standalone_release_tag,
        ),
        WorkflowScenario(
            workflow_file=str(CONFIG["standalone_auto_workflow_file"]),
            workflow_name=str(CONFIG["standalone_auto_workflow_name"]),
            release_job_name=str(CONFIG["standalone_auto_release_job_name"]),
            require_step_summary=str(CONFIG["standalone_auto_require_step_summary"]).lower() == "true",
            dispatch_inputs=None,
            release_tag="",
        ),
    )


def _validate_workflow(
    scenario: WorkflowScenario,
) -> WorkflowValidationObservation:
    service = _build_service(
        workflow_file=scenario.workflow_file,
        workflow_name=scenario.workflow_name,
        release_job_name=scenario.release_job_name,
        release_tag=scenario.release_tag,
        require_step_summary=scenario.require_step_summary,
        dispatch_inputs=scenario.dispatch_inputs,
    )
    audit = service.audit()

    failures: list[str] = []
    if audit.failures:
        failures.append(service.format_failures(audit.failures))

    workflow_run_url = audit.workflow_run.html_url if audit.workflow_run is not None else ""
    workflow_conclusion = audit.workflow_run.conclusion if audit.workflow_run is not None else ""
    release_job_url = audit.release_job.html_url if audit.release_job is not None else ""
    release_job_conclusion = audit.release_job.conclusion if audit.release_job is not None else ""
    release_url = audit.release.html_url if audit.release is not None else ""
    release_tag = audit.release.tag_name if audit.release is not None else scenario.release_tag
    release_body = audit.release.body if audit.release is not None else ""
    step_summary = audit.release_job.step_summary_markdown if audit.release_job is not None else ""
    raw_job_log = audit.release_job.raw_log_text if audit.release_job is not None else ""
    step_conclusions = dict(audit.release_job.step_conclusions) if audit.release_job is not None else {}

    for step_name in REQUIRED_SUCCESSFUL_STEPS:
        conclusion = step_conclusions.get(step_name)
        if conclusion is None:
            failures.append(
                f"Expected step {step_name!r} in job {scenario.release_job_name!r}, but it was missing."
            )
            continue
        if conclusion != "success":
            failures.append(
                f"Expected step {step_name!r} to conclude with 'success', got {conclusion!r}."
            )

    dmtools_core_test_command_present = ":dmtools-core:test" in raw_job_log
    if not dmtools_core_test_command_present:
        failures.append(
            "Expected the live job log to show the deprecated compatibility build invoking ':dmtools-core:test'."
        )

    observed_failure_markers = [marker for marker in FAILURE_MARKERS if marker in raw_job_log]
    missing_failure_markers = [marker for marker in FAILURE_MARKERS if marker not in raw_job_log]
    if not observed_failure_markers:
        failures.append(
            "Expected the live job log to include at least one configured dmtools-core test failure "
            f"marker {list(FAILURE_MARKERS)!r}. Observed excerpt: {_preview_text(raw_job_log, limit=500)}"
        )

    normalized_release_body = _normalize_visible_text(release_body)
    for marker in USER_VISIBLE_RELEASE_MARKERS:
        if marker not in normalized_release_body:
            failures.append(
                f"Expected visible release body text to include {marker!r}. "
                f"Observed: {_preview_text(release_body)}"
            )

    normalized_step_summary = _normalize_visible_text(step_summary)
    for marker in USER_VISIBLE_SUMMARY_MARKERS:
        if marker not in normalized_step_summary:
            failures.append(
                f"Expected visible step summary text to include {marker!r}. "
                f"Observed: {_preview_text(step_summary)}"
            )

    if scenario.require_step_summary and not step_summary.strip():
        failures.append("Expected a non-empty GITHUB_STEP_SUMMARY, but no summary content was captured.")

    if not release_body.strip():
        failures.append("Expected a non-empty release body, but the published release body was empty.")

    return WorkflowValidationObservation(
        workflow_file=scenario.workflow_file,
        workflow_name=scenario.workflow_name,
        workflow_run_url=workflow_run_url,
        workflow_conclusion=workflow_conclusion,
        release_job_url=release_job_url,
        release_job_conclusion=release_job_conclusion,
        release_url=release_url,
        release_tag=release_tag,
        step_conclusions=step_conclusions,
        step_summary_excerpt=_preview_text(step_summary),
        release_body_excerpt=_preview_text(release_body),
        dmtools_core_test_command_present=dmtools_core_test_command_present,
        observed_failure_markers=observed_failure_markers,
        missing_failure_markers=missing_failure_markers,
        failures=failures,
    )


def run_live_validation() -> list[WorkflowValidationObservation]:
    client = build_github_client()
    return [_validate_workflow(scenario) for scenario in _scenario_definitions(client)]


def _format_observation_failures(observation: WorkflowValidationObservation) -> list[str]:
    return [
        f"{observation.workflow_file}: {failure}"
        for failure in observation.failures
    ]


def test_dmc_1009_live_deprecated_workflows_publish_outputs_after_compatibility_build_step() -> None:
    observations = run_live_validation()

    print(
        "DMC1009_OBSERVATIONS="
        + json.dumps([asdict(observation) for observation in observations], sort_keys=True)
    )

    failure_messages: list[str] = []
    for observation in observations:
        failure_messages.extend(_format_observation_failures(observation))

    assert not failure_messages, "\n\n".join(failure_messages)
