from __future__ import annotations

import re
from pathlib import Path

from testing.core.models.job_reference import JobReference

REFERENCE_TABLE_HEADER = "| Job | Summary | Accepted `name` | Example |"
REFERENCE_TABLE_SEPARATOR = "|-----|---------|-----------------|---------|"
JSON_NAME_PATTERN = re.compile(r'"name"\s*:\s*"([^"\n]+)"')
BACKTICKED_IDENTIFIER_PATTERN = re.compile(r"`([A-Z][A-Za-z0-9]+)`")
JOB_LIKE_SUFFIXES = (
    "Teammate",
    "Runner",
    "Generator",
    "Report",
    "Creator",
    "Support",
    "Daily",
    "Job",
    "Expert",
)


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


def extract_backticked_job_like_identifiers(path: Path) -> list[str]:
    content = path.read_text(encoding="utf-8")
    return [
        token
        for token in BACKTICKED_IDENTIFIER_PATTERN.findall(content)
        if _looks_like_job_identifier(token)
    ]


def _looks_like_job_identifier(token: str) -> bool:
    if token in {"Teammate", "JSRunner", "Expert"}:
        return True
    return token.endswith(JOB_LIKE_SUFFIXES)

