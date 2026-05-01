const { RepositoryConfig } = require('../../core/config/repositoryConfig');
const { NodeFileSystemRepository } = require('../../frameworks/api/rest/nodeFileSystemRepository');
const {
  TeammateConfigExampleUsageAuditService,
} = require('./teammateConfigExampleUsageAuditService');

function createTeammateConfigExampleUsageAuditService({
  repoRoot,
  config = new RepositoryConfig(repoRoot),
  repository = new NodeFileSystemRepository(),
}) {
  return new TeammateConfigExampleUsageAuditService({ config, repository });
}

module.exports = { createTeammateConfigExampleUsageAuditService };
