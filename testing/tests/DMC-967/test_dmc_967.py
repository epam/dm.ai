from pathlib import Path

from testing.components.services.installer_rerun_idempotency_service import (
    InstallerRerunIdempotencyService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INITIAL_SKILLS = "jira"
FOLLOW_UP_SKILLS = "jira,github"
CORE_ARTIFACTS = ("dmtools.jar", "dmtools")
UNEXPECTED_SECOND_RUN_MARKERS = (
    "Downloading DMTools JAR...",
    "Downloading DMTools shell script",
    "dmtools.sh not found in release assets, downloading from repository...",
)


def test_dmc_967_adding_a_skill_keeps_core_artifacts_idempotent() -> None:
    service = InstallerRerunIdempotencyService(
        REPOSITORY_ROOT,
        initial_skills_csv=INITIAL_SKILLS,
        follow_up_skills_csv=FOLLOW_UP_SKILLS,
    )

    observation = service.exercise()

    assert observation.first_run.command.returncode == 0, (
        "The initial installer run failed unexpectedly.\n"
        f"{observation.first_run.command.combined_output}"
    )
    assert observation.second_run.command.returncode == 0, (
        "The follow-up installer run failed unexpectedly.\n"
        f"{observation.second_run.command.combined_output}"
    )

    second_run_output = observation.second_run.command.combined_output
    failures: list[str] = []

    if "Effective skills: jira, github" not in second_run_output:
        failures.append(
            "Expected the follow-up installer run to report the requested jira,github "
            "selection in its visible output."
        )

    unexpected_markers = [
        marker for marker in UNEXPECTED_SECOND_RUN_MARKERS if marker in second_run_output
    ]
    if unexpected_markers:
        failures.append(
            "Expected the follow-up run to avoid redownloading shared core artifacts, "
            "but the output still showed: "
            + ", ".join(repr(marker) for marker in unexpected_markers)
        )

    changed_core_artifacts = observation.changed_artifacts(CORE_ARTIFACTS)
    if changed_core_artifacts:
        failures.append(
            "Expected the follow-up run to leave the shared core artifacts unchanged, "
            "but these files were rewritten:\n" + "\n".join(changed_core_artifacts)
        )

    assert not failures, (
        "Re-running install.sh with an added skill should preserve the already-installed "
        "core CLI artifacts while extending the selected skill set.\n"
        + "\n\n".join(failures)
        + "\n\nSecond run output:\n"
        + second_run_output
    )
