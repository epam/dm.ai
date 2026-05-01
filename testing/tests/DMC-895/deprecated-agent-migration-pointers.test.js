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
    type: 'deprecated-alias',
    alias: 'ReportGeneratorJob',
    target: 'ReportGenerator',
    relatedPaths: ['report-generation.md', 'report-generator-job.json'],
  },
  {
    type: 'deprecated-alias',
    alias: 'ReportVisualizerJob',
    target: 'ReportVisualizer',
    relatedPaths: ['report-visualizer-job.json'],
  },
  {
    type: 'canonical-name',
    alias: 'KBProcessingJob',
    legacyAlias: 'KBProcessing',
    relatedPaths: ['kb-processing-job.json'],
  },
];

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
  }
}

function verifyCanonicalName(expectation, lines, failures) {
  const token = `\`${expectation.alias}\``;
  const lineIndexes = findLineIndexesContaining(lines, token);

  if (lineIndexes.length === 0) {
    failures.push(
      `${expectation.alias}: entry is missing from teammate-configs.md.`,
    );
    return;
  }

  const candidateWindows = buildWindows(lines, lineIndexes, 2);
  const canonicalWindows = candidateWindows.filter((window) => {
    const mentionsLegacyAlias = window.text.includes(`\`${expectation.legacyAlias}\``);
    const mentionsCanonicalContract =
      /canonical|preferred/i.test(window.text) ||
      window.line.includes(`\`${expectation.alias}\` / \`${expectation.legacyAlias}\``);

    return mentionsLegacyAlias && mentionsCanonicalContract;
  });

  if (canonicalWindows.length === 0) {
    failures.push(
      `${expectation.alias}: found ${lineIndexes.length} reference(s), but none document it as the canonical name with ${expectation.legacyAlias} as the legacy alias.\n${formatWindows(candidateWindows)}`,
    );
  }
}

function verifyDeprecatedMigrationPointers() {
  const content = readText(teammateConfigsPath);
  const lines = splitLines(content);
  const failures = [];

  for (const expectation of expectations) {
    if (expectation.type === 'deprecated-alias') {
      verifyDeprecatedAlias(expectation, lines, failures);
      continue;
    }

    verifyCanonicalName(expectation, lines, failures);
  }

  assert.deepStrictEqual(
    failures,
    [],
    [
      `${ticketKey} failed.`,
      `Expected teammate-configs.md to document deprecated aliases with migration pointers and keep canonical naming guidance aligned with the jobs reference.`,
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
