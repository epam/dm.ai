import sys
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.skill_summary_audit_service import (  # noqa: E402
    SkillSummaryAuditService,
)


class TestDMC896SkillSummaries(unittest.TestCase):
    def test_skill_descriptions_are_concise_and_technical(self) -> None:
        service = SkillSummaryAuditService(
            repository_root=REPOSITORY_ROOT,
            skill_roots=["dmtools-ai-docs", "agents/.github/skills"],
        )

        audits = service.audit_all()

        self.assertGreater(
            len(audits),
            0,
            "No SKILL.md descriptions were found in the configured roots.",
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
                "Skill summary audit failed for "
                f"{len(failures)} of {len(audits)} descriptions:\n{details}"
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
