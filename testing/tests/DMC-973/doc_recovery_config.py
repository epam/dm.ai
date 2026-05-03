from testing.components.services.mcp_docs_recovery_service import (
    McpDocsRecoveryConfig,
    SkillDocumentAudit,
)


DMC_973_CONFIG = McpDocsRecoveryConfig(
    ticket_key="DMC-973",
    docs_directory="dmtools-ai-docs/references/mcp-tools",
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
