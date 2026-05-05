from __future__ import annotations

import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.github_actions_release_client_factory import (  # noqa: E402
    create_github_actions_release_client,
)
from testing.components.factories.deprecated_workflow_run_audit_service_factory import (  # noqa: E402
    create_deprecated_workflow_run_audit_service,
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
FORBIDDEN_STRINGS = tuple(
    " ".join(str(value).lower().split()) for value in CONFIG["forbidden_strings"]
)
REQUIRED_NOTICE_MARKERS = tuple(
    " ".join(str(value).lower().split()) for value in CONFIG["required_notice_markers"]
)


def build_github_client() -> GitHubActionsReleaseClient:
    return create_github_actions_release_client(
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )


def _timestamped_standalone_release_tag(now: datetime | None = None) -> str:
    current = now or datetime.now(timezone.utc)
    return f"v{current.year}.{current.month:02d}{current.day:02d}.{current.hour:02d}{current.minute:02d}{current.second:02d}-standalone"

def _latest_stable_release_tag(client: GitHubActionsReleaseClient) -> str:
    stable_tag_pattern = re.compile(r"^v\d+\.\d+\.\d+$")
    for release in client.list_releases(per_page=20):
        tag_name = str(release.get("tag_name", ""))
        if stable_tag_pattern.match(tag_name):
            return tag_name
    raise AssertionError("Expected at least one stable vX.Y.Z release to exist for the standalone workflow.")


def _build_service(
    *,
    workflow_file: str,
    workflow_name: str,
    release_job_name: str,
    require_step_summary: bool,
    release_tag: str,
    dispatch_inputs: dict[str, object] | None = None,
    reuse_existing_release: bool = False,
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
        required_notice_markers=REQUIRED_NOTICE_MARKERS,
        forbidden_strings=FORBIDDEN_STRINGS,
        require_step_summary=require_step_summary,
        dispatch_inputs=dispatch_inputs,
        reuse_existing_release=reuse_existing_release,
    )


def _assert_successful_live_audit(audit: DeprecatedWorkflowRunAudit) -> None:
    assert audit.workflow_run is not None
    assert audit.release_job is not None
    assert audit.release is not None


def test_dmc_1003_live_deprecated_workflow_outputs_remove_installer_scripts_and_public_header() -> None:
    client = build_github_client()
    standalone_release_tag = _timestamped_standalone_release_tag()
    fatjar_release_tag = _latest_stable_release_tag(client)

    standalone_service = _build_service(
        workflow_file=str(CONFIG["standalone_workflow_file"]),
        workflow_name=str(CONFIG["standalone_workflow_name"]),
        release_job_name=str(CONFIG["standalone_release_job_name"]),
        require_step_summary=str(CONFIG["standalone_require_step_summary"]).lower() == "true",
        release_tag=standalone_release_tag,
        dispatch_inputs={
            "flutter_release_tag": "latest",
            "release_tag": standalone_release_tag,
            "fatjar_release_tag": fatjar_release_tag,
            "prerelease": True,
        },
    )
    standalone_audit = standalone_service.audit()

    standalone_auto_service = _build_service(
        workflow_file=str(CONFIG["standalone_auto_workflow_file"]),
        workflow_name=str(CONFIG["standalone_auto_workflow_name"]),
        release_job_name=str(CONFIG["standalone_auto_release_job_name"]),
        require_step_summary=str(CONFIG["standalone_auto_require_step_summary"]).lower() == "true",
        release_tag="",
        reuse_existing_release=str(CONFIG["standalone_auto_reuse_existing_release"]).lower()
        == "true",
    )
    standalone_auto_audit = standalone_auto_service.audit()

    assert not standalone_audit.failures, standalone_service.format_failures(standalone_audit.failures)
    assert not standalone_auto_audit.failures, standalone_auto_service.format_failures(
        standalone_auto_audit.failures
    )
    _assert_successful_live_audit(standalone_audit)
    _assert_successful_live_audit(standalone_auto_audit)
