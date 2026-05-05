from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.beta_release_summary_audit_service_factory import (  # noqa: E402
    create_beta_release_summary_audit_service,
)
from testing.core.interfaces.beta_release_summary_audit_service import (  # noqa: E402
    BetaReleaseSummaryAuditService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


def build_service(repository_root: Path = REPOSITORY_ROOT) -> BetaReleaseSummaryAuditService:
    return create_beta_release_summary_audit_service(
        repository_root=repository_root,
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=str(CONFIG["workflow_name"]),
        release_job_name=str(CONFIG["release_job_name"]),
        dispatch_timeout_seconds=int(str(CONFIG["dispatch_timeout_seconds"])),
        completion_timeout_seconds=int(str(CONFIG["completion_timeout_seconds"])),
        poll_interval_seconds=int(str(CONFIG["poll_interval_seconds"])),
    )


def test_dmc_996_beta_release_step_summary_matches_supported_packaging_model() -> None:
    service = build_service()

    audit = service.audit()

    assert audit.workflow_run is not None
    assert audit.release_job is not None
    assert audit.release is not None
    assert not audit.failures, service.format_failures(audit.failures)
