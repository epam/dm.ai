const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const JOB_RUNNER_MAIN_CLASS = 'com.github.istin.dmtools.job.JobRunner';
const JOB_RUNNER_JAVA_ARGS = [
  '-Dlog4j2.configurationFile=classpath:log4j2-cli.xml',
  '-Dlog4j.configuration=log4j2-cli.xml',
  '-Dlog4j2.disable.jmx=true',
  '-Djava.net.preferIPv4Stack=true',
  '--add-opens',
  'java.base/java.lang=ALL-UNNAMED',
  '-XX:-PrintWarnings',
  '-Dpolyglot.engine.WarnInterpreterOnly=false',
];

class DmtoolsCliService {
  constructor({ repositoryRoot, processRunner, options = {} }) {
    this.repositoryRoot = repositoryRoot;
    this.processRunner = processRunner;
    this.dmtoolsScriptPath =
      options.dmtoolsScriptPath || path.join(repositoryRoot, 'dmtools.sh');
    this.timeoutMs = options.timeoutMs || 120000;
    this.javaCommand = options.javaCommand || 'java';
    this.homeDirectory =
      options.homeDirectory || fs.mkdtempSync(path.join(os.tmpdir(), 'dmtools-cli-home-'));
    this.jarFile = options.jarFile || this.resolveJarFile();
  }

  runDmtools(args, options = {}) {
    return this.processRunner.run(this.dmtoolsScriptPath, args, {
      cwd: options.cwd || this.repositoryRoot,
      encoding: 'utf8',
      timeoutMs: options.timeoutMs || this.timeoutMs,
      env: {
        ...process.env,
        HOME: this.homeDirectory,
        ...options.env,
      },
    });
  }

  runJob(jobName, params = {}, options = {}) {
    const payload = Buffer.from(
      JSON.stringify({
        name: jobName,
        params,
      }),
    ).toString('base64');

    return this.processRunner.run(
      this.javaCommand,
      [...JOB_RUNNER_JAVA_ARGS, '-cp', this.jarFile, JOB_RUNNER_MAIN_CLASS, payload],
      {
        cwd: options.cwd || this.repositoryRoot,
        encoding: 'utf8',
        timeoutMs: options.timeoutMs || this.timeoutMs,
        env: {
          ...process.env,
          HOME: this.homeDirectory,
          ...options.env,
        },
      },
    );
  }

  combinedOutput(result) {
    return [result.stdout, result.stderr].filter(Boolean).join('\n').trim();
  }

  resolveJarFile() {
    const candidateDirectories = [
      path.join(this.repositoryRoot, 'build', 'libs'),
      path.join(this.repositoryRoot, 'dmtools-core', 'build', 'libs'),
    ];

    for (const directory of candidateDirectories) {
      const jarFile = findFirstFatJar(directory);
      if (jarFile) {
        return jarFile;
      }
    }

    throw new Error(
      [
        `Could not find a checkout-built DMTools JAR for ${this.repositoryRoot}.`,
        `Searched: ${candidateDirectories.join(', ')}`,
        'Build the current checkout first with ./gradlew :dmtools-core:shadowJar.',
        'Global ~/.dmtools/dmtools.jar fallback is intentionally disabled for this test.',
      ].join(' '),
    );
  }
}

function findFirstFatJar(directory) {
  if (!directory || !fs.existsSync(directory) || !fs.statSync(directory).isDirectory()) {
    return null;
  }

  const jarFiles = fs
    .readdirSync(directory)
    .filter((fileName) => fileName.endsWith('-all.jar'))
    .sort();

  return jarFiles.length > 0 ? path.join(directory, jarFiles[0]) : null;
}

module.exports = { DmtoolsCliService };
