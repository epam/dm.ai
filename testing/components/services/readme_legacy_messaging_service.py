from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")


@dataclass(frozen=True)
class PhraseMatch:
    phrase: str
    line_number: int
    line_text: str

    def format(self) -> str:
        return f"{self.phrase!r} at README.md:{self.line_number} -> {self.line_text}"


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


class ReadmeLegacyMessagingService:
    FORBIDDEN_PRIMARY_PHRASES = (
        "oauth",
        "web application",
        "live application",
        "swagger ui",
        "simple run (legacy)",
    )
    PRIMARY_USAGE_PATHS_HEADING = "Primary usage paths"
    UPGRADING_FROM_LEGACY_INSTALLS_HEADING = "Upgrading from legacy installs"
    BUILD_FROM_SOURCE_HEADING = "Build from source"
    REQUIRED_PRIMARY_USAGE_ROW = "CLI + MCP tools"
    CLI_FIRST_SIGNAL_PHRASES = (
        "orchestrator",
        "orchestration",
        "terminal",
        "cli",
        "mcp",
        "jobs",
        "agents",
    )

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.readme_path = repository_root / "README.md"
        self.readme_text = self.readme_path.read_text(encoding="utf-8")
        self.lines = self.readme_text.splitlines()
        self._headings = self._parse_headings()

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        primary_narrative = self.primary_narrative()
        forbidden_matches = self.forbidden_phrase_matches(primary_narrative)
        if forbidden_matches:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="README still contains legacy OAuth/web-app/Swagger entry-point language.",
                    expected=(
                        "The README primary entry-point narrative should not contain the legacy framing phrases: "
                        + ", ".join(repr(phrase) for phrase in self.FORBIDDEN_PRIMARY_PHRASES)
                    ),
                    actual=self._format_matches(forbidden_matches),
                )
            )

        usage_labels = self.primary_usage_path_labels()
        if not usage_labels:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="README is missing the visible 'Primary usage paths' entry-point table.",
                    expected=(
                        "A 'Primary usage paths' table whose first row starts with "
                        f"{self.REQUIRED_PRIMARY_USAGE_ROW!r}."
                    ),
                    actual="No usage-path labels were parsed from the README section.",
                )
            )
        elif usage_labels[0] != self.REQUIRED_PRIMARY_USAGE_ROW:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="README no longer leads with the CLI-first entry point.",
                    expected=(
                        f"The first primary usage path should be {self.REQUIRED_PRIMARY_USAGE_ROW!r}."
                    ),
                    actual="Observed usage path order: " + ", ".join(usage_labels),
                )
            )

        normalized_primary_narrative = self._normalize(primary_narrative)
        if not any(
            signal_phrase in normalized_primary_narrative
            for signal_phrase in self.CLI_FIRST_SIGNAL_PHRASES
        ):
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="README primary narrative is not visibly CLI/orchestration focused.",
                    expected=(
                        "The visible primary narrative before 'Build from source' should emphasize "
                        "CLI-first orchestration, terminal usage, MCP tools, jobs, or agents."
                    ),
                    actual=self._preview(primary_narrative),
                )
            )

        return failures

    def forbidden_phrase_matches(self, text: str) -> list[PhraseMatch]:
        matches: list[PhraseMatch] = []
        for line_number, line in enumerate(text.splitlines(), start=1):
            normalized_line = line.casefold()
            for phrase in self.FORBIDDEN_PRIMARY_PHRASES:
                if phrase.casefold() not in normalized_line:
                    continue
                matches.append(
                    PhraseMatch(
                        phrase=phrase,
                        line_number=line_number,
                        line_text=line.strip(),
                    )
                )
        return matches

    def primary_usage_path_labels(self) -> list[str]:
        section = self._section_body(self.PRIMARY_USAGE_PATHS_HEADING)
        if not section:
            return []

        labels: list[str] = []
        for line in section.splitlines():
            stripped = line.strip()
            if not stripped.startswith("|"):
                continue

            cells = [cell.strip() for cell in stripped.strip("|").split("|")]
            if not cells or cells[0].casefold() == "usage path":
                continue
            if all(not cell or set(cell) <= {"-", ":", " "} for cell in cells):
                continue

            labels.append(cells[0])
        return labels

    def primary_narrative(self) -> str:
        stop_line = self._primary_narrative_stop_line()
        return "\n".join(self.lines[:stop_line]).strip()

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def _parse_headings(self) -> list[tuple[int, int, str]]:
        headings: list[tuple[int, int, str]] = []
        for index, line in enumerate(self.lines):
            match = HEADING_PATTERN.match(line)
            if not match:
                continue
            headings.append((index, len(match.group(1)), match.group(2).strip()))
        return headings

    def _heading_line(self, title: str) -> int | None:
        normalized_title = self._normalize(title)
        return next(
            (
                line_index
                for line_index, _, heading_title in self._headings
                if self._normalize(heading_title) == normalized_title
            ),
            None,
        )

    def _primary_narrative_stop_line(self) -> int:
        stop_line_candidates = [
            line_number
            for line_number in (
                self._heading_line(self.UPGRADING_FROM_LEGACY_INSTALLS_HEADING),
                self._heading_line(self.BUILD_FROM_SOURCE_HEADING),
            )
            if line_number is not None
        ]
        if stop_line_candidates:
            return min(stop_line_candidates)
        return len(self.lines)

    def _section_body(self, title: str) -> str:
        normalized_title = self._normalize(title)
        for index, (line_index, level, heading_title) in enumerate(self._headings):
            if self._normalize(heading_title) != normalized_title:
                continue

            end_line = len(self.lines)
            for next_line_index, next_level, _ in self._headings[index + 1 :]:
                if next_level <= level:
                    end_line = next_line_index
                    break
            return "\n".join(self.lines[line_index + 1 : end_line]).strip()
        return ""

    @staticmethod
    def _format_matches(matches: list[PhraseMatch]) -> str:
        return "; ".join(match.format() for match in matches)

    @staticmethod
    def _normalize(text: str) -> str:
        return re.sub(r"\s+", " ", text).strip().casefold()

    @staticmethod
    def _preview(text: str, limit: int = 300) -> str:
        compact = re.sub(r"\s+", " ", text).strip()
        if len(compact) <= limit:
            return compact
        return compact[: limit - 3] + "..."
