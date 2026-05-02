from pathlib import Path
import os

from testing.components.services.documentation_publication_gate_service import (
    DocumentationPublicationGateService,
    GitHubRestClient,
)
from testing.core.utils.ticket_config_loader import load_ticket_config


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")


class FakeGitHubClient:
    def __init__(
        self,
        pull_requests: list[dict],
        files_by_pr: dict[int, list[dict]],
        reviews_by_pr: dict[int, list[dict]],
        comments_by_pr: dict[int, list[dict]],
        checks_by_sha: dict[str, list[dict]],
        workflow_runs_by_sha: dict[str, list[dict]] | None = None,
        jobs_by_run_id: dict[int, list[dict]] | None = None,
        logs_by_job_id: dict[int, str] | None = None,
    ) -> None:
        self._pull_requests = pull_requests
        self._files_by_pr = files_by_pr
        self._reviews_by_pr = reviews_by_pr
        self._comments_by_pr = comments_by_pr
        self._checks_by_sha = checks_by_sha
        self._workflow_runs_by_sha = workflow_runs_by_sha or {}
        self._jobs_by_run_id = jobs_by_run_id or {}
        self._logs_by_job_id = logs_by_job_id or {}

    def list_recent_pull_requests(self, limit: int = 20) -> list[dict]:
        return self._pull_requests[:limit]

    def pull_request(self, number: int) -> dict:
        for pull_request in self._pull_requests:
            if int(pull_request["number"]) == number:
                return pull_request
        raise KeyError(number)

    def pull_request_files(self, number: int) -> list[dict]:
        return self._files_by_pr[number]

    def pull_request_reviews(self, number: int) -> list[dict]:
        return self._reviews_by_pr.get(number, [])

    def pull_request_issue_comments(self, number: int) -> list[dict]:
        return self._comments_by_pr.get(number, [])

    def commit_check_runs(self, commit_sha: str) -> list[dict]:
        return self._checks_by_sha.get(commit_sha, [])

    def workflow_runs_for_head_sha(self, head_sha: str) -> list[dict]:
        return self._workflow_runs_by_sha.get(head_sha, [])

    def workflow_jobs(self, run_id: int) -> list[dict]:
        return self._jobs_by_run_id.get(run_id, [])

    def workflow_job_logs(self, job_id: int) -> str:
        return self._logs_by_job_id.get(job_id, "")


def test_dmc_918_service_uses_configured_target_pull_request(tmp_path: Path) -> None:
    ticket_dir = tmp_path / "input" / "DMC-918"
    ticket_dir.mkdir(parents=True)
    ticket_dir.joinpath("comments.md").write_text(
        (
            "Duplicate-check completed.\n"
            "JQL: project = DMC AND text ~ \"publication gates\"\n"
            "Repo search: repo:epam/dm.ai \"Duplicate-check: completed\"\n"
        ),
        encoding="utf-8",
    )

    fake_client = FakeGitHubClient(
        pull_requests=[
            {
                "number": 20,
                "title": "Latest test-only PR",
                "body": "Duplicate-check: completed — see ticket comment",
                "html_url": "https://example.com/pr/20",
                "merged_at": "2026-05-02T01:13:46Z",
                "head": {"sha": "sha-test"},
                "user": {"login": "ai-teammate"},
            },
            {
                "number": 19,
                "title": "Latest documentation PR",
                "body": "Duplicate-check: completed — see ticket comment",
                "html_url": "https://example.com/pr/19",
                "merged_at": "2026-05-02T00:08:51Z",
                "head": {"sha": "sha-docs"},
                "user": {"login": "ai-teammate"},
            },
        ],
        files_by_pr={
            20: [{"filename": "testing/tests/DMC-999/test_ticket.py"}],
            19: [{"filename": "dmtools-ai-docs/README.md"}],
        },
        reviews_by_pr={
            19: [
                {
                    "user": {"login": "core-maintainer"},
                    "state": "APPROVED",
                    "author_association": "MEMBER",
                    "body": "Looks good.",
                }
            ]
        },
        comments_by_pr={
            19: [
                {
                    "user": {"login": "docs-reviewer"},
                    "author_association": "CONTRIBUTOR",
                    "body": "Technical writer sign-off: approved.",
                }
            ]
        },
        checks_by_sha={
            "sha-docs": [
                {
                    "name": "Link Validation",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/check/link",
                },
                {
                    "name": "Documentation Smoke",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/check/smoke",
                },
            ]
        },
        workflow_runs_by_sha={
            "sha-docs": [
                {
                    "id": 501,
                    "name": "Documentation checks",
                    "status": "completed",
                    "conclusion": "success",
                }
            ]
        },
        jobs_by_run_id={
            501: [
                {
                    "id": 801,
                    "name": "Docs verification",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/jobs/801",
                }
            ]
        },
        logs_by_job_id={
            801: (
                "Duplicate-check completed. "
                "Link validation passed for all docs links. "
                "Documentation smoke check passed."
            )
        },
    )

    service = DocumentationPublicationGateService(
        tmp_path,
        "DMC-918",
        github_client=fake_client,
        target_pull_request_number=19,
        technical_writer_logins={"docs-reviewer"},
    )

    audit = service.audit()

    assert audit.pull_request is not None
    assert audit.pull_request.number == 19
    assert audit.validation_failures == ()


