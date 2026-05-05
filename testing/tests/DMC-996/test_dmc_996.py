from __future__ import annotations

import re
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
WORKFLOW_PATH = REPOSITORY_ROOT / ".github" / "workflows" / "beta-release.yml"
SUMMARY_RELEASE_NOTES_COMMAND = "cat release_notes.md >> $GITHUB_STEP_SUMMARY"
RELEASE_NOTE_REQUIRED_MARKERS = (
    "pre-release / beta build",
    "latest stable release",
    "DMTools CLI",
    "DMTools Agent Skill",
    "releases/download/",
    "install.sh",
)


def _workflow_text() -> str:
    return WORKFLOW_PATH.read_text(encoding="utf-8")


def _release_notes_block(workflow_text: str) -> str:
    match = re.search(
        r"cat > release_notes\.md << EOF\n(?P<value>.*?)\n\s*EOF",
        workflow_text,
        re.DOTALL,
    )
    assert match is not None, "Expected beta-release workflow to generate release_notes.md via heredoc"
    return match.group("value")


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


def test_dmc_996_beta_release_workflow_summary_reuses_supported_release_notes_copy() -> None:
    workflow_text = _workflow_text()
    release_notes = _release_notes_block(workflow_text)

    assert SUMMARY_RELEASE_NOTES_COMMAND in workflow_text

    normalized_release_notes = " ".join(release_notes.split())
    for marker in RELEASE_NOTE_REQUIRED_MARKERS:
        assert marker in normalized_release_notes
