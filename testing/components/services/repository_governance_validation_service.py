from __future__ import annotations

from dataclasses import dataclass
import json
import re
from pathlib import Path
from typing import Any

from testing.core.interfaces.process_runner import ProcessRunner
from testing.core.models.process_execution_result import ProcessExecutionResult


@dataclass(frozen=True)
class RepositoryGovernanceFailure:
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
class PlaybookReferenceAudit:
    reference: str
    line_number: int
    matches: tuple[str, ...]

    @property
    def resolved(self) -> bool:
        return bool(self.matches)


@dataclass(frozen=True)
class RepositoryGovernanceAudit:
    validator_command: tuple[str, ...]
    validator_result: ProcessExecutionResult | None
    metadata_source_path: Path
    playbook_path: Path
    topic_limit: int
    topic_count: int
    social_preview_fields: tuple[str, ...]
    playbook_references: tuple[PlaybookReferenceAudit, ...]
    failures: tuple[RepositoryGovernanceFailure, ...]


class RepositoryGovernanceValidationService:
    DEFAULT_VALIDATOR_COMMAND = (
        "./gradlew",
        "--no-daemon",
        ":dmtools-core:test",
        "--tests",
        "com.github.istin.dmtools.github.GitHubRepositoryDiscoverabilityTest",
    )
    REQUIRED_SOCIAL_PREVIEW_FIELDS = ("direction", "valueLine", "styleRules")
    _BACKTICK_PATTERN = re.compile(r"`([^`]+)`")
    _MARKDOWN_LINK_PATTERN = re.compile(r"\[[^\]]+\]\(([^)]+)\)")

    def __init__(
        self,
        repository_root: Path,
        runner: ProcessRunner,
        *,
        topic_limit: int = 20,
        validator_command: tuple[str, ...] = DEFAULT_VALIDATOR_COMMAND,
    ) -> None:
        self.repository_root = repository_root
        self.runner = runner
        self.topic_limit = topic_limit
        self.validator_command = validator_command
        self.metadata_source_path = (
            repository_root / "dmtools-core/src/main/resources/github-repository-discoverability.json"
        )
        self.playbook_path = (
            repository_root
            / "dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"
        )

    def audit(self, *, run_validator: bool = True) -> RepositoryGovernanceAudit:
        validator_result = self._run_validator() if run_validator else None
        failures: list[RepositoryGovernanceFailure] = []

        if validator_result is not None:
            failures.extend(self._validator_failures(validator_result))

        metadata_payload, metadata_failures = self._load_metadata_source()
        failures.extend(metadata_failures)

        topic_count = 0
        social_preview_fields: tuple[str, ...] = ()
        if metadata_payload is not None:
            topic_count, topic_failures = self._topic_failures(metadata_payload)
            failures.extend(topic_failures)
            social_preview_fields, social_preview_failures = self._social_preview_failures(
                metadata_payload
            )
            failures.extend(social_preview_failures)

        playbook_references, playbook_failures = self._playbook_reference_failures()
        failures.extend(playbook_failures)

        return RepositoryGovernanceAudit(
            validator_command=self.validator_command,
            validator_result=validator_result,
            metadata_source_path=self.metadata_source_path,
            playbook_path=self.playbook_path,
            topic_limit=self.topic_limit,
            topic_count=topic_count,
            social_preview_fields=social_preview_fields,
            playbook_references=playbook_references,
            failures=tuple(failures),
        )

    def human_observations(self, audit: RepositoryGovernanceAudit) -> list[str]:
        observations: list[str] = []

        if audit.validator_result is not None:
            build_outcome = (
                "BUILD SUCCESSFUL"
                if "BUILD SUCCESSFUL" in audit.validator_result.combined_output
                else "BUILD FAILED"
            )
            observations.append(
                "Maintainer flow: running "
                f"`{' '.join(audit.validator_command)}` exited {audit.validator_result.returncode} "
                f"and showed `{build_outcome}`."
            )

        if audit.metadata_source_path.exists():
            observations.append(
                "Metadata source: "
                f"`{audit.metadata_source_path.relative_to(self.repository_root).as_posix()}` exists."
            )
        else:
            observations.append(
                "Metadata source: "
                f"`{audit.metadata_source_path.relative_to(self.repository_root).as_posix()}` is missing."
            )

        if audit.social_preview_fields:
            observations.append(
                "Social-preview specification: metadata defines "
                + ", ".join(f"`{field}`" for field in audit.social_preview_fields)
                + "."
            )

        observations.append(
            f"GitHub topics: {audit.topic_count} configured topic(s) observed; limit is {audit.topic_limit}."
        )

        resolved_reference_count = sum(1 for reference in audit.playbook_references if reference.resolved)
        observations.append(
            "Playbook references: "
            f"{resolved_reference_count}/{len(audit.playbook_references)} documented repository path reference(s) "
            "resolved to real files or glob matches."
        )
        return observations

    def format_failures(self, audit: RepositoryGovernanceAudit) -> str:
        if not audit.failures:
            return (
                "DMC-990 expects the repository governance validation flow to pass, the canonical "
                "discoverability metadata source to exist with a social-preview specification, the "
                f"GitHub topic list to stay within {audit.topic_limit} topics, and the playbook's "
                "documented repository path references to resolve."
            )

        lines = [
            "DMC-990 repository governance validation failed.",
            "",
            *[failure.format() for failure in audit.failures],
        ]
        if audit.validator_result is not None:
            lines.extend(
                [
                    "",
                    "Validator stdout:",
                    audit.validator_result.stdout.rstrip() or "<empty>",
                    "",
                    "Validator stderr:",
                    audit.validator_result.stderr.rstrip() or "<empty>",
                ]
            )
        return "\n".join(lines)

    def _run_validator(self) -> ProcessExecutionResult:
        return self.runner.run(self.validator_command, cwd=self.repository_root)

    def _validator_failures(
        self,
        validator_result: ProcessExecutionResult,
    ) -> list[RepositoryGovernanceFailure]:
        failures: list[RepositoryGovernanceFailure] = []
        output = validator_result.combined_output
        command_display = " ".join(self.validator_command)

        if validator_result.returncode != 0:
            failures.append(
                RepositoryGovernanceFailure(
                    step=1,
                    summary="Repository governance validation flow did not complete successfully.",
                    expected=(
                        f"`{command_display}` should exit 0 and confirm the discoverability validation "
                        "passes for a maintainer."
                    ),
                    actual=(
                        f"`{command_display}` exited {validator_result.returncode}.\n"
                        f"{output or '<no validator output>'}"
                    ),
                )
            )
            return failures

        missing_markers = [marker for marker in ("BUILD SUCCESSFUL",) if marker not in output]
        if missing_markers:
            failures.append(
                RepositoryGovernanceFailure(
                    step=1,
                    summary="Repository governance validation output was incomplete.",
                    expected=(
                        f"`{command_display}` should exit cleanly and visibly report a successful "
                        "maintainer validation outcome."
                    ),
                    actual=(
                        "The validator output was missing: "
                        + ", ".join(repr(marker) for marker in missing_markers)
                        + ".\n"
                        + (output or "<no validator output>")
                    ),
                )
            )
        return failures

    def _load_metadata_source(
        self,
    ) -> tuple[dict[str, Any] | None, list[RepositoryGovernanceFailure]]:
        if not self.metadata_source_path.exists():
            return None, [
                RepositoryGovernanceFailure(
                    step=2,
                    summary="Canonical discoverability metadata source is missing.",
                    expected=(
                        "The governance validation should find "
                        "`dmtools-core/src/main/resources/github-repository-discoverability.json`."
                    ),
                    actual=(
                        f"`{self.metadata_source_path.relative_to(self.repository_root).as_posix()}` "
                        "does not exist."
                    ),
                )
            ]

        try:
            payload = json.loads(self.metadata_source_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            return None, [
                RepositoryGovernanceFailure(
                    step=2,
                    summary="Canonical discoverability metadata source is not valid JSON.",
                    expected="The metadata source should parse as valid JSON.",
                    actual=str(error),
                )
            ]

        if not isinstance(payload, dict):
            return None, [
                RepositoryGovernanceFailure(
                    step=2,
                    summary="Canonical discoverability metadata source has the wrong shape.",
                    expected="The metadata source should be a JSON object with discoverability fields.",
                    actual=f"Parsed JSON type was {type(payload)!r}.",
                )
            ]

        return payload, []

    def _social_preview_failures(
        self,
        metadata_payload: dict[str, Any],
    ) -> tuple[tuple[str, ...], list[RepositoryGovernanceFailure]]:
        social_preview = metadata_payload.get("socialPreview")
        if not isinstance(social_preview, dict):
            return (), [
                RepositoryGovernanceFailure(
                    step=2,
                    summary="Social-preview specification is missing from discoverability metadata.",
                    expected=(
                        "The metadata source should expose a `socialPreview` object so maintainers can "
                        "keep the discoverability asset guidance in sync."
                    ),
                    actual=f"`socialPreview` was {type(social_preview)!r}.",
                )
            ]

        missing_fields = [
            field
            for field in self.REQUIRED_SOCIAL_PREVIEW_FIELDS
            if not self._has_social_preview_value(field, social_preview.get(field))
        ]
        if missing_fields:
            return tuple(
                field
                for field in self.REQUIRED_SOCIAL_PREVIEW_FIELDS
                if field not in missing_fields
            ), [
                RepositoryGovernanceFailure(
                    step=2,
                    summary="Social-preview specification is incomplete.",
                    expected=(
                        "The metadata source should provide discoverability asset guidance through "
                        "`socialPreview.direction`, `socialPreview.valueLine`, and "
                        "`socialPreview.styleRules`."
                    ),
                    actual="Missing or empty fields: " + ", ".join(missing_fields),
                )
            ]

        return self.REQUIRED_SOCIAL_PREVIEW_FIELDS, []

    def _topic_failures(
        self,
        metadata_payload: dict[str, Any],
    ) -> tuple[int, list[RepositoryGovernanceFailure]]:
        topics = metadata_payload.get("topics")
        if not isinstance(topics, list) or not all(isinstance(topic, str) for topic in topics):
            return 0, [
                RepositoryGovernanceFailure(
                    step=3,
                    summary="Canonical GitHub topics are missing or malformed.",
                    expected="The metadata source should expose `topics` as a list of strings.",
                    actual=f"`topics` was {type(topics)!r} with value {topics!r}.",
                )
            ]

        topic_count = len(topics)
        if topic_count > self.topic_limit:
            return topic_count, [
                RepositoryGovernanceFailure(
                    step=3,
                    summary="Canonical GitHub topics exceed GitHub's limit.",
                    expected=f"The metadata source should define at most {self.topic_limit} topics.",
                    actual=(
                        f"`topics` contained {topic_count} entries: "
                        + ", ".join(repr(topic) for topic in topics)
                    ),
                )
            ]

        return topic_count, []

    def _playbook_reference_failures(
        self,
    ) -> tuple[tuple[PlaybookReferenceAudit, ...], list[RepositoryGovernanceFailure]]:
        if not self.playbook_path.exists():
            return (), [
                RepositoryGovernanceFailure(
                    step=4,
                    summary="Repository discoverability playbook is missing.",
                    expected=(
                        "The governance validation should inspect "
                        "`dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md`."
                    ),
                    actual=(
                        f"`{self.playbook_path.relative_to(self.repository_root).as_posix()}` "
                        "does not exist."
                    ),
                )
            ]

        lines = self.playbook_path.read_text(encoding="utf-8").splitlines()
        audits: list[PlaybookReferenceAudit] = []
        unresolved: list[str] = []
        for line_number, line in enumerate(lines, start=1):
            for reference in self._extract_repository_references(line):
                matches = tuple(self._resolve_reference(reference))
                audits.append(
                    PlaybookReferenceAudit(
                        reference=reference,
                        line_number=line_number,
                        matches=matches,
                    )
                )
                if not matches:
                    unresolved.append(f"{reference} (line {line_number})")

        failures: list[RepositoryGovernanceFailure] = []
        if unresolved:
            failures.append(
                RepositoryGovernanceFailure(
                    step=4,
                    summary="The playbook documents repository paths that do not resolve.",
                    expected=(
                        "Every repository file path or glob documented in the discoverability playbook "
                        "should resolve to a real file so maintainers can follow it."
                    ),
                    actual="Unresolved references: " + ", ".join(unresolved),
                )
            )
        return tuple(audits), failures

    def _extract_repository_references(self, line: str) -> list[str]:
        references: list[str] = []
        for candidate in self._BACKTICK_PATTERN.findall(line):
            normalized = candidate.strip()
            if self._looks_like_repository_reference(normalized):
                references.append(normalized)

        for candidate in self._MARKDOWN_LINK_PATTERN.findall(line):
            normalized = candidate.strip()
            if self._looks_like_repository_reference(normalized):
                references.append(normalized)

        deduplicated: list[str] = []
        seen: set[str] = set()
        for reference in references:
            if reference in seen:
                continue
            seen.add(reference)
            deduplicated.append(reference)
        return deduplicated

    @staticmethod
    def _looks_like_repository_reference(candidate: str) -> bool:
        if not candidate or "://" in candidate or candidate.startswith("#"):
            return False
        if "/" not in candidate and "*" not in candidate:
            return False
        if any(token in candidate for token in (" ", "\t", "\n")):
            return False
        return True

    def _resolve_reference(self, reference: str) -> list[str]:
        if any(wildcard in reference for wildcard in "*?[]{}"):
            return sorted(
                path.relative_to(self.repository_root).as_posix()
                for path in self.repository_root.glob(reference)
                if path.exists()
            )

        path = self.repository_root / reference
        if path.exists():
            return [path.relative_to(self.repository_root).as_posix()]
        return []

    @staticmethod
    def _has_social_preview_value(field: str, value: Any) -> bool:
        if field == "styleRules":
            return isinstance(value, list) and any(
                isinstance(item, str) and item.strip() for item in value
            )
        return isinstance(value, str) and bool(value.strip())
