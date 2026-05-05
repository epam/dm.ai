from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.stable_release_install_paths_service_factory import (  # noqa: E402
    create_stable_release_install_paths_service,
)
from testing.components.services.stable_release_install_paths_service import (  # noqa: E402
    StableReleaseInstallPathsService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


class FakeGitHubClient:
    def __init__(
        self,
        *,
        releases: list[dict],
        workflow_runs: list[dict],
        workflow_jobs: dict[int, list[dict]],
        workflow_logs: dict[int, str],
    ) -> None:
        self._releases = releases
        self._workflow_runs = workflow_runs
        self._workflow_jobs = workflow_jobs
        self._workflow_logs = workflow_logs

    def list_releases(self, limit: int = 20) -> list[dict]:
        return self._releases[:limit]

    def workflow_runs(
        self,
        workflow_file: str,
        *,
        branch: str | None = None,
        event: str | None = None,
        status: str | None = None,
        limit: int = 20,
    ) -> list[dict]:
        del workflow_file, branch, event, status
        return self._workflow_runs[:limit]

    def workflow_jobs(self, run_id: int) -> list[dict]:
        return self._workflow_jobs.get(run_id, [])

    def workflow_job_logs(self, job_id: int) -> str:
        return self._workflow_logs[job_id]


def build_live_service() -> StableReleaseInstallPathsService:
    return create_stable_release_install_paths_service(
        repository_root=REPOSITORY_ROOT,
        owner=str(CONFIG["owner"]),
        repo=str(CONFIG["repo"]),
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_job_name=str(CONFIG["workflow_job_name"]),
        release_limit=int(str(CONFIG["release_limit"])),
        run_limit=int(str(CONFIG["run_limit"])),
        max_run_match_seconds=int(str(CONFIG["max_run_match_seconds"])),
    )


def test_dmc_995_service_accepts_supported_release_asset_paths_only() -> None:
    fake_client = FakeGitHubClient(
        releases=[
            {
                "tag_name": "v1.7.200",
                "draft": False,
                "prerelease": False,
                "published_at": "2026-05-05T10:00:00Z",
                "html_url": "https://github.com/epam/dm.ai/releases/tag/v1.7.200",
                "body": (
                    "### Install DMTools CLI\n"
                    "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash\n"
                    "irm https://github.com/epam/dm.ai/releases/latest/download/install.ps1 | iex\n"
                    "### Install DMTools Agent Skill\n"
                    "curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.200/skill-install.sh | bash\n"
                    "irm https://github.com/epam/dm.ai/releases/download/v1.7.200/skill-install.ps1 | iex\n"
                ),
                "assets": [
                    {"name": "install.sh"},
                    {"name": "install.ps1"},
                    {"name": "skill-install.sh"},
                    {"name": "skill-install.ps1"},
                ],
            }
        ],
        workflow_runs=[
            {
                "id": 501,
                "html_url": "https://github.com/epam/dm.ai/actions/runs/501",
                "status": "completed",
                "conclusion": "success",
                "created_at": "2026-05-05T09:55:00Z",
                "updated_at": "2026-05-05T09:59:59Z",
                "head_branch": "main",
            }
        ],
        workflow_jobs={
            501: [
                {
                    "id": 801,
                    "name": "create-unified-release",
                    "html_url": "https://github.com/epam/dm.ai/actions/runs/501/job/801",
                    "steps": [
                        {"name": "Generate Release Notes"},
                        {"name": "Create Unified Release"},
                        {"name": "Summary"},
                    ],
                }
            ]
        },
        workflow_logs={
            801: (
                "2026-05-05T09:59:00Z Release notes generated with "
                "https://github.com/epam/dm.ai/releases/latest/download/install.sh\n"
                "2026-05-05T09:59:30Z Summary step completed successfully.\n"
            )
        },
    )

    service = StableReleaseInstallPathsService(
        REPOSITORY_ROOT,
        github_client=fake_client,
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_job_name=str(CONFIG["workflow_job_name"]),
        release_limit=1,
        run_limit=1,
        max_run_match_seconds=120,
    )

    audit = service.audit()

    assert audit.failures == ()
    assert audit.release is not None
    assert audit.workflow_run is not None
    assert audit.workflow_job is not None
    assert audit.release_cli_urls == (
        "https://github.com/epam/dm.ai/releases/latest/download/install.sh",
        "https://github.com/epam/dm.ai/releases/latest/download/install.ps1",
    )
    assert audit.release_skill_urls == (
        "https://github.com/epam/dm.ai/releases/download/v1.7.200/skill-install.sh",
        "https://github.com/epam/dm.ai/releases/download/v1.7.200/skill-install.ps1",
    )


def test_dmc_995_service_reports_raw_and_server_references() -> None:
    fake_client = FakeGitHubClient(
        releases=[
            {
                "tag_name": "v1.7.181",
                "draft": False,
                "prerelease": False,
                "published_at": "2026-05-03T14:30:03Z",
                "html_url": "https://github.com/epam/dm.ai/releases/tag/v1.7.181",
                "body": (
                    "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/main/install | bash\n"
                    "curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.181/skill-install.sh | bash\n"
                ),
                "assets": [
                    {"name": "install.sh"},
                    {"name": "skill-install.sh"},
                ],
            }
        ],
        workflow_runs=[
            {
                "id": 25281750669,
                "html_url": "https://github.com/epam/dm.ai/actions/runs/25281750669",
                "status": "completed",
                "conclusion": "success",
                "created_at": "2026-05-03T14:26:15Z",
                "updated_at": "2026-05-03T14:30:06Z",
                "head_branch": "main",
            }
        ],
        workflow_jobs={
            25281750669: [
                {
                    "id": 74120196514,
                    "name": "create-unified-release",
                    "html_url": "https://github.com/epam/dm.ai/actions/runs/25281750669/job/74120196514",
                    "steps": [
                        {"name": "Generate Release Notes"},
                        {"name": "Create Unified Release"},
                        {"name": "Summary"},
                    ],
                }
            ]
        },
        workflow_logs={
            74120196514: (
                "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/v${VERSION}/install.sh | bash\n"
                "# API_MACOS_ARM_SIZE=$(du -h standalone/api-bundles/dmtools-server-api-macos-aarch64.zip | cut -f1)\n"
            )
        },
    )

    service = StableReleaseInstallPathsService(
        REPOSITORY_ROOT,
        github_client=fake_client,
        workflow_file=str(CONFIG["workflow_file"]),
        workflow_job_name=str(CONFIG["workflow_job_name"]),
        release_limit=1,
        run_limit=1,
        max_run_match_seconds=int(str(CONFIG["max_run_match_seconds"])),
    )

    audit = service.audit()

    assert len(audit.failures) == 3
    assert audit.failures[0].step == 2
    assert audit.release_forbidden_lines == (
        "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/main/install | bash",
    )
    assert audit.workflow_log_forbidden_lines == (
        "curl -fsSL https://raw.githubusercontent.com/epam/dm.ai/v${VERSION}/install.sh | bash",
        "# API_MACOS_ARM_SIZE=$(du -h standalone/api-bundles/dmtools-server-api-macos-aarch64.zip | cut -f1)",
    )
    assert "supported cli install path" in audit.failures[0].summary.lower()
    assert "unsupported raw or server paths" in audit.failures[1].summary.lower()
    assert "workflow evidence" in audit.failures[2].summary.lower()


def test_dmc_995_live_stable_release_workflow_uses_only_supported_cli_and_skill_paths() -> None:
    service = build_live_service()

    audit = service.audit()

    assert audit.release is not None, "The GitHub API did not return a stable release to inspect."
    assert audit.workflow_run is not None, "The matching release workflow run could not be located."
    assert audit.workflow_job is not None, "The matching unified release job could not be located."
    assert not audit.failures, service.format_failures(audit.failures)
