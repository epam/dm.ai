class ProcessRunner {
  run(_command, _args, _options) {
    throw new Error('run must be implemented by a concrete process runner');
  }
}

module.exports = { ProcessRunner };
