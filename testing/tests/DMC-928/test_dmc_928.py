from pathlib import Path

from testing.components.services.installer_rerun_idempotency_service import (
    InstallerRerunIdempotencyService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_SKILLS = "jira,github"
NOOP_STATUS_MARKERS = ("already installed", "no-op")
UNEXPECTED_SECOND_RUN_MARKERS = (
    "Downloading DMTools JAR...",
    "Downloading DMTools shell script",
    "dmtools.sh not found in release assets, downloading from repository...",
)


def _reports_noop_status_for_selected_skills(output: str, skills_csv: str) -> bool:
    expected_skills = tuple(skill.strip().lower() for skill in skills_csv.split(",") if skill.strip())
    for line in output.splitlines():
        normalized_line = line.lower()
        if not any(marker in normalized_line for marker in NOOP_STATUS_MARKERS):
            continue
        if all(skill in normalized_line for skill in expected_skills):
            return True
    return False


def test_dmc_928_rerunning_installer_for_the_same_skill_set_is_idempotent() -> None:
    service = InstallerRerunIdempotencyService(REPOSITORY_ROOT, skills_csv=EXPECTED_SKILLS)

    observation = service.exercise()

    assert observation.first_run.command.returncode == 0, (
        "The initial installer run failed unexpectedly.\n"
        f"{observation.first_run.command.combined_output}"
    )
    assert observation.second_run.command.returncode == 0, (
        "The repeated installer run failed unexpectedly.\n"
        f"{observation.second_run.command.combined_output}"
    )

    second_run_output = observation.second_run.command.combined_output
    failures: list[str] = []

    if not _reports_noop_status_for_selected_skills(second_run_output, EXPECTED_SKILLS):
        failures.append(
            "Expected the rerun to report an already-installed or no-op status for the "
            "selected skills, but no matching status line was found in the output."
        )

    unexpected_markers = [
        marker for marker in UNEXPECTED_SECOND_RUN_MARKERS if marker in second_run_output
    ]
    if unexpected_markers:
        failures.append(
            "Expected the rerun to avoid download work, but the output still showed: "
            + ", ".join(repr(marker) for marker in unexpected_markers)
        )

    changed_artifacts = observation.changed_artifacts()
    if changed_artifacts:
        failures.append(
            "Expected the rerun to leave installer-managed files unchanged, but these "
            "artifacts were rewritten:\n" + "\n".join(changed_artifacts)
        )

    assert not failures, (
        "Re-running install.sh with the same skill selection should be a no-op for the "
        "target installation.\n"
        + "\n\n".join(failures)
        + "\n\nSecond run output:\n"
        + second_run_output
    )
