from __future__ import annotations

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
class WorkflowMetadataObservation:
    workflow_relative_path: str
    workflow_name: str
    artifact_output_description: str
    artifact_output_value: str
    step_names: tuple[str, ...]
    surfaced_messages: tuple[str, ...]
    packaged_artifact_commands: tuple[str, ...]


class CliPackagingWorkflowMetadataService:
    WORKFLOW_RELATIVE_PATH = ".github/workflows/package-cli.yml"
    REQUIRED_WORKFLOW_NAME = "Package CLI (Reusable)"
    REQUIRED_ARTIFACT_OUTPUT_DESCRIPTION = "Name of the uploaded artifact"
    REQUIRED_ARTIFACT_OUTPUT_VALUE = "cli-package"
    REQUIRED_STEP_NAMES = (
        "Download Build Artifacts",
        "Prepare CLI Package",
        "Upload CLI Package",
    )
    REQUIRED_SURFACED_MESSAGES = (
        "ERROR: CLI JAR not found in build artifacts",
        "Available files:",
        "CLI package prepared:",
    )
    REQUIRED_PACKAGED_ARTIFACT_COMMANDS = (
        "cp build-artifacts/dmtools-cli.jar cli-release/dmtools-v${{ inputs.version }}-all.jar",
        "cp build-artifacts/install.sh cli-release/",
        "cp build-artifacts/install cli-release/ 2>/dev/null || true",
        "cp build-artifacts/install.bat cli-release/ 2>/dev/null || true",
        "cp build-artifacts/install.ps1 cli-release/ 2>/dev/null || true",
        "cp build-artifacts/dmtools.sh cli-release/",
    )
    FORBIDDEN_LEGACY_PHRASES = ("server", "api-only", "api only")

    _WORKFLOW_NAME_PATTERN = re.compile(r"^name:\s*(?P<value>.+?)\s*$", re.MULTILINE)
    _OUTPUT_DESCRIPTION_PATTERN = re.compile(
        r"(?ms)outputs:\s+artifact_name:\s+description:\s*['\"](?P<value>.+?)['\"]\s+value:"
    )
    _OUTPUT_VALUE_PATTERN = re.compile(
        r"(?ms)outputs:\s+artifact_name:\s+description:\s*['\"].+?['\"]\s+value:\s*(?P<value>[^\n]+)"
    )
    _STEP_NAME_PATTERN = re.compile(r"^\s*-\s+name:\s*(?P<value>.+?)\s*$", re.MULTILINE)
    _ECHO_MESSAGE_PATTERN = re.compile(r'echo\s+"(?P<value>[^"]+)"')
    _COPY_COMMAND_PATTERN = re.compile(
        r"^\s*(?P<value>cp\s+build-artifacts/.+?)\s*$", re.MULTILINE
    )

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root
        self.workflow_path = repository_root / self.WORKFLOW_RELATIVE_PATH
        self.workflow_text = self.workflow_path.read_text(encoding="utf-8")

    def observation(self) -> WorkflowMetadataObservation:
        return WorkflowMetadataObservation(
            workflow_relative_path=self.WORKFLOW_RELATIVE_PATH,
            workflow_name=self._first_match(self._WORKFLOW_NAME_PATTERN),
            artifact_output_description=self._first_match(self._OUTPUT_DESCRIPTION_PATTERN),
            artifact_output_value=self._first_match(self._OUTPUT_VALUE_PATTERN).strip(),
            step_names=tuple(self._all_matches(self._STEP_NAME_PATTERN)),
            surfaced_messages=tuple(self._all_matches(self._ECHO_MESSAGE_PATTERN)),
            packaged_artifact_commands=tuple(
                command.strip() for command in self._all_matches(self._COPY_COMMAND_PATTERN)
            ),
        )

    def validate(self) -> list[ValidationFailure]:
        observation = self.observation()
        failures: list[ValidationFailure] = []

        step_one_findings: list[str] = []
        if observation.workflow_name != self.REQUIRED_WORKFLOW_NAME:
            step_one_findings.append(f"workflow name={observation.workflow_name!r}")

        if (
            observation.artifact_output_description != self.REQUIRED_ARTIFACT_OUTPUT_DESCRIPTION
            or observation.artifact_output_value != self.REQUIRED_ARTIFACT_OUTPUT_VALUE
        ):
            step_one_findings.append(
                "artifact metadata="
                f"(description={observation.artifact_output_description!r}, "
                f"value={observation.artifact_output_value!r})"
            )

        if step_one_findings:
            failures.append(
                ValidationFailure(
                    step=1,
                    summary="The reusable packaging workflow metadata no longer presents the uploaded bundle as the CLI package flow.",
                    expected=(
                        f"Workflow name should be {self.REQUIRED_WORKFLOW_NAME!r}; artifact_name output should keep the description "
                        f"{self.REQUIRED_ARTIFACT_OUTPUT_DESCRIPTION!r} and value "
                        f"{self.REQUIRED_ARTIFACT_OUTPUT_VALUE!r}."
                    ),
                    actual="; ".join(step_one_findings),
                )
            )

        missing_step_names = [
            step_name
            for step_name in self.REQUIRED_STEP_NAMES
            if step_name not in observation.step_names
        ]
        missing_packaged_artifacts = [
            command
            for command in self.REQUIRED_PACKAGED_ARTIFACT_COMMANDS
            if command not in observation.packaged_artifact_commands
        ]
        if missing_step_names or missing_packaged_artifacts:
            failures.append(
                ValidationFailure(
                    step=2,
                    summary="The workflow no longer clearly packages the versioned CLI JAR and installation scripts.",
                    expected=(
                        "The workflow should expose the CLI packaging step labels and copy commands for the "
                        "versioned JAR plus install.sh, install, install.bat, install.ps1, and dmtools.sh."
                    ),
                    actual=(
                        "Missing step names: "
                        + (", ".join(missing_step_names) if missing_step_names else "<none>")
                        + "; missing artifact commands: "
                        + (
                            ", ".join(missing_packaged_artifacts)
                            if missing_packaged_artifacts
                            else "<none>"
                        )
                    ),
                )
            )

        missing_messages = [
            message
            for message in self.REQUIRED_SURFACED_MESSAGES
            if message not in observation.surfaced_messages
        ]
        surfaced_lines = [
            observation.workflow_name,
            observation.artifact_output_description,
            *observation.step_names,
            *observation.surfaced_messages,
        ]
        forbidden_matches = self._forbidden_phrase_matches(surfaced_lines)
        if missing_messages or forbidden_matches:
            failures.append(
                ValidationFailure(
                    step=3,
                    summary="The user-visible packaging metadata/messages are missing CLI-specific cues or reintroduce legacy bundle wording.",
                    expected=(
                        "Surfaced workflow labels and messages should mention the CLI package flow and must not "
                        "contain legacy bundle wording such as "
                        + ", ".join(repr(phrase) for phrase in self.FORBIDDEN_LEGACY_PHRASES)
                        + "."
                    ),
                    actual=(
                        "Missing surfaced messages: "
                        + (", ".join(repr(message) for message in missing_messages) if missing_messages else "<none>")
                        + "; forbidden surfaced matches: "
                        + (", ".join(forbidden_matches) if forbidden_matches else "<none>")
                    ),
                )
            )

        return failures

    @staticmethod
    def format_failures(failures: list[ValidationFailure]) -> str:
        lines = [
            "DMC-999 expects .github/workflows/package-cli.yml to present the reusable packaging flow as a "
            "CLI-first bundle and to avoid legacy server/API-only wording."
        ]
        for failure in failures:
            lines.append(f"- Step {failure.step}: {failure.summary}")
            lines.append(f"  Expected: {failure.expected}")
            lines.append(f"  Actual: {failure.actual}")
        return "\n".join(lines)

    @classmethod
    def _forbidden_phrase_matches(cls, surfaced_lines: list[str]) -> list[str]:
        matches: list[str] = []
        for line in surfaced_lines:
            normalized_line = line.casefold()
            for phrase in cls.FORBIDDEN_LEGACY_PHRASES:
                if phrase in normalized_line:
                    matches.append(f"{phrase!r} in {line!r}")
        return matches

    def _all_matches(self, pattern: re.Pattern[str]) -> list[str]:
        return [match.group("value") for match in pattern.finditer(self.workflow_text)]

    def _first_match(self, pattern: re.Pattern[str]) -> str:
        for match in pattern.finditer(self.workflow_text):
            return match.group("value")
        raise AssertionError(
            f"Expected to find pattern {pattern.pattern!r} in {self.workflow_path}"
        )
