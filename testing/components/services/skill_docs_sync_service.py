from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

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
    GENERATED_INDEX_PATH = "dmtools-ai-docs/references/mcp-tools/README.md"
    GENERATED_INDEX_STALE_MARKER = "DMC-897 stale MCP tools index marker"
    GENERATED_INDEX_HEADER = "# DMtools MCP Tools Reference"
    GENERATED_INDEX_FOOTER = "*Generated from MCPToolRegistry on:"

    def __init__(
        self,
        repository_root: Path,
        sandbox_factory: Callable[[Path], Sandbox] = RepoSandbox,
    ) -> None:
        self.repository_root = repository_root
        self._sandbox_factory = sandbox_factory

    def run(self, ticket_config: TicketConfig) -> SyncRunResult:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            self._apply_manual_edits(sandbox, ticket_config)
            self._mark_generated_index_as_stale(sandbox)
            bootstrap_docs = sandbox.run("./buildInstallLocal.sh")
            update_docs = sandbox.run(ticket_config.scripts[0])
            generate_docs = sandbox.run(ticket_config.scripts[1])

            generated_index = sandbox.read_text(self.GENERATED_INDEX_PATH)
            changelog = sandbox.read_text("dmtools-ai-docs/CHANGELOG.md")
            skill_reference_row = self._find_skill_reference_row(
                sandbox.read_text("dmtools-ai-docs/SKILL.md"),
                ticket_config.audited_document.skill_reference_path,
            )

            return SyncRunResult(
                bootstrap_docs=bootstrap_docs,
                update_docs=update_docs,
                generate_docs=generate_docs,
                generated_index_refreshed=self._generated_index_was_refreshed(generated_index),
                changelog_mentions_correction=ticket_config.audited_document.changelog_marker in changelog,
                skill_reference_title_synced=ticket_config.audited_document.updated_title in skill_reference_row,
                skill_reference_summary_synced=ticket_config.audited_document.updated_summary in skill_reference_row,
                skill_reference_row=skill_reference_row,
            )
        finally:
            sandbox.cleanup()

    def _apply_manual_edits(self, sandbox: Sandbox, ticket_config: TicketConfig) -> None:
        audited_document = ticket_config.audited_document
        source_path = audited_document.source_relative_path
        source_text = sandbox.read_text(source_path)
        original_heading = "# AI Teammate Configuration Guide"
        replacement_heading = (
            f"# {audited_document.updated_title}\n\n"
            f"{audited_document.updated_summary}"
        )

        if original_heading not in source_text:
            raise AssertionError(f"Expected heading '{original_heading}' was not found in {source_path}")

        sandbox.write_text(source_path, source_text.replace(original_heading, replacement_heading, 1))

        changelog_path = "dmtools-ai-docs/CHANGELOG.md"
        changelog_text = sandbox.read_text(changelog_path)
        documentation_section = "### Documentation\n\n"
        changelog_entry = f"- {audited_document.changelog_marker}\n"

        if documentation_section in changelog_text:
            updated_changelog = changelog_text.replace(
                documentation_section,
                documentation_section + changelog_entry,
                1,
            )
        else:
            updated_changelog = changelog_entry + "\n" + changelog_text

        sandbox.write_text(changelog_path, updated_changelog)

    def _mark_generated_index_as_stale(self, sandbox: Sandbox) -> None:
        sandbox.write_text(
            self.GENERATED_INDEX_PATH,
            (
                f"{self.GENERATED_INDEX_HEADER}\n\n"
                "This content was intentionally replaced before the docs scripts ran.\n\n"
                f"{self.GENERATED_INDEX_STALE_MARKER}\n"
            ),
        )

    def _generated_index_was_refreshed(self, generated_index: str) -> bool:
        return (
            self.GENERATED_INDEX_STALE_MARKER not in generated_index
            and self.GENERATED_INDEX_HEADER in generated_index
            and self.GENERATED_INDEX_FOOTER in generated_index
        )

    @staticmethod
    def _find_skill_reference_row(skill_markdown: str, skill_reference_path: str) -> str:
        for line in skill_markdown.splitlines():
            if skill_reference_path in line:
                return line
        raise AssertionError(f"Could not find SKILL.md row for {skill_reference_path}")
