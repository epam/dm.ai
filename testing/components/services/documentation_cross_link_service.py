from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


MARKDOWN_LINK_PATTERN = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
MARKDOWN_HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*)$")


@dataclass(frozen=True)
class MarkdownLink:
    text: str
    target: str
    line_number: int


class DocumentationCrossLinkService:
    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self.installation_guide_path = (
            repository_root / "dmtools-ai-docs/references/installation/README.md"
        )
        self.troubleshooting_guide_path = (
            repository_root / "dmtools-ai-docs/references/installation/troubleshooting.md"
        )

    @property
    def detailed_guides(self) -> tuple[Path, Path]:
        return self.installation_guide_path, self.troubleshooting_guide_path

    @staticmethod
    def github_anchor(heading: str) -> str:
        normalized = heading.strip().lower()
        normalized = re.sub(r"[^\w\s-]", "", normalized)
        normalized = re.sub(r"\s+", "-", normalized)
        normalized = re.sub(r"-{2,}", "-", normalized).strip("-")
        return normalized

    @staticmethod
    def parse_markdown_links(markdown: str, start_line: int = 1) -> list[MarkdownLink]:
        links: list[MarkdownLink] = []
        for offset, line in enumerate(markdown.splitlines(), start=start_line):
            for match in MARKDOWN_LINK_PATTERN.finditer(line):
                links.append(
                    MarkdownLink(
                        text=match.group(1),
                        target=match.group(2),
                        line_number=offset,
                    )
                )
        return links

    def section_content(self, path: Path, heading: str) -> tuple[int, str]:
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines):
            heading_match = MARKDOWN_HEADING_PATTERN.match(line)
            if not heading_match or line.strip() != heading:
                continue

            level = len(heading_match.group(1))
            collected: list[str] = []
            for nested_index in range(index + 1, len(lines)):
                next_match = MARKDOWN_HEADING_PATTERN.match(lines[nested_index])
                if next_match and len(next_match.group(1)) <= level:
                    break
                collected.append(lines[nested_index])
            return index + 2, "\n".join(collected)

        raise ValueError(f"Heading {heading!r} not found in {path}")

    def readme_installation_links(self) -> list[MarkdownLink]:
        section_start_line, section = self.section_content(self.readme_path, "### Installation")
        return self.parse_markdown_links(section, start_line=section_start_line)

    @staticmethod
    def split_target(target: str) -> tuple[str, str]:
        link_target, _, fragment = target.partition("#")
        return link_target, fragment

    def resolve_target(self, source_path: Path, target: str) -> tuple[Path, str]:
        link_target, fragment = self.split_target(target)
        if not link_target:
            return source_path, fragment
        return (source_path.parent / link_target).resolve(), fragment

    def anchors_for(self, path: Path) -> set[str]:
        anchors: set[str] = set()
        for line in path.read_text(encoding="utf-8").splitlines():
            heading_match = MARKDOWN_HEADING_PATTERN.match(line)
            if not heading_match:
                continue
            anchors.add(self.github_anchor(heading_match.group(2)))
        return anchors

    def links_pointing_to(self, source_path: Path, target_path: Path, fragment: str) -> list[MarkdownLink]:
        matches: list[MarkdownLink] = []
        for link in self.parse_markdown_links(source_path.read_text(encoding="utf-8")):
            resolved_path, resolved_fragment = self.resolve_target(source_path, link.target)
            if resolved_path == target_path.resolve() and resolved_fragment == fragment:
                matches.append(link)
        return matches

    def relative_path(self, path: Path) -> str:
        return path.relative_to(self.repository_root).as_posix()

    def format_missing_readme_links(self, expected_paths: set[str], actual_links: list[MarkdownLink]) -> str:
        actual_targets = ", ".join(sorted(link.target for link in actual_links)) or "none"
        return (
            "README.md Quick Start > Installation must link to both detailed installation guides. "
            f"Expected links to: {', '.join(sorted(expected_paths))}. "
            f"Found links: {actual_targets}."
        )

    def format_invalid_target(self, source_path: Path, link: MarkdownLink, target_path: Path, fragment: str) -> str:
        relative_source = self.relative_path(source_path)
        relative_target = self.relative_path(target_path) if target_path.exists() else str(target_path)
        return (
            f"{relative_source}:{link.line_number} points to {link.target!r}, "
            f"but resolved target {relative_target!r} or anchor #{fragment} is invalid."
        )

    def format_missing_backlink(self, source_path: Path, found_readme_links: list[MarkdownLink]) -> str:
        relative_source = self.relative_path(source_path)
        if found_readme_links:
            link_descriptions = ", ".join(
                f"{link.target!r} (line {link.line_number})" for link in found_readme_links
            )
            return (
                f"{relative_source} does not link back to README.md#installation. "
                f"README links found instead: {link_descriptions}."
            )
        return f"{relative_source} does not contain a link back to README.md#installation."
