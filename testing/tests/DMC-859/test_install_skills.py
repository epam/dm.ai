import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INSTALL_SCRIPT = REPOSITORY_ROOT / "install.sh"
DOC_PATH = REPOSITORY_ROOT / "docs" / "install-skills.md"
ALL_SKILLS = (
    "dmtools,jira,confluence,github,gitlab,figma,teams,"
    "sharepoint,ado,testrail,xray,report,expert,teammate"
)
RUNTIME_SKILL_INTEGRATIONS = {
    "jira": {"jira"},
    "confluence": {"confluence"},
    "github": {"github"},
    "gitlab": {"gitlab"},
    "figma": {"figma"},
    "teams": {"teams", "teams_auth"},
    "sharepoint": {"sharepoint", "teams_auth"},
    "ado": {"ado"},
    "testrail": {"testrail"},
    "xray": {"jira_xray"},
}


def run_installer_functions(commands: str, extra_env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["DMTOOLS_INSTALLER_TEST_MODE"] = "true"
    if extra_env:
        env.update(extra_env)
    script = f"""
set -e
source "{INSTALL_SCRIPT}"
{commands}
"""
    return subprocess.run(
        ["bash", "-lc", script],
        cwd=REPOSITORY_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )


class TestInstallerSkillSelection(unittest.TestCase):
    def test_cli_flag_parsing_normalizes_and_preserves_version_argument(self) -> None:
        result = run_installer_functions(
            """
parse_installer_args --skills " Jira, github ,JIRA " v1.2.3
resolve_skill_selection
printf 'version=%s\\n' "$INSTALLER_VERSION_ARG"
printf 'skills=%s\\n' "$EFFECTIVE_SKILLS_CSV"
printf 'invalid=%s\\n' "$INVALID_SKILLS_CSV"
printf 'source=%s\\n' "$SKILLS_SOURCE"
printf 'integrations=%s\\n' "$EFFECTIVE_INTEGRATIONS_CSV"
"""
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("version=v1.2.3", result.stdout)
        self.assertIn("skills=jira,github", result.stdout)
        self.assertIn("invalid=", result.stdout)
        self.assertIn("source=cli", result.stdout)
        self.assertIn("integrations=ai,cli,file,kb,mermaid,jira,github", result.stdout)

    def test_env_empty_defaults_to_all_skills(self) -> None:
        result = run_installer_functions(
            """
parse_installer_args
resolve_skill_selection
printf 'skills=%s\\n' "$EFFECTIVE_SKILLS_CSV"
printf 'source=%s\\n' "$SKILLS_SOURCE"
printf 'all=%s\\n' "$INSTALL_ALL_SKILLS"
""",
            {"DMTOOLS_SKILLS": ""},
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Installing all skills (source: env)", result.stdout)
        self.assertIn(f"skills={ALL_SKILLS}", result.stdout)
        self.assertIn("source=env", result.stdout)
        self.assertIn("all=true", result.stdout)

    def test_unknown_skills_warn_and_known_skills_continue(self) -> None:
        result = run_installer_functions(
            """
parse_installer_args --skills jira,unknown,GITHUB
resolve_skill_selection
printf 'skills=%s\\n' "$EFFECTIVE_SKILLS_CSV"
printf 'invalid=%s\\n' "$INVALID_SKILLS_CSV"
"""
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Warning: Skipping unknown skills: unknown", result.stdout)
        self.assertIn("skills=jira,github", result.stdout)
        self.assertIn("invalid=unknown", result.stdout)

    def test_bitbucket_is_rejected_until_runtime_support_exists(self) -> None:
        result = run_installer_functions(
            """
printf 'available=%s\\n' "$(join_by_comma "${AVAILABLE_SKILLS[@]}")"
parse_installer_args --skills bitbucket
resolve_skill_selection
"""
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"available={ALL_SKILLS}", result.stdout)
        self.assertIn("No valid skills selected", result.stderr)
        self.assertIn("Unknown skills: bitbucket", result.stderr)

    def test_all_invalid_skills_fail_with_non_zero_exit(self) -> None:
        result = run_installer_functions(
            """
parse_installer_args --skills typo
resolve_skill_selection
"""
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("No valid skills selected", result.stderr)

    def test_repeated_skill_configuration_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            result = run_installer_functions(
                """
create_install_dir
parse_installer_args --skills jira
resolve_skill_selection
write_installer_skill_config
write_installer_skill_config
cat "$INSTALLER_ENV_PATH"
""",
                {
                    "DMTOOLS_INSTALL_DIR": temp_dir,
                    "DMTOOLS_BIN_DIR": f"{temp_dir}/bin",
                    "DMTOOLS_INSTALLER_ENV_PATH": f"{temp_dir}/bin/dmtools-installer.env",
                },
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Selected skills already installed: jira", result.stdout)
        self.assertIn('DMTOOLS_SKILLS="jira"', result.stdout)
        self.assertIn('DMTOOLS_INTEGRATIONS="ai,cli,file,kb,mermaid,jira"', result.stdout)

    def test_runtime_backed_skills_add_expected_integrations(self) -> None:
        command_lines = []
        for skill in RUNTIME_SKILL_INTEGRATIONS:
            command_lines.extend(
                [
                    f'parse_installer_args --skills "{skill}"',
                    "resolve_skill_selection",
                    'printf "%s=%s\\n" "$EFFECTIVE_SKILLS_CSV" "$EFFECTIVE_INTEGRATIONS_CSV"',
                ]
            )
        result = run_installer_functions("\n".join(command_lines))

        self.assertEqual(0, result.returncode, result.stderr)
        for skill, expected_integrations in RUNTIME_SKILL_INTEGRATIONS.items():
            matching_lines = [
                line for line in result.stdout.splitlines() if line.startswith(f"{skill}=")
            ]
            self.assertEqual(1, len(matching_lines), result.stdout)
            actual_integrations = set(matching_lines[0].split("=", 1)[1].split(","))
            for integration in expected_integrations:
                self.assertIn(integration, actual_integrations, matching_lines[0])


class TestInstallerSkillDocumentation(unittest.TestCase):
    def test_install_skills_doc_contains_required_endpoint_guidance(self) -> None:
        self.assertTrue(DOC_PATH.exists(), "docs/install-skills.md must exist")
        content = DOC_PATH.read_text(encoding="utf-8")
        self.assertIn("/dmtools/{skill}", content)
        self.assertIn("GET /dmtools/endpoints", content)
        self.assertIn("location ~ ^/dmtools/(?<skill>[a-z0-9_-]+)$", content)


if __name__ == "__main__":
    unittest.main(verbosity=2)
