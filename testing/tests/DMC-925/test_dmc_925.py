from testing.core.interfaces.installer_skill_selection import InstallerSkillSelection


def test_dmc_925_installer_normalizes_and_deduplicates_skills_from_env_and_cli(
    installer_skill_selection_service: InstallerSkillSelection,
) -> None:
    service = installer_skill_selection_service
    env_observation = service.resolve_with_env(" Jira, github, JIRA, , confluence ")
    assert env_observation.returncode == 0, service.format_execution_failure(env_observation)
    assert env_observation.effective_skills == ("jira", "github", "confluence"), (
        "DMTOOLS_SKILLS should be trimmed, lowercased, deduplicated, and logged in the "
        f"same order. Observed line: {env_observation.effective_skills_line!r}"
    )
    assert env_observation.skills_source == "env", (
        "The effective skill list must identify DMTOOLS_SKILLS as the source when the "
        f"environment variable is set. Observed line: {env_observation.effective_skills_line!r}"
    )
    assert not env_observation.invalid_skills, (
        "The normalized DMTOOLS_SKILLS input should not leave behind invalid entries. "
        f"Observed output:\n{env_observation.visible_output}"
    )
    assert env_observation.effective_skills_line == (
        "Effective skills: jira, github, confluence (source: env)"
    ), (
        "The env flow must preserve the exact user-facing installer line required by the "
        "ticket, including comma-and-space formatting.\n"
        f"Observed output:\n{env_observation.visible_output}"
    )

    cli_observation = service.resolve_with_cli("teams,TEAMS")
    assert cli_observation.returncode == 0, service.format_execution_failure(cli_observation)
    assert cli_observation.effective_skills == ("teams",), (
        "The CLI --skills input should be normalized and deduplicated without changing the "
        "selected valid skills. "
        f"Observed line: {cli_observation.effective_skills_line!r}"
    )
    assert cli_observation.skills_source == "cli", (
        "The installer log must identify --skills as the source when the CLI argument is used. "
        f"Observed line: {cli_observation.effective_skills_line!r}"
    )
    assert cli_observation.invalid_skills == (), (
        "Valid CLI skills should not produce an invalid-skill warning. "
        f"Observed output:\n{cli_observation.visible_output}"
    )
    assert "Effective skills: teams (source: cli)" in cli_observation.visible_output, (
        "The CLI flow should show the final visible effective skill line for the supported "
        f"selection. Observed output:\n{cli_observation.visible_output}"
    )
