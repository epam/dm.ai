import re
from pathlib import Path

from testing.core.interfaces.job_registry import JobRegistration, JobRegistry


class JobRegistryService(JobRegistry):
    _CASE_BLOCK_PATTERN = re.compile(
        r'((?:case\s+"[^"]+":\s*)+)return new ([\w\.]+)\(\);',
        re.MULTILINE,
    )
    _CASE_NAME_PATTERN = re.compile(r'case\s+"([^"]+)":')
    _JOB_INSTANCE_PATTERN = re.compile(r"new ([\w\.]+)\(\)")

    def __init__(self, job_runner_path: Path) -> None:
        self._job_runner_path = job_runner_path
        self._source = job_runner_path.read_text(encoding="utf-8")
        self._registrations = self._parse_registrations()
        self._listed_jobs = self._parse_listed_jobs()

    @staticmethod
    def _simple_name(type_name: str) -> str:
        return type_name.rsplit(".", 1)[-1]

    def _parse_registrations(self) -> list[JobRegistration]:
        registrations: list[JobRegistration] = []
        for match in self._CASE_BLOCK_PATTERN.finditer(self._source):
            aliases = tuple(self._CASE_NAME_PATTERN.findall(match.group(1)))
            class_name = self._simple_name(match.group(2))
            registrations.append(JobRegistration(aliases=aliases, class_name=class_name))
        return registrations

    def _parse_listed_jobs(self) -> set[str]:
        list_section = self._source.split("JOBS = Arrays.asList(", 1)[1].split(");", 1)[0]
        return {self._simple_name(name) for name in self._JOB_INSTANCE_PATTERN.findall(list_section)}

    @property
    def canonical_job_names(self) -> set[str]:
        return {registration.class_name for registration in self._registrations}

    @property
    def accepted_input_names(self) -> dict[str, str]:
        accepted: dict[str, str] = {}
        for registration in self._registrations:
            for alias in registration.aliases:
                accepted[alias.lower()] = registration.class_name
        return accepted

    @property
    def listed_job_names(self) -> set[str]:
        return set(self._listed_jobs)
