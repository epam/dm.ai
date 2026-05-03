from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from testing.components.services.documentation_cross_link_service import (
    DocumentationCrossLinkService,
)


MARKDOWN_LINK_PATTERN = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
MULTISPACE_PATTERN = re.compile(r"\s+")


@dataclass(frozen=True)
class MarkdownTableRow:
    cells: tuple[str, ...]
    line_number: int


class ReadmeProductPositioningService:
    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self._cross_link_service = DocumentationCrossLinkService(repository_root)
        self._lines = self.readme_path.read_text(encoding="utf-8").splitlines()

    def heading_line_number(self, heading: str) -> int:
        for index, line in enumerate(self._lines, start=1):
            if line.strip() == heading:
                return index
        raise ValueError(f"Heading {heading!r} not found in {self.readme_path}")

    def hero_markdown(self) -> str:
        collected: list[str] = []
        for line in self._lines:
            if line.startswith("## "):
                break
            collected.append(line)
        return "\n".join(collected).strip()

    def hero_visible_text(self) -> str:
        return self.visible_text(self.hero_markdown())

    def section_visible_text(self, heading: str) -> str:
        _, section = self._cross_link_service.section_content(self.readme_path, heading)
        return self.visible_text(section)

    def markdown_table_rows(self, heading: str) -> list[MarkdownTableRow]:
        section_start_line, section = self._cross_link_service.section_content(
            self.readme_path,
            heading,
        )
        rows: list[MarkdownTableRow] = []
        for offset, line in enumerate(section.splitlines(), start=section_start_line):
            stripped = line.strip()
            if not stripped.startswith("|"):
                continue

            cells = tuple(cell.strip() for cell in stripped.strip("|").split("|"))
            if not cells or all(set(cell) <= {"-"} for cell in cells):
                continue
            rows.append(MarkdownTableRow(cells=cells, line_number=offset))
        return rows

    @staticmethod
    def visible_text(markdown: str) -> str:
        text = MARKDOWN_LINK_PATTERN.sub(r"\1", markdown)
        cleaned_lines: list[str] = []
        in_code_block = False

        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("```"):
                in_code_block = not in_code_block
                continue
            if in_code_block or not stripped:
                continue

            stripped = re.sub(r"^#+\s*", "", stripped)
            stripped = re.sub(r"^>\s?", "", stripped)
            stripped = stripped.replace("|", " ")
            cleaned_lines.append(stripped)

        return MULTISPACE_PATTERN.sub(" ", " ".join(cleaned_lines)).strip()

    @staticmethod
    def normalized_text(text: str) -> str:
        return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()

    def opening_stops_before(self, heading: str) -> bool:
        documentation_map_line = self.heading_line_number("## Documentation map")
        return self.heading_line_number(heading) < documentation_map_line
