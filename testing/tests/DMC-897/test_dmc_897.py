from pathlib import Path
import sys


REPO_ROOT = Path(__file__).resolve().parents[3]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from testing.components.services.skill_docs_sync_service import SkillDocsSyncService
from testing.core.config.doc_sync_config import DMC_897_CONFIG


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
