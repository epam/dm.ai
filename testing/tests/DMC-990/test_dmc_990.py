from __future__ import annotations

import json
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.repository_governance_validation_service_factory import (  # noqa: E402
    create_repository_governance_validation_service,
)
from testing.components.services.repository_governance_validation_service import (  # noqa: E402
    RepositoryGovernanceValidationService,
)
from testing.core.utils.repo_sandbox import RepoSandbox  # noqa: E402
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
TOPIC_LIMIT = int(str(CONFIG["topic_limit"]))


def build_service(repository_root: Path = REPOSITORY_ROOT) -> RepositoryGovernanceValidationService:
    return create_repository_governance_validation_service(
        repository_root=repository_root,
        topic_limit=TOPIC_LIMIT,
    )


def assert_validator_failed(
    service: RepositoryGovernanceValidationService,
    audit,
    *expected_markers: str,
) -> None:
    assert audit.validator_result is not None
    assert audit.validator_result.returncode != 0, service.format_failures(audit)
    assert any(
        failure.step == 1
        and failure.summary == "Repository governance validation flow did not complete successfully."
        for failure in audit.failures
    ), service.format_failures(audit)

    validator_output = audit.validator_result.combined_output
    assert "GitHubRepositoryDiscoverabilityTest" in validator_output, service.format_failures(audit)
    for expected_marker in expected_markers:
        assert expected_marker in validator_output, service.format_failures(audit)


def test_dmc_990_repository_governance_validation_confirms_metadata_integrity() -> None:
    service = build_service()

    audit = service.audit()

    assert audit.validator_result is not None
    assert audit.validator_result.returncode == 0, service.format_failures(audit)
    assert not audit.failures, service.format_failures(audit)
    assert audit.topic_count <= TOPIC_LIMIT
    assert audit.social_preview_fields == ("direction", "valueLine", "styleRules")


def test_dmc_990_reports_missing_metadata_source_and_broken_playbook_reference() -> None:
    sandbox = RepoSandbox(REPOSITORY_ROOT)
    try:
        metadata_path = sandbox.path(
            "dmtools-core/src/main/resources/github-repository-discoverability.json"
        )
        metadata_path.unlink()

        playbook_path = sandbox.path(
            "dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"
        )
        playbook_path.write_text(
            playbook_path.read_text(encoding="utf-8")
            + "\n- `docs/missing-discoverability-note.md`\n",
            encoding="utf-8",
        )

        service = build_service(sandbox.workspace)

        audit = service.audit()

        assert_validator_failed(
            service,
            audit,
            "GitHub repository discoverability resource is missing: github-repository-discoverability.json",
        )

        assert any(
            failure.step == 2 and "metadata source is missing" in failure.summary.lower()
            for failure in audit.failures
        ), service.format_failures(audit)
        assert any(
            failure.step == 4 and "docs/missing-discoverability-note.md" in failure.actual
            for failure in audit.failures
        ), service.format_failures(audit)
    finally:
        sandbox.cleanup()


def test_dmc_990_reports_topic_count_over_github_limit() -> None:
    sandbox = RepoSandbox(REPOSITORY_ROOT)
    try:
        metadata_path = sandbox.path(
            "dmtools-core/src/main/resources/github-repository-discoverability.json"
        )
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        metadata["topics"] = [f"topic-{index:02d}" for index in range(TOPIC_LIMIT + 1)]
        metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")

        service = build_service(sandbox.workspace)

        audit = service.audit()

        assert_validator_failed(
            service,
            audit,
            "array lengths differed",
            f"actual.length={TOPIC_LIMIT + 1}",
        )

        assert any(
            failure.step == 3 and "exceed github's limit" in failure.summary.lower()
            for failure in audit.failures
        ), service.format_failures(audit)
        assert audit.topic_count == TOPIC_LIMIT + 1
    finally:
        sandbox.cleanup()
