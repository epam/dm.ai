from __future__ import annotations

from abc import ABC, abstractmethod


class ReleasePlaceholderAudit(ABC):
    @abstractmethod
    def release_tag_placeholder_references(self) -> list[tuple[int, str]]:
        raise NotImplementedError

    @abstractmethod
    def version_placeholder_references(self) -> list[tuple[int, str]]:
        raise NotImplementedError

    @abstractmethod
    def version_pinned_release_examples(self) -> list[tuple[int, str]]:
        raise NotImplementedError

    @abstractmethod
    def latest_release_examples(self) -> list[tuple[int, str]]:
        raise NotImplementedError

    @abstractmethod
    def latest_release_note_paragraphs(self) -> list[str]:
        raise NotImplementedError

    @abstractmethod
    def audit(self) -> list[str]:
        raise NotImplementedError

    @abstractmethod
    def format_findings(self, findings: list[str]) -> str:
        raise NotImplementedError
