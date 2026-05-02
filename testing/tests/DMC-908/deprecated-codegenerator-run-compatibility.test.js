const assert = require('node:assert/strict');
const path = require('node:path');

const { DmtoolsCliService } = require('../../components/services/dmtools_cli_service');

const ticketKey = 'DMC-908';
const repositoryRoot = path.resolve(__dirname, '../../..');

function formatResultDetails(result) {
  const sections = [
    `Command: ${result.command}`,
    `Exit code: ${result.exitCode}`,
    `Signal: ${result.signal ?? 'none'}`,
    `STDOUT:\n${result.stdout || '<empty>'}`,
    `STDERR:\n${result.stderr || '<empty>'}`,
  ];

  if (result.error) {
    sections.push(`Spawn error:\n${result.error.stack || String(result.error)}`);
  }

  return sections.join('\n\n');
}

function verifyDeprecatedCodeGeneratorRunCompatibility() {
  const service = new DmtoolsCliService(repositoryRoot);
  const result = service.runDmtools(['run', 'codegenerator', '--param1', 'test']);
  const visibleOutput = service.combinedOutput(result);

  assert.equal(
    result.exitCode,
    0,
    [
      `${ticketKey} failed.`,
      "Expected `./dmtools.sh run codegenerator --param1=test` to succeed.",
      formatResultDetails(result),
    ].join('\n\n'),
  );

  assert.match(
    visibleOutput,
    /deprecated/i,
    [
      `${ticketKey} failed.`,
      'Expected the visible console output or logs to include a deprecation warning.',
      formatResultDetails(result),
    ].join('\n\n'),
  );

  assert.match(
    visibleOutput,
    /v?1\.8\.0/,
    [
      `${ticketKey} failed.`,
      'Expected the deprecation warning to mention the target removal version 1.8.0.',
      formatResultDetails(result),
    ].join('\n\n'),
  );

  assert.match(
    visibleOutput,
    /compatibility shim executed successfully/i,
    [
      `${ticketKey} failed.`,
      'Expected the compatibility shim success response to be visible to the user.',
      formatResultDetails(result),
    ].join('\n\n'),
  );

  assert.doesNotMatch(
    visibleOutput,
    /configuration file not found|run command requires at least one argument/i,
    [
      `${ticketKey} failed.`,
      'The wrapper treated the runtime job name like an invalid run-file input instead of executing the shim.',
      formatResultDetails(result),
    ].join('\n\n'),
  );
}

try {
  verifyDeprecatedCodeGeneratorRunCompatibility();
  console.log(
    `PASS ${ticketKey}: deprecated codegenerator runtime name resolves to the compatibility shim with a deprecation warning.`,
  );
} catch (error) {
  console.error(
    `FAIL ${ticketKey}: deprecated codegenerator runtime invocation did not behave like a compatibility shim.`,
  );
  throw error;
}
