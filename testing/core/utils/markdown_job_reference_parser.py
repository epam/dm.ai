from __future__ import annotations

from dataclasses import dataclass
import re
from pathlib import Path

from testing.core.models.job_reference import JobReference

REFERENCE_TABLE_HEADER = "| Job | Summary | Accepted `name` | Example |"
REFERENCE_TABLE_SEPARATOR = "|-----|---------|-----------------|---------|"
JSON_NAME_PATTERN = re.compile(r'"name"\s*:\s*"([^"\n]+)"')
BACKTICKED_IDENTIFIER_PATTERN = re.compile(r"`([A-Z][A-Za-z0-9]+)`")
CAMEL_CASE_IDENTIFIER_PATTERN = re.compile(r"\b([A-Z][A-Za-z0-9]+)\b")


@dataclass(frozen=True)
class MarkdownParagraph:
    start_line: int
    text: str


def extract_job_reference_table(path: Path) -> list[JobReference]:
    lines = path.read_text(encoding="utf-8").splitlines()

    for index, line in enumerate(lines):
        if line.strip() != REFERENCE_TABLE_HEADER:
            continue

        if index + 1 >= len(lines) or lines[index + 1].strip() != REFERENCE_TABLE_SEPARATOR:
            raise ValueError(f"Malformed reference table in {path}")

        table_rows: list[JobReference] = []
        for row in lines[index + 2 :]:
            stripped_row = row.strip()
            if not stripped_row.startswith("|"):
                break

            cells = [cell.strip() for cell in stripped_row.strip("|").split("|")]
            if len(cells) != 4:
                raise ValueError(f"Unexpected table row in {path}: {row}")

            table_rows.append(JobReference.from_markdown_cells(*cells))

        if not table_rows:
            raise ValueError(f"Reference table in {path} is empty")

        return table_rows

    raise ValueError(f"Reference table header not found in {path}")


def extract_json_name_values(path: Path) -> list[str]:
    content = path.read_text(encoding="utf-8")
    return JSON_NAME_PATTERN.findall(content)


def extract_identifier_tokens(path: Path) -> list[str]:
    content = path.read_text(encoding="utf-8")
    tokens = set(JSON_NAME_PATTERN.findall(content))
    tokens.update(BACKTICKED_IDENTIFIER_PATTERN.findall(content))
    tokens.update(CAMEL_CASE_IDENTIFIER_PATTERN.findall(content))
    return sorted(tokens)


def extract_markdown_paragraphs(path: Path) -> list[MarkdownParagraph]:
    paragraphs: list[MarkdownParagraph] = []
    current_lines: list[str] = []
    current_start_line = 0
    inside_fence = False

    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped_line = line.strip()

        if stripped_line.startswith("```"):
            if current_lines:
                paragraphs.append(
                    MarkdownParagraph(
                        start_line=current_start_line,
                        text=" ".join(current_lines),
                    )
                )
                current_lines = []
                current_start_line = 0
            inside_fence = not inside_fence
            continue

        if inside_fence:
            continue

        if not stripped_line:
            if current_lines:
                paragraphs.append(
                    MarkdownParagraph(
                        start_line=current_start_line,
                        text=" ".join(current_lines),
                    )
                )
                current_lines = []
                current_start_line = 0
            continue

        if not current_lines:
            current_start_line = line_number
        current_lines.append(stripped_line)

    if current_lines:
        paragraphs.append(
            MarkdownParagraph(
                start_line=current_start_line,
                text=" ".join(current_lines),
            )
        )

    return paragraphs
