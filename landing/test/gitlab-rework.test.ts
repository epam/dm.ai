import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { defaults, renderGitlabFiles, GITLAB_TEMPLATE_NAMES, type GitlabTemplates } from '../src/lib/factory.ts';

const here = dirname(fileURLToPath(import.meta.url));
const templates = Object.fromEntries(GITLAB_TEMPLATE_NAMES.map((name) =>
  [name, readFileSync(resolve(here, 'fixtures/gitlab', name), 'utf8')])) as GitlabTemplates;
const config = { ...defaults, repo: 'team/app', gitlabAutoLifecycle: false };
const generated = renderGitlabFiles(config, templates);
const script = generated.find((file) => file.path.endsWith('gitlab-factory-rework.sh'))!.content;

const mockCommand = `#!${process.execPath}
const fs = require('node:fs');
const path = require('node:path');
const command = path.basename(process.argv[1]);
const args = process.argv.slice(2);
const mode = process.env.TEST_MODE;
const root=process.env.TEST_ROOT;
const stateFile=name=>path.join(root,name);
const head = process.env.TEST_HEAD || 'a'.repeat(40);
const next = 'b'.repeat(40);
const log = (event) => fs.appendFileSync(stateFile('events.jsonl'), JSON.stringify(event) + '\\n');
log({ command, args, cwd:process.env.PWD });
if (command === 'git') {
  if(mode==='real-publication'){
    const fetch=args.indexOf('fetch');
    const push=args.indexOf('push');
    const remote=args.indexOf('ls-remote');
    if(fetch>=0)args.splice(fetch+2,1,stateFile('remote.git'));
    if(push>=0)args.splice(args.findIndex(a=>a.startsWith('https://')),1,stateFile('remote.git'));
    if(remote>=0)args.splice(remote+2,1,stateFile('remote.git'));
    const result=require('node:child_process').spawnSync('/usr/bin/git',args,{stdio:'inherit'});
    if(result.status===0&&push>=0&&!args.includes('--dry-run'))fs.writeFileSync(stateFile('pushed'),'');
    process.exit(result.status??1);
  }
  if(args.includes('ls-remote')){
    const pushed=fs.existsSync(stateFile('pushed'));
    const value=(mode==='remote-concurrent'&&!pushed)||(mode==='remote-changed-after-push'&&pushed)
      ? 'c'.repeat(40) : pushed&&fs.existsSync(stateFile('committed'))?next:head;
    console.log(value+'\\trefs/heads/ai/existing');process.exit(0);
  }
  if(mode==='runner-token-rewrite'&&(args.includes('fetch')||args.includes('push'))&&
      args.includes('https://gitlab.example.com/team/app.git')){
    console.error('Job token cannot push');process.exit(1);
  }
  if(mode==='real-dirty-checkout'||mode==='real-expected-branch'){
    const fetch=args.indexOf('fetch');
    if(fetch>=0)args.splice(fetch+2,1,root);
    if(args.includes('push')){if(!args.includes('--dry-run'))fs.writeFileSync(stateFile('pushed'),'');process.exit(0);}
    const result=require('node:child_process').spawnSync('/usr/bin/git',args,{stdio:'inherit'});
    process.exit(result.status??1);
  }
  if(args[0]==='worktree'&&args[1]==='add')fs.mkdirSync(args[3],{recursive:true});
  else if(args[0]==='worktree'&&args[1]==='remove')fs.rmSync(args[3],{recursive:true});
  else if (args[0] === 'status') { if (mode === 'dirty') console.log(' M existing.txt'); }
  else if (args[0] === 'branch') console.log(mode === 'wrong-branch' ? 'main' : mode === 'expected-branch' ? 'ai/existing' : '');
  else if (args[0] === 'merge-base' && mode === 'unrelated-head') process.exit(1);
  else if (args[0] === 'rev-parse') console.log(mode==='stale-fetch'?'c'.repeat(40):fs.existsSync(stateFile('committed')) ? next : head);
  else if (args[0] === 'diff' && args.includes('--quiet')) process.exit(mode === 'no-change' ? 0 : 1);
  else if (args[0] === 'commit') fs.writeFileSync(stateFile('committed'), '');
  else if (args.includes('push')) {
    if (mode === 'push-failure') process.exit(1);
    if(!args.includes('--dry-run'))fs.writeFileSync(stateFile('pushed'), '');
  }
} else if (command === 'curl') {
  const method = args[args.indexOf('--request') + 1];
  const url = new URL(args.at(-1));
  const base = '/api/v4/projects/123/merge_requests/36';
  let data = {};
  let status = '200';
  if (method === 'GET' && url.pathname === base) {
    const calls = fs.existsSync(stateFile('mr-reads')) ? Number(fs.readFileSync(stateFile('mr-reads'), 'utf8')) : 0;
    fs.writeFileSync(stateFile('mr-reads'), String(calls + 1));
    const pushed=fs.existsSync(stateFile('pushed'));
    let published=fs.existsSync(stateFile('committed'))?next:head;
    if(mode==='real-publication'&&pushed)published=require('node:child_process').execFileSync('/usr/bin/git',
      ['--git-dir='+stateFile('remote.git'),'rev-parse','refs/heads/ai/existing'],{encoding:'utf8'}).trim();
    const delayed=['delayed-mr-head','empty-mr-head','real-publication'].includes(mode)&&calls<4;
    data = {
      state: mode === 'closed'||(mode==='closed-after-push'&&pushed) ? 'closed' : 'opened',
      source_project_id: mode === 'fork'||(mode==='fork-after-push'&&pushed) ? 999 : 123,
      source_branch: mode==='switched-after-push'&&pushed?'ai/other':'ai/existing', target_branch: 'main',
      sha: (mode === 'concurrent' && calls > 0)||(mode==='unexpected-mr-head'&&pushed) ? 'c'.repeat(40)
        : pushed&&delayed&&mode==='empty-mr-head'?null:pushed&&mode!=='stale-head'&&!delayed?published:head
    };
    log({mrHead:data.sha,pushed});
  } else if (method === 'GET' && url.pathname === base + '/discussions') {
    const discussion = i => ({id: i.toString(16), notes: [{id: i, body: 'Please fix suggestion ' + i, resolvable: true, resolved: mode === 'pagination' && i <= 100}]});
    data = mode === 'no-threads' ? [] : mode === 'pagination'
      ? (url.searchParams.get('page') === '1' ? Array.from({length: 100}, (_, i) => discussion(i + 1)) : [discussion(101)])
      : [discussion(42)];
  } else if (method === 'GET' && url.pathname === '/api/v4/projects/123/pipelines') {
    if(mode==='pipeline-api-failure'){status='500';data={message:'500 Internal Server Error'};}
    else if(mode==='pipeline-invalid-list')data={message:'Not an array'};
    else data=[];
  } else if (method === 'GET' && url.pathname === '/api/v4/projects/123/issues') {
    data = process.env.TEST_ENTRY === 'issue' ? [{iid: 8, labels: ['agent:rework']}] : [];
  } else if (method === 'GET' && url.pathname === '/api/v4/projects/123/issues/8/related_merge_requests') {
    data = mode === 'no-mr' ? [] : mode === 'ambiguous-mr'
      ? [{iid: 36, state: 'opened'}, {iid: 37, state: 'opened'}] : [{iid:36, state:'opened'}];
  } else if (method === 'GET' && url.pathname === '/api/v4/projects/123/merge_requests') {
    data = [{iid:36, labels:['agent:rework']}];
  } else if (url.pathname === '/api/v4/user') data = {username: 'dev-bot'};
  else if (url.pathname === '/api/v4/projects/123') data = {http_url_to_repo: 'https://gitlab.example.com/team/app.git'};
  else if (mode === 'api-failure' && url.pathname.endsWith('/changes')) { status = '500'; data = {message: 'failed'}; }
  else if (mode === 'reply-failure' && method === 'POST' && url.pathname.includes('/discussions/')) {
    status = '500'; data = {message: 'failed to post reply'};
  }
  process.stdout.write(JSON.stringify(data) + '\\n' + status);
} else if (command === 'dmtools') {
  if(mode==='real-publication'){
    for(const args of [['rm','-r','.cursor'],['-c','user.name=Agent','-c','user.email=agent@example.com','commit','-m','Address review feedback']]){
      const result=require('node:child_process').spawnSync('/usr/bin/git',args,{stdio:'inherit'});
      if(result.status!==0)process.exit(result.status??1);
    }
  }
  if(mode==='real-expected-branch'){
    const switched=require('node:child_process').spawnSync('/usr/bin/git',['switch','ai/existing'],{stdio:'inherit'});
    if(switched.status!==0)process.exit(switched.status??1);
  }
  if (mode === 'agent-failure') process.exit(1);
  if (mode === 'agent-reported-failure') {console.log('Agent completed with exit code: 1'); process.exit(0);}
  if (mode === 'missing-output') process.exit(0);
  const context = process.env.DMTOOLS_REWORK_CONTEXT;
  const threads = JSON.parse(fs.readFileSync(path.join(context, 'pr_discussions_raw.json'), 'utf8')).threads;
  log({ contextThreads: threads.length, runner: JSON.parse(fs.readFileSync(args[1], 'utf8')) });
  fs.mkdirSync('outputs/review_replies', {recursive:true});
  fs.writeFileSync('outputs/response.md', mode === 'blocked' ? 'BLOCKED: Cannot complete safely.' : 'Fixed and tested suggestions.');
  const replies = threads.map(t => {
    const reply = 'outputs/review_replies/thread_' + t.threadId + '.md';
    fs.writeFileSync(reply, 'Fixed this suggestion in the existing MR.');
    return {threadId:t.threadId, inReplyToId:t.rootCommentId, reply};
  });
  if (mode === 'missing-reply') replies.pop();
  if (mode === 'wrong-root') replies[0].inReplyToId = 9999;
  if (mode === 'unsafe-path') replies[0].reply = '../secret.md';
  fs.writeFileSync('outputs/review_replies.json', JSON.stringify({replies}));
}
`;

