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
    SKILL_NAME_PATTERN = re.compile(r"\bdmtools-[a-z0-9-]+\b")
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

        rows = self.catalog_rows()
        row_preview = self._row_preview(rows)
        expected_skill_names = {skill.skill_name for skill in self.EXPECTED_SKILLS}
        documented_skill_names = {
            match.group(0)
            for row in rows
            for match in self.SKILL_NAME_PATTERN.finditer(row.text)
        }

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
                for value in (skill.slash_command, skill.java_package)
                if value not in row.text
            ]
            if missing_mapping_parts:
                mapping_issues.append(
                    f"line {row.line_number} for {skill.skill_name} is missing "
                    + ", ".join(missing_mapping_parts)
                    + f" | row: {row.text}"
                )

            if skill.artifact_alias not in row.text:
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
        rows: list[CatalogRow] = []
        for line_number, line in enumerate(
            self.catalog_path.read_text(encoding="utf-8").splitlines(),
            start=1,
        ):
            normalized = line.strip()
            if "dmtools-" not in normalized:
                continue
            if normalized.startswith("|") or normalized.startswith("-"):
                rows.append(CatalogRow(line_number=line_number, text=normalized))
                continue
            rows.append(CatalogRow(line_number=line_number, text=normalized))
        return rows

    @staticmethod
    def row_for_skill(
        skill_name: str,
        rows: list[CatalogRow],
    ) -> CatalogRow | None:
        return next((row for row in rows if skill_name in row.text), None)

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
