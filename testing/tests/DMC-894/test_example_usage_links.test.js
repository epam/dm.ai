const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const { RepositoryConfig } = require('../../core/config/repositoryConfig');
const { NodeFileSystemRepository } = require('../../frameworks/api/rest/nodeFileSystemRepository');
const {
  TeammateConfigExampleUsageAuditService,
} = require('../../components/services/teammateConfigExampleUsageAuditService');

test('DMC-894 validates teammate example usage links', () => {
  const repoRoot = path.resolve(__dirname, '../../..');
  const config = new RepositoryConfig(repoRoot);
  const repository = new NodeFileSystemRepository();
  const auditService = new TeammateConfigExampleUsageAuditService({ config, repository });

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