def test_dmc_918_service_reports_complete_publication_gate_evidence(tmp_path: Path) -> None:
    ticket_dir = tmp_path / "input" / "DMC-918"
    ticket_dir.mkdir(parents=True)
    ticket_dir.joinpath("comments.md").write_text(
        (
            "Duplicate-check entry\n"
            "JQL: project = DMC AND summary ~ \"publication gates\"\n"
            "Repo search: repo:epam/dm.ai \"documentation smoke\"\n"
        ),
        encoding="utf-8",
    )

    fake_client = FakeGitHubClient(
        pull_requests=[
            {
                "number": 18,
                "title": "Documentation gates PR",
                "body": "Duplicate-check: completed — see ticket comment",
                "html_url": "https://example.com/pr/18",
                "merged_at": "2026-05-02T00:08:51Z",
                "head": {"sha": "sha-docs"},
                "user": {"login": "ai-teammate"},
            }
        ],
        files_by_pr={18: [{"filename": "README.md"}]},
        reviews_by_pr={
            18: [
                {
                    "user": {"login": "core-maintainer"},
                    "state": "APPROVED",
                    "author_association": "COLLABORATOR",
                    "body": "Approved.",
                }
            ]
        },
        comments_by_pr={
            18: [
                {
                    "user": {"login": "writer-reviewer"},
                    "author_association": "CONTRIBUTOR",
                    "body": "Approved after reviewing the PR and workflow evidence.",
                }
            ]
        },
        checks_by_sha={
            "sha-docs": [
                {
                    "name": "Markdown Link Validation",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/check/link",
                },
                {
                    "name": "Documentation Smoke Check",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/check/smoke",
                },
            ]
        },
        workflow_runs_by_sha={
            "sha-docs": [
                {
                    "id": 601,
                    "name": "Documentation checks",
                    "status": "completed",
                    "conclusion": "success",
                }
            ]
        },
        jobs_by_run_id={
            601: [
                {
                    "id": 901,
                    "name": "Doc validation",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/jobs/901",
                },
                {
                    "id": 902,
                    "name": "Doc smoke",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/jobs/902",
                },
            ]
        },
        logs_by_job_id={
            901: "Link validation result: success. 0 broken links.",
            902: "Documentation smoke check result: success.",
        },
    )

    service = DocumentationPublicationGateService(
        tmp_path,
        "DMC-918",
        github_client=fake_client,
        technical_writer_logins={"writer-reviewer"},
    )

    audit = service.audit()

    assert audit.validation_failures == ()
    assert audit.ticket_comment_preview.startswith("Duplicate-check entry")
    assert [check.name for check in audit.successful_checks] == [
        "Doc validation",
        "Doc smoke",
    ]


def test_dmc_918_configured_technical_writer_requires_explicit_signoff(tmp_path: Path) -> None:
    ticket_dir = tmp_path / "input" / "DMC-918"
    ticket_dir.mkdir(parents=True)
    ticket_dir.joinpath("comments.md").write_text(
        (
            "Duplicate-check entry\n"
            "JQL: project = DMC AND summary ~ \"publication gates\"\n"
            "Repo search: repo:epam/dm.ai \"documentation smoke\"\n"
        ),
        encoding="utf-8",
    )

    fake_client = FakeGitHubClient(
        pull_requests=[
            {
                "number": 18,
                "title": "Documentation gates PR",
                "body": "Duplicate-check: completed — see ticket comment",
                "html_url": "https://example.com/pr/18",
                "merged_at": "2026-05-02T00:08:51Z",
                "head": {"sha": "sha-docs"},
                "user": {"login": "ai-teammate"},
            }
        ],
        files_by_pr={18: [{"filename": "README.md"}]},
        reviews_by_pr={
            18: [
                {
                    "user": {"login": "core-maintainer"},
                    "state": "APPROVED",
                    "author_association": "COLLABORATOR",
                    "body": "Approved.",
                }
            ]
        },
        comments_by_pr={
            18: [
                {
                    "user": {"login": "writer-reviewer"},
                    "author_association": "CONTRIBUTOR",
                    "body": "I noticed a wording nit in the intro paragraph.",
                }
            ]
        },
        checks_by_sha={"sha-docs": []},
        workflow_runs_by_sha={
            "sha-docs": [
                {
                    "id": 601,
                    "name": "Documentation checks",
                    "status": "completed",
                    "conclusion": "success",
                }
            ]
        },
        jobs_by_run_id={
            601: [
                {
                    "id": 901,
                    "name": "Doc validation",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/jobs/901",
                },
                {
                    "id": 902,
                    "name": "Doc smoke",
                    "status": "completed",
                    "conclusion": "success",
                    "html_url": "https://example.com/jobs/902",
                },
            ]
        },
        logs_by_job_id={
            901: "Link validation result: success. 0 broken links.",
            902: "Documentation smoke check result: success.",
        },
    )

    service = DocumentationPublicationGateService(
        tmp_path,
        "DMC-918",
        github_client=fake_client,
        technical_writer_logins={"writer-reviewer"},
    )

    audit = service.audit()

    assert [failure.step for failure in audit.validation_failures] == [4]
    assert audit.technical_writer_signoffs == ()


def test_dmc_918_documentation_publication_gates_are_recorded() -> None:
    github_client = GitHubRestClient(
        owner="epam",
        repo="dm.ai",
        token=os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"),
    )
    service = DocumentationPublicationGateService(
        REPOSITORY_ROOT,
        "DMC-918",
        github_client=github_client,
        target_pull_request_number=int(str(CONFIG["target_pr_number"])),
        technical_writer_logins=CONFIG.get("technical_writer_logins", []),
    )

    audit = service.audit()

    assert not audit.validation_failures, service.format_failures(audit.validation_failures)
