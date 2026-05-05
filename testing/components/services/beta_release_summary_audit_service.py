from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from threading import Event
from typing import Any
from urllib.error import HTTPError

from testing.core.interfaces.beta_release_summary_audit_service import (
    BetaReleaseSummaryAuditService as BetaReleaseSummaryAuditServiceContract,
)
from testing.core.interfaces.github_actions_release_client import (
    GitHubActionsReleaseClient,
)
from testing.core.models.beta_release_summary_audit import (
    BetaReleaseAuditFailure,
    BetaReleaseJobObservation,
    BetaReleaseReleaseObservation,
    BetaReleaseRunObservation,
    BetaReleaseSummaryAudit,
)


class BetaReleaseSummaryAuditService(BetaReleaseSummaryAuditServiceContract):
    RELEASE_BODY_MARKERS = (
        "this is a pre-release / beta build",
        "dmtools cli",
        "dmtools agent skill",
        "releases/download/",
        "install.sh",
        "install.ps1",
    )
    SUMMARY_REQUIRED_MARKERS = (
        "pre-release",
        "latest",
        "dmtools cli",
        "dmtools agent skill",
        "releases/download/",
        "install.sh",
    )
    REQUIRED_ASSET_MARKERS = (
        "dmtools-v",
        "install.sh",
        "install.ps1",
        "dmtools.sh",
        "dmtools-skill-v",
        "skill-install.sh",
        "skill-install.ps1",
        "skill-checksums.sha256",
    )
    SUMMARY_ECHO_PATTERN = re.compile(r'echo (?P<argument>.+?) >> \$GITHUB_STEP_SUMMARY$')
    ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
    TIMESTAMP_PREFIX_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T[^ ]+\s+")
    RELEASE_URL_PATTERN = re.compile(
        r"https://github\.com/(?P<owner>[^/]+)/(?P<repo>[^/]+)/releases/tag/(?P<tag>[^\s\"']+)"
    )

    def __init__(
        self,
        github_client: GitHubActionsReleaseClient,
        *,
        workflow_file: str,
        workflow_ref: str,
        workflow_name: str,
        release_job_name: str,
        dispatch_timeout_seconds: int,
        completion_timeout_seconds: int,
        poll_interval_seconds: int,
    ) -> None:
        self.github_client = github_client
        self.workflow_file = workflow_file
        self.workflow_ref = workflow_ref
        self.workflow_name = workflow_name
        self.release_job_name = release_job_name
        self.dispatch_timeout_seconds = dispatch_timeout_seconds
        self.completion_timeout_seconds = completion_timeout_seconds
        self.poll_interval_seconds = poll_interval_seconds
        self._waiter = Event()

    def audit(self) -> BetaReleaseSummaryAudit:
        failures: list[BetaReleaseAuditFailure] = []

        dispatch_started_at = datetime.now(timezone.utc)
        target_head_sha = self.github_client.branch_head_sha(self.workflow_ref)
        existing_run_ids = {
            int(run["id"])
            for run in self.github_client.workflow_runs_for_workflow(
                self.workflow_file,
                branch=self.workflow_ref,
                per_page=20,
            )
            if run.get("id") is not None
        }

        self.github_client.dispatch_workflow(self.workflow_file, ref=self.workflow_ref)
        workflow_run = self._wait_for_dispatched_run(
            existing_run_ids=existing_run_ids,
            target_head_sha=target_head_sha,
            dispatch_started_at=dispatch_started_at,
        )
        if workflow_run is None:
            failures.append(
                BetaReleaseAuditFailure(
                    step=1,
                    summary="The beta-release workflow did not appear after dispatch.",
                    expected=(
                        f"A new {self.workflow_name!r} run triggered by workflow_dispatch on "
                        f"{self.workflow_ref!r}."
                    ),
                    actual=(
                        f"No new workflow_dispatch run was listed for {self.workflow_file!r} "
                        f"within {self.dispatch_timeout_seconds} seconds."
                    ),
                )
            )
            return BetaReleaseSummaryAudit(
                workflow_run=None,
                release_job=None,
                release=None,
                failures=tuple(failures),
            )

        workflow_run = self._wait_for_completion(workflow_run.run_id)
        if workflow_run.status != "completed" or workflow_run.conclusion != "success":
            failures.append(
                BetaReleaseAuditFailure(
                    step=2,
                    summary="The dispatched beta-release workflow did not finish successfully.",
                    expected="A completed workflow run with conclusion 'success'.",
                    actual=(
                        f"Run {workflow_run.html_url} finished with status={workflow_run.status!r} "
                        f"and conclusion={workflow_run.conclusion!r}."
                    ),
                )
            )
            return BetaReleaseSummaryAudit(
                workflow_run=workflow_run,
                release_job=None,
                release=None,
                failures=tuple(failures),
            )

        release_job_payload = self._find_release_job_payload(workflow_run.run_id)
        if release_job_payload is None:
            failures.append(
                BetaReleaseAuditFailure(
                    step=3,
                    summary="The completed workflow run does not expose the publication job.",
                    expected=f"A job named {self.release_job_name!r} in the completed workflow run.",
                    actual=f"No job matched {self.release_job_name!r} in run {workflow_run.html_url}.",
                )
            )
            return BetaReleaseSummaryAudit(
                workflow_run=workflow_run,
                release_job=None,
                release=None,
                failures=tuple(failures),
            )

        release_job = self._build_release_job_observation(release_job_payload)
        if release_job.status != "completed" or release_job.conclusion != "success":
            failures.append(
                BetaReleaseAuditFailure(
                    step=3,
                    summary="The publication job did not finish successfully.",
                    expected="A completed create-beta-release job with conclusion 'success'.",
                    actual=(
                        f"Job {release_job.html_url} finished with status={release_job.status!r} "
                        f"and conclusion={release_job.conclusion!r}."
                    ),
                )
            )
            return BetaReleaseSummaryAudit(
                workflow_run=workflow_run,
                release_job=release_job,
                release=None,
                failures=tuple(failures),
            )

        release = self._find_release_observation(release_job, dispatch_started_at)
        if release is None:
            failures.append(
                BetaReleaseAuditFailure(
                    step=4,
                    summary="The beta release was not discoverable after the workflow succeeded.",
                    expected=(
                        "A prerelease created by the publication job, with a release tag URL "
                        "visible in the job logs or a matching recent prerelease in GitHub Releases."
                    ),
                    actual=(
                        f"Job {release_job.html_url} completed successfully, but no matching "
                        "prerelease could be resolved."
                    ),
                )
            )
            return BetaReleaseSummaryAudit(
                workflow_run=workflow_run,
                release_job=release_job,
                release=None,
                failures=tuple(failures),
            )

        missing_release_markers = self._missing_markers(release.body, self.RELEASE_BODY_MARKERS)
        missing_assets = self._missing_asset_markers(release.asset_names)
        if not release.is_prerelease or missing_release_markers or missing_assets:
            failures.append(
                BetaReleaseAuditFailure(
                    step=4,
                    summary=(
                        "The live beta release page does not fully match the supported prerelease "
                        "packaging model."
                    ),
                    expected=(
                        "A prerelease release page that clearly describes the DMTools CLI and "
                        "DMTools Agent Skill, uses GitHub Releases install examples, and publishes "
                        "the maintained installer assets."
                    ),
                    actual=(
                        f"Release {release.html_url} prerelease={release.is_prerelease!r}; "
                        f"missing body markers: {missing_release_markers or ['none']}; "
                        f"missing asset markers: {missing_assets or ['none']}. "
                        f"Visible body preview: {self._preview_text(release.body, limit=420)}"
                    ),
                )
            )

        missing_summary_markers = self._missing_markers(
            release_job.step_summary_markdown,
            self.SUMMARY_REQUIRED_MARKERS,
        )
        if missing_summary_markers:
            failures.append(
                BetaReleaseAuditFailure(
                    step=5,
                    summary=(
                        "The publication job Step Summary does not mirror the supported packaging "
                        "copy with beta-specific framing."
                    ),
                    expected=(
                        "The Step Summary should visibly mention the DMTools CLI and DMTools Agent "
                        "Skill, include GitHub Releases install guidance, and keep the prerelease/"
                        "stable-latest framing."
                    ),
                    actual=(
                        f"Job {release_job.html_url} produced summary markdown missing markers "
                        f"{missing_summary_markers}. Summary preview: "
                        f"{self._preview_text(release_job.step_summary_markdown, limit=420)}"
                    ),
                )
            )

        return BetaReleaseSummaryAudit(
            workflow_run=workflow_run,
            release_job=release_job,
            release=release,
            failures=tuple(failures),
        )

    @staticmethod
    def format_failures(
        failures: tuple[BetaReleaseAuditFailure, ...] | list[BetaReleaseAuditFailure],
    ) -> str:
        return "\n\n".join(failure.format() for failure in failures)

    def human_observations(self, audit: BetaReleaseSummaryAudit) -> list[str]:
        observations: list[str] = []
        if audit.workflow_run is not None:
            observations.append(
                f"Triggered live workflow run #{audit.workflow_run.run_number}: "
                f"{audit.workflow_run.html_url}"
            )
        if audit.release_job is not None:
            observations.append(
                f"Publication job visible in Actions UI: {audit.release_job.html_url}"
            )
            observations.append(
                "Observed Step Summary markdown generated by the job: "
                + self._preview_text(audit.release_job.step_summary_markdown, limit=300)
            )
        if audit.release is not None:
            observations.append(f"Observed prerelease page: {audit.release.html_url}")
            observations.append(
                "Visible release notes preview: "
                + self._preview_text(audit.release.body, limit=300)
            )
            observations.append(
                "Published asset names: " + ", ".join(audit.release.asset_names[:8])
            )
        return observations

    def _wait_for_dispatched_run(
        self,
        *,
        existing_run_ids: set[int],
        target_head_sha: str,
        dispatch_started_at: datetime,
    ) -> BetaReleaseRunObservation | None:
        deadline = self._deadline(self.dispatch_timeout_seconds)
        while datetime.now(timezone.utc) < deadline:
            runs = self.github_client.workflow_runs_for_workflow(
                self.workflow_file,
                branch=self.workflow_ref,
                event="workflow_dispatch",
                per_page=20,
            )
            for run in runs:
                run_id = run.get("id")
                if run_id is None:
                    continue
                if int(run_id) in existing_run_ids:
                    continue
                if str(run.get("head_sha", "")) != target_head_sha:
                    continue
                created_at = self._parse_github_datetime(str(run.get("created_at", "")))
                if created_at is None or created_at < dispatch_started_at - timedelta(minutes=1):
                    continue
                return self._build_run_observation(run)
            self._waiter.wait(self.poll_interval_seconds)
        return None

    def _wait_for_completion(self, run_id: int) -> BetaReleaseRunObservation:
        deadline = self._deadline(self.completion_timeout_seconds)
        last_seen = self._build_run_observation(self.github_client.workflow_run(run_id))
        while datetime.now(timezone.utc) < deadline:
            current = self._build_run_observation(self.github_client.workflow_run(run_id))
            last_seen = current
            if current.status == "completed":
                return current
            self._waiter.wait(self.poll_interval_seconds)
        return last_seen

    def _find_release_job_payload(self, run_id: int) -> dict[str, Any] | None:
        for job in self.github_client.workflow_jobs(run_id):
            name = str(job.get("name", ""))
            if name == self.release_job_name:
                return job
        return None

    def _build_release_job_observation(self, payload: dict[str, Any]) -> BetaReleaseJobObservation:
        job_id = int(payload["id"])
        log_text = self.github_client.workflow_job_logs(job_id)
        step_summary_markdown = self._extract_step_summary_markdown(log_text)
        return BetaReleaseJobObservation(
            job_id=job_id,
            name=str(payload.get("name", "")),
            html_url=str(payload.get("html_url", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            step_summary_markdown=step_summary_markdown,
            log_excerpt=self._log_excerpt(log_text),
        )

    def _find_release_observation(
        self,
        release_job: BetaReleaseJobObservation,
        dispatch_started_at: datetime,
    ) -> BetaReleaseReleaseObservation | None:
        tag = self._release_tag_from_text(release_job.step_summary_markdown) or self._release_tag_from_text(
            release_job.log_excerpt
        )
        release_payload: dict[str, Any] | None = None
        lookup_deadline = self._deadline(self.poll_interval_seconds * 3)
        while datetime.now(timezone.utc) < lookup_deadline and release_payload is None:
            if tag:
                try:
                    release_payload = self.github_client.release_by_tag(tag)
                except HTTPError as error:
                    if error.code != 404:
                        raise

            if release_payload is None:
                for release in self.github_client.list_releases(per_page=20):
                    if not bool(release.get("prerelease", False)):
                        continue
                    created_at = self._parse_github_datetime(str(release.get("created_at", "")))
                    if created_at is None or created_at < dispatch_started_at:
                        continue
                    if tag and str(release.get("tag_name", "")) != tag:
                        continue
                    release_payload = release
                    break

            if release_payload is None:
                self._waiter.wait(self.poll_interval_seconds)

        if release_payload is None:
            return None

        assets = release_payload.get("assets", [])
        asset_names = tuple(
            str(asset.get("name", ""))
            for asset in assets
            if isinstance(asset, dict) and str(asset.get("name", "")).strip()
        )
        return BetaReleaseReleaseObservation(
            tag_name=str(release_payload.get("tag_name", "")),
            html_url=str(release_payload.get("html_url", "")),
            is_prerelease=bool(release_payload.get("prerelease", False)),
            body=str(release_payload.get("body", "")),
            asset_names=asset_names,
        )

    def _extract_step_summary_markdown(self, raw_log_text: str) -> str:
        lines: list[str] = []
        for raw_line in raw_log_text.splitlines():
            line = self._normalize_log_line(raw_line)
            if line.startswith("##[group]Run "):
                continue
            if ">> $GITHUB_STEP_SUMMARY" not in line:
                continue
            match = self.SUMMARY_ECHO_PATTERN.search(line)
            if not match:
                continue
            lines.append(self._decode_echo_argument(match.group("argument")))
        return "\n".join(lines).strip()

    def _log_excerpt(self, raw_log_text: str, limit: int = 6000) -> str:
        cleaned = "\n".join(self._normalize_log_line(line) for line in raw_log_text.splitlines())
        if len(cleaned) <= limit:
            return cleaned
        return cleaned[: limit - 3] + "..."

    @classmethod
    def _normalize_log_line(cls, line: str) -> str:
        without_ansi = cls.ANSI_PATTERN.sub("", line)
        return cls.TIMESTAMP_PREFIX_PATTERN.sub("", without_ansi).strip()

    @staticmethod
    def _decode_echo_argument(argument: str) -> str:
        value = argument.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        return (
            value.replace(r"\\", "\\")
            .replace(r"\"", '"')
            .replace(r"\`", "`")
            .replace(r"\$", "$")
        )

    @classmethod
    def _release_tag_from_text(cls, text: str) -> str:
        match = cls.RELEASE_URL_PATTERN.search(text)
        if match:
            return match.group("tag")
        tag_match = re.search(r"\bv\d+\.\d+\.\d+(?:-beta\.\d+\.\d+)?\b", text)
        return tag_match.group(0) if tag_match else ""

    @staticmethod
    def _missing_markers(text: str, markers: tuple[str, ...]) -> list[str]:
        normalized = " ".join(text.lower().split())
        return [marker for marker in markers if marker not in normalized]

    @classmethod
    def _missing_asset_markers(cls, asset_names: tuple[str, ...]) -> list[str]:
        normalized_assets = " ".join(name.lower() for name in asset_names)
        return [marker for marker in cls.REQUIRED_ASSET_MARKERS if marker not in normalized_assets]

    @staticmethod
    def _preview_text(value: str, *, limit: int) -> str:
        compact = " ".join(value.split())
        if not compact:
            return "no visible text"
        if len(compact) <= limit:
            return compact
        return compact[: limit - 3] + "..."

    @staticmethod
    def _build_run_observation(payload: dict[str, Any]) -> BetaReleaseRunObservation:
        return BetaReleaseRunObservation(
            run_id=int(payload["id"]),
            html_url=str(payload.get("html_url", "")),
            event=str(payload.get("event", "")),
            status=str(payload.get("status", "")),
            conclusion=str(payload.get("conclusion", "")),
            head_branch=str(payload.get("head_branch", "")),
            head_sha=str(payload.get("head_sha", "")),
            created_at=str(payload.get("created_at", "")),
            run_number=int(payload.get("run_number", 0)),
        )

    @staticmethod
    def _parse_github_datetime(value: str) -> datetime | None:
        if not value:
            return None
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)

    @staticmethod
    def _deadline(timeout_seconds: int) -> datetime:
        return datetime.now(timezone.utc).replace(microsecond=0) + timedelta(seconds=timeout_seconds)
