import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
INSTALL_ENTRYPOINTS = (REPOSITORY_ROOT / "install.sh", REPOSITORY_ROOT / "install")
DOC_PATH = REPOSITORY_ROOT / "docs" / "install-skills.md"
ALL_SKILLS = (
    "dmtools,jira,confluence,github,gitlab,figma,teams,"
    "sharepoint,ado,testrail,xray"
)
BASE_INTEGRATIONS = {"ai", "cli", "file", "kb", "mermaid"}
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


def run_installer_functions(
    commands: str,
    extra_env: dict[str, str] | None = None,
    installer_script: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["DMTOOLS_INSTALLER_TEST_MODE"] = "true"
    if extra_env:
        env.update(extra_env)
    target_script = installer_script or INSTALL_ENTRYPOINTS[0]
    script = f"""
set -e
source "{target_script}"
{commands}
"""
    return subprocess.run(
        ["bash", "-lc", script],
        cwd=REPOSITORY_ROOT,
        env=env,
        capture_output=True,
        text=True,
    )


def run_installer_via_stdin(
    commands: str,
    extra_env: dict[str, str] | None = None,
    installer_script: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["DMTOOLS_INSTALLER_TEST_MODE"] = "true"
    if extra_env:
        env.update(extra_env)
    target_script = installer_script or INSTALL_ENTRYPOINTS[0]
    script = target_script.read_text(encoding="utf-8")
    return subprocess.run(
        ["bash", "-s", "--"],
        cwd=REPOSITORY_ROOT,
        env=env,
        input=f"{script}\n{commands}\n",
        capture_output=True,
        text=True,
    )


class TestInstallerSkillSelection(unittest.TestCase):
    def test_entrypoints_accept_strict_flag_and_reject_unknown_skills(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                result = run_installer_functions(
                    """
parse_installer_args --skills jira,unknown --strict
resolve_skill_selection
""",
                    installer_script=installer_script,
                )

                self.assertNotEqual(0, result.returncode)
                self.assertIn(
                    "Unknown skills are not allowed in strict mode: unknown.", result.stderr
                )
                self.assertIn("Allowed skills:", result.stderr)

    def test_entrypoints_accept_strict_env_and_reject_unknown_skills(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                result = run_installer_functions(
                    """
parse_installer_args --skills jira,unknown
resolve_skill_selection
""",
                    {"DMTOOLS_STRICT_INSTALL": "true"},
                    installer_script=installer_script,
                )

                self.assertNotEqual(0, result.returncode)
                self.assertIn(
                    "Unknown skills are not allowed in strict mode: unknown.", result.stderr
                )
                self.assertIn("Allowed skills:", result.stderr)

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

    def test_env_selection_via_bash_stdin_matches_supported_pipe_flow(self) -> None:
        result = run_installer_via_stdin(
            """
parse_installer_args
resolve_skill_selection
printf 'skills=%s\\n' "$EFFECTIVE_SKILLS_CSV"
printf 'source=%s\\n' "$SKILLS_SOURCE"
printf 'integrations=%s\\n' "$EFFECTIVE_INTEGRATIONS_CSV"
""",
            {"DMTOOLS_SKILLS": "jira,github"},
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("skills=jira,github", result.stdout)
        self.assertIn("source=env", result.stdout)
        self.assertIn("integrations=ai,cli,file,kb,mermaid,jira,github", result.stdout)

    def test_env_selection_logs_effective_skills_with_comma_space_formatting(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                result = run_installer_functions(
                    """
parse_installer_args
resolve_skill_selection
""",
                    {"DMTOOLS_SKILLS": " Jira, github, JIRA, , confluence "},
                    installer_script=installer_script,
                )

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn(
                    "Effective skills: jira, github, confluence (source: env)",
                    result.stdout,
                )

    def test_cli_unknown_skills_fail_without_skip_unknown(self) -> None:
        result = run_installer_functions(
            """
            parse_installer_args --skills jira,unknown,GITHUB
            resolve_skill_selection
            """
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "Unknown skills: unknown. Use --skip-unknown to continue.", result.stderr
        )

    def test_cli_skip_unknown_warns_and_keeps_known_skills(self) -> None:
        result = run_installer_functions(
            """
parse_installer_args --skills=jira,unknown,GITHUB --skip-unknown
resolve_skill_selection
printf 'skills=%s\\n' "$EFFECTIVE_SKILLS_CSV"
printf 'invalid=%s\\n' "$INVALID_SKILLS_CSV"
"""
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Warning: Skipping unknown skills: unknown", result.stdout)
        self.assertIn("skills=jira,github", result.stdout)
        self.assertIn("invalid=unknown", result.stdout)

    def test_unsupported_skills_are_rejected_until_runtime_support_exists(self) -> None:
        for skill in ("bitbucket", "report", "expert", "teammate"):
            with self.subTest(skill=skill):
                result = run_installer_functions(
                    f"""
printf 'available=%s\\n' "$(join_by_comma "${{AVAILABLE_SKILLS[@]}}")"
parse_installer_args --skills {skill}
resolve_skill_selection
"""
                )

                self.assertNotEqual(0, result.returncode)
                self.assertIn(f"available={ALL_SKILLS}", result.stdout)
                self.assertIn("No valid skills selected", result.stderr)
                self.assertIn(f"Unknown skills: {skill}", result.stderr)

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

    def test_repeated_main_run_skips_download_when_configuration_and_artifacts_match(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                with tempfile.TemporaryDirectory() as temp_dir:
                    result = run_installer_functions(
                        """
check_java() { printf 'stub check_java\\n'; }
get_latest_version() { printf 'v0.0.0-test'; }
download_dmtools() {
    local version="$1"
    printf 'SIDE download_dmtools %s\\n' "$version"
    mkdir -p "$(dirname "$JAR_PATH")" "$BIN_DIR"
    printf 'stub jar for %s\\n' "$version" > "$JAR_PATH"
    cat > "$SCRIPT_PATH" <<'EOF'
#!/bin/bash
echo "dmtools stub"
EOF
    chmod +x "$SCRIPT_PATH"
}
update_shell_config() { printf 'SIDE update_shell_config\\n'; }
verify_installation() { printf 'SIDE verify_installation\\n'; }
print_instructions() { printf 'SIDE print_instructions\\n'; }
main --skills jira,github
main --skills jira,github
""",
                        {
                            "DMTOOLS_INSTALL_DIR": temp_dir,
                            "DMTOOLS_BIN_DIR": f"{temp_dir}/bin",
                            "DMTOOLS_INSTALLER_ENV_PATH": f"{temp_dir}/bin/dmtools-installer.env",
                        },
                        installer_script=installer_script,
                    )

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(
                    1, result.stdout.count("SIDE download_dmtools v0.0.0-test"), result.stdout
                )
                self.assertIn("Selected skills already installed: jira,github", result.stdout)
                self.assertIn(
                    "Installer-managed artifacts already present for version v0.0.0-test; "
                    "skipping DMTools download.",
                    result.stdout,
                )
                self.assertIn(
                    "Installer metadata already present for version v0.0.0-test; "
                    "skipping metadata rewrite.",
                    result.stdout,
                )

    def test_repeated_main_run_restores_only_missing_shell_script(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                with tempfile.TemporaryDirectory() as temp_dir:
                    result = run_installer_functions(
                        """
check_java() { printf 'stub check_java\\n'; }
get_latest_version() { printf 'v0.0.0-test'; }
download_file() {
    local url="$1"
    local output="$2"
    local desc="$3"
    printf 'SIDE download_file %s\\n' "$desc"
    mkdir -p "$(dirname "$output")" "$BIN_DIR"
    case "$desc" in
        "DMTools JAR")
            printf 'stub jar for %s\\n' "$url" > "$output"
            ;;
        *)
            cat > "$output" <<'EOF'
#!/bin/bash
echo "dmtools stub"
EOF
            ;;
    esac
    return 0
}
download_script_from_repo() { printf 'SIDE download_script_from_repo\\n'; return 1; }
update_shell_config() { printf 'SIDE update_shell_config\\n'; }
verify_installation() { printf 'SIDE verify_installation\\n'; }
print_instructions() { printf 'SIDE print_instructions\\n'; }
main --skills jira,github
rm -f "$SCRIPT_PATH"
main --skills jira,github
""",
                        {
                            "DMTOOLS_INSTALL_DIR": temp_dir,
                            "DMTOOLS_BIN_DIR": f"{temp_dir}/bin",
                            "DMTOOLS_INSTALLER_ENV_PATH": f"{temp_dir}/bin/dmtools-installer.env",
                        },
                        installer_script=installer_script,
                    )

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(
                    1,
                    result.stdout.count("SIDE download_file DMTools JAR"),
                    result.stdout,
                )
                self.assertEqual(
                    2,
                    result.stdout.count("SIDE download_file DMTools shell script"),
                    result.stdout,
                )
                self.assertIn("Selected skills already installed: jira,github", result.stdout)

    def test_main_run_with_added_skill_reuses_core_artifacts_and_updates_runtime_env(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                with tempfile.TemporaryDirectory() as temp_dir:
                    result = run_installer_functions(
                        """
check_java() { printf 'stub check_java\\n'; }
get_latest_version() { printf 'v0.0.0-test'; }
download_dmtools() {
    local version="$1"
    printf 'SIDE download_dmtools %s\\n' "$version"
    mkdir -p "$(dirname "$JAR_PATH")" "$BIN_DIR"
    printf 'stub jar for %s\\n' "$version" > "$JAR_PATH"
    cat > "$SCRIPT_PATH" <<'EOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/dmtools-installer.env" ]; then
    . "$SCRIPT_DIR/dmtools-installer.env"
fi
printf '%s\\n' "${DMTOOLS_INTEGRATIONS:-}"
EOF
    chmod +x "$SCRIPT_PATH"
}
update_shell_config() { printf 'SIDE update_shell_config\\n'; }
verify_installation() { printf 'SIDE verify_installation\\n'; }
print_instructions() { printf 'SIDE print_instructions\\n'; }
main --skills jira
printf '%s\\n' '---ENV-AFTER-FIRST---'
cat "$INSTALLER_ENV_PATH"
printf '%s\\n' '---INSTALLED-SKILLS-AFTER-FIRST---'
cat "$INSTALLED_SKILLS_JSON_PATH"
main --skills jira,github
printf '%s\\n' '---ENV-AFTER-SECOND---'
cat "$INSTALLER_ENV_PATH"
printf '%s\\n' '---INSTALLED-SKILLS-AFTER-SECOND---'
cat "$INSTALLED_SKILLS_JSON_PATH"
printf '%s\\n' '---WRAPPER-INTEGRATIONS-AFTER-SECOND---'
"$SCRIPT_PATH"
""",
                        {
                            "DMTOOLS_INSTALL_DIR": temp_dir,
                            "DMTOOLS_BIN_DIR": f"{temp_dir}/bin",
                            "DMTOOLS_INSTALLER_ENV_PATH": f"{temp_dir}/bin/dmtools-installer.env",
                        },
                        installer_script=installer_script,
                    )

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(
                    1, result.stdout.count("SIDE download_dmtools v0.0.0-test"), result.stdout
                )

                first_env = (
                    result.stdout.split("---ENV-AFTER-FIRST---\n", 1)[1]
                    .split("\n---INSTALLED-SKILLS-AFTER-FIRST---\n", 1)[0]
                    .strip()
                )
                second_env = (
                    result.stdout.split("---ENV-AFTER-SECOND---\n", 1)[1]
                    .split("\n---INSTALLED-SKILLS-AFTER-SECOND---\n", 1)[0]
                    .strip()
                )
                wrapper_integrations = (
                    result.stdout.split("---WRAPPER-INTEGRATIONS-AFTER-SECOND---\n", 1)[1].strip()
                )
                second_metadata = json.loads(
                    result.stdout.split("\n---WRAPPER-INTEGRATIONS-AFTER-SECOND---\n", 1)[0]
                    .split("---INSTALLED-SKILLS-AFTER-SECOND---\n", 1)[1]
                    .strip()
                )

                self.assertNotEqual(first_env, second_env)
                self.assertIn('DMTOOLS_SKILLS="jira,github"', second_env)
                self.assertIn(
                    'DMTOOLS_INTEGRATIONS="ai,cli,file,kb,mermaid,jira,github"',
                    second_env,
                )
                self.assertEqual("ai,cli,file,kb,mermaid,jira,github", wrapper_integrations)
                self.assertEqual(
                    [{"name": "jira"}, {"name": "github"}],
                    second_metadata["installed_skills"],
                )
                self.assertEqual(
                    ["ai", "cli", "file", "kb", "mermaid", "jira", "github"],
                    second_metadata["integrations"],
                )

    def test_repeated_main_run_redownloads_when_requested_version_changes(self) -> None:
        for installer_script in INSTALL_ENTRYPOINTS:
            with self.subTest(installer_script=installer_script.name):
                with tempfile.TemporaryDirectory() as temp_dir:
                    result = run_installer_functions(
                        """
check_java() { printf 'stub check_java\\n'; }
download_dmtools() {
    local version="$1"
    printf 'SIDE download_dmtools %s\\n' "$version"
    mkdir -p "$(dirname "$JAR_PATH")" "$BIN_DIR"
    printf 'stub jar for %s\\n' "$version" > "$JAR_PATH"
    cat > "$SCRIPT_PATH" <<'EOF'
#!/bin/bash
echo "dmtools stub"
EOF
    chmod +x "$SCRIPT_PATH"
}
update_shell_config() { printf 'SIDE update_shell_config\\n'; }
verify_installation() { printf 'SIDE verify_installation\\n'; }
print_instructions() { printf 'SIDE print_instructions\\n'; }
main --skills jira,github v1.2.3
main --skills jira,github v2.0.0
cat "$INSTALLED_SKILLS_JSON_PATH"
""",
                        {
                            "DMTOOLS_INSTALL_DIR": temp_dir,
                            "DMTOOLS_BIN_DIR": f"{temp_dir}/bin",
                            "DMTOOLS_INSTALLER_ENV_PATH": f"{temp_dir}/bin/dmtools-installer.env",
                        },
                        installer_script=installer_script,
                    )

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(1, result.stdout.count("SIDE download_dmtools v1.2.3"), result.stdout)
                self.assertEqual(1, result.stdout.count("SIDE download_dmtools v2.0.0"), result.stdout)
                self.assertIn("Selected skills already installed: jira,github", result.stdout)
                self.assertNotIn(
                    "Installer-managed artifacts already present for version v2.0.0; "
                    "skipping DMTools download.",
                    result.stdout,
                )
                self.assertIn('"version": "v2.0.0"', result.stdout)

    def test_main_writes_machine_readable_metadata_files_for_selected_skills(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            install_dir = Path(temp_dir)
            installed_skills_path = install_dir / "installed-skills.json"
            endpoints_path = install_dir / "endpoints.json"
            result = run_installer_functions(
                """
check_java() { :; }
download_dmtools() {
    mkdir -p "$(dirname "$JAR_PATH")" "$(dirname "$SCRIPT_PATH")"
    printf 'jar' > "$JAR_PATH"
    printf '#!/bin/bash\nexit 0\n' > "$SCRIPT_PATH"
    chmod +x "$SCRIPT_PATH"
}
update_shell_config() { :; }
verify_installation() { :; }
print_instructions() { :; }
main --skills jira,confluence v1.2.3
""",
                {
                    "DMTOOLS_INSTALL_DIR": str(install_dir),
                    "DMTOOLS_BIN_DIR": str(install_dir / "bin"),
                    "DMTOOLS_INSTALLER_ENV_PATH": str(install_dir / "bin" / "dmtools-installer.env"),
                },
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(installed_skills_path.exists(), result.stdout)
            self.assertTrue(endpoints_path.exists(), result.stdout)

            installed_skills_payload = json.loads(installed_skills_path.read_text(encoding="utf-8"))
            self.assertEqual("v1.2.3", installed_skills_payload["version"])
            self.assertEqual(
                [{"name": "jira"}, {"name": "confluence"}],
                installed_skills_payload["installed_skills"],
            )
            self.assertEqual(
                ["ai", "cli", "file", "kb", "mermaid", "jira", "confluence"],
                installed_skills_payload["integrations"],
            )

            endpoints_payload = json.loads(endpoints_path.read_text(encoding="utf-8"))
            self.assertEqual("v1.2.3", endpoints_payload["version"])
            self.assertEqual(
                [
                    {"name": "jira", "path": "/dmtools/jira"},
                    {"name": "confluence", "path": "/dmtools/confluence"},
                ],
                endpoints_payload["endpoints"],
            )

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
            self.assertTrue(BASE_INTEGRATIONS.issubset(actual_integrations), matching_lines[0])

    def test_advertised_skills_match_runtime_backed_selection(self) -> None:
        result = run_installer_functions(
            'printf "available=%s\\n" "$(join_by_comma "${AVAILABLE_SKILLS[@]}")"'
        )

        self.assertEqual(0, result.returncode, result.stderr)
        advertised_skills = set(result.stdout.strip().split("=", 1)[1].split(","))
        self.assertEqual({"dmtools", *RUNTIME_SKILL_INTEGRATIONS.keys()}, advertised_skills)


class TestInstallerSkillDocumentation(unittest.TestCase):
    def test_install_skills_doc_contains_required_endpoint_guidance(self) -> None:
        self.assertTrue(DOC_PATH.exists(), "docs/install-skills.md must exist")
        content = DOC_PATH.read_text(encoding="utf-8")
        self.assertIn("/dmtools/{skill}", content)
        self.assertIn("GET /dmtools/endpoints", content)
        self.assertIn("location ~ ^/dmtools/(?<skill>[a-z0-9_-]+)$", content)

    def test_env_based_examples_target_the_installer_process(self) -> None:
        for path in (*INSTALL_ENTRYPOINTS, DOC_PATH):
            with self.subTest(path=path.name):
                content = path.read_text(encoding="utf-8")
                self.assertIn("| DMTOOLS_SKILLS=jira,github bash", content)
                self.assertNotIn("DMTOOLS_SKILLS=jira,github curl", content)


if __name__ == "__main__":
    unittest.main(verbosity=2)
