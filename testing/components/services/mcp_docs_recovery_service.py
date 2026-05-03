from __future__ import annotations

import shutil
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from testing.components.services.skill_docs_sync_support import SkillDocsSyncSupport
from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class SkillDocumentAudit:
    source_relative_path: str
    skill_reference_path: str
    updated_title: str
    updated_summary: str
    changelog_marker: str


@dataclass(frozen=True)
class McpDocsRecoveryConfig:
    ticket_key: str
    docs_directory: str
    scripts: tuple[str, ...]
    audited_document: SkillDocumentAudit


@dataclass(frozen=True)
class McpDocsRecoveryResult:
    bootstrap_docs: CommandResult
    update_docs: CommandResult
    generate_docs: CommandResult
    docs_directory_recreated: bool
    generated_index_refreshed: bool
    changelog_mentions_correction: bool
    skill_reference_title_synced: bool
    skill_reference_summary_synced: bool
    skill_reference_row: str


class Sandbox(Protocol):
    workspace: Path

    def cleanup(self) -> None: ...

    def read_text(self, relative_path: str) -> str: ...

    def write_text(self, relative_path: str, content: str) -> None: ...

    def run(self, command: str, timeout: int = 1800) -> CommandResult: ...


class McpDocsRecoveryService:
    def __init__(
        self,
        repository_root: Path,
        sandbox_factory: Callable[[Path], Sandbox] = RepoSandbox,
        sync_support: SkillDocsSyncSupport | None = None,
    ) -> None:
        self.repository_root = repository_root
        self._sandbox_factory = sandbox_factory
        self._sync_support = sync_support or SkillDocsSyncSupport()

    def run(self, ticket_config: McpDocsRecoveryConfig) -> McpDocsRecoveryResult:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            self._sync_support.apply_manual_edits(sandbox, ticket_config)
            self._sync_support.mark_generated_index_as_stale(sandbox)

            bootstrap_docs = sandbox.run("./buildInstallLocal.sh")
            update_docs = sandbox.run(ticket_config.scripts[0])

            docs_directory = sandbox.workspace / ticket_config.docs_directory
            shutil.rmtree(docs_directory, ignore_errors=True)

            generate_docs = sandbox.run(ticket_config.scripts[1])

            generated_index = self._read_text_if_present(sandbox, self._sync_support.GENERATED_INDEX_PATH)
            changelog = self._read_text_if_present(sandbox, "dmtools-ai-docs/CHANGELOG.md")
            skill_markdown = self._read_text_if_present(sandbox, "dmtools-ai-docs/SKILL.md")
            skill_reference_row = self._find_skill_reference_row_or_empty(
                skill_markdown,
                ticket_config.audited_document.skill_reference_path,
            )

            return McpDocsRecoveryResult(
                bootstrap_docs=bootstrap_docs,
                update_docs=update_docs,
                generate_docs=generate_docs,
                docs_directory_recreated=docs_directory.is_dir(),
                generated_index_refreshed=generated_index is not None
                and self._sync_support.generated_index_was_refreshed(generated_index),
                changelog_mentions_correction=changelog is not None
                and ticket_config.audited_document.changelog_marker in changelog,
                skill_reference_title_synced=ticket_config.audited_document.updated_title in skill_reference_row,
                skill_reference_summary_synced=ticket_config.audited_document.updated_summary
                in skill_reference_row,
                skill_reference_row=skill_reference_row,
            )
        finally:
            sandbox.cleanup()

    @staticmethod
    def _read_text_if_present(sandbox: Sandbox, relative_path: str) -> str | None:
        if not (sandbox.workspace / relative_path).exists():
            return None
        return sandbox.read_text(relative_path)

    @staticmethod
    def _find_skill_reference_row_or_empty(
        skill_markdown: str | None,
        skill_reference_path: str,
    ) -> str:
        if skill_markdown is None:
            return ""
        try:
            return SkillDocsSyncSupport.find_skill_reference_row(skill_markdown, skill_reference_path)
        except AssertionError:
            return ""
