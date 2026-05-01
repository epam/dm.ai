from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class JobRegistration:
    aliases: tuple[str, ...]
    class_name: str


class JobRegistry(ABC):
    @property
    @abstractmethod
    def canonical_job_names(self) -> set[str]:
        raise NotImplementedError

    @property
    @abstractmethod
    def accepted_input_names(self) -> dict[str, str]:
        raise NotImplementedError

    @property
    @abstractmethod
    def listed_job_names(self) -> set[str]:
        raise NotImplementedError
