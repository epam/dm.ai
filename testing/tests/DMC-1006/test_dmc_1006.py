from __future__ import annotations

import os
import re
import sys
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
from testing.core.models.deprecated_workflow_run_audit import (  # noqa: E402
    DeprecatedWorkflowRunAudit,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
TEST_REPORTER_FAILURE_MARKERS = tuple(str(value) for value in CONFIG["test_reporter_failure_markers"])
STABLE_RELEASE_TAG_PATTERN = re.compile(r"^v\d+\.\d+\.\d+$")
RELEASE_TAG_ENV = "DMC_1006_RELEASE_TAG"


def build_github_client() -> GitHubActionsReleaseClient:
    return create_github_actions_release_client(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def release_tag_for_run(now: datetime | None = None) -> str:
    configured = os.environ.get(RELEASE_TAG_ENV, "").strip()
    if configured:
        return configured

    current = now or datetime.now(timezone.utc)
    generated = (
        f"v{current.year}.{current.month:02d}{current.day:02d}."
        f"{current.hour:02d}{current.minute:02d}{current.second:02d}-standalone"
    )
    os.environ[RELEASE_TAG_ENV] = generated
    return generated


def latest_stable_release_tag(client: GitHubActionsReleaseClient) -> str | None:
    for release in client.list_releases(per_page=100):
        tag_name = str(release.get("tag_name", ""))
        if STABLE_RELEASE_TAG_PATTERN.match(tag_name):
            return tag_name
    return None


def build_service(
    *,
    release_tag: str,
    reuse_existing_release: bool = False,
) -> DeprecatedWorkflowRunAuditService:
    dispatch_inputs: dict[str, object] | None = None
    if not reuse_existing_release:
        fatjar_release_tag = latest_stable_release_tag(build_github_client())
        dispatch_inputs = {
            "flutter_release_tag": "latest",
            "release_tag": release_tag,
            "prerelease": True,
        }
        if fatjar_release_tag:
            dispatch_inputs["fatjar_release_tag"] = fatjar_release_tag

    return create_deprecated_workflow_run_audit_service(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=str(CONFIG["workflow_name"]),
        release_job_name=str(CONFIG["release_job_name"]),
        release_tag=release_tag,
        dispatch_timeout_seconds=int(str(CONFIG["dispatch_timeout_seconds"])),
        completion_timeout_seconds=int(str(CONFIG["completion_timeout_seconds"])),
        poll_interval_seconds=int(str(CONFIG["poll_interval_seconds"])),
        required_notice_markers=(),
        forbidden_strings=(),
        require_step_summary=str(CONFIG["require_step_summary"]).lower() == "true",
        dispatch_inputs=dispatch_inputs,
        reuse_existing_release=reuse_existing_release,
    )


def run_live_audit(*, reuse_existing_release: bool = False) -> tuple[DeprecatedWorkflowRunAudit, str]:
    client = build_github_client()
    release_tag = release_tag_for_run()
    service = build_service(
        release_tag=release_tag,
        reuse_existing_release=reuse_existing_release,
    )
    audit = service.audit()
    raw_job_log = ""
    if audit.release_job is not None:
        raw_job_log = client.workflow_job_logs(audit.release_job.job_id)
    return audit, raw_job_log


def assert_no_test_reporter_failure(raw_job_log: str) -> None:
    normalized_log = " ".join(raw_job_log.split())
    for marker in TEST_REPORTER_FAILURE_MARKERS:
        assert marker not in raw_job_log, (
            "The standalone release job still surfaced the historical test-reporter failure "
            f"marker {marker!r}. Recent log excerpt: {normalized_log[-500:] or 'no log output'}"
        )


def test_dmc_1006_live_standalone_release_workflow_completes_without_test_reporter_failure() -> None:
    audit, raw_job_log = run_live_audit()

    service = build_service(release_tag=release_tag_for_run(), reuse_existing_release=True)
    assert audit.workflow_run is not None
    assert audit.release_job is not None
    assert_no_test_reporter_failure(raw_job_log)
    assert not audit.failures, service.format_failures(audit.failures)
    assert audit.release is not None
    assert audit.release_job.step_summary_markdown.strip()
    assert audit.release.body.strip()
