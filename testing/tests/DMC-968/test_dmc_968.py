from pathlib import Path

from testing.components.services.installer_rerun_idempotency_service import (
    InstallerManagedPaths,
    InstallerRerunIdempotencyService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EXPECTED_SKILLS = "jira,github"
EXPECTED_RESTORED_SCRIPT_MARKER = "Downloading DMTools shell script"
UNEXPECTED_JAR_DOWNLOAD_MARKERS = (
    "Downloading DMTools JAR...",
    "Downloading DMTools JAR",
)


def _delete_only_shell_script(paths: InstallerManagedPaths) -> None:
    assert paths.jar_path.exists(), (
        "The test precondition requires an existing dmtools.jar before deleting the "
        f"shell script.\nMissing path: {paths.jar_path}"
    )
    assert paths.script_path.exists(), (
        "The test precondition requires an existing dmtools shell script before the "
        f"manual deletion step.\nMissing path: {paths.script_path}"
    )

    paths.script_path.unlink()

    assert not paths.script_path.exists(), (
        "The inter-run setup should remove only the dmtools shell script before the "
        f"rerun.\nPath still exists: {paths.script_path}"
    )
    assert paths.jar_path.exists(), (
        "Deleting the dmtools shell script must leave dmtools.jar intact.\n"
        f"Missing path after deletion: {paths.jar_path}"
    )


def test_dmc_968_rerun_restores_only_missing_shell_script() -> None:
    service = InstallerRerunIdempotencyService(REPOSITORY_ROOT, skills_csv=EXPECTED_SKILLS)

    observation = service.exercise(before_second_run=_delete_only_shell_script)

    assert observation.first_run.command.returncode == 0, (
        "The initial installer run failed unexpectedly.\n"
        f"{observation.first_run.command.combined_output}"
    )
    assert observation.second_run.command.returncode == 0, (
        "The rerun after deleting the shell script failed unexpectedly.\n"
        f"{observation.second_run.command.combined_output}"
    )
    assert observation.inter_run_artifacts is not None, (
        "The test should capture installer-managed artifact states after the manual "
        "deletion step and before the rerun."
    )

    inter_run_artifacts = observation.inter_run_artifacts
    assert inter_run_artifacts["dmtools"].exists is False, (
        "After the manual deletion step, the dmtools shell script should be missing "
        "before the rerun starts."
    )
    assert inter_run_artifacts["dmtools.jar"].exists is True, (
        "After deleting the shell script, the existing dmtools.jar should still be "
        "present before the rerun starts."
    )

    second_run_output = observation.second_run.command.combined_output
    failures: list[str] = []

    if EXPECTED_RESTORED_SCRIPT_MARKER not in second_run_output:
        failures.append(
            "Expected the rerun to visibly restore the missing dmtools shell script, "
            f"but the output did not contain {EXPECTED_RESTORED_SCRIPT_MARKER!r}."
        )

    unexpected_jar_downloads = [
        marker for marker in UNEXPECTED_JAR_DOWNLOAD_MARKERS if marker in second_run_output
    ]
    if unexpected_jar_downloads:
        failures.append(
            "Expected the rerun to avoid redownloading the existing dmtools.jar when "
            "only the shell script was missing, but the output still showed: "
            + ", ".join(repr(marker) for marker in unexpected_jar_downloads)
        )

    first_jar_snapshot = observation.first_run.artifacts["dmtools.jar"]
    second_jar_snapshot = observation.second_run.artifacts["dmtools.jar"]
    if first_jar_snapshot.mtime_ns != second_jar_snapshot.mtime_ns:
        failures.append(
            "Expected the existing dmtools.jar timestamp to remain unchanged, but it "
            "was rewritten.\n"
            f"First snapshot: {first_jar_snapshot}\n"
            f"Second snapshot: {second_jar_snapshot}"
        )
    if first_jar_snapshot.size != second_jar_snapshot.size:
        failures.append(
            "Expected the existing dmtools.jar contents to remain unchanged, but its "
            "size changed.\n"
            f"First snapshot: {first_jar_snapshot}\n"
            f"Second snapshot: {second_jar_snapshot}"
        )

    second_script_snapshot = observation.second_run.artifacts["dmtools"]
    if second_script_snapshot.size <= 0:
        failures.append(
            "Expected the rerun to recreate a non-empty dmtools shell script.\n"
            f"Final snapshot: {second_script_snapshot}"
        )

    assert not failures, (
        "Re-running install.sh after deleting only the dmtools shell script should "
        "restore the missing script without rewriting dmtools.jar.\n"
        + "\n\n".join(failures)
        + "\n\nSecond run output:\n"
        + second_run_output
    )
