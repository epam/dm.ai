import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const here = dirname(fileURLToPath(import.meta.url));
const script = readFileSync(resolve(here, 'fixtures/gitlab/gitlab-factory-publish-review.sh'), 'utf8');

const mockCurl = `#!${process.execPath}
const fs = require('node:fs');
const args = process.argv.slice(2);
const method = args[args.indexOf('--request') + 1];
const url = args.at(-1);
const data = args.includes('--data') ? JSON.parse(args[args.indexOf('--data') + 1]) : undefined;
fs.appendFileSync(process.env.TEST_ROOT + '/requests.jsonl', JSON.stringify({ method, url, data }) + '\\n');
const respond = (status, body) => { process.stdout.write(JSON.stringify(body) + '\\n' + status); process.exit(0); };
const diffs = JSON.parse(fs.readFileSync(process.env.TEST_ROOT + '/diffs.json', 'utf8'));
if (method === 'GET' && url.endsWith('/merge_requests/48')) respond(200, { diff_refs: { base_sha: 'b', head_sha: 'h', start_sha: 's' } });
if (method === 'GET' && url.includes('/merge_requests/48/diffs?')) {
  const page = Number(new URL(url).searchParams.get('page'));
  respond(200, page === 1 ? diffs : []);
}
if (method === 'POST' && url.endsWith('/notes')) respond(201, { id: 1 });
if (method === 'POST' && url.endsWith('/discussions')) {
  const p = data.position;
  if (p && p.old_line === undefined && p.new_line === undefined) respond(400, { message: 'line_code' });
  respond(201, { id: 'd' });
}
respond(500, { message: 'Unexpected mock request' });
`;

const diff = [
  '@@ -30,4 +30,5 @@ class App {',
  '   build() {',
  '-    old();',
  '+    replaced();',
  '+    added();',
  '     return child;',
  '   }',
].join('\n');

function publish(inlineComments: object[]) {
  const dir = mkdtempSync(join(tmpdir(), 'gitlab-publish-review-'));
  try {
    mkdirSync(join(dir, 'bin'));
    mkdirSync(join(dir, 'outputs/pr_review_comments'), { recursive: true });
    writeFileSync(join(dir, 'bin/curl'), mockCurl, { mode: 0o755 });
    writeFileSync(join(dir, 'publish.sh'), script);
    writeFileSync(join(dir, 'diffs.json'), JSON.stringify([
      { old_path: 'lib/main.dart', new_path: 'lib/main.dart', diff },
      { old_path: 'lib/old_name.dart', new_path: 'lib/new_name.dart', diff: '@@ -1,2 +1,2 @@\n keep();\n-gone();\n+came();\n' },
    ]));
    writeFileSync(join(dir, 'outputs/pr_review_general.md'), 'General');
    writeFileSync(join(dir, 'outputs/pr_review_comments/c.md'), 'Finding');
    writeFileSync(join(dir, 'outputs/pr_review.json'), JSON.stringify({
      generalComment: 'outputs/pr_review_general.md',
      inlineComments: inlineComments.map((comment) => ({ comment: 'outputs/pr_review_comments/c.md', ...comment })),
    }));
    const result = spawnSync('bash', ['publish.sh', '48'], {
      cwd: dir, encoding: 'utf8',
      env: {
        ...process.env, PATH: `${join(dir, 'bin')}:${process.env.PATH}`, TEST_ROOT: dir,
        GITLAB_TOKEN: 'secret', CI_API_V4_URL: 'https://gitlab.example.com/api/v4', CI_PROJECT_ID: '123', CI_PIPELINE_ID: '9',
      },
    });
    const requests = readFileSync(join(dir, 'requests.jsonl'), 'utf8').trim().split('\n').map((line) => JSON.parse(line));
    return { result, discussions: requests.filter((r) => r.url.endsWith('/discussions')).map((r) => r.data) };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const position = (data: { position?: Record<string, unknown> }) => {
  const { old_path, new_path, old_line, new_line } = data.position ?? {};
  return { old_path, new_path, old_line, new_line };
};

describe('GitLab review publisher inline anchors', () => {
  it('sends both old_line and new_line for unchanged context lines', () => {
    const { result, discussions } = publish([{ path: 'lib/main.dart', line: 30 }, { path: 'lib/main.dart', line: 33 }]);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(discussions.map(position), [
      { old_path: 'lib/main.dart', new_path: 'lib/main.dart', old_line: 30, new_line: 30 },
      { old_path: 'lib/main.dart', new_path: 'lib/main.dart', old_line: 32, new_line: 33 },
    ]);
  });

  it('sends only new_line for added lines and only old_line for removed lines', () => {
    const { result, discussions } = publish([
      { path: 'lib/main.dart', line: 32 },
      { path: 'lib/main.dart', line: 31, side: 'LEFT' },
    ]);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(discussions.map(position), [
      { old_path: 'lib/main.dart', new_path: 'lib/main.dart', old_line: undefined, new_line: 32 },
      { old_path: 'lib/main.dart', new_path: 'lib/main.dart', old_line: 31, new_line: undefined },
    ]);
  });

  it('uses the old path of renamed files', () => {
    const { result, discussions } = publish([{ path: 'lib/new_name.dart', line: 2 }, { path: 'lib/old_name.dart', line: 2, side: 'LEFT' }]);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(discussions.map(position), [
      { old_path: 'lib/old_name.dart', new_path: 'lib/new_name.dart', old_line: undefined, new_line: 2 },
      { old_path: 'lib/old_name.dart', new_path: 'lib/new_name.dart', old_line: 2, new_line: undefined },
    ]);
  });

  it('posts findings outside the MR diff as general threads instead of failing', () => {
    const { result, discussions } = publish([{ path: 'lib/main.dart', line: 90 }, { path: 'lib/untouched.dart', line: 4 }]);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(discussions.map((d) => d.position), [undefined, undefined]);
    assert.match(discussions[0].body, /^\*\*`lib\/main\.dart:90`\*\*\n\nFinding$/);
    assert.match(discussions[1].body, /lib\/untouched\.dart:4/);
    assert.match(result.stdout, /general thread \(line is outside the MR diff\)/);
  });
});
