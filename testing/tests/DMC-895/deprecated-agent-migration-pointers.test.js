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
const {
  createExampleUsageAuditService,
} = require('../../components/services/createExampleUsageAuditService');

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
    expectedExamplePath: 'dmtools-ai-docs/references/examples/report-generator-job.json',
  },
  {
    alias: 'ReportVisualizerJob',
    target: 'ReportVisualizer',
    relatedPaths: ['report-visualizer-job.json'],
    expectedExamplePath: 'dmtools-ai-docs/references/examples/report-visualizer-job.json',
  },
  {
    alias: 'KBProcessingJob',
    target: 'KBProcessing',
    relatedPaths: ['kb-processing-job.json'],
    expectedExamplePath: 'dmtools-ai-docs/references/examples/kb-processing-job.json',
  },
];

function verifyExpectedExampleUsage(
  expectation,
  commonJobReferenceAudit,
  exampleUsageAuditService,
  failures,
) {
  const entry = exampleUsageAuditService.findCommonJobReferenceEntryByAcceptedName(
    commonJobReferenceAudit.entries,
    expectation.alias,
  );

  if (!entry) {
    failures.push(
      `${expectation.alias}: Common job reference is missing a row whose accepted names include ${expectation.alias}.`,
    );
    return;
  }

  const entryIssues = commonJobReferenceAudit.issues.filter((issue) =>
    issue.startsWith(`[${entry.job}]`),
  );

  if (entryIssues.length > 0) {
    failures.push(
      `${expectation.alias}: shared example-usage audit reported link issues for ${entry.job}.\n${entryIssues.join('\n')}`,
    );
    return;
  }

  const resolvedExamplePath = exampleUsageAuditService.resolveExampleUsageRelativePath(entry);

  if (resolvedExamplePath !== expectation.expectedExamplePath) {
    failures.push(
      `${expectation.alias}: expected migration example to resolve to ${expectation.expectedExamplePath}, but found ${resolvedExamplePath ?? 'no resolved example path'}.`,
    );
  }
}

function verifyDeprecatedAlias(expectation, lines, commonJobReferenceAudit, exampleUsageAuditService, failures) {
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

  verifyExpectedExampleUsage(
    expectation,
    commonJobReferenceAudit,
    exampleUsageAuditService,
    failures,
  );
}

function verifyDeprecatedMigrationPointers() {
  const content = readText(teammateConfigsPath);
  const lines = splitLines(content);
  const failures = [];
  const exampleUsageAuditService = createExampleUsageAuditService();
  const commonJobReferenceAudit = exampleUsageAuditService.auditCommonJobReference();

  for (const expectation of expectations) {
    verifyDeprecatedAlias(
      expectation,
      lines,
      commonJobReferenceAudit,
      exampleUsageAuditService,
      failures,
    );
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
