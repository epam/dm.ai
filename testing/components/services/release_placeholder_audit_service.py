from __future__ import annotations

import re
from pathlib import Path


class ReleasePlaceholderAuditService:
    _DEFAULT_DOC_RELATIVE_PATH = "dmtools-ai-docs/references/installation/README.md"
    _RELEASE_TAG_PLACEHOLDER_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/download/\$\{DMTOOLS_RELEASE_TAG\}/"
    )
    _VERSION_PLACEHOLDER_PATTERN = re.compile(
        r"\$\{DMTOOLS_VERSION\}"
    )
    _VERSIONED_RELEASE_URL_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/download/v\d+\.\d+\.\d+/"
    )
    _LATEST_RELEASE_URL_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/latest/download/"
    )
    _RELEASE_TAG_REFERENCE_PATTERN = re.compile(r"release[- ]tags?", re.IGNORECASE)

    def __init__(
        self,
        repository_root: Path,
        doc_relative_path: str | None = None,
    ) -> None:
        self.repository_root = repository_root
        self.doc_path = repository_root / (
            doc_relative_path or self._DEFAULT_DOC_RELATIVE_PATH
        )

    def release_tag_placeholder_references(self) -> list[tuple[int, str]]:
        return [
            (line_number, line.strip())
            for line_number, line in self._iter_lines()
            if self._RELEASE_TAG_PLACEHOLDER_PATTERN.search(line)
        ]

    def version_placeholder_references(self) -> list[tuple[int, str]]:
        return [
            (line_number, line.strip())
            for line_number, line in self._iter_lines()
            if self._VERSION_PLACEHOLDER_PATTERN.search(line)
            and ("releases/download/" in line or "dmtools-v${DMTOOLS_VERSION}" in line)
        ]

    def version_pinned_release_examples(self) -> list[tuple[int, str]]:
        return [
            (line_number, line.strip())
            for line_number, line in self._iter_lines()
            if self._VERSIONED_RELEASE_URL_PATTERN.search(line)
        ]

    def latest_release_examples(self) -> list[tuple[int, str]]:
        return [
            (line_number, line.strip())
            for line_number, line in self._iter_lines()
            if self._LATEST_RELEASE_URL_PATTERN.search(line)
        ]

    def latest_release_note_paragraphs(self) -> list[str]:
        return [
            paragraph
            for paragraph in self._prose_paragraphs()
            if self._is_mutable_latest_note(paragraph)
        ]

    def audit(self) -> list[str]:
        findings: list[str] = []

        release_tag_references = self.release_tag_placeholder_references()
        if not release_tag_references:
            findings.append(
                "Missing a reusable installation snippet that uses "
                "${DMTOOLS_RELEASE_TAG} in the GitHub release download path.\n"
                + self._format_line_matches(
                    "Observed hard-coded release-tag examples",
                    self.version_pinned_release_examples(),
                )
            )

        version_references = self.version_placeholder_references()
        if not version_references:
            findings.append(
                "Missing a reusable installation snippet that uses "
                "${DMTOOLS_VERSION} for a version-pinned release example."
            )

        latest_examples = self.latest_release_examples()
        if not latest_examples:
            findings.append(
                "Expected at least one installation example that uses the "
                "'latest' release alias."
            )
        elif not self.latest_release_note_paragraphs():
            findings.append(
                "Found installation examples that use the 'latest' release alias, "
                "but the guide does not include an explicit user-facing note that "
                "'latest' is mutable and pinned release-tags are the authoritative "
                "reference.\n"
                + self._format_line_matches(
                    "Latest-alias examples",
                    latest_examples,
                )
            )

        return findings

    def format_findings(self, findings: list[str]) -> str:
        return "\n\n".join(
            (
                "Installation release placeholder audit failed for "
                f"{self.doc_path.relative_to(self.repository_root)}:",
                *[f"- {finding}" for finding in findings],
            )
        )

    def _iter_lines(self) -> list[tuple[int, str]]:
        return list(
            enumerate(
                self.doc_path.read_text(encoding="utf-8").splitlines(),
                start=1,
            )
        )

    def _prose_paragraphs(self) -> list[str]:
        paragraphs: list[str] = []
        current_lines: list[str] = []
        in_code_block = False

        for _, raw_line in self._iter_lines():
            stripped = raw_line.strip()
            if stripped.startswith("```"):
                if current_lines:
                    paragraphs.append(" ".join(current_lines))
                    current_lines = []
                in_code_block = not in_code_block
                continue

            if in_code_block:
                continue

            if not stripped:
                if current_lines:
                    paragraphs.append(" ".join(current_lines))
                    current_lines = []
                continue

            if stripped.startswith("|"):
                continue

            current_lines.append(stripped)

        if current_lines:
            paragraphs.append(" ".join(current_lines))

        return paragraphs

    def _is_mutable_latest_note(self, paragraph: str) -> bool:
        normalized = " ".join(paragraph.casefold().split())
        return (
            "latest" in normalized
            and "mutable" in normalized
            and "pinned" in normalized
            and "authoritative" in normalized
            and self._RELEASE_TAG_REFERENCE_PATTERN.search(normalized) is not None
        )

    def _format_line_matches(
        self,
        title: str,
        matches: list[tuple[int, str]],
    ) -> str:
        if not matches:
            return f"{title}: none."

        rendered_matches = "\n".join(
            f"  - line {line_number}: {line}"
            for line_number, line in matches
        )
        return f"{title}:\n{rendered_matches}"
