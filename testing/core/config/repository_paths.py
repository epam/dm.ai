from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
DMTOOLS_AI_DOCS_ROOT = REPOSITORY_ROOT / "dmtools-ai-docs"
JOB_RUNNER_PATH = (
    REPOSITORY_ROOT
    / "dmtools-core"
    / "src"
    / "main"
    / "java"
    / "com"
    / "github"
    / "istin"
    / "dmtools"
    / "job"
    / "JobRunner.java"
)
TEAMMATE_CONFIGS_DOC_PATH = (
    DMTOOLS_AI_DOCS_ROOT / "references" / "agents" / "teammate-configs.md"
)

