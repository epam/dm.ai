from __future__ import annotations

import re
import sys
from datetime import datetime, timezone
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.beta_release_summary_audit_service_factory import (  # noqa: E402
    create_beta_release_summary_audit_service,
)
from testing.components.services.beta_release_summary_audit_service import (  # noqa: E402
    BetaReleaseSummaryAuditService as BetaReleaseSummaryAuditServiceImpl,
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


class FakeGitHubActionsReleaseClient:
    def __init__(self, workflow_log_text: str, *, tag: str, created_at: str) -> None:
        self._workflow_log_text = workflow_log_text
        self._tag = tag
        self._created_at = created_at
        self._dispatched = False
        self._run = {
            "id": 901,
            "html_url": "https://github.com/epam/dm.ai/actions/runs/901",
            "event": "workflow_dispatch",
            "status": "completed",
            "conclusion": "success",
            "head_branch": "main",
            "head_sha": "abc123def456",
            "created_at": created_at,
            "run_number": 77,
        }
        self._job = {
            "id": 801,
            "name": "create-beta-release",
            "html_url": "https://github.com/epam/dm.ai/actions/runs/901/job/801",
            "status": "completed",
            "conclusion": "success",
        }
        self._release = {
            "tag_name": tag,
            "html_url": f"https://github.com/epam/dm.ai/releases/tag/{tag}",
            "prerelease": True,
            "created_at": created_at,
            "body": _release_notes_block(_workflow_text()),
            "assets": [
                {"name": "dmtools-v1.7.181-all.jar"},
                {"name": "install.sh"},
                {"name": "install.ps1"},
                {"name": "dmtools.sh"},
                {"name": "dmtools-skill-v1.7.181.zip"},
                {"name": "skill-install.sh"},
                {"name": "skill-install.ps1"},
                {"name": "skill-checksums.sha256"},
            ],
        }

    def dispatch_workflow(self, workflow_id: str, *, ref: str) -> None:
        del workflow_id, ref
        self._dispatched = True

    def branch_head_sha(self, branch: str) -> str:
        del branch
        return str(self._run["head_sha"])

    def workflow_runs_for_workflow(
        self,
        workflow_id: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        per_page: int = 20,
    ) -> list[dict]:
        del workflow_id, branch, event, per_page
        return [self._run] if self._dispatched else []

    def workflow_run(self, run_id: int) -> dict:
        assert run_id == self._run["id"]
        return self._run

    def workflow_jobs(self, run_id: int) -> list[dict]:
        assert run_id == self._run["id"]
        return [self._job]

    def workflow_job_logs(self, job_id: int) -> str:
        assert job_id == self._job["id"]
        return self._workflow_log_text

    def release_by_tag(self, tag: str) -> dict:
        assert tag == self._tag
        return self._release

    def list_releases(self, per_page: int = 20) -> list[dict]:
        del per_page
        return [self._release]


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


def test_dmc_996_service_reads_release_notes_backed_step_summary_from_logs() -> None:
    release_notes = _release_notes_block(_workflow_text())
    tag = "v1.7.181-beta.43.1"
    created_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    workflow_log_text = (
        "##[group]Run VERSION=\"1.7.181-beta.43.1\"\n"
        "SHORT_SHA=\"abc123de\"\n"
        "cat > release_notes.md << EOF\n"
        f"{release_notes}\n"
        "EOF\n"
        "Release notes generated\n"
        f"Release URL: https://github.com/epam/dm.ai/releases/tag/{tag}\n"
        "##[group]Run cat release_notes.md >> $GITHUB_STEP_SUMMARY\n"
        "2026-05-05T09:59:36Z cat release_notes.md >> $GITHUB_STEP_SUMMARY\n"
        "2026-05-05T09:59:37Z ##[endgroup]\n"
    )
    service = BetaReleaseSummaryAuditServiceImpl(
        github_client=FakeGitHubActionsReleaseClient(
            workflow_log_text,
            tag=tag,
            created_at=created_at,
        ),
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_ref=str(CONFIG["workflow_ref"]),
        workflow_name=str(CONFIG["workflow_name"]),
        release_job_name=str(CONFIG["release_job_name"]),
        dispatch_timeout_seconds=1,
        completion_timeout_seconds=1,
        poll_interval_seconds=1,
    )

    audit = service.audit()

    assert audit.release_job is not None
    assert not audit.failures, service.format_failures(audit.failures)
    normalized_summary = " ".join(audit.release_job.step_summary_markdown.split())
    normalized_release_notes = " ".join(release_notes.split())
    assert normalized_summary == normalized_release_notes
