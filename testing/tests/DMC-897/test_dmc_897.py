import sys
from pathlib import Path
from runpy import run_path


REPO_ROOT = Path(__file__).resolve().parents[3]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from testing.components.services.skill_docs_sync_service import SkillDocsSyncService


DMC_897_CONFIG = run_path(str(Path(__file__).with_name("doc_sync_config.py")))["DMC_897_CONFIG"]


def test_dmc_897_skill_docs_stay_synchronized() -> None:
    service = SkillDocsSyncService(REPO_ROOT)
    result = service.run(DMC_897_CONFIG)

    failures: list[str] = []

    if result.bootstrap_docs.returncode != 0:
        failures.append(
            "buildInstallLocal.sh failed while preparing the isolated repository.\n"
            f"{result.bootstrap_docs.combined_output}"
        )
    if result.update_docs.returncode != 0:
        failures.append(
            "scripts/update-skill-docs.sh failed.\n"
            f"{result.update_docs.combined_output}"
        )
    if result.generate_docs.returncode != 0:
        failures.append(
            "scripts/generate-mcp-docs.sh failed.\n"
            f"{result.generate_docs.combined_output}"
        )
    if not result.generated_index_refreshed:
        failures.append("The generated MCP tools index was not refreshed by the documentation scripts.")
    if not result.changelog_mentions_correction:
        failures.append(
            "CHANGELOG.md no longer mentions the audited documentation correction after running the scripts."
        )
    if not result.skill_reference_title_synced:
        failures.append(
            "SKILL.md did not update the teammate guide title after the source document changed.\n"
            f"Actual row: {result.skill_reference_row}"
        )
    if not result.skill_reference_summary_synced:
        failures.append(
            "SKILL.md did not update the teammate guide summary after the source document changed.\n"
            f"Actual row: {result.skill_reference_row}"
        )

    assert not failures, "\n\n".join(failures)


class FakeSandbox:
    def __init__(self, skill_row: str, refresh_generated_index: bool = True) -> None:
        self._files = {
            "dmtools-ai-docs/references/agents/teammate-configs.md": "# AI Teammate Configuration Guide\n\nOriginal summary.",
            "dmtools-ai-docs/CHANGELOG.md": "## 1.7.133\n\n### Documentation\n\n- Existing item\n",
            "dmtools-ai-docs/references/mcp-tools/README.md": "Initial README content",
            "dmtools-ai-docs/SKILL.md": skill_row,
        }
        self.commands: list[str] = []
        self.cleaned_up = False
        self.refresh_generated_index = refresh_generated_index

    def cleanup(self) -> None:
        self.cleaned_up = True

    def read_text(self, relative_path: str) -> str:
        return self._files[relative_path]

    def write_text(self, relative_path: str, content: str) -> None:
        self._files[relative_path] = content

    def run(self, command: str, timeout: int = 1800):
        del timeout
        self.commands.append(command)
        if command == "./scripts/generate-mcp-docs.sh" and self.refresh_generated_index:
            self._files["dmtools-ai-docs/references/mcp-tools/README.md"] = (
                "# DMtools MCP Tools Reference\n\n"
                "**Total Integrations**: 17\n\n"
                "*Generated from MCPToolRegistry on: Fri May 01 21:34:54 UTC 2026*"
            )
        return type(
            "CommandResultLike",
            (),
            {"command": command, "returncode": 0, "stdout": "", "stderr": "", "combined_output": ""},
        )()

def test_dmc_897_service_accepts_injected_sandbox_without_masking_skill_sync_bug() -> None:
    skill_row = (
        "| | [Teammate Configs|references/agents/teammate-configs.md] | "
        "JSON-based AI workflows (CLI safety v1.7.133+) |"
    )
    sandbox = FakeSandbox(skill_row)
    service = SkillDocsSyncService(REPO_ROOT, sandbox_factory=lambda _: sandbox)

    result = service.run(DMC_897_CONFIG)

    assert sandbox.commands == [
        "./buildInstallLocal.sh",
        "./scripts/update-skill-docs.sh",
        "./scripts/generate-mcp-docs.sh",
    ]
    assert result.generated_index_refreshed is True
    assert result.changelog_mentions_correction is True
    assert result.skill_reference_title_synced is False
    assert result.skill_reference_summary_synced is False
    assert result.skill_reference_row == skill_row
    assert sandbox.cleaned_up is True


def test_dmc_897_service_detects_stale_generated_index() -> None:
    skill_row = (
        "| | [Teammate Configs|references/agents/teammate-configs.md] | "
        "JSON-based AI workflows (CLI safety v1.7.133+) |"
    )
    sandbox = FakeSandbox(skill_row, refresh_generated_index=False)
    service = SkillDocsSyncService(REPO_ROOT, sandbox_factory=lambda _: sandbox)

    result = service.run(DMC_897_CONFIG)

    assert result.generated_index_refreshed is False