interface Event {
  command?: string;
  args?: string[];
  contextThreads?: number;
  cwd?: string;
  mrHead?: string | null;
  pushed?: boolean;
}

function runRework(mode = 'success', entry: 'direct' | 'issue' | 'mr' = 'direct') {
  const dir = mkdtempSync(join(tmpdir(), 'dmtools-rework-test-'));
  try {
    mkdirSync(join(dir, '.dmtools/runners'), { recursive: true });
    writeFileSync(join(dir, '.dmtools/runners/fa-rework.json'),
      generated.find((file) => file.path.endsWith('fa-rework.json'))!.content);
    mkdirSync(join(dir, '.dmtools/scripts'), { recursive: true });
    writeFileSync(join(dir, '.dmtools/scripts/gitlab-factory-rework.sh'), script);
    if (mode === 'pipeline-api-failure' || mode === 'pipeline-invalid-list') {
      writeFileSync(join(dir, '.dmtools/scripts/gitlab-factory-common.sh'), templates['gitlab-factory-common.sh']);
    }
    writeFileSync(join(dir, 'payload.json'), JSON.stringify({ object_kind: 'issue', object_attributes: { iid: 8 } }));
    const poller = generated.find((file) => file.path.endsWith('gitlab-factory-poll-issues.sh'))!.content;
    mkdirSync(join(dir, 'bin'));
    for (const command of ['git', 'curl', 'dmtools', 'sleep']) {
      writeFileSync(join(dir, 'bin', command), mockCommand, { mode: 0o700 });
    }
    let head: string | undefined;
    if (mode === 'real-dirty-checkout' || mode === 'real-expected-branch' || mode === 'real-publication') {
      const git = (...args: string[]) => {
        const result = spawnSync('/usr/bin/git', args, {cwd:dir,encoding:'utf8'});
        assert.equal(result.status,0,result.stderr);
        return result.stdout.trim();
      };
      git('init','--initial-branch=main');
      mkdirSync(join(dir,'.cursor/skills'),{recursive:true});
      writeFileSync(join(dir,'.cursor/skills/installed-skills.json'),'original tracked metadata');
      git('add','.dmtools','.cursor');
      git('-c','user.name=Test','-c','user.email=test@example.com','commit','-m','Rework fixture');
      head=git('rev-parse','HEAD');
      git('update-ref','refs/heads/ai/existing',head);
      if(mode==='real-publication'){
        git('init','--bare',join(dir,'remote.git'));
        git('push',join(dir,'remote.git'),'refs/heads/ai/existing');
      }
      writeFileSync(join(dir,'.cursor/skills/installed-skills.json'),'installer mutation');
      writeFileSync(join(dir,'.cursor/skills/new-skill'),'installer untracked file');
    }
    const result = spawnSync('bash', ['-s', '--', '36'], {
      input: entry === 'direct' ? script : poller,
      encoding: 'utf8',
      cwd: dir,
      env: {
        ...process.env,
        PATH: `${join(dir, 'bin')}:${process.env.PATH}`,
        TEST_MODE: mode,
        TEST_ROOT: dir,
        ...(head ? {TEST_HEAD:head}:{}),
        TEST_ENTRY: entry,
        GITLAB_TOKEN: 'test-dev-token',
        CI_API_V4_URL: 'https://gitlab.example.com/api/v4',
        CI_PROJECT_ID: '123',
        CI_PIPELINE_ID: '456',
        DMTOOLS_FACTORY_TRIGGER: entry === 'issue' ? 'issue_webhook' : '',
        TRIGGER_PAYLOAD: join(dir, 'payload.json'),
      },
    });
    const events: Event[] = readFileSync(join(dir, 'events.jsonl'), 'utf8').trim().split('\n').map((line) => JSON.parse(line));
    if (mode === 'real-dirty-checkout' || mode === 'real-expected-branch' || mode === 'real-publication') {
      assert.equal(readFileSync(join(dir,'.cursor/skills/installed-skills.json'),'utf8'),'installer mutation');
      assert.equal(readFileSync(join(dir,'.cursor/skills/new-skill'),'utf8'),'installer untracked file');
      const reviewRun=events.find(e=>e.command==='dmtools')!;
      assert.ok(reviewRun);
      assert.equal(existsSync(reviewRun.cwd!),false,'Temporary worktree must be removed');
      if(mode==='real-publication'){
        const published=spawnSync('/usr/bin/git',['--git-dir='+join(dir,'remote.git'),'rev-parse','refs/heads/ai/existing'],{encoding:'utf8'});
        assert.equal(published.status,0,published.stderr);
        assert.notEqual(published.stdout.trim(),head,'The agent-created commit must actually be pushed');
        assert.equal(events.filter(e=>e.pushed).at(-1)?.mrHead,published.stdout.trim());
      }
    }
    return { result, events };
  } finally {
    rmSync(dir, { recursive: true });
  }
}

