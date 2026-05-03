import sys
from pathlib import Path
from runpy import run_path


REPO_ROOT = Path(__file__).resolve().parents[3]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from testing.components.services.mcp_docs_recovery_service import McpDocsRecoveryService
from testing.core.utils.repo_sandbox import CommandResult


DMC_973_CONFIG = run_path(str(Path(__file__).with_name("doc_recovery_config.py")))["DMC_973_CONFIG"]


def test_dmc_973_generate_mcp_docs_recreates_missing_target_directory() -> None:
    service = McpDocsRecoveryService(REPO_ROOT)
    result = service.run(DMC_973_CONFIG)

    failures: list[str] = []

    if result.bootstrap_docs.returncode != 0:
        failures.append(
            "buildInstallLocal.sh failed while preparing the isolated repository.\n"
            f"{result.bootstrap_docs.combined_output}"
        )
    if result.update_docs.returncode != 0:
        failures.append(
            "scripts/update-skill-docs.sh failed before the missing-folder recovery step.\n"
            f"{result.update_docs.combined_output}"
        )
    if result.generate_docs.returncode != 0:
        failures.append(
            "scripts/generate-mcp-docs.sh failed after dmtools-ai-docs/references/mcp-tools/ was removed.\n"
            f"{result.generate_docs.combined_output}"
        )
    if not result.docs_directory_recreated:
        failures.append(
            "scripts/generate-mcp-docs.sh did not recreate dmtools-ai-docs/references/mcp-tools/."
        )
    if not result.generated_index_refreshed:
        failures.append(
            "The generated MCP tools index was not recreated with the expected header/footer markers."
        )
    if not result.changelog_mentions_correction:
        failures.append(
            "CHANGELOG.md no longer mentions the audited documentation correction after the docs flow ran."
        )
    if not result.skill_reference_title_synced:
        failures.append(
            "SKILL.md did not keep the teammate guide title at the latest synced value after regenerating MCP docs.\n"
            f"Actual row: {result.skill_reference_row}"
        )
    if not result.skill_reference_summary_synced:
        failures.append(
            "SKILL.md did not keep the teammate guide summary at the latest synced value after regenerating MCP docs.\n"
            f"Actual row: {result.skill_reference_row}"
        )

    assert not failures, "\n\n".join(failures)


class FakeSandbox:
    def __init__(self, root: Path) -> None:
        self.workspace = root / "workspace"
        self.workspace.mkdir(parents=True)
        self.commands: list[str] = []
        self.cleaned_up = False

        self._write(
            "dmtools-ai-docs/references/agents/teammate-configs.md",
            "# AI Teammate Configuration Guide\n\nOriginal summary.\n",
        )
        self._write(
            "dmtools-ai-docs/CHANGELOG.md",
            "## 1.7.133\n\n### Documentation\n\n- Existing item\n",
        )
        self._write(
            "dmtools-ai-docs/references/mcp-tools/README.md",
            "# DMtools MCP Tools Reference\n\nplaceholder\n",
        )
        self._write(
            "dmtools-ai-docs/SKILL.md",
            (
                "| **Agents** | [Teammate Configs](references/agents/teammate-configs.md) | "
                "Legacy summary |\n"
            ),
        )

    def cleanup(self) -> None:
        self.cleaned_up = True

    def read_text(self, relative_path: str) -> str:
        return (self.workspace / relative_path).read_text(encoding="utf-8")

    def write_text(self, relative_path: str, content: str) -> None:
        self._write(relative_path, content)

    def run(self, command: str, timeout: int = 1800) -> CommandResult:
        del timeout
        self.commands.append(command)

        if command == "./scripts/update-skill-docs.sh":
            self._write(
                "dmtools-ai-docs/SKILL.md",
                (
                    "| **Agents** | [Audited Teammate Configurations]"
                    "(references/agents/teammate-configs.md) | "
                    "Audited configuration reference for teammate workflow documentation corrections. |\n"
                ),
            )
        elif command == "./scripts/generate-mcp-docs.sh":
            self._write(
                "dmtools-ai-docs/references/mcp-tools/README.md",
                "# DMtools MCP Tools Reference\n\n*Generated from MCPToolRegistry on: Sun May 03 00:00:00 UTC 2026*\n",
            )

        return CommandResult(
            command=command,
            returncode=0,
            stdout="",
            stderr="",
        )

    def _write(self, relative_path: str, content: str) -> None:
        path = self.workspace / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def test_dmc_973_service_supports_injected_sandbox_recovery(tmp_path: Path) -> None:
    sandbox = FakeSandbox(tmp_path)
    service = McpDocsRecoveryService(REPO_ROOT, sandbox_factory=lambda _: sandbox)

    result = service.run(DMC_973_CONFIG)

    assert sandbox.commands == [
        "./buildInstallLocal.sh",
        "./scripts/update-skill-docs.sh",
        "./scripts/generate-mcp-docs.sh",
    ]
    assert result.docs_directory_recreated is True
    assert result.generated_index_refreshed is True
    assert result.changelog_mentions_correction is True
    assert result.skill_reference_title_synced is True
    assert result.skill_reference_summary_synced is True
    assert sandbox.cleaned_up is True
