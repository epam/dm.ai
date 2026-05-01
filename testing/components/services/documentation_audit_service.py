import json
import re
from pathlib import Path

from testing.core.interfaces.documentation_audit import DocumentationAudit, DocumentationRow


class DocumentationAuditService(DocumentationAudit):
    _JSON_NAME_PATTERN = re.compile(r'"name"\s*:\s*"([^"]+)"')
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

    def read_job_name_from_example(self, example_path: Path) -> str:
        return json.loads(example_path.read_text(encoding="utf-8"))["name"]

    def find_inexact_name_field_mentions(
        self,
        accepted_names: dict[str, str],
        exact_name_spellings: set[str],
    ) -> list[str]:
        hits: list[str] = []
        for doc_path in self._docs_root.rglob("*.md"):
            text = doc_path.read_text(encoding="utf-8")
            for match in self._JSON_NAME_PATTERN.finditer(text):
                documented_name = match.group(1)
                if documented_name in exact_name_spellings:
                    continue
                canonical_name = accepted_names.get(documented_name.lower())
                if canonical_name is None:
                    continue
                allowed_names = ", ".join(
                    sorted(name for name in exact_name_spellings if accepted_names.get(name.lower()) == canonical_name)
                )
                line = text.count("\n", 0, match.start()) + 1
                hits.append(
                    f"{doc_path.relative_to(self._docs_root)}:{line}: "
                    f"{documented_name!r} should use exact identifier(s): {allowed_names}"
                )
        return hits

    def find_deprecated_mentions(self, identifiers: list[str]) -> list[str]:
        patterns = [
            lambda name: re.compile(rf"~~\s*`?{re.escape(name)}`?\s*~~"),
            lambda name: re.compile(rf"\bdeprecated\b[^\n]*`?{re.escape(name)}`?", re.IGNORECASE),
            lambda name: re.compile(rf"`?{re.escape(name)}`?[^\n]*\bdeprecated\b", re.IGNORECASE),
            lambda name: re.compile(rf"`?{re.escape(name)}`?\s*\(deprecated\)", re.IGNORECASE),
        ]
        hits: list[str] = []
        for doc_path in self._docs_root.rglob("*.md"):
            text = doc_path.read_text(encoding="utf-8")
            for name in identifiers:
                for factory in patterns:
                    match = factory(name).search(text)
                    if match:
                        hits.append(f"{doc_path.relative_to(self._docs_root)}: {match.group(0)}")
                        break
        return hits
