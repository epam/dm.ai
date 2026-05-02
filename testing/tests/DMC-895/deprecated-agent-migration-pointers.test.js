const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const {
  buildWindows,
  findLineIndexesContaining,
  formatWindows,
  hasMigrationPointer,
  readText,
  splitLines,
} = require('../../core/utils/documentAudit');

const ticketKey = 'DMC-895';
const teammateConfigsPath = path.resolve(
  __dirname,
  '../../../dmtools-ai-docs/references/agents/teammate-configs.md',
);

const expectations = [
  {
    alias: 'ReportGeneratorJob',
    target: 'ReportGenerator',
    relatedPaths: ['report-generation.md', 'report-generator-job.json'],
  },
  {
    alias: 'ReportVisualizerJob',
    target: 'ReportVisualizer',
    relatedPaths: ['report-visualizer-job.json'],
  },
  {
    alias: 'KBProcessingJob',
    target: 'KBProcessing',
    relatedPaths: ['kb-processing-job.json'],
  },
];

function extractMarkdownTargets(text) {
  return Array.from(text.matchAll(/\[[^\]]+\]\(([^)]+)\)/g), (match) => match[1]);
}

function resolveExistingRelatedTargets(basePath, text, relatedPaths) {
  return extractMarkdownTargets(text)
    .filter((target) =>
      relatedPaths.some((relatedPath) => target.includes(relatedPath)),
    )
    .map((target) => {
      const [relativeTarget] = target.split('#', 1);

      return {
        target,
        resolvedPath: path.resolve(path.dirname(basePath), relativeTarget),
      };
    })
    .filter(({ resolvedPath }) => fs.existsSync(resolvedPath));
}

function verifyDeprecatedAlias(expectation, lines, failures) {
  const token = `\`${expectation.alias}\``;
  const lineIndexes = findLineIndexesContaining(lines, token);

  if (lineIndexes.length === 0) {
    failures.push(
      `${expectation.alias}: entry is missing from teammate-configs.md.`,
    );
    return;
  }

  const candidateWindows = buildWindows(lines, lineIndexes, 2);
  const deprecatedWindows = candidateWindows.filter((window) =>
    /\[deprecated\]/i.test(window.text),
  );

  if (deprecatedWindows.length === 0) {
    failures.push(
      `${expectation.alias}: found ${lineIndexes.length} reference(s), but none mark the alias as [deprecated].\n${formatWindows(candidateWindows)}`,
    );
    return;
  }

  const migrationWindows = deprecatedWindows.filter((window) =>
    hasMigrationPointer(window.text, expectation.target, expectation.relatedPaths),
  );

  if (migrationWindows.length === 0) {
    failures.push(
      `${expectation.alias}: deprecated marker exists, but no explicit migration pointer to ${expectation.target} was found near the alias.\n${formatWindows(deprecatedWindows)}`,
    );
    return;
  }

  const linkedTargets = migrationWindows.flatMap((window) =>
    resolveExistingRelatedTargets(
      teammateConfigsPath,
      window.text,
      expectation.relatedPaths,
    ),
  );

  if (linkedTargets.length === 0) {
    failures.push(
      `${expectation.alias}: deprecated migration text exists, but none of the expected guide/example links resolve on disk.\n${formatWindows(migrationWindows)}`,
    );
  }
}

function verifyDeprecatedMigrationPointers() {
  const content = readText(teammateConfigsPath);
  const lines = splitLines(content);
  const failures = [];

  for (const expectation of expectations) {
    verifyDeprecatedAlias(expectation, lines, failures);
  }

  assert.deepStrictEqual(
    failures,
    [],
    [
      `${ticketKey} failed.`,
      `Expected teammate-configs.md to mark ReportGeneratorJob, ReportVisualizerJob, and KBProcessingJob as deprecated aliases with explicit migration pointers to their public configuration names.`,
      ...failures,
      `Checked file: ${teammateConfigsPath}`,
    ].join('\n\n'),
  );
}

try {
  verifyDeprecatedMigrationPointers();
  console.log(`PASS ${ticketKey}: deprecated agent migration pointers are documented correctly.`);
} catch (error) {
  console.error(`FAIL ${ticketKey}: deprecated agent migration pointers audit failed.`);
  throw error;
}
