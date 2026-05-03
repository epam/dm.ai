from __future__ import annotations
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SkillCatalogExpectation:
    skill_name: str
    slash_command: str
    java_package: str
    artifact_alias: str


@dataclass(frozen=True)
class CatalogRow:
    line_number: int
    text: str
    cells: tuple[str, ...]


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


class PerSkillCatalogService:
    CATALOG_RELATIVE_PATH = "dmtools-ai-docs/per-skill-packages/index.md"
    REQUIRED_COLUMN_COUNT = 4
    SEPARATOR_CELL_PATTERN = re.compile(r"^:?-{3,}:?$")
    MARKDOWN_LINK_PATTERN = re.compile(r"^\[([^\]]+)\]\([^)]+\)$")
    EXPECTED_SKILLS: tuple[SkillCatalogExpectation, ...] = (
        SkillCatalogExpectation(
            "dmtools-jira",
            "/dmtools-jira",
            "com.github.istin.dmtools.atlassian.jira",
            "com.github.istin:dmtools-jira",
        ),
        SkillCatalogExpectation(
            "dmtools-confluence",
            "/dmtools-confluence",
            "com.github.istin.dmtools.atlassian.confluence",
            "com.github.istin:dmtools-confluence",
        ),
        SkillCatalogExpectation(
            "dmtools-github",
            "/dmtools-github",
            "com.github.istin.dmtools.github",
            "com.github.istin:dmtools-github",
        ),
        SkillCatalogExpectation(
            "dmtools-gitlab",
            "/dmtools-gitlab",
            "com.github.istin.dmtools.gitlab",
            "com.github.istin:dmtools-gitlab",
        ),
        SkillCatalogExpectation(
            "dmtools-ado",
            "/dmtools-ado",
            "com.github.istin.dmtools.microsoft.ado",
            "com.github.istin:dmtools-ado",
        ),
        SkillCatalogExpectation(
            "dmtools-figma",
            "/dmtools-figma",
            "com.github.istin.dmtools.figma",
            "com.github.istin:dmtools-figma",
        ),
        SkillCatalogExpectation(
            "dmtools-testrail",
            "/dmtools-testrail",
            "com.github.istin.dmtools.testrail",
            "com.github.istin:dmtools-testrail",
        ),
        SkillCatalogExpectation(
            "dmtools-teams",
            "/dmtools-teams",
            "com.github.istin.dmtools.microsoft.teams",
            "com.github.istin:dmtools-teams",
        ),
        SkillCatalogExpectation(
            "dmtools-report",
            "/dmtools-report",
            "com.github.istin.dmtools.report",
            "com.github.istin:dmtools-report",
        ),
    )

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.catalog_path = repository_root / self.CATALOG_RELATIVE_PATH

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []
        if not self.catalog_path.exists():
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="The per-skill catalogue file is missing.",
                    expected=(
                        f"A canonical catalogue at {self.CATALOG_RELATIVE_PATH} that documents "
                        "all 9 approved focused skills."
                    ),
                    actual=f"File not found: {self.catalog_path}",
                )
            )
            return failures

        try:
            rows = self.catalog_rows()
        except ValueError as error:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="The catalogue does not present the required canonical table.",
                    expected=(
                        "A user-visible 4-column markdown catalogue table with one row "
                        "per approved skill."
                    ),
                    actual=str(error),
                )
            )
            return failures

        row_preview = self._row_preview(rows)
        expected_skill_names = {skill.skill_name for skill in self.EXPECTED_SKILLS}
        documented_skill_names = {self._display_text(row.cells[0]) for row in rows}

        if len(rows) != len(self.EXPECTED_SKILLS):
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="The catalogue does not provide exactly one canonical row per approved skill.",
                    expected=(
                        f"Exactly {len(self.EXPECTED_SKILLS)} data rows in the visible "
                        "4-column catalogue table."
                    ),
                    actual=f"Found {len(rows)} data row(s): {row_preview}",
                )
            )

        missing_skill_names = sorted(expected_skill_names - documented_skill_names)
        unexpected_skill_names = sorted(documented_skill_names - expected_skill_names)
        if missing_skill_names or unexpected_skill_names:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="The catalogue does not expose the exact approved 9-skill set.",
                    expected=(
                        "Rows for exactly these skills: "
                        + ", ".join(sorted(expected_skill_names))
                    ),
                    actual=(
                        f"Missing: {', '.join(missing_skill_names) or 'none'}; "
                        f"unexpected: {', '.join(unexpected_skill_names) or 'none'}; "
                        f"catalog rows: {row_preview}"
                    ),
                )
            )

        if failures:
            return failures

        mapping_issues: list[str] = []
        alias_issues: list[str] = []
        for skill in self.EXPECTED_SKILLS:
            row = self.row_for_skill(skill.skill_name, rows)
            if row is None:
                mapping_issues.append(f"{skill.skill_name}: no catalogue row found")
                alias_issues.append(f"{skill.skill_name}: no catalogue row found")
                continue

            missing_mapping_parts = [
                value
                for value, cell in (
                    (skill.slash_command, row.cells[1]),
                    (skill.java_package, row.cells[2]),
                )
                if self._display_text(cell) != value
            ]
            if missing_mapping_parts:
                mapping_issues.append(
                    f"line {row.line_number} for {skill.skill_name} is missing "
                    + ", ".join(missing_mapping_parts)
                    + f" | row: {row.text}"
                )

            if self._display_text(row.cells[3]) != skill.artifact_alias:
                alias_issues.append(
                    f"line {row.line_number} for {skill.skill_name} is missing "
                    f"{skill.artifact_alias} | row: {row.text}"
                )

        if mapping_issues:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="One or more skills are not mapped to the required slash command and Java package.",
                    expected=(
                        "Each skill row must pair the skill name with its exact /dmtools-<name> "
                        "slash command and canonical Java package identifier."
                    ),
                    actual="; ".join(mapping_issues),
                )
            )

        if alias_issues:
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="One or more skills are missing the required artifact alias.",
                    expected=(
                        "Each skill row must display its artifact alias in the form "
                        "com.github.istin:dmtools-<name>."
                    ),
                    actual="; ".join(alias_issues),
                )
            )

        return failures

    def catalog_rows(self) -> list[CatalogRow]:
        lines = self.catalog_path.read_text(encoding="utf-8").splitlines()
        tables = self._candidate_tables(lines)
        if not tables:
            raise ValueError(
                f"No 4-column markdown table was found in {self.catalog_path}."
            )
        rows = max(tables, key=self._table_match_score)
        if self._table_match_score(rows) == 0:
            raise ValueError(
                f"No 4-column markdown table in {self.catalog_path} exposes approved "
                "skill names in its first column."
            )
        return rows

    @staticmethod
    def row_for_skill(
        skill_name: str,
        rows: list[CatalogRow],
    ) -> CatalogRow | None:
        normalized_skill_name = skill_name.strip("`")
        return next(
            (
                row
                for row in rows
                if PerSkillCatalogService._display_text(row.cells[0]) == normalized_skill_name
            ),
            None,
        )

    def _candidate_tables(self, lines: list[str]) -> list[list[CatalogRow]]:
        tables: list[list[CatalogRow]] = []
        index = 0
        in_code_block = False
        while index < len(lines):
            line = lines[index]
            if self._is_fenced_code_delimiter(line):
                in_code_block = not in_code_block
                index += 1
                continue
            if in_code_block:
                index += 1
                continue

            header_cells = self._parse_table_row(line)
            separator_cells = (
                self._parse_table_row(lines[index + 1])
                if index + 1 < len(lines)
                else None
            )
            if (
                header_cells is None
                or len(header_cells) != self.REQUIRED_COLUMN_COUNT
                or not self._is_separator_row(
                    separator_cells,
                    expected_count=len(header_cells),
                )
            ):
                index += 1
                continue

            table_rows: list[CatalogRow] = []
            row_index = index + 2
            while row_index < len(lines):
                row = lines[row_index]
                if self._is_fenced_code_delimiter(row):
                    break
                cells = self._parse_table_row(row)
                if cells is None or len(cells) != len(header_cells):
                    break
                table_rows.append(
                    CatalogRow(
                        line_number=row_index + 1,
                        text=row.strip(),
                        cells=cells,
                    )
                )
                row_index += 1

            if table_rows:
                tables.append(table_rows)
            index = row_index

        return tables

    def _is_separator_row(
        self,
        cells: tuple[str, ...] | None,
        *,
        expected_count: int,
    ) -> bool:
        return cells is not None and len(cells) == expected_count and all(
            self.SEPARATOR_CELL_PATTERN.fullmatch(cell) for cell in cells
        )

    def _table_match_score(self, rows: list[CatalogRow]) -> int:
        expected_skill_names = {skill.skill_name for skill in self.EXPECTED_SKILLS}
        return sum(
            1
            for row in rows
            if self._display_text(row.cells[0]) in expected_skill_names
        )

    @staticmethod
    def _is_fenced_code_delimiter(line: str) -> bool:
        return line.lstrip().startswith("```")

    @staticmethod
    def _parse_table_row(line: str) -> tuple[str, ...] | None:
        stripped = line.strip()
        if stripped.count("|") < 3:
            return None

        cells = [cell.strip() for cell in stripped.split("|")]
        if stripped.startswith("|"):
            cells = cells[1:]
        if stripped.endswith("|"):
            cells = cells[:-1]
        if len(cells) < 2:
            return None

        return tuple(cells)

    @staticmethod
    def _display_text(cell: str) -> str:
        normalized = " ".join(cell.strip().split())
        link_match = PerSkillCatalogService.MARKDOWN_LINK_PATTERN.fullmatch(normalized)
        if link_match:
            normalized = link_match.group(1).strip()

        wrappers = ("`", "**", "*", "__", "_")
        changed = True
        while changed:
            changed = False
            for wrapper in wrappers:
                if normalized.startswith(wrapper) and normalized.endswith(wrapper):
                    normalized = normalized[len(wrapper) : -len(wrapper)].strip()
                    changed = True
                    break

        return normalized

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    @staticmethod
    def _row_preview(rows: list[CatalogRow]) -> str:
        if not rows:
            return "no rows mentioning dmtools-* were found"
        return " || ".join(
            f"L{row.line_number}: {row.text}" for row in rows[:6]
        )
