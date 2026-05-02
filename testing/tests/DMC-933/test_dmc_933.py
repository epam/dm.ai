from __future__ import annotations

import shlex
import site
import sys
from collections.abc import Iterable
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.factories.documentation_consistency_checker_factory import (  # noqa: E402
    create_documentation_consistency_checker,
)
from testing.core.interfaces.documentation_consistency_checker import (  # noqa: E402
    DocumentationConsistencyChecker,
)
from testing.core.models.job_reference import JobReference  # noqa: E402
from testing.core.utils.repo_sandbox import RepoSandbox  # noqa: E402


CANONICAL_DRIFT_COMMAND = (
    f"PYTHONPATH={shlex.quote(site.getusersitepackages())}${{PYTHONPATH:+:$PYTHONPATH}} "
    "python3 -m pytest testing/tests/DMC-898/test_dmc_898.py -q"
)
TEAMMATE_CONFIGS_PATH = "dmtools-ai-docs/references/agents/teammate-configs.md"
CLI_INTEGRATION_DOCUMENT = "cli-integration.md"
DUMMY_SUMMARY = "Dummy summary text used to simulate drift for automated regression testing."


def test_dmc_933_modified_teammate_summary_triggers_detectable_drift() -> None:
    sandbox = RepoSandbox(REPOSITORY_ROOT)
    try:
        baseline_result = sandbox.run(CANONICAL_DRIFT_COMMAND)
        assert baseline_result.returncode == 0, baseline_result.combined_output
        assert "2 passed" in baseline_result.stdout

        baseline_checker = create_documentation_consistency_checker(sandbox.workspace)
        cli_integration_path = _secondary_path(
            baseline_checker,
            CLI_INTEGRATION_DOCUMENT,
        )
        assert (
            baseline_checker.inconsistent_secondary_summaries(
                cli_integration_path,
                _reference_by_name(baseline_checker.canonical_reference_tables()[0]),
            )
            == []
        )

        original_summary = baseline_checker.reference_by_name()["Teammate"].summary
        sandbox.write_text(
            TEAMMATE_CONFIGS_PATH,
            _replace_once(
                sandbox.read_text(TEAMMATE_CONFIGS_PATH),
                original_summary,
                DUMMY_SUMMARY,
            ),
        )

        drift_checker = create_documentation_consistency_checker(sandbox.workspace)
        drift_findings = drift_checker.inconsistent_secondary_summaries(
            _secondary_path(drift_checker, CLI_INTEGRATION_DOCUMENT),
            _reference_by_name(drift_checker.canonical_reference_tables()[0]),
        )

        assert len(drift_findings) == 1
        assert "Teammate summary drift" in drift_findings[0]
        assert f"expected '{DUMMY_SUMMARY}'" in drift_findings[0]
        assert "under 'Overview'" in drift_findings[0]

        failure_result = sandbox.run(CANONICAL_DRIFT_COMMAND)
        assert failure_result.returncode != 0, (
            "The DMC-898 audit should fail after the canonical Teammate summary changes."
        )
        assert (
            "Canonical job names/summaries differ between teammate-configs.md and "
            "jobs/README.md."
        ) in failure_result.combined_output
        assert DUMMY_SUMMARY in failure_result.combined_output
        assert original_summary in failure_result.combined_output
    finally:
        sandbox.cleanup()


def _secondary_path(
    checker: DocumentationConsistencyChecker,
    document_name: str,
) -> Path:
    for path in checker.secondary_paths:
        if path.name == document_name:
            return path
    raise AssertionError(f"Secondary document {document_name!r} was not configured.")


def _replace_once(text: str, original: str, replacement: str) -> str:
    if original not in text:
        raise AssertionError(f"Expected text not found: {original!r}")
    return text.replace(original, replacement, 1)


def _reference_by_name(references: Iterable[JobReference]) -> dict[str, JobReference]:
    by_name: dict[str, JobReference] = {}
    for reference in references:
        for name in reference.all_names:
            by_name[name] = reference
    return by_name
