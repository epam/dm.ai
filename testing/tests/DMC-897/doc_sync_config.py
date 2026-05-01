from dataclasses import dataclass


@dataclass(frozen=True)
class SkillDocumentAudit:
    source_relative_path: str
    skill_reference_path: str
    updated_title: str
    updated_summary: str
    changelog_marker: str


@dataclass(frozen=True)
class TicketConfig:
    ticket_key: str
    scripts: tuple[str, ...]
    audited_document: SkillDocumentAudit


DMC_897_CONFIG = TicketConfig(
    ticket_key="DMC-897",
    scripts=(
        "./scripts/update-skill-docs.sh",
        "./scripts/generate-mcp-docs.sh",
    ),
    audited_document=SkillDocumentAudit(
        source_relative_path="dmtools-ai-docs/references/agents/teammate-configs.md",
        skill_reference_path="references/agents/teammate-configs.md",
        updated_title="Audited Teammate Configurations",
        updated_summary="Audited configuration reference for teammate workflow documentation corrections.",
        changelog_marker="Documentation corrections: teammate configuration references were audited for the skill index.",
    ),
)
