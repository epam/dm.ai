# DMC-1031

Validates that `ReportGenerator` keeps retrying GitHub-backed report collection when a rate-limited response does not provide usable reset metadata, and that the fallback delay remains user-visible in the implementation.
