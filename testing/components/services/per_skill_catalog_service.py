from __future__ import annotations
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
    TABLE_HEADER = "| Skill | Slash command | Java package | Artifact alias |"
    TABLE_SEPARATOR = "| --- | --- | --- | --- |"
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
                        "A markdown table with the header "
                        f"{self.TABLE_HEADER} and one row per approved skill."
                    ),
                    actual=str(error),
                )
            )
            return failures

        row_preview = self._row_preview(rows)
        expected_skill_names = {skill.skill_name for skill in self.EXPECTED_SKILLS}
        documented_skill_names = {row.cells[0].strip("`") for row in rows}

        if len(rows) != len(self.EXPECTED_SKILLS):
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="The catalogue does not provide exactly one canonical row per approved skill.",
                    expected=(
                        f"Exactly {len(self.EXPECTED_SKILLS)} data rows in the canonical "
                        f"table headed by {self.TABLE_HEADER}."
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
                if value not in cell
            ]
            if missing_mapping_parts:
                mapping_issues.append(
                    f"line {row.line_number} for {skill.skill_name} is missing "
                    + ", ".join(missing_mapping_parts)
                    + f" | row: {row.text}"
                )

            if skill.artifact_alias not in row.cells[3]:
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
        header_indexes = [
            index
            for index, line in enumerate(lines)
            if line.strip() == self.TABLE_HEADER
        ]
        if not header_indexes:
            raise ValueError(
                f"Catalogue table header {self.TABLE_HEADER!r} not found in {self.catalog_path}."
            )
        if len(header_indexes) > 1:
            raise ValueError(
                "Expected exactly one canonical catalogue table with header "
                f"{self.TABLE_HEADER!r} in {self.catalog_path}, but found {len(header_indexes)}."
            )

        index = header_indexes[0]
        if index + 1 >= len(lines) or lines[index + 1].strip() != self.TABLE_SEPARATOR:
            raise ValueError(
                f"Malformed catalogue table in {self.catalog_path}: expected separator "
                f"{self.TABLE_SEPARATOR!r} after header."
            )

        rows: list[CatalogRow] = []
        for row_index, row in enumerate(lines[index + 2 :], start=index + 3):
            normalized = row.strip()
            if not normalized.startswith("|"):
                break
            cells = tuple(cell.strip() for cell in normalized.strip("|").split("|"))
            if len(cells) != 4:
                raise ValueError(
                    f"Unexpected catalogue row in {self.catalog_path} at line "
                    f"{row_index}: {normalized}"
                )
            rows.append(CatalogRow(line_number=row_index, text=normalized, cells=cells))

        if not rows:
            raise ValueError(
                f"Catalogue table in {self.catalog_path} is empty after header {self.TABLE_HEADER!r}."
            )

        return rows

    @staticmethod
    def row_for_skill(
        skill_name: str,
        rows: list[CatalogRow],
    ) -> CatalogRow | None:
        normalized_skill_name = skill_name.strip("`")
        return next(
            (row for row in rows if row.cells[0].strip("`") == normalized_skill_name),
            None,
        )

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
