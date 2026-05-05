from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class WorkflowOutputSurface:
    workflow_path: str
    surface_name: str
    source: str
    content: str


@dataclass(frozen=True)
class WorkflowOutputFinding:
    workflow_path: str
    surface_name: str
    summary: str
    expected: str
    actual: str


class DeprecatedWorkflowOutputService:
    REQUIRED_NOTICE_PATTERN = re.compile(r"\b(?:deprecated|internal[- ]only)\b", re.IGNORECASE)
    FORBIDDEN_REFERENCE_PATTERNS = (
        ("Flutter external release reference", re.compile(r"istin/dmtools-flutter", re.IGNORECASE)),
        ("Swagger guidance reference", re.compile(r"\bswagger\b", re.IGNORECASE)),
        ("localhost login guidance reference", re.compile(r"\blocalhost\b", re.IGNORECASE)),
    )
    CUSTOMER_FACING_INSTALL_PATTERNS = (
        (
            "customer-facing install heading",
            re.compile(r"supported public install(?:ation)? paths?", re.IGNORECASE),
        ),
        (
            "customer-facing install command",
            re.compile(r"curl\s+-fsSL\s+https://github\.com/.+/install\.sh", re.IGNORECASE),
        ),
        (
            "Windows installer command",
            re.compile(r"\binstall\.ps1\b", re.IGNORECASE),
        ),
        (
            "agent skill install command",
            re.compile(r"\bskill-install\.sh\b", re.IGNORECASE),
        ),
    )
    STEP_SUMMARY_ECHO_PATTERN = re.compile(
        r"""^\s*echo\s+("([^"\\]|\\.)*"|'[^']*')\s*>>\s*\$GITHUB_STEP_SUMMARY\s*$"""
    )

    def __init__(
        self,
        *,
        workflow_paths: Iterable[str],
        owner: str = "epam",
        repo: str = "dm.ai",
        ref: str = "main",
        repository_root: Path | None = None,
        workflow_text_by_path: dict[str, str] | None = None,
    ) -> None:
        self.workflow_paths = tuple(workflow_paths)
        self.owner = owner
        self.repo = repo
        self.ref = ref
        self.repository_root = repository_root
        self.workflow_text_by_path = dict(workflow_text_by_path or {})

    def output_surfaces(self) -> list[WorkflowOutputSurface]:
        surfaces: list[WorkflowOutputSurface] = []
        for workflow_path in self.workflow_paths:
            source_label, workflow_text = self._load_workflow_text(workflow_path)

            release_body = self._extract_release_body(workflow_text)
            if release_body:
                surfaces.append(
                    WorkflowOutputSurface(
                        workflow_path=workflow_path,
                        surface_name="release body",
                        source=source_label,
                        content=release_body,
                    )
                )

            step_summary = self._extract_step_summary(workflow_text)
            if step_summary:
                surfaces.append(
                    WorkflowOutputSurface(
                        workflow_path=workflow_path,
                        surface_name="step summary",
                        source=source_label,
                        content=step_summary,
                    )
                )

        return surfaces

    def audit(self) -> list[WorkflowOutputFinding]:
        findings: list[WorkflowOutputFinding] = []
        for surface in self.output_surfaces():
            findings.extend(self._audit_surface(surface))
        return findings

    def human_observations(self) -> list[str]:
        observations: list[str] = []
        surfaces = self.output_surfaces()
        surfaced_workflows = {surface.workflow_path for surface in surfaces}

        for workflow_path in self.workflow_paths:
            matching_surfaces = [surface for surface in surfaces if surface.workflow_path == workflow_path]
            if matching_surfaces:
                for surface in matching_surfaces:
                    observations.append(
                        f"{workflow_path} {surface.surface_name} visible content: "
                        f"{self._preview_text(surface.content)}"
                    )
            elif workflow_path not in surfaced_workflows:
                observations.append(
                    f"{workflow_path} does not define a release body or step summary, so it does not "
                    "publish a customer-facing workflow message by itself."
                )

        return observations

    @staticmethod
    def format_findings(findings: list[WorkflowOutputFinding]) -> str:
        lines = [
            "Deprecated workflow outputs still publish customer-facing or legacy messaging:"
        ]
        for finding in findings:
            lines.append(f"- {finding.workflow_path} [{finding.surface_name}] {finding.summary}")
            lines.append(f"  Expected: {finding.expected}")
            lines.append(f"  Actual: {finding.actual}")
        return "\n".join(lines)

    def _audit_surface(self, surface: WorkflowOutputSurface) -> list[WorkflowOutputFinding]:
        findings: list[WorkflowOutputFinding] = []

        if not self.REQUIRED_NOTICE_PATTERN.search(surface.content):
            findings.append(
                WorkflowOutputFinding(
                    workflow_path=surface.workflow_path,
                    surface_name=surface.surface_name,
                    summary="is missing an explicit deprecated/internal-only notice.",
                    expected=(
                        "The published output should clearly position the workflow as deprecated or "
                        "internal-only."
                    ),
                    actual=self._preview_text(surface.content),
                )
            )

        findings.extend(
            self._find_pattern_matches(
                surface,
                self.FORBIDDEN_REFERENCE_PATTERNS,
                expected=(
                    "The published output must not mention Flutter external releases, Swagger guidance, "
                    "or localhost login instructions."
                ),
            )
        )
        findings.extend(
            self._find_pattern_matches(
                surface,
                self.CUSTOMER_FACING_INSTALL_PATTERNS,
                expected=(
                    "The published output must not include customer-facing installation headings or "
                    "actionable install commands."
                ),
            )
        )
        return findings

    def _find_pattern_matches(
        self,
        surface: WorkflowOutputSurface,
        patterns: tuple[tuple[str, re.Pattern[str]], ...],
        *,
        expected: str,
    ) -> list[WorkflowOutputFinding]:
        findings: list[WorkflowOutputFinding] = []
        for label, pattern in patterns:
            match = self._first_matching_line(surface.content, pattern)
            if match is None:
                continue
            findings.append(
                WorkflowOutputFinding(
                    workflow_path=surface.workflow_path,
                    surface_name=surface.surface_name,
                    summary=f"still contains {label}.",
                    expected=expected,
                    actual=match,
                )
            )
        return findings

    def _load_workflow_text(self, workflow_path: str) -> tuple[str, str]:
        if workflow_path in self.workflow_text_by_path:
            return f"in-memory:{workflow_path}", self.workflow_text_by_path[workflow_path]

        if self.repository_root is not None:
            local_path = self.repository_root / workflow_path
            return local_path.as_posix(), local_path.read_text(encoding="utf-8")

        raw_url = (
            f"https://raw.githubusercontent.com/{self.owner}/{self.repo}/{self.ref}/{workflow_path}"
        )
        request = Request(
            raw_url,
            headers={"User-Agent": "dm-ai-testing-deprecated-workflow-audit"},
        )
        with urlopen(request, timeout=30) as response:
            return raw_url, response.read().decode("utf-8")

    @staticmethod
    def _extract_release_body(workflow_text: str) -> str:
        literal_block = DeprecatedWorkflowOutputService._extract_literal_block(workflow_text, "body")
        if literal_block:
            return literal_block
        return DeprecatedWorkflowOutputService._extract_release_notes_heredoc(workflow_text)

    def _extract_step_summary(self, workflow_text: str) -> str:
        lines: list[str] = []
        for workflow_line in workflow_text.splitlines():
            match = self.STEP_SUMMARY_ECHO_PATTERN.match(workflow_line)
            if not match:
                continue
            lines.append(self._decode_shell_string(match.group(1)))
        return "\n".join(lines).strip()

    @staticmethod
    def _extract_literal_block(workflow_text: str, key: str) -> str:
        lines = workflow_text.splitlines()
        key_pattern = re.compile(rf"^\s*{re.escape(key)}:\s*\|\s*$")

        for index, line in enumerate(lines):
            if not key_pattern.match(line):
                continue

            content_indent: int | None = None
            block_lines: list[str] = []

            for candidate in lines[index + 1 :]:
                if content_indent is None:
                    if not candidate.strip():
                        continue
                    content_indent = len(candidate) - len(candidate.lstrip(" "))

                if candidate.strip():
                    current_indent = len(candidate) - len(candidate.lstrip(" "))
                    if current_indent < content_indent:
                        break
                    block_lines.append(candidate[content_indent:])
                else:
                    block_lines.append("")

            return "\n".join(block_lines).strip()

        return ""

    @staticmethod
    def _extract_release_notes_heredoc(workflow_text: str) -> str:
        lines = workflow_text.splitlines()

        for index, line in enumerate(lines):
            if "cat > release_notes.md << EOF" not in line:
                continue

            block_lines: list[str] = []
            for candidate in lines[index + 1 :]:
                if candidate.strip() == "EOF":
                    break
                block_lines.append(candidate)
            return "\n".join(block_lines).strip()

        return ""

    @staticmethod
    def _decode_shell_string(token: str) -> str:
        if token.startswith("'") and token.endswith("'"):
            return token[1:-1]
        value = token[1:-1]
        return (
            value.replace(r"\\", "\\")
            .replace(r"\"", '"')
            .replace(r"\`", "`")
            .replace(r"\$", "$")
        )

    @staticmethod
    def _first_matching_line(content: str, pattern: re.Pattern[str]) -> str | None:
        for line in content.splitlines():
            if pattern.search(line):
                return line.strip()
        return None

    @staticmethod
    def _preview_text(content: str, limit: int = 220) -> str:
        normalized = re.sub(r"\s+", " ", content).strip()
        if len(normalized) <= limit:
            return normalized
        return normalized[: limit - 3] + "..."
