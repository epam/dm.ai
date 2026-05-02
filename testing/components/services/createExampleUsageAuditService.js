const path = require('node:path');

const { RepositoryConfig } = require('../../core/config/repositoryConfig');
const { NodeFileSystemRepository } = require('../../frameworks/api/rest/nodeFileSystemRepository');
const {
  TeammateConfigExampleUsageAuditService,
} = require('./teammateConfigExampleUsageAuditService');

function createExampleUsageAuditService() {
  const repoRoot = path.resolve(__dirname, '../../..');
  const config = new RepositoryConfig(repoRoot);
  const repository = new NodeFileSystemRepository();

  return new TeammateConfigExampleUsageAuditService({ config, repository });
}

module.exports = { createExampleUsageAuditService };
