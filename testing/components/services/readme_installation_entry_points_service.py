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
HEADING_PATTERN = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")
MARKDOWN_LINK_PATTERN = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
HTML_LINK_PATTERN = re.compile(r"""<a\s+[^>]*href=["']([^"']+)["']""", re.IGNORECASE)
AUTO_LINK_PATTERN = re.compile(r"<(https?://[^>]+)>")
BARE_URL_PATTERN = re.compile(r"(?<![<(])https?://[^\s)>]+")
INSTALLATION_ANCHOR = "installation"


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
class Heading:
    line_index: int
    level: int
    title: str


class ReadmeInstallationEntryPointsService:
    def __init__(self, repository_root: Path) -> None:
        self.readme_path = repository_root / "README.md"
        self.readme_text = self.readme_path.read_text(encoding="utf-8")
        self.lines = self.readme_text.splitlines()
        self._headings = self._parse_headings()

    def validate(self) -> list[ValidationFailure]:
        failures: list[ValidationFailure] = []

        banner_region = self._banner_region()
        banner_preview = self._preview(banner_region)

        if not banner_region:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="README quick-install banner is missing directly under the title.",
                    expected="A quick-install banner region immediately after '# DMTools'.",
                    actual="No banner content was found between the title and the first top-level section.",
                )
            )
        elif (
            EXPECTED_BASH_COMMAND not in banner_region
            or EXPECTED_POWERSHELL_PATH not in banner_region
        ):
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="The banner region under the README title does not expose the required install entry points.",
                    expected=(
                        "The content directly under '# DMTools' should expose the latest-release "
                        "Bash command and PowerShell install path before the next top-level section."
                    ),
                    actual=f"Banner region content: {banner_preview}",
                )
            )

        if EXPECTED_BASH_COMMAND not in banner_region:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="README does not expose the required Bash installer command.",
                    expected=EXPECTED_BASH_COMMAND,
                    actual=f"Banner region content: {banner_preview}",
                )
            )

        if EXPECTED_POWERSHELL_PATH not in banner_region:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="README does not expose the required PowerShell installer path.",
                    expected=EXPECTED_POWERSHELL_PATH,
                    actual=f"Banner region content: {banner_preview}",
                )
            )

        headings_preview = ", ".join(self.headings()) or "none"
        installation_section = self._section_body("Installation")
        navigation_region = self._navigation_region_for_installation()
        navigation_targets = self._link_targets(navigation_region)
        if not navigation_region:
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="README does not provide a navigation area above Installation.",
                    expected="A table of contents or equivalent navigation area linking to the Installation section.",
                    actual=f"Available headings: {headings_preview}",
                )
            )
        elif not any(
            self._is_installation_anchor(target) for target in navigation_targets
        ):
            failures.append(
                ValidationFailure(
                    step=4,
                    summary="README does not expose a semantic navigation link to the Installation section.",
                    expected="A top-of-page navigation link targeting the Installation anchor.",
                    actual=(
                        "Navigation link targets: "
                        + (", ".join(navigation_targets) if navigation_targets else "none")
                    ),
                )
            )

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
            installation_links = self._link_targets(installation_section)
            if EXPECTED_RELEASES_URL not in installation_links:
                actual_links = (
                    ", ".join(installation_links)
                    if installation_links
                    else "no clickable links found"
                )
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
        return [heading.title for heading in self._headings]

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def _banner_region(self) -> str:
        title_heading = self._title_heading()
        if title_heading is None:
            return ""

        return self._body_between(
            title_heading.line_index + 1,
            self._next_heading_line(
                title_heading.line_index,
                max_level=title_heading.level + 1,
            ),
        )

    def _section_body(self, section_title: str) -> str:
        heading = self._heading_by_title(section_title)
        if heading is None:
            return ""

        return self._body_between(
            heading.line_index + 1,
            self._next_heading_line(heading.line_index, max_level=heading.level),
        )

    def _navigation_region_for_installation(self) -> str:
        installation_heading = self._heading_by_title("Installation")
        title_heading = self._title_heading()
        if installation_heading is None or title_heading is None:
            return ""
        return self._body_between(title_heading.line_index + 1, installation_heading.line_index)

    def _parse_headings(self) -> list[Heading]:
        headings: list[Heading] = []
        for index, line in enumerate(self.lines):
            match = HEADING_PATTERN.match(line)
            if match:
                headings.append(
                    Heading(
                        line_index=index,
                        level=len(match.group(1)),
                        title=match.group(2).strip(),
                    )
                )
        return headings

    def _title_heading(self) -> Heading | None:
        return next((heading for heading in self._headings if heading.level == 1), None)

    def _heading_by_title(self, section_title: str) -> Heading | None:
        expected_title = section_title.strip().lower()
        return next(
            (
                heading
                for heading in self._headings
                if heading.title.lower() == expected_title
            ),
            None,
        )

    def _next_heading_line(self, start_line: int, max_level: int) -> int:
        for heading in self._headings:
            if heading.line_index > start_line and heading.level <= max_level:
                return heading.line_index
        return len(self.lines)

    def _body_between(self, start_line: int, end_line: int) -> str:
        return "\n".join(self.lines[start_line:end_line]).strip()

    def _link_targets(self, text: str) -> list[str]:
        targets: list[str] = []
        seen: set[str] = set()
        for pattern in (
            MARKDOWN_LINK_PATTERN,
            HTML_LINK_PATTERN,
            AUTO_LINK_PATTERN,
            BARE_URL_PATTERN,
        ):
            for match in pattern.findall(text):
                target = match.strip()
                if target and target not in seen:
                    seen.add(target)
                    targets.append(target)
        return targets

    @staticmethod
    def _is_installation_anchor(target: str) -> bool:
        if "#" not in target:
            return False
        fragment = target.split("#", maxsplit=1)[1].strip().lower()
        normalized_fragment = re.sub(r"[\s_]+", "-", fragment)
        return normalized_fragment == INSTALLATION_ANCHOR

    @staticmethod
    def _preview(value: str, limit: int = 240) -> str:
        compact = re.sub(r"\s+", " ", value.strip())
        if not compact:
            return "<empty>"
        if len(compact) <= limit:
            return compact
        return compact[: limit - 3] + "..."
