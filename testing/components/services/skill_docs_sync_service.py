from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

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


class AuditedDocumentConfig(Protocol):
    source_relative_path: str
    skill_reference_path: str
    updated_title: str
    updated_summary: str
    changelog_marker: str


class TicketConfig(Protocol):
    scripts: tuple[str, ...]
    audited_document: AuditedDocumentConfig


class Sandbox(Protocol):
    def cleanup(self) -> None: ...

    def read_text(self, relative_path: str) -> str: ...

    def write_text(self, relative_path: str, content: str) -> None: ...

    def run(self, command: str, timeout: int = 1800) -> CommandResult: ...


class SkillDocsSyncService:
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
            bootstrap_docs = sandbox.run("./buildInstallLocal.sh")
            update_docs = sandbox.run(ticket_config.scripts[0])
            generate_docs = sandbox.run(ticket_config.scripts[1])

            generated_index = sandbox.read_text("dmtools-ai-docs/references/mcp-tools/README.md")
            changelog = sandbox.read_text("dmtools-ai-docs/CHANGELOG.md")
            skill_reference_row = self._find_skill_reference_row(
                sandbox.read_text("dmtools-ai-docs/SKILL.md"),
                ticket_config.audited_document.skill_reference_path,
            )

            return SyncRunResult(
                bootstrap_docs=bootstrap_docs,
                update_docs=update_docs,
                generate_docs=generate_docs,
                generated_index_refreshed="Auto-generated from `dmtools list` on:" in generated_index,
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

    @staticmethod
    def _find_skill_reference_row(skill_markdown: str, skill_reference_path: str) -> str:
        for line in skill_markdown.splitlines():
            if skill_reference_path in line:
                return line
        raise AssertionError(f"Could not find SKILL.md row for {skill_reference_path}")
