const path = require('node:path');

class RepositoryConfig {
  constructor(repoRoot) {
    this.repoRoot = repoRoot;
    this.teammateConfigsPath = path.join(
      repoRoot,
      'dmtools-ai-docs',
      'references',
      'agents',
      'teammate-configs.md',
    );
    this.allowedExampleRoots = [
      path.join(repoRoot, 'agents'),
      path.join(repoRoot, 'dmtools-ai-docs'),
    ];
  }
}

module.exports = { RepositoryConfig };
