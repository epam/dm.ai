from __future__ import annotations

import os
import sys
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
from testing.core.models.deprecated_workflow_run_audit import (  # noqa: E402
    DeprecatedWorkflowRunAudit,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def _normalized_config_list(key: str) -> tuple[str, ...]:
    values = CONFIG.get(key, [])
    if not isinstance(values, list):
        raise TypeError(f"Expected {key!r} to be a list in {TEST_DIRECTORY / 'config.yaml'}.")
    return tuple(" ".join(str(value).lower().split()) for value in values)


FORBIDDEN_LOG_FRAGMENTS = tuple(
    _normalized_config_list("forbidden_log_fragments")
)


def build_github_client() -> GitHubActionsReleaseClient:
    return create_github_actions_release_client(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def build_service() -> DeprecatedWorkflowRunAuditService:
    return create_deprecated_workflow_run_audit_service(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=str(CONFIG["standalone_auto_workflow_file"]),
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=str(CONFIG["standalone_auto_workflow_name"]),
        release_job_name=str(CONFIG["standalone_auto_release_job_name"]),
        release_tag="",
        dispatch_timeout_seconds=int(str(CONFIG["dispatch_timeout_seconds"])),
        completion_timeout_seconds=int(str(CONFIG["completion_timeout_seconds"])),
        poll_interval_seconds=int(str(CONFIG["poll_interval_seconds"])),
        required_notice_markers=_normalized_config_list("required_notice_markers"),
        forbidden_strings=_normalized_config_list("forbidden_strings"),
        require_step_summary=str(CONFIG["standalone_auto_require_step_summary"]).lower() == "true",
    )


def _assert_successful_audit(audit: DeprecatedWorkflowRunAudit) -> None:
    assert audit.workflow_run is not None
    assert audit.release_job is not None
    assert audit.release is not None


def _normalize_visible_text(value: str) -> str:
    return " ".join(value.lower().split())


def test_dmc_1007_live_auto_standalone_workflow_completes_without_removed_project_gradle_errors() -> None:
    client = build_github_client()
    service = build_service()

    audit = service.audit()

    assert not audit.failures, service.format_failures(audit.failures)
    _assert_successful_audit(audit)

    job_log_text = client.workflow_job_logs(audit.release_job.job_id)
    normalized_job_log = _normalize_visible_text(job_log_text)

    for forbidden_fragment in FORBIDDEN_LOG_FRAGMENTS:
        assert forbidden_fragment not in normalized_job_log, (
            "Expected the completed auto-standalone-release job log to exclude the removed-project "
            f"Gradle failure {forbidden_fragment!r}, but it was present in {audit.release_job.html_url}."
        )

    assert audit.workflow_run.conclusion == "success"
    assert audit.release_job.conclusion == "success"
    assert audit.release.html_url
    assert audit.release.body.strip()
    assert audit.release_job.step_summary_markdown.strip()
