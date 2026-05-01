import sys
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.skill_summary_audit_service import (  # noqa: E402
    SkillSummaryAuditService,
)
from testing.core.utils.ticket_config_loader import load_ticket_config  # noqa: E402


TEST_DIRECTORY = Path(__file__).resolve().parent
CONFIG = load_ticket_config(TEST_DIRECTORY / "config.yaml")
EXPECTED_AGENTS = list(CONFIG.get("expected_agents", []))


class TestDMC896SkillSummaries(unittest.TestCase):
    def test_ticket_scope_matches_expected_agents(self) -> None:
        service = SkillSummaryAuditService(
            repository_root=REPOSITORY_ROOT,
            summary_table_path=str(CONFIG["summary_table_path"]),
        )

        audits = service.audit_all()
        audited_agents = [audit.name for audit in audits]

        self.assertEqual(
            10,
            len(EXPECTED_AGENTS),
            "The ticket must explicitly configure the 10 audited agents.",
        )
        self.assertListEqual(
            EXPECTED_AGENTS,
            audited_agents,
            "The audited agent set does not match the ticket's 10-agent scope.",
        )

    def test_agent_summaries_are_concise_and_technical(self) -> None:
        service = SkillSummaryAuditService(
            repository_root=REPOSITORY_ROOT,
            summary_table_path=str(CONFIG["summary_table_path"]),
        )

        audits = service.audit_all()

        self.assertGreater(
            len(audits),
            0,
            "No agent summaries were found in the configured job reference table.",
        )

        failures = [audit for audit in audits if not audit.is_valid]
        if failures:
            details = "\n".join(
                [
                    (
                        f"- {audit.name} ({audit.path.relative_to(REPOSITORY_ROOT)}): "
                        + "; ".join(audit.failure_reasons())
                        + f" | description={audit.description!r}"
                    )
                    for audit in failures
                ]
            )
            self.fail(
                "Agent summary audit failed for "
                f"{len(failures)} of {len(audits)} descriptions:\n{details}"
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
