from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path


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


@dataclass(frozen=True)
class TemplateObservation:
    label: str
    path: Path
    about: str
    about_line_number: int
    prompt_headings: tuple[str, ...]
    visible_text: str
    signal_hits: tuple[str, ...]
    opening_prompt_body_text: str
    opening_prompt_signal_hits: tuple[str, ...]
    generic_placeholder_matches: tuple[str, ...]


class GitHubIssueTemplatePositioningService:
    FEATURE_TEMPLATE_LABEL = "feature request template"
    BUG_TEMPLATE_LABEL = "bug report template"
    _OPENING_PROMPT_COUNT = 3
    _METADATA_RELATIVE_PATH = "dmtools-core/src/main/resources/github-repository-discoverability.json"
    _TEMPLATE_SPECS = {
        FEATURE_TEMPLATE_LABEL: (
            ".github/ISSUE_TEMPLATE/feature_request.md",
            "featureRequestAbout",
        ),
        BUG_TEMPLATE_LABEL: (
            ".github/ISSUE_TEMPLATE/bug_report.md",
            "bugReportAbout",
        ),
    }
    _PROMPT_HEADING_PATTERN = re.compile(r"^\*\*(.+?)\*\*$")
    _GENERIC_PLACEHOLDER_PATTERNS = {
        "Describe the bug": re.compile(r"\bdescribe the bug\b", re.IGNORECASE),
        "A clear and concise description of what the bug is.": re.compile(
            r"\ba clear and concise description of what the bug is\b",
            re.IGNORECASE,
        ),
        "A clear and concise description of what you expected to happen.": re.compile(
            r"\ba clear and concise description of what you expected to happen\b",
            re.IGNORECASE,
        ),
        "Describe the solution you'd like": re.compile(
            r"\bdescribe the solution you(?:'|’)d like\b",
            re.IGNORECASE,
        ),
        "Describe alternatives you've considered": re.compile(
            r"\bdescribe alternatives you(?:'|’)ve considered\b",
            re.IGNORECASE,
        ),
        "If applicable, add screenshots to help explain your problem.": re.compile(
            r"\bif applicable, add screenshots to help explain your problem\b",
            re.IGNORECASE,
        ),
        "A clear and concise description of what the problem is.": re.compile(
            r"\ba clear and concise description of what the problem is\b",
            re.IGNORECASE,
        ),
    }
    _SIGNAL_PATTERNS = {
        "automation": re.compile(r"\bautomation\b", re.IGNORECASE),
        "workflow": re.compile(r"\bworkflow(?:s)?\b", re.IGNORECASE),
        "surface": re.compile(r"\bsurface(?:s)?\b", re.IGNORECASE),
        "cli": re.compile(r"\bcli\b", re.IGNORECASE),
        "mcp": re.compile(r"\bmcp\b", re.IGNORECASE),
        "jobs_or_agents": re.compile(r"\b(?:job|jobs|agent|agents)\b", re.IGNORECASE),
        "integrations": re.compile(r"\bintegration(?:s)?\b", re.IGNORECASE),
        "github": re.compile(r"\bgithub\b", re.IGNORECASE),
        "docs": re.compile(r"\b(?:docs|documentation)\b", re.IGNORECASE),
    }

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        metadata = json.loads(
            (repository_root / self._METADATA_RELATIVE_PATH).read_text(encoding="utf-8")
        )
        self._repo_backed_surfaces = metadata["repoBackedSurfaces"]
        self._observations = {
            label: self._observe_template(label)
            for label in self._TEMPLATE_SPECS
        }

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        for label, (_, metadata_key) in self._TEMPLATE_SPECS.items():
            observation = self.observation_for_label(label)
            expected_about = self.expected_about_for_label(label)

            if observation.about != expected_about:
                failures.append(
                    ValidationFailure(
                        step=3,
                        summary=f"{label.capitalize()} front-matter is not synced with the metadata source.",
                        expected=expected_about,
                        actual=(
                            f"{observation.path.relative_to(self.repository_root)}:"
                            f"L{observation.about_line_number} -> {observation.about}"
                        ),
                    )
                )

            if "dmtools" not in observation.opening_prompt_body_text.casefold():
                failures.append(
                    ValidationFailure(
                        step=3,
                        summary=f"{label.capitalize()} opening prompt copy does not visibly describe DMTools.",
                        expected=(
                            "The introductory prompt descriptions should name DMTools and frame "
                            "the issue around DMTools workflows or surfaces."
                        ),
                        actual=self._preview(observation.opening_prompt_body_text),
                    )
                )

            if not {"automation", "workflow", "surface"} & set(
                observation.opening_prompt_signal_hits
            ):
                failures.append(
                    ValidationFailure(
                        step=3,
                        summary=f"{label.capitalize()} opening prompt copy is not orchestration focused.",
                        expected=(
                            "The introductory prompt descriptions should guide contributors toward "
                            "DMTools workflows, automation, or affected surfaces rather than generic "
                            "project wording."
                        ),
                        actual=(
                            "Observed opening-prompt signal hits: "
                            + (
                                ", ".join(observation.opening_prompt_signal_hits)
                                if observation.opening_prompt_signal_hits
                                else "none"
                            )
                        ),
                    )
                )

            if observation.generic_placeholder_matches:
                failures.append(
                    ValidationFailure(
                        step=4,
                        summary=f"{label.capitalize()} still contains generic GitHub template placeholders.",
                        expected=(
                            "Issue templates should use DMTools-specific workflow prompts instead of "
                            "generic starter copy."
                        ),
                        actual=", ".join(observation.generic_placeholder_matches),
                    )
                )

        return failures

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def expected_about_for_label(self, label: str) -> str:
        _, metadata_key = self._TEMPLATE_SPECS[label]
        return self._repo_backed_surfaces[metadata_key]

    def observation_for_label(self, label: str) -> TemplateObservation:
        return self._observations[label]

    def _observe_template(self, label: str) -> TemplateObservation:
        relative_path, _ = self._TEMPLATE_SPECS[label]
        path = self.repository_root / relative_path
        lines = path.read_text(encoding="utf-8").splitlines()
        frontmatter, body_start_index = self._parse_frontmatter(lines, path)
        visible_lines = [line.strip() for line in lines[body_start_index:] if line.strip()]
        visible_text = " ".join(visible_lines)
        prompt_blocks = self._collect_prompt_blocks(visible_lines)
        prompt_headings = tuple(heading for heading, _ in prompt_blocks)
        opening_prompt_body_text = " ".join(
            line
            for _, block_lines in prompt_blocks[: self._OPENING_PROMPT_COUNT]
            for line in block_lines
        )
        signal_hits = tuple(
            label_name
            for label_name, pattern in self._SIGNAL_PATTERNS.items()
            if pattern.search(visible_text) is not None
        )
        opening_prompt_signal_hits = tuple(
            label_name
            for label_name, pattern in self._SIGNAL_PATTERNS.items()
            if pattern.search(opening_prompt_body_text) is not None
        )
        placeholder_matches = tuple(
            phrase
            for phrase, pattern in self._GENERIC_PLACEHOLDER_PATTERNS.items()
            if pattern.search(visible_text) is not None
        )

        about_line_number, about = frontmatter["about"]
        return TemplateObservation(
            label=label,
            path=path,
            about=about,
            about_line_number=about_line_number,
            prompt_headings=prompt_headings,
            visible_text=visible_text,
            signal_hits=signal_hits,
            opening_prompt_body_text=opening_prompt_body_text,
            opening_prompt_signal_hits=opening_prompt_signal_hits,
            generic_placeholder_matches=placeholder_matches,
        )

    def _collect_prompt_blocks(
        self,
        visible_lines: list[str],
    ) -> tuple[tuple[str, tuple[str, ...]], ...]:
        prompt_blocks: list[tuple[str, tuple[str, ...]]] = []
        current_heading: str | None = None
        current_lines: list[str] = []

        for line in visible_lines:
            match = self._PROMPT_HEADING_PATTERN.match(line)
            if match is not None:
                if current_heading is not None:
                    prompt_blocks.append((current_heading, tuple(current_lines)))
                current_heading = match.group(1).strip()
                current_lines = []
                continue

            if current_heading is not None:
                current_lines.append(line)

        if current_heading is not None:
            prompt_blocks.append((current_heading, tuple(current_lines)))

        return tuple(prompt_blocks)

    @staticmethod
    def _parse_frontmatter(
        lines: list[str],
        path: Path,
    ) -> tuple[dict[str, tuple[int, str]], int]:
        if not lines or lines[0].strip() != "---":
            raise AssertionError(f"{path.as_posix()} is missing YAML front-matter.")

        closing_index = next(
            (index for index in range(1, len(lines)) if lines[index].strip() == "---"),
            None,
        )
        if closing_index is None:
            raise AssertionError(f"{path.as_posix()} has an unterminated YAML front-matter block.")

        frontmatter: dict[str, tuple[int, str]] = {}
        for index in range(1, closing_index):
            stripped = lines[index].strip()
            if not stripped or ":" not in stripped:
                continue
            key, value = stripped.split(":", 1)
            frontmatter[key.strip()] = (index + 1, value.strip().strip("'\""))

        if "about" not in frontmatter:
            raise AssertionError(f"{path.as_posix()} is missing the 'about' front-matter field.")

        return frontmatter, closing_index + 1

    @staticmethod
    def _preview(text: str, limit: int = 260) -> str:
        compact = re.sub(r"\s+", " ", text).strip()
        if len(compact) <= limit:
            return compact
        return compact[: limit - 3] + "..."
