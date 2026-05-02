const test = require('node:test');
const assert = require('node:assert/strict');

const { createExampleUsageAuditService } = require('./createExampleUsageAuditService');

test('DMC-894 validates teammate example usage links', () => {
  const auditService = createExampleUsageAuditService();
  const auditResult = auditService.auditCommonJobReference();
  const auditedJobs = auditResult.entries.map((entry) => entry.job);
  const expectedJobs = [
    'Teammate',
    'JSRunner',
    'TestCasesGenerator',
    'InstructionsGenerator',
    'DevProductivityReport',
    'BAProductivityReport',
    'QAProductivityReport',
    'ReportGenerator',
    'ReportVisualizer',
    'KBProcessingJob',
  ];

  assert.ok(
    auditResult.entries.length >= expectedJobs.length,
    `Expected to audit at least ${expectedJobs.length} agent rows, but found ${auditResult.entries.length}.`,
  );

  for (const expectedJob of expectedJobs) {
    assert.ok(
      auditedJobs.includes(expectedJob),
      `Expected the Common job reference table to include "${expectedJob}". Audited jobs: ${auditedJobs.join(', ')}`,
    );
  }

  assert.deepStrictEqual(
    auditResult.issues,
    [],
    `Found invalid example usage links:\n${auditResult.issues.join('\n')}`,
  );
});
