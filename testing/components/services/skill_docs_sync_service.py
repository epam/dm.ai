from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from testing.components.services.skill_docs_sync_support import SkillDocsSyncSupport
from testing.core.interfaces.skill_docs_sync import AuditedDocumentConfig, Sandbox, TicketConfig
from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class SyncRunResult:
    bootstrap_docs: CommandResult
    update_docs: CommandResult
    generate_docs: CommandResult
    generated_index_refreshed: bool
    changelog_mentions_correction: bool
    skill_reference_title_synced: bool
    skill_reference_summary_synced: bool
    skill_reference_row: str

class SkillDocsSyncService:
    def __init__(
        self,
        repository_root: Path,
        sandbox_factory: Callable[[Path], Sandbox] = RepoSandbox,
        sync_support: SkillDocsSyncSupport | None = None,
    ) -> None:
        self.repository_root = repository_root
        self._sandbox_factory = sandbox_factory
        self._sync_support = sync_support or SkillDocsSyncSupport()

    def run(self, ticket_config: TicketConfig) -> SyncRunResult:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            self._sync_support.apply_manual_edits(sandbox, ticket_config)
            self._sync_support.mark_generated_index_as_stale(sandbox)
            bootstrap_docs = sandbox.run("./buildInstallLocal.sh")
            update_docs = sandbox.run(ticket_config.scripts[0])
            generate_docs = sandbox.run(ticket_config.scripts[1])

            generated_index = sandbox.read_text(self._sync_support.GENERATED_INDEX_PATH)
            changelog = sandbox.read_text("dmtools-ai-docs/CHANGELOG.md")
            skill_reference_row = self._sync_support.find_skill_reference_row(
                sandbox.read_text("dmtools-ai-docs/SKILL.md"),
                ticket_config.audited_document.skill_reference_path,
            )

            return SyncRunResult(
                bootstrap_docs=bootstrap_docs,
                update_docs=update_docs,
                generate_docs=generate_docs,
                generated_index_refreshed=self._sync_support.generated_index_was_refreshed(generated_index),
                changelog_mentions_correction=ticket_config.audited_document.changelog_marker in changelog,
                skill_reference_title_synced=ticket_config.audited_document.updated_title in skill_reference_row,
                skill_reference_summary_synced=ticket_config.audited_document.updated_summary in skill_reference_row,
                skill_reference_row=skill_reference_row,
            )
        finally:
            sandbox.cleanup()
