import sys
import tempfile
import textwrap
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

        self.assertEqual(
            10,
            len(EXPECTED_AGENTS),
            "The ticket must explicitly configure the 10 audited agents.",
        )
        self.assertEqual(
            len(EXPECTED_AGENTS),
            len(set(EXPECTED_AGENTS)),
            "The configured ticket scope must not contain duplicate agents.",
        )

        available_agents = service.available_agent_names()
        missing_agents = [
            agent for agent in EXPECTED_AGENTS if agent not in available_agents
        ]

        self.assertFalse(
            missing_agents,
            "The configured ticket scope is missing audited agents from the "
            f"reference table: {', '.join(missing_agents)}",
        )

    def test_agent_summaries_are_concise_and_technical(self) -> None:
        service = SkillSummaryAuditService(
            repository_root=REPOSITORY_ROOT,
            summary_table_path=str(CONFIG["summary_table_path"]),
        )

        audits = service.audit_all(EXPECTED_AGENTS)

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

    def test_expected_agents_are_audited_without_requiring_an_exact_table_match(
        self,
    ) -> None:
        table = textwrap.dedent(
            """
            | Job | Summary |
            |-----|---------|
            | `ExtraJob` | Extra rows should not affect the configured audit scope. |
            | `TargetA` | Target summaries stay concise and active. |
            | `TargetB` | Target summaries remain technical and brief. |
            """
        ).strip()

        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            table_path = temp_root / "summaries.md"
            table_path.write_text(table, encoding="utf-8")
            service = SkillSummaryAuditService(
                repository_root=temp_root,
                summary_table_path=table_path.name,
            )

            audits = service.audit_all(["TargetB", "TargetA"])

        self.assertListEqual(["TargetB", "TargetA"], [audit.name for audit in audits])

    def test_active_voice_does_not_require_a_leading_action_verb(self) -> None:
        table = textwrap.dedent(
            """
            | Job | Summary |
            |-----|---------|
            | `TargetA` | This summary describes technical behavior without filler. |
            """
        ).strip()

        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            table_path = temp_root / "summaries.md"
            table_path.write_text(table, encoding="utf-8")
            service = SkillSummaryAuditService(
                repository_root=temp_root,
                summary_table_path=table_path.name,
            )

            audits = service.audit_all(["TargetA"])

        self.assertTrue(audits[0].is_valid)


if __name__ == "__main__":
    unittest.main(verbosity=2)
