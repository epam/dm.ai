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

const ticketKey = 'DMC-905';
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

function verifyDeprecatedAlias(expectation, lines, failures) {
  const token = `\`${expectation.alias}\``;
  const lineIndexes = findLineIndexesContaining(lines, token);

  if (lineIndexes.length === 0) {
    failures.push(`${expectation.alias}: entry is missing from teammate-configs.md.`);
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
  }
}

function verifyDeprecatedInternalJobAliases() {
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
      'Expected teammate-configs.md to label internal aliases as [deprecated] and point readers to the supported public configuration names.',
      ...failures,
      `Checked file: ${teammateConfigsPath}`,
    ].join('\n\n'),
  );
}

try {
  verifyDeprecatedInternalJobAliases();
  console.log(
    `PASS ${ticketKey}: deprecated internal job aliases are documented with migration pointers.`,
  );
} catch (error) {
  console.error(
    `FAIL ${ticketKey}: deprecated internal job alias documentation audit failed.`,
  );
  throw error;
}
