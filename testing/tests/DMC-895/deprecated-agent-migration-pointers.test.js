const assert = require('node:assert/strict');
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

function verifyDeprecatedMigrationPointers() {
  const content = readText(teammateConfigsPath);
  const lines = splitLines(content);
  const failures = [];

  for (const expectation of expectations) {
    const token = `\`${expectation.alias}\``;
    const lineIndexes = findLineIndexesContaining(lines, token);

    if (lineIndexes.length === 0) {
      failures.push(
        `${expectation.alias}: entry is missing from teammate-configs.md.`,
      );
      continue;
    }

    const candidateWindows = buildWindows(lines, lineIndexes, 2);
    const deprecatedWindows = candidateWindows.filter((window) =>
      /\[deprecated\]/i.test(window.text),
    );

    if (deprecatedWindows.length === 0) {
      failures.push(
        `${expectation.alias}: found ${lineIndexes.length} reference(s), but none mark the alias as [deprecated].\n${formatWindows(candidateWindows)}`,
      );
      continue;
    }

    const migrationWindows = deprecatedWindows.filter((window) =>
      hasMigrationPointer(window.text, expectation.target, expectation.relatedPaths),
    );

    if (migrationWindows.length === 0) {
      failures.push(
        `${expectation.alias}: deprecated marker exists, but no explicit migration pointer to ${expectation.target} was found near the alias.\n${formatWindows(deprecatedWindows)}`,
      );
    }
  }

  assert.deepStrictEqual(
    failures,
    [],
    [
      `${ticketKey} failed.`,
      `Expected teammate-configs.md to mark deprecated internal aliases and point readers to the public configuration names.`,
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
