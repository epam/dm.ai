from __future__ import annotations

import re
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from testing.core.interfaces.skill_docs_sync import Sandbox
from testing.core.utils.repo_sandbox import CommandResult, RepoSandbox


@dataclass(frozen=True)
class McpDocsGenerationResult:
    compile_java: CommandResult
    generate_docs: CommandResult
    generated_files: tuple[str, ...]
    expected_tools_doc_filename: str
    expected_tools_heading: str
    registry_updated: bool
    special_character_tools_generated: bool
    special_character_index_linked: bool
    special_character_heading_correct: bool


class McpDocsGenerationService:
    REGISTRY_PATH = (
        "dmtools-core/build/generated/sources/annotationProcessor/java/main/"
        "com/github/istin/dmtools/mcp/generated/MCPToolRegistry.java"
    )
    GENERATED_INDEX_PATH = "dmtools-ai-docs/references/mcp-tools/README.md"
    SPECIAL_CHARACTER_AGENT_NAME = "Mermaid/Docs: Audit Agent"

    def __init__(
        self,
        repository_root: Path,
        sandbox_factory: Callable[[Path], Sandbox] = RepoSandbox,
    ) -> None:
        self.repository_root = repository_root
        self._sandbox_factory = sandbox_factory

    def run(self) -> McpDocsGenerationResult:
        sandbox = self._sandbox_factory(self.repository_root)
        try:
            expected_tools_doc_filename = self._tools_doc_filename(self.SPECIAL_CHARACTER_AGENT_NAME)
            expected_tools_heading = f"# {self.SPECIAL_CHARACTER_AGENT_NAME} MCP Tools"
            compile_java = sandbox.run("./gradlew :dmtools-core:compileJava --quiet")
            registry_updated = False
            if compile_java.returncode == 0:
                registry_updated = self._inject_special_character_integration_name(sandbox)
            generate_docs = sandbox.run("./scripts/generate-mcp-docs.sh")
            generated_files = self._generated_files(sandbox)
            generated_doc_markdown = self._read_if_present(
                sandbox,
                f"dmtools-ai-docs/references/mcp-tools/{expected_tools_doc_filename}",
            )
            generated_index = self._read_if_present(sandbox, self.GENERATED_INDEX_PATH)

            return McpDocsGenerationResult(
                compile_java=compile_java,
                generate_docs=generate_docs,
                generated_files=generated_files,
                expected_tools_doc_filename=expected_tools_doc_filename,
                expected_tools_heading=expected_tools_heading,
                registry_updated=registry_updated,
                special_character_tools_generated=expected_tools_doc_filename in generated_files,
                special_character_index_linked=(
                    f"[{self.SPECIAL_CHARACTER_AGENT_NAME}]({expected_tools_doc_filename})"
                    in generated_index
                ),
                special_character_heading_correct=generated_doc_markdown.startswith(
                    expected_tools_heading
                ),
            )
        finally:
            sandbox.cleanup()

    def _inject_special_character_integration_name(self, sandbox: Sandbox) -> bool:
        registry_text = self._read_if_present(sandbox, self.REGISTRY_PATH)
        if not registry_text:
            return False

        updated_registry_text, replacements = re.subn(
            (
                r'(tools\.put\("[^"]+",\s*new\s+MCPToolDefinition\('
                r'"[^"]+",\s*"[^"]+",\s*)"mermaid"'
            ),
            lambda match: f'{match.group(1)}"{self.SPECIAL_CHARACTER_AGENT_NAME}"',
            registry_text,
        )

        if replacements == 0:
            return False

        sandbox.write_text(self.REGISTRY_PATH, updated_registry_text)
        return True

    def _generated_files(self, sandbox: Sandbox) -> tuple[str, ...]:
        files_result = sandbox.run(
            "find dmtools-ai-docs/references/mcp-tools -maxdepth 1 -type f -printf '%f\n' | sort"
        )
        return tuple(
            line.strip()
            for line in files_result.stdout.splitlines()
            if line.strip()
        )

    @staticmethod
    def _read_if_present(sandbox: Sandbox, relative_path: str) -> str:
        try:
            return sandbox.read_text(relative_path)
        except FileNotFoundError:
            return ""

    @staticmethod
    def _tools_doc_filename(integration_name: str) -> str:
        sanitized_name = re.sub(r"[^a-z0-9._-]+", "-", integration_name.lower()).strip("-")
        return f"{sanitized_name or 'misc'}-tools.md"
