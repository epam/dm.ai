from __future__ import annotations

import re
import shlex
from datetime import datetime, timezone
from pathlib import Path

from testing.core.interfaces.stable_release_audit_client import StableReleaseAuditClient
from testing.core.models.stable_release_install_paths_audit import (
    ReleaseRecord,
    StableReleaseInstallPathsAudit,
    StableReleaseInstallPathsFailure,
    WorkflowJobRecord,
    WorkflowRunRecord,
)


class StableReleaseInstallPathsService:
    ANSI_ESCAPE_PATTERN = re.compile(r"\x1B\[[0-?]*[ -/]*[@-~]")
    RAW_GITHUB_PATTERN = re.compile(r"raw\.githubusercontent\.com", re.IGNORECASE)
    UNSUPPORTED_SERVER_PATTERN = re.compile(r"dmtools-server", re.IGNORECASE)
    STABLE_TAG_PATTERN = re.compile(r"^v\d+\.\d+\.\d+$")
    RELEASE_URL_PATTERN = re.compile(r"https://github\.com/epam/dm\.ai/releases/[^\s)`\"']+")
    CLI_RELEASE_URL_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/"
        r"(?:latest/download|download/v\d+\.\d+\.\d+)/install(?:\.sh|\.bat|\.ps1)?\b"
    )
    SKILL_RELEASE_URL_PATTERN = re.compile(
        r"https://github\.com/epam/dm\.ai/releases/download/v\d+\.\d+\.\d+/skill-install(?:\.sh|\.ps1)\b"
    )
    DEFAULT_MAX_RUN_MATCH_SECONDS = 7200

    def __init__(
        self,
        repository_root: Path,
        github_client: StableReleaseAuditClient,
        *,
        owner: str = "epam",
        repo: str = "dm.ai",
        workflow_file: str = "release.yml",
        workflow_job_name: str = "create-unified-release",
        release_limit: int = 10,
        run_limit: int = 20,
        max_run_match_seconds: int = DEFAULT_MAX_RUN_MATCH_SECONDS,
    ) -> None:
        self.repository_root = repository_root
        self.github_client = github_client
        self.owner = owner
        self.repo = repo
        self.workflow_file = workflow_file
        self.workflow_job_name = workflow_job_name
        self.release_limit = release_limit
        self.run_limit = run_limit
        self.max_run_match_seconds = max_run_match_seconds

    def audit(self) -> StableReleaseInstallPathsAudit:
        failures: list[StableReleaseInstallPathsFailure] = []

        release = self._latest_stable_release()
        if release is None:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=1,
                    summary="A stable GitHub release was not available for verification.",
                    expected=(
                        f"A recent non-draft, non-prerelease release in {self.owner}/{self.repo} "
                        "with a semantic tag like v1.2.3."
                    ),
                    actual="No stable release was returned by the GitHub Releases API.",
                )
            )
            return StableReleaseInstallPathsAudit(
                release=None,
                workflow_run=None,
                workflow_job=None,
                release_urls=(),
                workflow_summary_urls=(),
                release_cli_urls=(),
                release_skill_urls=(),
                workflow_summary_cli_urls=(),
                workflow_summary_skill_urls=(),
                release_forbidden_lines=(),
                workflow_summary_forbidden_lines=(),
                failures=tuple(failures),
            )

        release_urls = tuple(self.RELEASE_URL_PATTERN.findall(release.body))
        release_cli_urls = tuple(self.CLI_RELEASE_URL_PATTERN.findall(release.body))
        release_skill_urls = tuple(self.SKILL_RELEASE_URL_PATTERN.findall(release.body))
        release_forbidden_lines = self._forbidden_lines(release.body)

        if not release_cli_urls:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=2,
                    summary="The live stable release body does not expose a supported CLI install path.",
                    expected=(
                        "At least one GitHub Releases asset URL for the CLI install flow, such as "
                        "https://github.com/epam/dm.ai/releases/latest/download/install.sh."
                    ),
                    actual=self._preview(release.body),
                )
            )

        if not release_skill_urls:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=3,
                    summary="The live stable release body does not expose a supported skill install path.",
                    expected=(
                        "At least one GitHub Releases asset URL for the skill install flow, such as "
                        "https://github.com/epam/dm.ai/releases/download/v1.2.3/skill-install.sh."
                    ),
                    actual=self._preview(release.body),
                )
            )

        if release_forbidden_lines:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=4,
                    summary="The live stable release body still references unsupported raw or server paths.",
                    expected=(
                        "Only supported GitHub Releases install URLs for CLI and skills, with no "
                        "raw.githubusercontent.com or dmtools-server references."
                    ),
                    actual="\n".join(release_forbidden_lines),
                )
            )

        workflow_run = self._matching_workflow_run(release)
        if workflow_run is None:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=5,
                    summary="A matching successful release workflow run could not be identified.",
                    expected=(
                        f"A successful {self.workflow_file} workflow_dispatch run on main close to "
                        f"the release publication time for {release.tag_name}."
                    ),
                    actual=(
                        f"No completed successful {self.workflow_file} run on main was found within "
                        f"{self.max_run_match_seconds} seconds of {release.published_at}."
                    ),
                )
            )
            return StableReleaseInstallPathsAudit(
                release=release,
                workflow_run=None,
                workflow_job=None,
                release_urls=release_urls,
                workflow_summary_urls=(),
                release_cli_urls=release_cli_urls,
                release_skill_urls=release_skill_urls,
                workflow_summary_cli_urls=(),
                workflow_summary_skill_urls=(),
                release_forbidden_lines=release_forbidden_lines,
                workflow_summary_forbidden_lines=(),
                failures=tuple(failures),
            )

        workflow_job = self._workflow_job(workflow_run.run_id)
        if workflow_job is None:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=6,
                    summary="The matching release workflow run does not expose the unified-release job.",
                    expected=(
                        f"A job named {self.workflow_job_name!r} in the matching release workflow run."
                    ),
                    actual=(
                        f"Run {workflow_run.run_id} did not include a job named "
                        f"{self.workflow_job_name!r}."
                    ),
                )
            )
            return StableReleaseInstallPathsAudit(
                release=release,
                workflow_run=workflow_run,
                workflow_job=None,
                release_urls=release_urls,
                workflow_summary_urls=(),
                release_cli_urls=release_cli_urls,
                release_skill_urls=release_skill_urls,
                workflow_summary_cli_urls=(),
                workflow_summary_skill_urls=(),
                release_forbidden_lines=release_forbidden_lines,
                workflow_summary_forbidden_lines=(),
                failures=tuple(failures),
            )

        if "Summary" not in workflow_job.step_names:
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=7,
                    summary="The unified-release job is missing the user-visible Summary step.",
                    expected="A job step named 'Summary' that writes the workflow summary.",
                    actual=", ".join(workflow_job.step_names) or "No steps were reported for the job.",
                )
            )

        workflow_summary_urls = tuple(self.RELEASE_URL_PATTERN.findall(workflow_job.summary_text))
        workflow_summary_cli_urls = tuple(self.CLI_RELEASE_URL_PATTERN.findall(workflow_job.summary_text))
        workflow_summary_skill_urls = tuple(
            self.SKILL_RELEASE_URL_PATTERN.findall(workflow_job.summary_text)
        )
        workflow_summary_forbidden_lines = self._forbidden_lines(workflow_job.summary_text)

        if "Summary" in workflow_job.step_names and not workflow_job.summary_text.strip():
            failures.append(
                StableReleaseInstallPathsFailure(
                    step=8,
                    summary="The live release workflow Summary output could not be reconstructed.",
                    expected=(
                        "A user-visible Summary step body showing the generated release copy for CLI "
                        "and skill installs."
                    ),
                    actual=self._preview(workflow_job.log_text),
                )
            )
        elif "Summary" in workflow_job.step_names:
            if not workflow_summary_cli_urls:
                failures.append(
                    StableReleaseInstallPathsFailure(
                        step=9,
                        summary=(
                            "The live release workflow Summary does not expose a supported CLI install path."
                        ),
                        expected=(
                            "At least one GitHub Releases asset URL for the CLI install flow, such as "
                            "https://github.com/epam/dm.ai/releases/latest/download/install.sh."
                        ),
                        actual=self._preview(workflow_job.summary_text),
                    )
                )

            if not workflow_summary_skill_urls:
                failures.append(
                    StableReleaseInstallPathsFailure(
                        step=10,
                        summary=(
                            "The live release workflow Summary does not expose a supported skill install path."
                        ),
                        expected=(
                            "At least one GitHub Releases asset URL for the skill install flow, such as "
                            "https://github.com/epam/dm.ai/releases/download/v1.2.3/skill-install.sh."
                        ),
                        actual=self._preview(workflow_job.summary_text),
                    )
                )

            if workflow_summary_forbidden_lines:
                failures.append(
                    StableReleaseInstallPathsFailure(
                        step=11,
                        summary=(
                            "The live release workflow Summary still references unsupported raw or server paths."
                        ),
                        expected=(
                            "Workflow Summary output for the release should not contain "
                            "raw.githubusercontent.com or dmtools-server references."
                        ),
                        actual="\n".join(workflow_summary_forbidden_lines),
                    )
                )

        return StableReleaseInstallPathsAudit(
            release=release,
            workflow_run=workflow_run,
            workflow_job=workflow_job,
            release_urls=release_urls,
            workflow_summary_urls=workflow_summary_urls,
            release_cli_urls=release_cli_urls,
            release_skill_urls=release_skill_urls,
            workflow_summary_cli_urls=workflow_summary_cli_urls,
            workflow_summary_skill_urls=workflow_summary_skill_urls,
            release_forbidden_lines=release_forbidden_lines,
            workflow_summary_forbidden_lines=workflow_summary_forbidden_lines,
            failures=tuple(failures),
        )

    @staticmethod
    def format_failures(
        failures: tuple[StableReleaseInstallPathsFailure, ...]
        | list[StableReleaseInstallPathsFailure],
    ) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def human_observations(self, audit: StableReleaseInstallPathsAudit) -> list[str]:
        observations: list[str] = []
        if audit.release is None:
            observations.append("No stable GitHub Release page was available to inspect.")
            return observations

        observations.append(
            f"Release page opened at {audit.release.html_url} for {audit.release.tag_name}, "
            f"published {audit.release.published_at}."
        )
        observations.append(
            "Release page body visibly shows these install URLs: "
            + (", ".join(audit.release_urls[:6]) if audit.release_urls else "none")
        )
        if audit.release_forbidden_lines:
            observations.append(
                "Release page body still shows unsupported markers: "
                + " | ".join(audit.release_forbidden_lines)
            )
        else:
            observations.append(
                "Release page body did not show raw.githubusercontent.com or dmtools-server."
            )

        if audit.workflow_run is None:
            observations.append("No matching release workflow run could be opened.")
            return observations

        observations.append(
            f"Matching workflow run opened at {audit.workflow_run.html_url} "
            f"({audit.workflow_run.status}/{audit.workflow_run.conclusion})."
        )
        if audit.workflow_job is None:
            observations.append(
                f"The workflow run did not expose the {self.workflow_job_name} job for inspection."
            )
            return observations

        observations.append(
            f"Unified release job visible at {audit.workflow_job.html_url} with steps: "
            + ", ".join(audit.workflow_job.step_names)
        )
        if audit.workflow_summary_forbidden_lines:
            observations.append(
                "Workflow Summary still shows unsupported markers: "
                + " | ".join(audit.workflow_summary_forbidden_lines)
            )
        else:
            observations.append(
                "Workflow Summary did not show raw.githubusercontent.com or dmtools-server."
            )
        return observations

    def _latest_stable_release(self) -> ReleaseRecord | None:
        for release_payload in self.github_client.list_releases(limit=self.release_limit):
            tag_name = str(release_payload.get("tag_name") or "").strip()
            if not tag_name or not self.STABLE_TAG_PATTERN.fullmatch(tag_name):
                continue
            if bool(release_payload.get("draft")) or bool(release_payload.get("prerelease")):
                continue

            assets = release_payload.get("assets") or []
            asset_names = tuple(
                str(asset.get("name") or "").strip()
                for asset in assets
                if isinstance(asset, dict) and str(asset.get("name") or "").strip()
            )
            return ReleaseRecord(
                tag_name=tag_name,
                html_url=str(release_payload.get("html_url") or "").strip(),
                published_at=str(release_payload.get("published_at") or "").strip(),
                body=str(release_payload.get("body") or ""),
                asset_names=asset_names,
            )
        return None

    def _matching_workflow_run(self, release: ReleaseRecord) -> WorkflowRunRecord | None:
        release_published_at = self._parse_github_datetime(release.published_at)
        candidates: list[tuple[float, WorkflowRunRecord]] = []

        for run_payload in self.github_client.workflow_runs(
            self.workflow_file,
            branch="main",
            event="workflow_dispatch",
            status="completed",
            limit=self.run_limit,
        ):
            if str(run_payload.get("conclusion") or "").lower() != "success":
                continue

            updated_at = str(run_payload.get("updated_at") or "").strip()
            created_at = str(run_payload.get("created_at") or "").strip()
            if not updated_at or not created_at:
                continue

            updated_at_dt = self._parse_github_datetime(updated_at)
            distance_seconds = abs((updated_at_dt - release_published_at).total_seconds())
            if distance_seconds > self.max_run_match_seconds:
                continue

            candidates.append(
                (
                    distance_seconds,
                    WorkflowRunRecord(
                        run_id=int(run_payload["id"]),
                        html_url=str(run_payload.get("html_url") or "").strip(),
                        status=str(run_payload.get("status") or "").strip(),
                        conclusion=str(run_payload.get("conclusion") or "").strip(),
                        created_at=created_at,
                        updated_at=updated_at,
                        head_branch=str(run_payload.get("head_branch") or "").strip(),
                    ),
                )
            )

        if not candidates:
            return None

        candidates.sort(key=lambda item: item[0])
        return candidates[0][1]

    def _workflow_job(self, run_id: int) -> WorkflowJobRecord | None:
        for job_payload in self.github_client.workflow_jobs(run_id):
            if str(job_payload.get("name") or "").strip() != self.workflow_job_name:
                continue
            step_names = tuple(
                str(step.get("name") or "").strip()
                for step in (job_payload.get("steps") or [])
                if isinstance(step, dict) and str(step.get("name") or "").strip()
            )
            job_id = int(job_payload["id"])
            log_text = self.github_client.workflow_job_logs(job_id)
            return WorkflowJobRecord(
                job_id=job_id,
                name=self.workflow_job_name,
                html_url=str(job_payload.get("html_url") or "").strip(),
                step_names=step_names,
                log_text=log_text,
                summary_text=self._extract_step_summary(log_text),
            )
        return None

    def _forbidden_lines(self, text: str) -> tuple[str, ...]:
        lines: list[str] = []
        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if self.RAW_GITHUB_PATTERN.search(line) or self.UNSUPPORTED_SERVER_PATTERN.search(line):
                lines.append(line)
        return tuple(lines)

    @staticmethod
    def _preview(text: str, limit: int = 400) -> str:
        collapsed = " ".join(text.split())
        if len(collapsed) <= limit:
            return collapsed
        return collapsed[: limit - 3] + "..."

    @staticmethod
    def _strip_log_prefix(line: str) -> str:
        without_prefix = re.sub(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\s+", "", line)
        return StableReleaseInstallPathsService.ANSI_ESCAPE_PATTERN.sub("", without_prefix)

    @classmethod
    def _extract_step_summary(cls, log_text: str) -> str:
        summary_lines: list[str] = []
        in_summary_group = False

        for raw_line in log_text.splitlines():
            stripped = cls._strip_log_prefix(raw_line).strip()
            if not stripped:
                continue

            if stripped.startswith("##[group]Run ") and "$GITHUB_STEP_SUMMARY" in stripped:
                in_summary_group = True
                continue

            if in_summary_group and stripped.startswith("##[endgroup]"):
                break

            if not in_summary_group or "$GITHUB_STEP_SUMMARY" not in stripped:
                continue

            content = cls._extract_summary_write_content(stripped)
            if content is not None:
                summary_lines.append(content)

        if not summary_lines:
            for raw_line in log_text.splitlines():
                stripped = cls._strip_log_prefix(raw_line).strip()
                if "$GITHUB_STEP_SUMMARY" not in stripped or stripped.startswith("##[group]Run "):
                    continue
                content = cls._extract_summary_write_content(stripped)
                if content is not None:
                    summary_lines.append(content)

        return "\n".join(summary_lines)

    @staticmethod
    def _extract_summary_write_content(line: str) -> str | None:
        normalized = re.sub(r'\s*>>\s*"?\$GITHUB_STEP_SUMMARY"?\s*$', "", line).strip()
        if not normalized.startswith("echo "):
            return None

        try:
            parts = shlex.split(normalized, posix=True)
        except ValueError:
            return None

        if not parts or parts[0] != "echo":
            return None

        if len(parts) == 1:
            return ""
        return " ".join(parts[1:])

    @staticmethod
    def _parse_github_datetime(value: str) -> datetime:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
