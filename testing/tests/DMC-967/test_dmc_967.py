from pathlib import Path

from testing.components.services.installer_rerun_idempotency_service import (
    InstallerRerunIdempotencyService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INITIAL_SKILLS = "jira"
FOLLOW_UP_SKILLS = "jira,github"
UNCHANGED_INSTALLER_ARTIFACTS = ("dmtools.jar", "dmtools", "dmtools-installer.env")
UNEXPECTED_SECOND_RUN_MARKERS = (
    "Downloading DMTools JAR...",
    "Downloading DMTools shell script",
    "dmtools.sh not found in release assets, downloading from repository...",
)


def extract_installed_skill_names(payload: object) -> set[str]:
    if not isinstance(payload, dict):
        return set()

    observed_skills: set[str] = set()
    for item in payload.get("installed_skills", ()):
        if isinstance(item, str) and item.strip():
            observed_skills.add(item.strip().lower())
            continue
        if not isinstance(item, dict):
            continue
        for key in ("skill", "slug", "name", "id"):
            value = item.get(key)
            if isinstance(value, str) and value.strip():
                observed_skills.add(value.strip().lower())
    return observed_skills


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

    second_run_metadata = observation.second_run_metadata
    installed_skill_names = (
        extract_installed_skill_names(second_run_metadata.installed_skills_payload)
        if second_run_metadata is not None
        else set()
    )
    installer_env_assignments = (
        second_run_metadata.installer_env_assignments if second_run_metadata is not None else {}
    )
    runtime_override_env_assignments = (
        second_run_metadata.runtime_override_env_assignments
        if second_run_metadata is not None
        else {}
    )
    if "github" not in installed_skill_names:
        failures.append(
            "Expected the follow-up run to persist github in installed-skills.json, "
            f"but observed skills were: {sorted(installed_skill_names)!r}"
        )
    if installer_env_assignments.get("DMTOOLS_SKILLS") != INITIAL_SKILLS:
        failures.append(
            "Expected the follow-up run to preserve DMTOOLS_SKILLS in dmtools-installer.env "
            f"as {INITIAL_SKILLS!r}, but observed: "
            f"{installer_env_assignments.get('DMTOOLS_SKILLS')!r}"
        )
    if installer_env_assignments.get("DMTOOLS_INTEGRATIONS") != "ai,cli,file,kb,mermaid,jira":
        failures.append(
            "Expected the follow-up run to preserve DMTOOLS_INTEGRATIONS in "
            "dmtools-installer.env for the original jira install, but observed: "
            f"{installer_env_assignments.get('DMTOOLS_INTEGRATIONS')!r}"
        )
    if runtime_override_env_assignments.get("DMTOOLS_SKILLS") != FOLLOW_UP_SKILLS:
        failures.append(
            "Expected the follow-up run to persist DMTOOLS_SKILLS in the runtime override "
            f"env as {FOLLOW_UP_SKILLS!r}, but observed: "
            f"{runtime_override_env_assignments.get('DMTOOLS_SKILLS')!r}"
        )
    if (
        runtime_override_env_assignments.get("DMTOOLS_INTEGRATIONS")
        != "ai,cli,file,kb,mermaid,jira,github"
    ):
        failures.append(
            "Expected the follow-up run to persist DMTOOLS_INTEGRATIONS in the runtime "
            "override env for the added github skill, but observed: "
            f"{runtime_override_env_assignments.get('DMTOOLS_INTEGRATIONS')!r}"
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

    changed_installer_artifacts = observation.changed_artifacts(*UNCHANGED_INSTALLER_ARTIFACTS)
    if changed_installer_artifacts:
        failures.append(
            "Expected the follow-up run to leave the shared core CLI artifacts unchanged, "
            "but these files were rewritten:\n"
            + "\n".join(changed_installer_artifacts)
        )

    assert not failures, (
        "Re-running install.sh with an added skill should preserve the already-installed "
        "core CLI artifacts while extending the selected skill set.\n"
        + "\n\n".join(failures)
        + "\n\nSecond run output:\n"
        + second_run_output
    )
