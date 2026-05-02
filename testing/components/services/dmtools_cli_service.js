const path = require('node:path');
const { spawnSync } = require('node:child_process');

class DmtoolsCliService {
  constructor(repositoryRoot, options = {}) {
    this.repositoryRoot = repositoryRoot;
    this.dmtoolsScriptPath = options.dmtoolsScriptPath || path.join(repositoryRoot, 'dmtools.sh');
    this.timeoutMs = options.timeoutMs || 120000;
  }

  runDmtools(args, options = {}) {
    const result = spawnSync(this.dmtoolsScriptPath, args, {
      cwd: options.cwd || this.repositoryRoot,
      encoding: 'utf8',
      timeout: options.timeoutMs || this.timeoutMs,
      env: { ...process.env, ...options.env },
    });

    return {
      args,
      command: [this.dmtoolsScriptPath, ...args].join(' '),
      exitCode: result.status,
      signal: result.signal,
      stdout: result.stdout || '',
      stderr: result.stderr || '',
      error: result.error || null,
    };
  }

  combinedOutput(result) {
    return [result.stdout, result.stderr].filter(Boolean).join('\n').trim();
  }
}

module.exports = { DmtoolsCliService };
