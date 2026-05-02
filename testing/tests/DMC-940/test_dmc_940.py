from __future__ import annotations

import shlex
import shutil
import site
import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.core.utils.repo_sandbox import RepoSandbox  # noqa: E402


VALIDATION_COMMAND = (
    f"PYTHONPATH={shlex.quote(site.getusersitepackages())}${{PYTHONPATH:+:$PYTHONPATH}} "
    "python3 -m pytest testing/tests/DMC-916/test_dmc_916.py -q"
)
DIRECTORY_MISSING_MESSAGE = (
    "Expected per-skill catalogue directory dmtools-ai-docs/per-skill-packages to exist, but it is missing."
)
MISSING_PAGE_MESSAGE = (
    "Expected mandatory skill pages under dmtools-ai-docs/per-skill-packages are missing: "
    "dmtools-github.md."
)


def test_dmc_940_baseline_validation_suite_passes() -> None:
    result = _run_validation_suite()

    assert result.returncode == 0, result.combined_output
    assert "1 passed" in result.stdout


def test_dmc_940_missing_catalog_directory_fails_with_clear_message() -> None:
    result = _run_validation_suite(
        lambda sandbox: shutil.rmtree(sandbox.path("dmtools-ai-docs/per-skill-packages"))
    )

    assert result.returncode != 0, "Validation suite should fail when the catalogue directory is missing."
    assert DIRECTORY_MISSING_MESSAGE in result.combined_output
    assert "FAILED testing/tests/DMC-916/test_dmc_916.py::" in result.stdout


def test_dmc_940_missing_mandatory_skill_page_fails_with_clear_message() -> None:
    result = _run_validation_suite(
        lambda sandbox: sandbox.path("dmtools-ai-docs/per-skill-packages/dmtools-github.md").unlink()
    )

    assert result.returncode != 0, "Validation suite should fail when a mandatory skill page is missing."
    assert MISSING_PAGE_MESSAGE in result.combined_output
    assert "dmtools-github.md" in result.combined_output
    assert "FAILED testing/tests/DMC-916/test_dmc_916.py::" in result.stdout


def _run_validation_suite(mutator=None):
    sandbox = RepoSandbox(REPOSITORY_ROOT)
    try:
        if mutator is not None:
            mutator(sandbox)
        return sandbox.run(VALIDATION_COMMAND)
    finally:
        sandbox.cleanup()
