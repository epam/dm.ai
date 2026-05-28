from __future__ import annotations

import json
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.report_generator_rate_limit_service_factory import (  # noqa: E402
    create_report_generator_rate_limit_service,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
COMMITS_PATH = "/repos/test-workspace/test-repo/commits"


def build_live_service():
    return create_report_generator_rate_limit_service(
        REPOSITORY_ROOT,
        workspace=str(CONFIG["workspace"]),
        repository=str(CONFIG["repository"]),
        branch=str(CONFIG["branch"]),
        start_date=str(CONFIG["start_date"]),
        end_date=str(CONFIG["end_date"]),
        retry_after_seconds=int(str(CONFIG["retry_after_seconds"])),
        minimum_observed_retry_seconds=float(str(CONFIG["minimum_observed_retry_seconds"])),
    )


def is_commits_page_one_call(record) -> bool:
    return record.path == COMMITS_PATH and record.query.get("page") == ("1",)


def test_dmc_1032_report_generator_preserves_partial_progress_after_github_rate_limit() -> None:
    audit = build_live_service().audit()

    print("DMC1032_OBSERVATION=" + json.dumps(audit.to_summary(), sort_keys=True))

    assert audit.returncode == 0, (
        "ReportGenerator should complete successfully after retrying the throttled commits metric.\n"
        f"stdout:\n{audit.stdout}\n\nstderr:\n{audit.stderr}"
    )
    assert audit.observed_retry_seconds >= audit.minimum_observed_retry_seconds, (
        "ReportGenerator retried the throttled commits request too early.\n"
        f"Observed retry delay: {audit.observed_retry_seconds:.3f}s\n"
        f"Expected at least: {audit.minimum_observed_retry_seconds:.3f}s\n"
        f"Requests: {audit.request_records!r}"
    )

    assert audit.report_name == "DMC-1032 Partial Progress Report"
    assert audit.report_metrics["PR Approvals"]["count"] == 1
    assert audit.report_metrics["PR Approvals"]["totalWeight"] == 1.0
    assert audit.report_metrics["Commits"]["count"] == 1
    assert audit.report_metrics["Commits"]["totalWeight"] == 1.0

    request_records = list(audit.request_records)
    pull_requests_calls = [
        record
        for record in request_records
        if record.path == "/repos/test-workspace/test-repo/pulls"
    ]
    commits_page_one_calls = [
        record
        for record in request_records
        if is_commits_page_one_call(record)
    ]
    assert len(pull_requests_calls) == 1, (
        "The earlier pull request metric should not be recollected after the later commits rate limit.\n"
        f"Recorded requests: {audit.request_records!r}"
    )
    assert [record.status for record in commits_page_one_calls] == [429, 200], (
        "The commits metric should fail once with a rate limit and then succeed on retry.\n"
        f"Recorded requests: {audit.request_records!r}"
    )

    first_commits_attempt_index = next(
        index for index, record in enumerate(request_records) if is_commits_page_one_call(record)
    )
    assert first_commits_attempt_index > 0, (
        "Expected the earlier pull request metric to finish collecting before the later commits source started.\n"
        f"Recorded requests: {audit.request_records!r}"
    )
    assert all(
        record.path != COMMITS_PATH
        for record in request_records[:first_commits_attempt_index]
    ), (
        "The commits source should not start before the earlier pull request metric has completed.\n"
        f"Recorded requests: {audit.request_records!r}"
    )
    assert not [
        record
        for record in request_records[first_commits_attempt_index + 1 :]
        if record.path != COMMITS_PATH
    ], (
        "Previously collected pull request data should remain untouched while the later commits source waits and retries.\n"
        f"Recorded requests: {audit.request_records!r}"
    )

    assert audit.html_contains_report_name, audit.html_excerpt
    assert audit.html_contains_pr_approvals, audit.html_excerpt
    assert audit.html_contains_commits, audit.html_excerpt
