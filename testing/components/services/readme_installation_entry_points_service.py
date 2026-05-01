from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


EXPECTED_BASH_COMMAND = (
    "curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash"
)
EXPECTED_POWERSHELL_PATH = (
    "https://github.com/epam/dm.ai/releases/latest/download/install.ps1"
)
EXPECTED_RELEASES_URL = "https://github.com/epam/dm.ai/releases/latest"
EXPECTED_INSTALLATION_TOC_LINK = "[Installation](#installation)"
HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")
MARKDOWN_LINK_PATTERN = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


@dataclass(frozen=True)
class ValidationFailure:
    step: int
    summary: str
    expected: str
    actual: str

    def format(self) -> str:
        return (
            f"Step {self.step}: {self.summary}\n"
            f"Expected: {self.expected}\n"
            f"Actual: {self.actual}"
        )


class ReadmeInstallationEntryPointsService:
    def __init__(self, repository_root: Path) -> None:
        self.readme_path = repository_root / "README.md"
        self.readme_text = self.readme_path.read_text(encoding="utf-8")
        self.lines = self.readme_text.splitlines()

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        banner_block = self._first_content_block_after_title()
        banner_preview = self._preview(banner_block)

        if not banner_block:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="README quick-install banner is missing directly under the title.",
                    expected="A quick-install banner block immediately after '# DMTools'.",
                    actual="No content block was found after the title.",
                )
            )
        elif EXPECTED_BASH_COMMAND not in banner_block or EXPECTED_POWERSHELL_PATH not in banner_block:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="The first block under the README title is not the required quick-install banner.",
                    expected=(
                        "The first content block after '# DMTools' should expose the latest-release "
                        "Bash and PowerShell install entry points."
                    ),
                    actual=f"First block content: {banner_preview}",
                )
            )

        if EXPECTED_BASH_COMMAND not in banner_block:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="README does not expose the required Bash installer command.",
                    expected=EXPECTED_BASH_COMMAND,
                    actual=f"Banner block content: {banner_preview}",
                )
            )

        if EXPECTED_POWERSHELL_PATH not in banner_block:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="README does not expose the required PowerShell installer path.",
                    expected=EXPECTED_POWERSHELL_PATH,
                    actual=f"Banner block content: {banner_preview}",
                )
            )

        table_of_contents = self._section_body("Table of Contents")
        headings_preview = ", ".join(self.headings()) or "none"
        if not table_of_contents:
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="README is missing a Table of Contents section for Installation discovery.",
                    expected="A 'Table of Contents' section containing an Installation anchor link.",
                    actual=f"Available headings: {headings_preview}",
                )
            )
        elif EXPECTED_INSTALLATION_TOC_LINK not in table_of_contents:
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="Table of Contents does not link to the Installation section.",
                    expected=EXPECTED_INSTALLATION_TOC_LINK,
                    actual=f"Table of Contents content: {self._preview(table_of_contents)}",
                )
            )

        installation_section = self._section_body("Installation")
        if not installation_section:
            failures.append(
                ValidationFailure(
                    step=5,
                    summary="README Installation section is missing.",
                    expected="An Installation section with a canonical releases link.",
                    actual=f"Available headings: {headings_preview}",
                )
            )
        else:
            installation_links = MARKDOWN_LINK_PATTERN.findall(installation_section)
            if EXPECTED_RELEASES_URL not in installation_links:
                actual_links = ", ".join(installation_links) if installation_links else "no markdown links found"
                failures.append(
                    ValidationFailure(
                        step=5,
                        summary="Installation section does not link to the canonical latest releases page.",
                        expected=EXPECTED_RELEASES_URL,
                        actual=f"Installation section links: {actual_links}",
                    )
                )

        return failures

    def headings(self) -> list[str]:
        headings: list[str] = []
        for line in self.lines:
            match = HEADING_PATTERN.match(line)
            if match:
                headings.append(match.group(2))
        return headings

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def _first_content_block_after_title(self) -> str:
        title_index = next(
            (index for index, line in enumerate(self.lines) if line.strip().startswith("# ")),
            None,
        )
        if title_index is None:
            return ""

        block_lines: list[str] = []
        started = False
        for line in self.lines[title_index + 1 :]:
            if not started and not line.strip():
                continue
            if started and not line.strip():
                break
            started = True
            block_lines.append(line)

        return "\n".join(block_lines).strip()

    def _section_body(self, section_title: str) -> str:
        section_index: int | None = None
        section_level: int | None = None

        for index, line in enumerate(self.lines):
            match = HEADING_PATTERN.match(line)
            if not match:
                continue
            if match.group(2).strip().lower() == section_title.lower():
                section_index = index
                section_level = len(match.group(1))
                break

        if section_index is None or section_level is None:
            return ""

        body_lines: list[str] = []
        for line in self.lines[section_index + 1 :]:
            match = HEADING_PATTERN.match(line)
            if match and len(match.group(1)) <= section_level:
                break
            body_lines.append(line)

        return "\n".join(body_lines).strip()

    @staticmethod
    def _preview(value: str, limit: int = 240) -> str:
        compact = re.sub(r"\s+", " ", value.strip())
        if not compact:
            return "<empty>"
        if len(compact) <= limit:
            return compact
        return compact[: limit - 3] + "..."