describe('GitLab rework', () => {
  it('uses the existing MR on every entry point and disables pack-side publishing', () => {
    const pipeline = generated.find((file) => file.path === '.gitlab-ci.yml')!.content;
    assert.match(pipeline, /gitlab-factory-rework.sh "\$CI_MERGE_REQUEST_IID"/);
    assert.match(pipeline, /gitlab-factory-rework.sh "\$DMTOOLS_TICKET_ID"/);
    const poller = generated.find((file) => file.path.endsWith('gitlab-factory-poll-issues.sh'))!.content;
    assert.match(poller, /gitlab-factory-rework.sh "\$mr_iid"/);
    assert.match(poller, /gitlab-factory-rework.sh "\$iid"/);
    assert.match(poller, /multiple open merge requests are linked to the issue; cannot choose rework target safely/);
    const reworkBlock = poller.split(`    if [ "$trigger" = 'agent:rework' ]; then`)[1].split('    elif')[0];
    assert.doesNotMatch(reworkBlock, /publish_issue_changes|run_agent/);
    const runner = JSON.parse(generated.find((file) => file.path.endsWith('fa-rework.json'))!.content);
    for (const hook of ['preJSAction', 'preCliJSAction', 'postJSAction', 'timerJSAction']) {
      assert.equal(runner.params[hook], '');
    }
    assert.match(runner.params.cliPrompts[0], /DMTOOLS_REWORK_CONTEXT/);
    assert.match(runner.params.cliPrompts[0], /Never create a branch or merge request/);
    assert.match(runner.params.cliPrompts[0], /EVERY open thread/);
  });

  it('pushes to the existing branch before replying and resolving suggestions on that MR', () => {
    const { result, events } = runRework();
    assert.equal(result.status, 0, result.stderr);
    const push = events.findIndex((e) => e.command === 'git' && e.args?.includes('push') && !e.args.includes('--dry-run'));
    assert.ok(push >= 0);
    assert.equal(events[push].args?.at(-1), 'HEAD:refs/heads/ai/existing');
    assert.ok(events.some((e) => e.command === 'git' && e.args?.slice(0,3).join(' ') === 'worktree add --detach'));
    assert.ok(!events.some((e) => e.command === 'git' && e.args?.[0] === 'switch'));
    const replies = events.filter((e) => e.command === 'curl' && e.args?.includes('POST') &&
      e.args?.at(-1)?.includes('/discussions/2a/notes'));
    assert.equal(replies.length, 1);
    assert.ok(events.indexOf(replies[0]) > push);
    assert.ok(events.some((e) => e.command === 'curl' && e.args?.includes('resolved=true')));
    assert.ok(events.some((e) => e.command === 'curl' && e.args?.includes('remove_labels=agent:rework')));
    assert.ok(events.every((e) => !(e.command === 'curl' && e.args?.includes('POST') && e.args?.at(-1)?.endsWith('/merge_requests'))));
    assert.equal(events.find((e) => e.contextThreads !== undefined)?.contextThreads, 1);
  });

  for (const mode of ['no-threads', 'no-change', 'pagination', 'expected-branch']) {
    it(`handles ${mode} without creating another MR`, () => {
      const { result, events } = runRework(mode);
      assert.equal(result.status, 0, result.stderr);
      const count = mode === 'no-threads' ? 0 : 1;
      assert.equal(events.filter((e) => e.command === 'curl' && e.args?.includes('POST') && e.args?.at(-1)?.includes('/discussions/')).length, count);
      if (mode === 'pagination') {
        assert.ok(events.some((e) => e.args?.at(-1)?.endsWith('discussions?per_page=100&page=2')));
      }
    });
  }

  for (const mode of ['dirty', 'real-dirty-checkout', 'real-expected-branch']) {
    it(`isolates rework from installer-mutated tracked and untracked files (${mode})`, () => {
      const { result, events } = runRework(mode);
      assert.equal(result.status, 0, result.stderr);
      const worktree = events.find(e => e.command === 'git' && e.args?.[0] === 'worktree' && e.args[1] === 'add')!;
      const agent = events.find(e => e.command === 'dmtools')!;
      assert.equal(agent.cwd, worktree.args?.[3]);
      assert.ok(events.some(e => e.command === 'git' && e.args?.[1] === 'remove'));
      assert.ok(!events.some(e => e.command === 'git' && e.args?.[0] === 'switch'));
      assert.ok(events.some(e => e.command === 'git' && e.args?.includes('HEAD:refs/heads/ai/existing')));
    });
  }

  it('uses the developer username on fetch and push to bypass Runner job-token URL rewriting', () => {
    const { result, events } = runRework('runner-token-rewrite');
    assert.equal(result.status, 0, result.stderr);
    for (const action of ['fetch','push']) {
      const request=events.find(e=>e.command==='git'&&e.args?.includes(action))!;
      assert.ok(request.args?.includes('https://dev-bot@gitlab.example.com/team/app.git'));
      assert.ok(request.args?.includes('credential.helper='));
    }
  });

  for (const mode of ['delayed-mr-head','empty-mr-head','real-publication']) {
    it(`waits for MR metadata before replying after a verified Git push (${mode})`, () => {
      const {result,events}=runRework(mode);
      assert.equal(result.status,0,result.stderr);
      assert.match(result.stdout,/Waiting for GitLab MR metadata/);
      assert.equal(events.filter(e=>e.command==='dmtools').length,1,'Metadata delays must not rerun AI');
      assert.equal(events.filter(e=>e.command==='sleep').length,2);
      const postPushReads=events.filter(e=>e.pushed);
      assert.equal(postPushReads.length,3);
      const firstReply=events.findIndex(e=>e.command==='curl'&&e.args?.includes('POST')&&e.args.at(-1)?.includes('/discussions/'));
      assert.ok(firstReply>events.indexOf(postPushReads.at(-1)!),'Replies require synchronized MR metadata');
      assert.ok(events.some(e=>e.command==='curl'&&e.args?.includes('resolved=true')));
    });
  }

  it('bounds stale metadata waits and reports that the branch was already pushed',()=>{
    const {result,events}=runRework('stale-head');
    assert.notEqual(result.status,0);
    assert.equal(events.filter(e=>e.pushed).length,12);
    assert.equal(events.filter(e=>e.command==='sleep').length,11);
    assert.match(result.stderr,/Timed out.*push succeeded/);
    assert.ok(!events.some(e=>e.command==='curl'&&e.args?.includes('POST')));
  });

  for(const mode of ['remote-concurrent','remote-changed-after-push','unexpected-mr-head',
    'closed-after-push','switched-after-push','fork-after-push']){
    it(`does not retry or publish replies on ${mode}`,()=>{
      const {result,events}=runRework(mode);
      assert.notEqual(result.status,0);
      assert.ok(!events.some(e=>e.command==='sleep'));
      assert.ok(!events.some(e=>e.command==='curl'&&(e.args?.includes('POST')||e.args?.includes('resolved=true'))));
      if(mode==='remote-concurrent')assert.ok(!events.some(e=>e.command==='git'&&e.args?.includes('push')&&!e.args.includes('--dry-run')));
    });
  }

  it('keeps a username-qualified Git URL outside a real Runner-style insteadOf rule', () => {
    const dir=mkdtempSync(join(tmpdir(),'gitlab-rewrite-test-'));
    try {
      const git=(...args:string[])=>{
        const r=spawnSync('/usr/bin/git',args,{cwd:dir,encoding:'utf8'});
        assert.equal(r.status,0,r.stderr);return r.stdout.trim();
      };
      git('init');
      git('config','url.https://gitlab-ci-token:dummy-job-token@gitlab.example.com/.insteadOf','https://gitlab.example.com/');
      git('remote','add','bare','https://gitlab.example.com/team/app.git');
      git('remote','add','developer','https://dev-bot@gitlab.example.com/team/app.git');
      assert.match(git('remote','get-url','bare'),/gitlab-ci-token:dummy-job-token@/);
      assert.equal(git('remote','get-url','developer'),'https://dev-bot@gitlab.example.com/team/app.git');
    } finally { rmSync(dir,{recursive:true}); }
  });

  for (const mode of ['pipeline-api-failure','pipeline-invalid-list']) {
    it(`stops before invoking AI instead of treating ${mode} as no CI failures`,()=>{
      const { result, events }=runRework(mode);
      assert.notEqual(result.status,0);
      assert.ok(!events.some(e=>e.command==='dmtools'));
      assert.ok(!events.some(e=>e.command==='git'&&e.args?.includes('push')&&!e.args.includes('--dry-run')));
      assert.doesNotMatch(result.stderr,/invalid JSON text|integer expected/);
      assert.match(result.stderr,mode==='pipeline-api-failure'?/HTTP 500/:/not an array/);
    });
  }

  for (const mode of ['closed', 'fork', 'stale-fetch', 'unrelated-head', 'agent-failure', 'agent-reported-failure', 'api-failure',
    'missing-output', 'missing-reply', 'wrong-root', 'unsafe-path', 'wrong-branch', 'concurrent', 'push-failure', 'stale-head', 'blocked']) {
    it(`fails visibly and preserves review threads and trigger labels on ${mode}`, () => {
      const { result, events } = runRework(mode);
      assert.notEqual(result.status, 0);
      assert.ok(events.every((e) => !(e.command === 'curl' && e.args?.includes('resolved=true'))));
      assert.ok(events.every((e) => !(e.command === 'curl' && e.args?.includes('remove_labels=agent:rework'))));
    });
  }

  it('leaves a discussion open when its reply fails to publish', () => {
    const { result, events } = runRework('reply-failure');
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /failed to post reply/);
    assert.ok(events.every((e) => !(e.command === 'curl' && e.args?.includes('resolved=true'))));
  });

  for (const entry of ['issue', 'mr'] as const) {
    it(`routes ${entry}-based rework to the existing MR without development publishing`, () => {
      const { result, events } = runRework('success', entry);
      assert.equal(result.status, 0, result.stderr);
      const runs = events.filter((e) => e.command === 'dmtools');
      assert.equal(runs.length, 1);
      assert.equal(runs[0].args?.at(-1), '36', 'the rework agent must receive MR IID, never issue IID');
      const pushes = events.filter((e) => e.command === 'git' && e.args?.includes('push') && !e.args.includes('--dry-run'));
      assert.equal(pushes.length, 1);
      assert.equal(pushes[0].args?.at(-1), 'HEAD:refs/heads/ai/existing');
      assert.doesNotMatch(result.stdout, /Created GitLab merge request/);
    });
  }

  for (const mode of ['no-mr', 'ambiguous-mr']) {
    it(`does not guess or create a target MR on ${mode}`, () => {
      const { result, events } = runRework(mode, 'issue');
      assert.notEqual(result.status, 0);
      assert.match(result.stderr, /existing open merge request|multiple open merge requests/);
      assert.ok(events.every((e) => e.command !== 'dmtools'));
      assert.ok(events.every((e) => !e.args?.includes('push')));
    });
  }
});
