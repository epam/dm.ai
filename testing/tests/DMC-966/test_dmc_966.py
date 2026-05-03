from pathlib import Path

from testing.components.services.installer_rerun_idempotency_service import (
    InstallerRerunIdempotencyService,
    reports_noop_status_for_selected_skills,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INITIAL_SKILLS = "jira,github"
RERUN_SKILLS = "github,jira"
CORE_ARTIFACTS = ("dmtools.jar", "dmtools")
UNEXPECTED_SECOND_RUN_MARKERS = (
    "Downloading DMTools JAR...",
    "Downloading DMTools shell script",
    "dmtools.sh not found in release assets, downloading from repository...",
)


def test_dmc_966_rerunning_installer_with_permuted_skill_order_keeps_core_artifacts_unchanged() -> None:
    service = InstallerRerunIdempotencyService(
        REPOSITORY_ROOT,
        initial_skills_csv=INITIAL_SKILLS,
        rerun_skills_csv=RERUN_SKILLS,
    )

    observation = service.exercise()

    assert observation.first_run.command.returncode == 0, (
        "The initial installer run failed unexpectedly.\n"
        f"{observation.first_run.command.combined_output}"
    )
    assert observation.second_run.command.returncode == 0, (
        "The repeated installer run with a permuted skill list failed unexpectedly.\n"
        f"{observation.second_run.command.combined_output}"
    )

    second_run_output = observation.second_run.command.combined_output
    failures: list[str] = []

    if not reports_noop_status_for_selected_skills(second_run_output, RERUN_SKILLS):
        failures.append(
            "Expected the rerun to report an already-installed or no-op status for the "
            "same selected skills even when they are provided in a different order, "
            "but no matching status line was found in the output."
        )

    unexpected_markers = [
        marker for marker in UNEXPECTED_SECOND_RUN_MARKERS if marker in second_run_output
    ]
    if unexpected_markers:
        failures.append(
            "Expected the rerun with a permuted skill order to avoid download work, "
            "but the output still showed: "
            + ", ".join(repr(marker) for marker in unexpected_markers)
        )

    changed_artifacts = observation.changed_artifacts(*CORE_ARTIFACTS)
    if changed_artifacts:
        failures.append(
            "Expected the rerun with the same skill set in a different order to leave "
            "the core installer-managed artifacts unchanged, but these artifacts were rewritten:\n"
            + "\n".join(changed_artifacts)
        )

    assert not failures, (
        "Re-running install.sh with the same skills in a different order should be a "
        "no-op for the target installation.\n"
        + "\n\n".join(failures)
        + "\n\nSecond run output:\n"
        + second_run_output
    )
