from pathlib import Path

from testing.components.services.per_skill_page_audit_service import (
    PerSkillPageAuditService,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_dmc_916_individual_skill_pages_follow_the_mandatory_template() -> None:
    service = PerSkillPageAuditService(REPOSITORY_ROOT)

    findings = service.audit()

    assert not findings, (
        "Per-skill documentation pages do not satisfy the required template.\n"
        "Expected child pages in dmtools-ai-docs/per-skill-packages/ to include all "
        "mandatory sections, the correct Java package identifier, the matching "
        "/dmtools- slash command, and linkbacks to the installation guide and per-skill index.\n\n"
        + "\n".join(f"- {finding}" for finding in findings)
    )
