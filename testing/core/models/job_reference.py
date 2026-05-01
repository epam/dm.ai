from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class JobReference:
    job: str
    summary: str
    accepted_names: tuple[str, ...]
    example: str

    @classmethod
    def from_markdown_cells(
        cls,
        job: str,
        summary: str,
        accepted_names: str,
        example: str,
    ) -> "JobReference":
        return cls(
            job=_clean_markdown_cell(job),
            summary=_clean_markdown_cell(summary),
            accepted_names=tuple(
                _clean_markdown_cell(name)
                for name in accepted_names.split("/")
                if _clean_markdown_cell(name)
            ),
            example=_clean_markdown_cell(example),
        )


def _clean_markdown_cell(value: str) -> str:
    return value.strip().strip("`").strip()

