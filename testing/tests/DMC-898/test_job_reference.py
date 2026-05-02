from testing.core.models.job_reference import JobReference


def test_from_markdown_cells_extracts_deprecated_alias_names() -> None:
    reference = JobReference.from_markdown_cells(
        "`KBProcessing`",
        "Runs the knowledge-base pipeline.",
        "`KBProcessing`; `KBProcessingJob` **[deprecated]** -> `KBProcessing`",
        "example.json",
    )

    assert reference.job == "KBProcessing"
    assert reference.accepted_names == ("KBProcessing", "KBProcessingJob")
    assert reference.all_names == ("KBProcessing", "KBProcessingJob")
