from __future__ import annotations

import shlex
import site
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.core.utils.repo_sandbox import RepoSandbox  # noqa: E402


CANONICAL_VALIDATION_COMMAND = (
    f"PYTHONPATH={shlex.quote(site.getusersitepackages())}${{PYTHONPATH:+:$PYTHONPATH}} "
    "python3 -m pytest testing/tests/DMC-916/test_dmc_916.py -q"
)
TARGET_PAGE = "dmtools-ai-docs/per-skill-packages/dmtools-jira.md"
MANDATORY_SECTION_HEADING = "## Linkbacks"
REPLACEMENT_HEADING = "## Navigation"
EXPECTED_FINDING = (
    "dmtools-ai-docs/per-skill-packages/dmtools-jira.md is missing mandatory sections: "
    "Linkbacks."
)


def test_dmc_942_missing_mandatory_section_is_reported_by_validation_suite() -> None:
    sandbox = RepoSandbox(REPOSITORY_ROOT)
    try:
        baseline_result = sandbox.run(CANONICAL_VALIDATION_COMMAND)
        assert baseline_result.returncode == 0, (
            "The canonical per-skill page audit should pass before the regression is introduced.\n"
            f"{baseline_result.combined_output}"
        )
        assert "1 passed" in baseline_result.stdout, (
            "Expected the DMC-916 validation suite to report a clean passing baseline.\n"
            f"stdout:\n{baseline_result.stdout}\n\nstderr:\n{baseline_result.stderr}"
        )

        original_page = sandbox.read_text(TARGET_PAGE)
        assert MANDATORY_SECTION_HEADING in original_page, (
            f"Expected {TARGET_PAGE} to contain the audited {MANDATORY_SECTION_HEADING!r} heading."
        )

        sandbox.write_text(
            TARGET_PAGE,
            _replace_once(original_page, MANDATORY_SECTION_HEADING, REPLACEMENT_HEADING),
        )
        mutated_page = sandbox.read_text(TARGET_PAGE)
        assert MANDATORY_SECTION_HEADING not in mutated_page, (
            f"The simulated user edit should remove the {MANDATORY_SECTION_HEADING!r} heading "
            f"from {TARGET_PAGE}."
        )
        assert REPLACEMENT_HEADING in mutated_page, (
            "The sandbox mutation should leave the surrounding content intact while making the "
            "mandatory section heading visibly missing."
        )

        failure_result = sandbox.run(CANONICAL_VALIDATION_COMMAND)
        assert failure_result.returncode != 0, (
            "The validation suite should fail after a mandatory section heading is removed."
        )

        combined_output = failure_result.combined_output
        assert "AssertionError: Per-skill documentation pages do not satisfy the required template." in (
            combined_output
        ), (
            "The user-visible failure should identify the per-skill template validation assertion.\n"
            f"{combined_output}"
        )
        assert EXPECTED_FINDING in combined_output, (
            "The failure should explicitly name the affected markdown file and the missing "
            "mandatory section.\n"
            f"{combined_output}"
        )
        assert "does not link back to the central installation guide" not in combined_output, (
            "This regression should isolate the missing-section failure instead of masking it "
            "behind unrelated cross-link errors.\n"
            f"{combined_output}"
        )
        assert "does not link back to the per-skill index" not in combined_output, (
            "The simulated failure should preserve the existing backlinks while removing only "
            "the mandatory heading.\n"
            f"{combined_output}"
        )
    finally:
        sandbox.cleanup()


def _replace_once(text: str, original: str, replacement: str) -> str:
    if original not in text:
        raise AssertionError(f"Expected text not found: {original!r}")
    return text.replace(original, replacement, 1)
