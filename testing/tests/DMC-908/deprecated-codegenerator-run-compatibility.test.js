const assert = require('node:assert/strict');
const { createDmtoolsCliService } = require('./createDmtoolsCliService');

const ticketKey = 'DMC-908';
const compatibilityMessage =
  'CodeGenerator compatibility shim executed successfully. No action was taken and no code artifacts were produced.';

function formatResultDetails(label, result) {
  const sections = [
    label,
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

function verifyUserFacingRuntimeCommand(service, failures) {
  const result = service.runDmtools(['run', 'codegenerator', '--param1', 'test']);
  const visibleOutput = service.combinedOutput(result);

  expectEqual(
    failures,
    result.exitCode,
    0,
    "Expected `./dmtools.sh run codegenerator --param1=test` to succeed.",
    formatResultDetails('User-facing wrapper result', result),
  );
  expectMatch(
    failures,
    visibleOutput,
    /deprecated/i,
    'Expected the visible console output or logs to include a deprecation warning.',
    formatResultDetails('User-facing wrapper result', result),
  );
  expectMatch(
    failures,
    visibleOutput,
    /v?1\.8\.0/,
    'Expected the deprecation warning to mention the target removal version 1.8.0.',
    formatResultDetails('User-facing wrapper result', result),
  );
  expectMatch(
    failures,
    visibleOutput,
    /compatibility shim executed successfully/i,
    'Expected the compatibility shim success response to be visible to the user.',
    formatResultDetails('User-facing wrapper result', result),
  );
  expectMatch(
    failures,
    visibleOutput,
    /No action was taken and no code artifacts were produced\./i,
    'Expected a visible no-op confirmation showing that no real code generation ran.',
    formatResultDetails('User-facing wrapper result', result),
  );
  expectDoesNotMatch(
    failures,
    visibleOutput,
    /configuration file not found|run command requires at least one argument/i,
    'The wrapper treated the runtime job name like an invalid run-file input instead of executing the shim.',
    formatResultDetails('User-facing wrapper result', result),
  );
}

function verifyDirectCompatibilityShim(service, failures) {
  const result = service.runJob('codegenerator', { param1: 'test' });
  const visibleOutput = service.combinedOutput(result);

  expectEqual(
    failures,
    result.exitCode,
    0,
    'Expected the direct JobRunner invocation to execute the compatibility shim successfully.',
    formatResultDetails('Direct JobRunner result', result),
  );
  expectMatch(
    failures,
    visibleOutput,
    /No action was taken and no code artifacts were produced\./i,
    'Expected the direct JobRunner invocation to prove the shim is a no-op.',
    formatResultDetails('Direct JobRunner result', result),
  );

  let parsedResult;
  try {
    parsedResult = JSON.parse(result.stdout);
  } catch (error) {
    failures.push(
      [
        'Expected the direct JobRunner invocation to return JSON output.',
        formatResultDetails('Direct JobRunner result', result),
        `JSON parse error: ${error.message}`,
      ].join('\n\n'),
    );
    return;
  }

  expectEqual(
    failures,
    Array.isArray(parsedResult),
    true,
    'Expected the direct JobRunner invocation to return a JSON array.',
    formatResultDetails('Direct JobRunner result', result),
  );

  if (!Array.isArray(parsedResult) || parsedResult.length === 0) {
    failures.push(
      [
        'Expected the direct JobRunner invocation to return at least one result item.',
        formatResultDetails('Direct JobRunner result', result),
      ].join('\n\n'),
    );
    return;
  }

  const [firstItem] = parsedResult;
  expectEqual(
    failures,
    firstItem.key,
    'CodeGenerator',
    'Expected the compatibility shim to report the CodeGenerator result key.',
    formatResultDetails('Direct JobRunner result', result),
  );
  expectEqual(
    failures,
    firstItem.result,
    compatibilityMessage,
    'Expected the compatibility shim response to confirm the no-op behavior exactly.',
    formatResultDetails('Direct JobRunner result', result),
  );
}

function expectEqual(failures, actual, expected, message, details) {
  try {
    assert.equal(actual, expected, message);
  } catch (error) {
    failures.push([error.message, details].join('\n\n'));
  }
}

function expectMatch(failures, value, pattern, message, details) {
  try {
    assert.match(value, pattern, message);
  } catch (error) {
    failures.push([error.message, details].join('\n\n'));
  }
}

function expectDoesNotMatch(failures, value, pattern, message, details) {
  try {
    assert.doesNotMatch(value, pattern, message);
  } catch (error) {
    failures.push([error.message, details].join('\n\n'));
  }
}

function verifyDeprecatedCodeGeneratorRunCompatibility() {
  const service = createDmtoolsCliService();
  const failures = [];

  verifyUserFacingRuntimeCommand(service, failures);
  verifyDirectCompatibilityShim(service, failures);

  assert.deepStrictEqual(
    failures,
    [],
    [
      `${ticketKey} failed.`,
      "Expected `./dmtools.sh run codegenerator --param1=test` to resolve the deprecated runtime name to the compatibility shim, and expected the underlying `codegenerator` job to prove the exact no-op behavior.",
      ...failures,
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
