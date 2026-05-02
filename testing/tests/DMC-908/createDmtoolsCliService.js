const path = require('node:path');

const { DmtoolsCliService } = require('../../components/services/dmtools_cli_service');
const { NodeProcessRunner } = require('../../frameworks/api/cli/nodeProcessRunner');

function createDmtoolsCliService() {
  const repositoryRoot = path.resolve(__dirname, '../../..');
  const processRunner = new NodeProcessRunner();

  return new DmtoolsCliService({
    repositoryRoot,
    processRunner,
  });
}

module.exports = { createDmtoolsCliService };
