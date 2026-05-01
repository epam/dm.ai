from __future__ import annotations

from typing import Protocol

from testing.core.utils.repo_sandbox import CommandResult


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
