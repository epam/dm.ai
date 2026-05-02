const { spawnSync } = require('node:child_process');

const { ProcessRunner } = require('../../../core/interfaces/processRunner');

class NodeProcessRunner extends ProcessRunner {
  run(command, args = [], options = {}) {
    const result = spawnSync(command, args, {
      cwd: options.cwd,
      encoding: options.encoding || 'utf8',
      timeout: options.timeoutMs,
      env: options.env,
    });

    return {
      args,
      command: [command, ...args].join(' '),
      exitCode: result.status,
      signal: result.signal,
      stdout: result.stdout || '',
      stderr: result.stderr || '',
      error: result.error || null,
    };
  }
}

module.exports = { NodeProcessRunner };
