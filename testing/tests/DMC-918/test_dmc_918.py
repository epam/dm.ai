from pathlib import Path

from testing.components.services.documentation_publication_gate_service import (
    DocumentationPublicationGateService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


class FakeGitHubClient:
    def __init__(
        self,
        pull_requests: list[dict],
        files_by_pr: dict[int, list[dict]],
        reviews_by_pr: dict[int, list[dict]],
        comments_by_pr: dict[int, list[dict]],
        checks_by_sha: dict[str, list[dict]],
    ) -> None:
        self._pull_requests = pull_requests
        self._files_by_pr = files_by_pr
        self._reviews_by_pr = reviews_by_pr
        self._comments_by_pr = comments_by_pr
        self._checks_by_sha = checks_by_sha

    def list_recent_pull_requests(self, limit: int = 20) -> list[dict]:
        return self._pull_requests[:limit]

    def pull_request_files(self, number: int) -> list[dict]:
        return self._files_by_pr[number]

    def pull_request_reviews(self, number: int) -> list[dict]:
        return self._reviews_by_pr.get(number, [])

    def pull_request_issue_comments(self, number: int) -> list[dict]:
        return self._comments_by_pr.get(number, [])

    def commit_check_runs(self, commit_sha: str) -> list[dict]:
        return self._checks_by_sha.get(commit_sha, [])


def test_dmc_918_service_prefers_latest_merged_documentation_pull_request(tmp_path: Path) -> None:
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
    )

    service = DocumentationPublicationGateService(
        tmp_path,
        "DMC-918",
        github_client=fake_client,
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
                    "body": "Technical writer sign-off: approved after reading the PR description and checks.",
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
    )

    service = DocumentationPublicationGateService(
        tmp_path,
        "DMC-918",
        github_client=fake_client,
    )

    audit = service.audit()

    assert audit.validation_failures == ()
    assert audit.ticket_comment_preview.startswith("Duplicate-check entry")
    assert [check.name for check in audit.successful_checks] == [
        "Markdown Link Validation",
        "Documentation Smoke Check",
    ]


def test_dmc_918_documentation_publication_gates_are_recorded() -> None:
    service = DocumentationPublicationGateService(REPOSITORY_ROOT, "DMC-918")

    audit = service.audit()

    assert not audit.validation_failures, service.format_failures(audit.validation_failures)
