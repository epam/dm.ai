import sys
import unittest
from pathlib import Path


TESTING_ROOT = Path(__file__).resolve().parents[2]
REPOSITORY_ROOT = TESTING_ROOT.parent
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.core.config.repository_paths import (
    DMTOOLS_AI_DOCS_ROOT,
    JOB_RUNNER_PATH,
    TEAMMATE_CONFIGS_DOC_PATH,
)
from testing.core.factories.repository_audit_factory import (
    create_documentation_audit,
    create_job_registry,
)
from testing.core.interfaces.documentation_audit import DocumentationAudit
from testing.core.interfaces.job_registry import JobRegistry


class AgentNamesAndStatusAuditTest(unittest.TestCase):
    AUDITED_NAMES = [
        "Teammate",
        "JSRunner",
        "TestCasesGenerator",
        "InstructionsGenerator",
        "DevProductivityReport",
        "BAProductivityReport",
        "QAProductivityReport",
        "ReportGeneratorJob",
        "ReportVisualizerJob",
        "KBProcessingJob",
    ]

    registry: JobRegistry
    docs: DocumentationAudit

    @classmethod
    def setUpClass(cls) -> None:
        cls.registry = create_job_registry(JOB_RUNNER_PATH)
        cls.docs = create_documentation_audit(TEAMMATE_CONFIGS_DOC_PATH, DMTOOLS_AI_DOCS_ROOT)

    def test_audited_jobs_are_registered_and_listed(self) -> None:
        missing_from_factory = sorted(
            name for name in self.AUDITED_NAMES if name not in self.registry.canonical_job_names
        )
        missing_from_listing = sorted(
            name for name in self.AUDITED_NAMES if name not in self.registry.listed_job_names
        )
        self.assertEqual([], missing_from_factory, f"Missing from createJobInstance: {missing_from_factory}")
        self.assertEqual([], missing_from_listing, f"Missing from getJobs listing: {missing_from_listing}")

    def test_teammate_configs_rows_reference_registered_names(self) -> None:
        missing_names: list[str] = []
        invalid_examples: list[str] = []
        accepted_inputs = self.registry.accepted_input_names

        for canonical_name in self.AUDITED_NAMES:
            row = self.docs.get_row_for_canonical_name(canonical_name)
            if canonical_name not in row.accepted_names:
                missing_names.append(f"{canonical_name} not present in accepted names {row.accepted_names}")

            example_name = self.docs.read_job_name_from_example(row.example_path)
            resolved_name = accepted_inputs.get(example_name.lower())
            if example_name not in row.accepted_names or resolved_name != canonical_name:
                invalid_examples.append(
                    f"{row.example_path.relative_to(REPOSITORY_ROOT)} uses {example_name!r}, resolves to {resolved_name!r}"
                )

        self.assertEqual([], missing_names, f"Doc rows do not include canonical names: {missing_names}")
        self.assertEqual([], invalid_examples, f"Example configs do not resolve to audited jobs: {invalid_examples}")

    def test_active_audited_jobs_are_not_marked_deprecated(self) -> None:
        deprecated_mentions = self.docs.find_deprecated_mentions(self.AUDITED_NAMES)
        self.assertEqual([], deprecated_mentions, f"Audited active jobs marked deprecated: {deprecated_mentions}")

    def test_documented_name_fields_use_exact_registered_identifiers(self) -> None:
        audited_inputs = {
            alias: canonical_name
            for alias, canonical_name in self.registry.accepted_input_names.items()
            if canonical_name in self.AUDITED_NAMES
        }
        exact_name_spellings = {
            accepted_name
            for canonical_name in self.AUDITED_NAMES
            for accepted_name in self.docs.get_row_for_canonical_name(canonical_name).accepted_names
        }
        inexact_mentions = self.docs.find_inexact_name_field_mentions(
            audited_inputs,
            exact_name_spellings,
        )
        self.assertEqual([], inexact_mentions, f"Docs use non-exact job identifiers in name fields: {inexact_mentions}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
