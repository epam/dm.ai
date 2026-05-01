import json
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class DocumentationRow:
    job: str
    accepted_names: tuple[str, ...]
    example_path: Path


class DocumentationAuditService:
    _TARGET_ROWS = {
        "Teammate": "Teammate",
        "JSRunner": "JSRunner",
        "TestCasesGenerator": "TestCasesGenerator",
        "InstructionsGenerator": "InstructionsGenerator",
        "DevProductivityReport": "DevProductivityReport",
        "BAProductivityReport": "BAProductivityReport",
        "QAProductivityReport": "QAProductivityReport",
        "ReportGeneratorJob": "ReportGenerator",
        "ReportVisualizerJob": "ReportVisualizer",
        "KBProcessingJob": "KBProcessingJob",
    }

    def __init__(self, teammate_configs_path: Path, docs_root: Path) -> None:
        self._teammate_configs_path = teammate_configs_path
        self._docs_root = docs_root
        self._doc_text = teammate_configs_path.read_text(encoding="utf-8")
        self._rows = self._parse_common_job_reference()

    @staticmethod
    def _clean_cell(value: str) -> str:
        return value.strip().strip("`")

    def _parse_common_job_reference(self) -> dict[str, DocumentationRow]:
        rows: dict[str, DocumentationRow] = {}
        for line in self._doc_text.splitlines():
            if not line.startswith("| `"):
                continue
            cells = [cell.strip() for cell in line.strip().split("|")[1:-1]]
            if len(cells) != 4:
                continue
            job = self._clean_cell(cells[0])
            if job not in self._TARGET_ROWS.values():
                continue
            accepted_names = tuple(
                self._clean_cell(name)
                for name in cells[2].split("/")
            )
            link_match = re.search(r"\((.*?)\)", cells[3])
            if not link_match:
                continue
            example_path = (self._teammate_configs_path.parent / link_match.group(1)).resolve()
            rows[job] = DocumentationRow(
                job=job,
                accepted_names=accepted_names,
                example_path=example_path,
            )
        return rows

    def get_row_for_canonical_name(self, canonical_name: str) -> DocumentationRow:
        row_key = self._TARGET_ROWS[canonical_name]
        return self._rows[row_key]

    @staticmethod
    def read_job_name_from_example(example_path: Path) -> str:
        return json.loads(example_path.read_text(encoding="utf-8"))["name"]

    def find_deprecated_mentions(self, canonical_names: list[str]) -> list[str]:
        patterns = [
            lambda name: re.compile(rf"~~\s*`?{re.escape(name)}`?\s*~~"),
            lambda name: re.compile(rf"\bdeprecated\b[^\n]*`?{re.escape(name)}`?", re.IGNORECASE),
            lambda name: re.compile(rf"`?{re.escape(name)}`?[^\n]*\bdeprecated\b", re.IGNORECASE),
            lambda name: re.compile(rf"`?{re.escape(name)}`?\s*\(deprecated\)", re.IGNORECASE),
        ]
        hits: list[str] = []
        for doc_path in self._docs_root.rglob("*.md"):
            text = doc_path.read_text(encoding="utf-8")
            for name in canonical_names:
                for factory in patterns:
                    match = factory(name).search(text)
                    if match:
                        hits.append(f"{doc_path.relative_to(self._docs_root)}: {match.group(0)}")
                        break
        return hits

