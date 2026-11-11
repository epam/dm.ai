import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import {
  defaults, renderGitlabFiles, GITLAB_TEMPLATE_NAMES, ensureGitlabFactorySchedule, type GitlabTemplates,
} from '../src/lib/factory.ts';

const here = dirname(fileURLToPath(import.meta.url));
const templates = Object.fromEntries(GITLAB_TEMPLATE_NAMES.map((name) =>
  [name, readFileSync(resolve(here, 'fixtures/gitlab', name), 'utf8')])) as GitlabTemplates;
const config = {
  ...defaults, repo: 'team/app', gitlabMultipleTeammates: true, qualityPreset: 'crap4js' as const,
};
const files = renderGitlabFiles(config, templates);

const mock = `#!${process.execPath}
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const command = path.basename(process.argv[1]);
const args = process.argv.slice(2);
const mode = process.env.TEST_MODE;
const root = process.env.TEST_ROOT;
const state = JSON.parse(fs.readFileSync(path.join(root,'state.json'), 'utf8'));
const log = (event) => fs.appendFileSync(path.join(root,'events.jsonl'), JSON.stringify(event) + '\\n');
const save = () => fs.writeFileSync(path.join(root,'state.json'), JSON.stringify(state));
log({command, args, identity:process.env.GITLAB_TOKEN,cwd:process.env.PWD});
if (command === 'curl') {
  const method = args[args.indexOf('--request') + 1];
  const url = new URL(args.at(-1));
  const p = url.pathname.replace('/api/v4', '');
  const form = {};
  args.forEach((v,i) => {
    if (['--data-urlencode', '--form-string'].includes(v)) {
      const raw=args[i+1]; const split=raw.indexOf('=');
      if(split>=0)form[raw.slice(0,split)]=raw.slice(split+1);
    }
  });
  let data={}, status='200';
  const mr = () => ({iid:36, id:360, state:'opened', source_project_id:123, source_branch:'ai/issue-8-1',
    target_branch:'main', sha:state.sha, labels:state.mrLabels, diff_refs:{base_sha:'c'.repeat(40),head_sha:state.sha,start_sha:'c'.repeat(40)}});
  if(p==='/user')data={id:process.env.GITLAB_TOKEN==='review-token'?2:1,username:'factory-bot'};
  else if(p==='/projects/123/merge_requests/36' && method==='GET')data=mr();
  else if(p==='/projects/123/merge_requests/36/notes' && method==='GET')data=state.notes;
  else if(p==='/projects/123/merge_requests/36/notes' && method==='POST'){
    const note={id:state.notes.length+1,body:form.body,author:{id:process.env.GITLAB_TOKEN==='review-token'?2:1}};
    state.notes.push(note);data=note;
  }
  else if(p==='/projects/123/merge_requests/36/discussions')data=state.unresolved
    ?[{id:'abc',notes:[{id:42,resolvable:true,resolved:false,body:'Please fix this.'}]}]:[];
  else if(p==='/projects/123/merge_requests/36/closes_issues')data=[{iid:8,project_id:123,state:'opened'}];
  else if(p==='/projects/123/merge_requests/36/changes')data={changes:[{new_path:'file.ts',diff:'@@ -1 +1 @@\\n-old\\n+new'}]};
  else if(p==='/projects/123/issues/8' && method==='GET')data={iid:8,state:'opened',title:'Money counter',
    web_url:'https://gitlab.example.com/team/app/-/issues/8',description:state.description,labels:state.issueLabels};
  else if(p==='/projects/123/issues/8' && method==='PUT'){
    if(form.description!==undefined)state.description=form.description;
    state.issueLabels=labels(state.issueLabels);data={iid:8};
  }
  else if(p==='/projects/123/merge_requests/36' && method==='PUT'){state.mrLabels=labels(state.mrLabels);data=mr();}
  else if(p==='/projects/123/pipeline' && method==='POST'){
    state.validations++;data={id:100+state.validations,sha:mode==='wrong-validation-head'?'f'.repeat(40):state.sha,web_url:'https://gitlab.example.com/pipeline'};
    state.pipelineHeads=state.pipelineHeads||{};state.pipelineHeads[data.id]=data.sha;
  }
  else if(p.startsWith('/projects/123/pipelines/')){
    data={id:101,sha:state.pipelineHeads[p.split('/').at(-1)],web_url:'https://gitlab.example.com/pipeline',
      status:mode==='ci-pending'?'pending':mode==='ci-failure-once'&&state.validations===1?'failed':mode==='ci-always-fails'?'failed':'success'};
  }
  else if(p==='/projects/123/issues')data=mode==='issue-rework'?[{iid:8,labels:state.issueLabels,assignees:[]}]:
    mode==='intake-dev'||mode==='intake-blocked'||mode.startsWith('real-intake')?[{iid:8,labels:['agent:dev'],assignees:[]}]:
    mode==='intake-assigned'?[{iid:8,labels:[],assignees:[{username:'factory-bot'}]}]:[];
  else if(p==='/projects/123/issues/8/related_merge_requests')data=state.created?[mr()]:[];
  else if(p==='/projects/123/merge_requests' && method==='GET')data=state.created?[mr()]:[];
  else if(p==='/projects/123/merge_requests' && method==='POST'){state.created=true;data={...mr(),web_url:'https://gitlab.example.com/mr/36'};}
  else {status='500';data={message:'Unexpected mock request '+method+' '+p};}
  if(mode==='progress-failure' && p==='/projects/123/issues/8' && method==='PUT'){status='500';data={message:'Cannot write progress'};}
  save();process.stdout.write(JSON.stringify(data)+'\\n'+status);
  function labels(current){
    return [...new Set([...current.filter(x=>!(form.remove_labels||'').split(',').includes(x)),...(form.add_labels||'').split(',').filter(Boolean)])];
  }
} else if(command==='dmtools'){
  if(args[1].includes('fa-story-dev')){
    const runner=JSON.parse(fs.readFileSync(args[1],'utf8'));
    log({devRequest:runner.params.agentParams?.request,devArgs:args});
    if(mode==='intake-blocked'){fs.mkdirSync('outputs',{recursive:true});fs.writeFileSync('outputs/needs-input.md','No request');process.exit(0);}
    if(mode==='real-intake-installer-only')process.exit(0);
    if(mode==='real-intake'){fs.mkdirSync('lib',{recursive:true});fs.writeFileSync('lib/main.dart','void main() {}');process.exit(0);}
    fs.writeFileSync('dev-change','changed');process.exit(0);
  }
  state.reviews++;
  if(mode==='review-context-files'||mode==='review-context-failure'||mode==='real-review-isolation'){
    const context=process.env.DMTOOLS_REVIEW_CONTEXT;
    fs.mkdirSync(path.join(context,'.agent-cache','nested'),{recursive:true});
    fs.writeFileSync(path.join(context,'.agent-cache','nested','session'),'runtime');
    fs.writeFileSync(path.join(context,'scratch.md'),'agent scratch file');
    fs.symlinkSync(root,path.join(context,'orchestrator-link'));
  }
  if(mode==='review-context-failure'){save();process.exit(7);}
  if(mode==='review-failure'){save();process.exit(1);}
  fs.mkdirSync('outputs',{recursive:true});
  const decision=mode==='endless-rework'||mode==='explicit-request-changes'||(mode==='review-rework'&&state.reviews===1)?'request_changes':'approve';
  fs.writeFileSync('outputs/pr_review.json',JSON.stringify({decision:mode==='invalid-verdict'?'unknown':decision,generalComment:'outputs/pr_review_general.md',inlineComments:[]}));
  fs.writeFileSync('outputs/pr_review_general.md','Review result: '+decision);
  if(mode==='review-contamination'||mode==='real-review-isolation'||mode==='review-contamination-failure'){
    fs.mkdirSync('.fah',{recursive:true});fs.writeFileSync('.fah/session','review runtime');
    fs.mkdirSync('.cursor/skills',{recursive:true});fs.writeFileSync('.cursor/skills/generated','review runtime');
    fs.writeFileSync('app.txt','review mutation');
  }
  if(mode==='review-contamination-failure'){save();process.exit(1);}
  if(mode==='stale-review')state.sha='e'.repeat(40);
  save();
} else if(command==='git'){
  if(mode.startsWith('real-intake')&&!fs.existsSync(path.join(root,'pushed'))){
    if(args[0]==='remote'){console.log('https://gitlab.example.com/team/app.git');process.exit(0);}
    if(args.includes('push')){fs.writeFileSync(path.join(root,'pushed'),'');process.exit(0);}
    const result=spawnSync('/usr/bin/git',args,{stdio:'inherit'});process.exit(result.status??1);
  }
  if(mode==='real-review-isolation'){
    const result=spawnSync('/usr/bin/git',args,{stdio:'inherit'});process.exit(result.status??1);
  }
  if(args[0]==='worktree'&&args[1]==='add'){
    fs.mkdirSync(args[3],{recursive:true});fs.writeFileSync(path.join(args[3],'app.txt'),'original');
  }
  else if(args[0]==='worktree'&&args[1]==='remove')fs.rmSync(args[3],{recursive:true});
  else if(args[0]==='fetch'&&mode==='review-fetch-failure')process.exit(1);
  else if(args[0]==='rev-parse')console.log(mode==='review-fetch-stale'?'f'.repeat(40):state.sha);
  else if(args[0]==='status'&&fs.existsSync('dev-change'))console.log(' M file.ts');
  else if(args[0]==='rev-list')console.log('0');
  else if(args[0]==='diff'&&args.includes('--quiet'))process.exit(1);
  else if(args[0]==='remote')console.log('https://gitlab.example.com/team/app.git');
} else if(command==='sleep'){}
`;

interface State {
  sha: string;
  notes: { id: number; body: string; author: { id: number } }[];
  description: string;
  mrLabels: string[];
  issueLabels: string[];
  unresolved: boolean;
  reviews: number;
  reworks: number;
  validations: number;
  created: boolean;
}
interface Event { command?: string; args?: string[]; identity?: string; cwd?: string; devRequest?: string; devArgs?: string[] }

function exercise(mode = 'approve', entry = 'run', payload?: unknown) {
  const dir = mkdtempSync(join(tmpdir(), 'gitlab-lifecycle-'));
  const initial: State = {
    sha: 'a'.repeat(40), notes: [], description: '# Original issue\n\nKeep these requirements.',
    mrLabels: [], issueLabels: [], unresolved: mode === 'suggestions', reviews: 0, reworks: 0,
    validations: 0, created: !['intake-dev', 'intake-assigned', 'intake-blocked'].includes(mode) && !mode.startsWith('real-intake'),
  };
  if (mode === 'stopped') initial.mrLabels = ['needs-human'];
  if (mode === 'mr-rework' || mode === 'no-progress' || mode === 'explicit-request-changes') initial.mrLabels = ['agent:rework'];
  if (mode === 'issue-rework') initial.issueLabels = ['agent:rework'];
  if (mode === 'requested-changes') initial.notes = [
    { id: 1, body: `<!-- dmtools-review:${initial.sha}:request_changes -->`, author: { id: 2 } },
  ];
  if (mode === 'no-progress') initial.notes = [
    { id: 1, body: `<!-- dmtools-review:${initial.sha}:request_changes -->`, author: { id: 2 } },
  ];
  if (mode === 'approved' || mode === 'spoofed') {
    const author = mode === 'spoofed' ? 99 : 2;
    initial.notes = [
      { id: 1, body: `<!-- dmtools-review:${initial.sha}:approve -->`, author: { id: author } },
      { id: 2, body: `<!-- dmtools-validation:${initial.sha}:success -->`, author: { id: mode === 'spoofed' ? 99 : 1 } },
    ];
  }
  try {
    for (const file of files) {
      const path = join(dir, file.path);
      mkdirSync(dirname(path), { recursive: true });
      writeFileSync(path, file.content);
    }
    writeFileSync(join(dir, '.dmtools/scripts/gitlab-factory-rework.sh'), `#!/usr/bin/env bash
set -e
node -e 'const fs=require("fs");const s=JSON.parse(fs.readFileSync("state.json"));s.reworks++;s.unresolved=false;if(process.env.TEST_MODE!=="no-progress")s.sha="b".repeat(39)+s.reworks;fs.writeFileSync("state.json",JSON.stringify(s));fs.appendFileSync("events.jsonl",JSON.stringify({command:"rework",args:process.argv.slice(1)})+"\\n");' "$1"
`);
    writeFileSync(join(dir, '.dmtools/scripts/gitlab-factory-publish-review.sh'), '#!/usr/bin/env bash\nset -e\nprintf \'{"command":"publish-review"}\\n\' >> "$TEST_ROOT/events.jsonl"\n');
    writeFileSync(join(dir, 'app.txt'), 'original');
    writeFileSync(join(dir, 'state.json'), JSON.stringify(initial));
    writeFileSync(join(dir, 'events.jsonl'), '');
    mkdirSync(join(dir, 'bin'));
    for (const command of ['curl', 'dmtools', 'git', 'sleep']) {
      writeFileSync(join(dir, 'bin', command), mock, { mode: 0o700 });
    }
    if (mode === 'real-review-isolation') {
      const git = (...args: string[]) => {
        const result = spawnSync('/usr/bin/git', args, { cwd: dir, encoding: 'utf8' });
        assert.equal(result.status, 0, result.stderr);
        return result.stdout.trim();
      };
      git('init', '--initial-branch=main');
      git('add', '.dmtools', 'app.txt');
      git('-c', 'user.name=Test', '-c', 'user.email=test@example.com', 'commit', '-m', 'Review fixture');
      initial.sha = git('rev-parse', 'HEAD');
      git('update-ref', 'refs/merge-requests/36/head', initial.sha);
      git('remote', 'add', 'origin', dir);
      writeFileSync(join(dir, 'state.json'), JSON.stringify(initial));
    }
    if (payload) writeFileSync(join(dir, 'payload.json'), JSON.stringify(payload));
    const realGit = (...args: string[]) => {
      const result = spawnSync('/usr/bin/git', args, { cwd: dir, encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      return result.stdout.trim();
    };
    if (mode.startsWith('real-intake')) {
      realGit('init', '--initial-branch=main');
      writeFileSync(join(dir, '.git/info/exclude'), 'state.json\nevents.jsonl\nbin/\npayload.json\npushed\n');
      mkdirSync(join(dir, '.cursor/skills'), { recursive: true });
      writeFileSync(join(dir, '.cursor/skills/tracked.md'), 'committed');
      realGit('add', '-A');
      realGit('-c', 'user.name=Test', '-c', 'user.email=test@example.com', 'commit', '-m', 'Intake fixture');
      writeFileSync(join(dir, '.cursor/skills/tracked.md'), 'installer rewrite');
      writeFileSync(join(dir, '.cursor/skills/installed-skills.json'), '{"version":"latest"}');
    }
    const command = entry === 'intake'
      ? ['.dmtools/scripts/gitlab-factory-poll-issues.sh']
      : entry === 'wake' ? ['.dmtools/scripts/gitlab-factory-lifecycle.sh', 'wake']
      : entry === 'review' ? ['.dmtools/scripts/gitlab-factory-lifecycle.sh', 'review', '36']
      : entry === 'job-failure' || entry === 'job-cancelled'
        ? ['.dmtools/scripts/gitlab-factory-job.sh', '8', 'dev', 'bash', '-c', entry === 'job-cancelled' ? 'exit 143' : 'exit 9']
        : ['.dmtools/scripts/gitlab-factory-lifecycle.sh', 'run', '36', '', entry === 'manual-rework' ? 'rework' : ''];
    const options = {
      cwd: dir, encoding: 'utf8',
      env: {
        ...process.env, PATH: `${join(dir, 'bin')}:${process.env.PATH}`, TEST_ROOT: dir,
        TEST_MODE: mode === 'ci-resume' ? 'ci-pending' : mode, GITLAB_DEV_TOKEN: 'dev-token', GITLAB_REVIEWER_TOKEN: 'review-token',
        CI_API_V4_URL: 'https://gitlab.example.com/api/v4', CI_PROJECT_ID: '123',
        CI_DEFAULT_BRANCH: 'main',
        CI_PIPELINE_ID: '456', CI_JOB_ID: '789', CI_JOB_URL: 'https://gitlab.example.com/job/789',
        CI_PIPELINE_URL: 'https://gitlab.example.com/pipeline/456',
        ...(payload ? { DMTOOLS_FACTORY_TRIGGER: 'issue_webhook', TRIGGER_PAYLOAD: join(dir, 'payload.json') } : {}),
      },
    } as const;
    let result = spawnSync('bash', command, options);
    let pendingState: State | undefined;
    if (mode === 'ci-resume') {
      assert.equal(result.status, 0, result.stderr);
      pendingState = JSON.parse(readFileSync(join(dir, 'state.json'), 'utf8'));
      result = spawnSync('bash', command, { ...options, env: { ...options.env, TEST_MODE: 'approve' } });
    }
    if (mode === 'review-rework' || mode === 'suggestions' || mode === 'requested-changes' || mode === 'explicit-request-changes') {
      assert.equal(result.status, 0, result.stderr);
      result = spawnSync('bash', command, options);
    }
    const state: State = JSON.parse(readFileSync(join(dir, 'state.json'), 'utf8'));
    const events: Event[] = readFileSync(join(dir, 'events.jsonl'), 'utf8').trim().split('\n').filter(Boolean).map((line) => JSON.parse(line));
    const checkoutPreserved = readFileSync(join(dir, 'app.txt'), 'utf8') === 'original';
    const runtimeLeaked = ['.fah/session', '.cursor/skills/generated'].some((path) => existsSync(join(dir, path)));
    const reviewRuns = events.filter((e) => e.command === 'dmtools' && !e.args?.[1]?.includes('fa-story-dev'));
    const contextsRemoved = reviewRuns.every((e) => !existsSync(dirname(e.cwd!)));
    const published = mode.startsWith('real-intake') && existsSync(join(dir, 'pushed'))
      ? realGit('diff', '--name-only', 'main', 'ai/issue-8-456').split('\n').filter(Boolean) : undefined;
    return { result, state, events, pendingState, checkoutPreserved, runtimeLeaked, contextsRemoved, published };
  } finally {
    rmSync(dir, { recursive: true });
  }
}

describe('GitLab automatic lifecycle', { concurrency: 4 }, () => {
  it('generates validation-only API pipelines without agent lock and no automatic merge', () => {
    const pipeline = files.find((f) => f.path === '.gitlab-ci.yml')!.content;
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "api" && \$DMTOOLS_FACTORY_PHASE == "validation"/);
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "schedule"/);
    assert.doesNotMatch(pipeline, /CI_PIPELINE_SOURCE == "trigger"|DMTOOLS_FACTORY_TRIGGER/);
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "api" && \$DMTOOLS_FACTORY_PHASE == "reconcile"/);
    assert.match(pipeline, /after_script:[\s\S]*gitlab-factory-lifecycle\.sh wake/);
    assert.equal(pipeline.match(/resource_group:/g)?.length, 1);
    assert.doesNotMatch(pipeline, /merge-approved|\/merge"|merge_when_pipeline_succeeds/);
    for (const file of files.filter((f) => f.path.endsWith('.sh'))) {
      const syntax = spawnSync('bash', ['-n'], { input: file.content, encoding: 'utf8' });
      assert.equal(syntax.status, 0, `${file.path}: ${syntax.stderr}`);
      assert.doesNotMatch(file.content, /@@[A-Z_]+@@/);
    }
    const review = JSON.parse(files.find((f) => f.path.endsWith('fa-review.json'))!.content);
    assert.equal(review.merge, undefined);
    assert.match(review.params.cliPrompts[0], /DMTOOLS_REVIEW_CONTEXT/);
    assert.match(review.params.cliPrompts[0], /request_changes/);
  });

  it('resumes reconciliation on the default branch after the quality job completes', () => {
    const { result, events } = exercise('approve', 'wake');
    assert.equal(result.status, 0, result.stderr);
    const request = events.find((e) => e.command === 'curl')!;
    assert.equal(request.identity, 'dev-token');
    assert.ok(request.args?.includes('ref=main'));
    assert.ok(request.args?.includes('variables[][key]=DMTOOLS_FACTORY_PHASE'));
    assert.ok(request.args?.includes('variables[][value]=reconcile'));
  });

  it('uses Flutter commands for Flutter projects and rejects a mismatched quality preset', () => {
    const flutter = renderGitlabFiles({ ...config, qualityPreset: 'crap4dart' }, templates)
      .find((f) => f.path === '.gitlab-ci.yml')!.content;
    assert.match(flutter, /flutter pub get/);
    assert.match(flutter, /flutter analyze/);
    assert.match(flutter, /flutter test --coverage/);
    assert.match(flutter, /Configured quality gate does not match this repository: missing pubspec.yaml/);
    const withoutGate = renderGitlabFiles({ ...config, qualityPreset: 'none' }, templates);
    assert.match(withoutGate.find((f) => f.path.endsWith('gitlab-factory-lifecycle.sh'))!.content,
      /if \[ 'false' != true \]/);
  });

  it('validates and reviews automatically, tracks jobs, and leaves approval for human merge', () => {
    const { result, state, events } = exercise();
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.validations, 1);
    assert.equal(state.reviews, 1);
    assert.equal(state.reworks, 0);
    assert.ok(state.mrLabels.includes('pr_approved'));
    assert.ok(state.issueLabels.includes('pr_approved'));
    assert.match(state.description, /^# Original issue\n\nKeep these requirements/);
    assert.match(state.description, /## Machine jobs/);
    assert.match(state.description, /\[validation\].*success/);
    assert.match(state.description, /\[review-0\].*success/);
    assert.match(state.description, /started \d\d:\d\d UTC - done \d\d:\d\d UTC success/);
    assert.match(result.stdout, /remains OPEN for a human/);
    const reviewRun = events.find((e) => e.command === 'dmtools');
    assert.equal(reviewRun?.identity, 'review-token');
    const validationRequest = events.find((e) => e.command === 'curl' && e.args?.at(-1)?.endsWith('/pipeline'))!;
    const payload = JSON.parse(validationRequest.args![validationRequest.args!.indexOf('--data') + 1]);
    assert.deepEqual(payload, { ref: 'ai/issue-8-1', variables: [{ key: 'DMTOOLS_FACTORY_PHASE', value: 'validation' }] });
    assert.ok(events.every((e) => !e.args?.at(-1)?.endsWith('/merge')));
  });

  it('releases the runner while validation is pending and resumes without launching duplicate validation', () => {
    const { result, state, pendingState } = exercise('ci-resume');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(pendingState?.reviews, 0);
    assert.equal(pendingState?.validations, 1);
    assert.ok(pendingState);
    assert.match(pendingState.description, /- \[ \] \[validation\]/);
    assert.ok(!pendingState.mrLabels.includes('agent:rework'));
    assert.equal(state.validations, 1);
    assert.equal(state.reviews, 1);
    assert.ok(state.mrLabels.includes('pr_approved'));
    assert.match(state.description, /- \[x\] \[validation\].*success/);
    assert.equal(state.description.match(/dmtools-job:101:0:validation/g)?.length, 1);
  });

  for (const mode of ['review-rework', 'suggestions', 'requested-changes', 'ci-failure-once']) {
    it(`waits for an explicit rework request for ${mode}, including subsequent reconciliation`, () => {
      const { result, state, events } = exercise(mode);
      assert.equal(result.status, 0, result.stderr);
      assert.equal(state.reworks, 0);
      assert.ok(!state.mrLabels.includes('pr_approved'));
      assert.ok(!state.mrLabels.includes('agent:rework'));
      assert.ok(!state.issueLabels.includes('agent:rework'));
      assert.ok(!state.mrLabels.includes('needs-human'));
      assert.ok(state.reviews <= 1);
      assert.match(result.stdout, /agent:rework explicitly/);
      assert.ok(events.every((e) => !(e.args?.includes('POST') && e.args?.at(-1)?.endsWith('/merge_requests'))));
    });
  }

  for (const mode of ['endless-rework', 'ci-always-fails']) {
    it(`does not automatically retry rework for ${mode}`, () => {
      const { result, state } = exercise(mode);
      assert.equal(result.status, 0, result.stderr);
      assert.equal(state.reworks, 0);
      assert.ok(!state.mrLabels.includes('needs-human'));
      assert.ok(!state.mrLabels.includes('pr_approved'));
    });
  }

  for (const [mode, entry] of [
    ['mr-rework', 'intake'], ['issue-rework', 'intake'], ['approve', 'manual-rework'],
  ]) {
    it(`runs one explicitly requested rework for ${mode}/${entry}`, () => {
      const { result, state } = exercise(mode, entry);
      assert.equal(result.status, 0, result.stderr);
      assert.equal(state.reworks, 1);
      assert.equal(state.reviews, 1);
      assert.ok(state.mrLabels.includes('pr_approved'));
      assert.ok(!state.mrLabels.includes('agent:rework'));
      assert.ok(!state.issueLabels.includes('agent:rework'));
      assert.match(state.description, /\[rework-0\].*success/);
    });
  }

  it('consumes an explicit request once even when the new review still requires changes', () => {
    const { result, state } = exercise('explicit-request-changes');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.reworks, 1);
    assert.equal(state.reviews, 1);
    assert.ok(!state.mrLabels.includes('agent:rework'));
    assert.ok(!state.mrLabels.includes('pr_approved'));
    assert.match(result.stdout, /agent:rework explicitly/);
  });

  for (const mode of ['review-contamination', 'real-review-isolation']) {
    it(`isolates review mutations and runtime artifacts from the next rework checkout (${mode})`, () => {
      const { result, events, checkoutPreserved, runtimeLeaked, contextsRemoved } = exercise(mode);
      assert.equal(result.status, 0, result.stderr);
      assert.equal(checkoutPreserved, true);
      assert.equal(runtimeLeaked, false);
      assert.equal(contextsRemoved, true);
      const review = events.find((e) => e.command === 'dmtools')!;
      const worktree = events.find((e) => e.command === 'git' && e.args?.[0] === 'worktree' && e.args[1] === 'add')!;
      assert.equal(review.cwd, worktree.args?.[3]);
      assert.equal(review.identity, 'review-token');
      assert.ok(events.some((e) => e.command === 'git' && e.args?.[1] === 'remove' && e.args[3] === review.cwd));
    });
  }

  it('cleans the isolated worktree when the review agent fails after creating runtime files', () => {
    const { result, events, checkoutPreserved, runtimeLeaked } = exercise('review-contamination-failure');
    assert.notEqual(result.status, 0);
    assert.equal(checkoutPreserved, true);
    assert.equal(runtimeLeaked, false);
    assert.ok(events.some((e) => e.command === 'git' && e.args?.[1] === 'remove'));
    assert.ok(!events.some((e) => e.command === 'publish-review'));
  });

  for (const mode of ['review-context-files', 'review-context-failure']) {
    it(`removes the entire review context without masking lifecycle success or failure (${mode})`, () => {
      const { result, state, events, contextsRemoved, checkoutPreserved } = exercise(mode);
      assert.equal(result.status, mode === 'review-context-failure' ? 1 : 0, result.stderr);
      assert.equal(contextsRemoved, true);
      assert.equal(checkoutPreserved, true, 'Cleanup must not follow symlinks into the orchestration checkout');
      assert.equal(events.filter((e) => e.command === 'publish-review').length, mode === 'review-context-failure' ? 0 : 1);
      assert.equal(state.mrLabels.includes('needs-human'), mode === 'review-context-failure');
      assert.doesNotMatch(result.stderr, /Directory not empty/);
    });
  }

  it('preserves the original agent exit status when cleaning a failed direct review', () => {
    const { result, contextsRemoved, checkoutPreserved } = exercise('review-context-failure', 'review');
    assert.equal(result.status, 7, result.stderr);
    assert.equal(contextsRemoved, true);
    assert.equal(checkoutPreserved, true);
    assert.doesNotMatch(result.stderr, /Directory not empty/);
  });

  for (const mode of ['review-failure', 'invalid-verdict', 'stale-review', 'review-fetch-failure', 'review-fetch-stale', 'wrong-validation-head', 'progress-failure', 'no-progress']) {
    it(`never arms approval on ${mode}`, () => {
      const { result, state } = exercise(mode);
      assert.notEqual(result.status, 0);
      assert.ok(!state.mrLabels.includes('pr_approved'));
      assert.ok(state.mrLabels.includes('needs-human'));
    });
  }

  it('does not re-run a trusted reviewed/validated head on webhook echoes or reconciliation', () => {
    const { result, state } = exercise('approved');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.reviews, 0);
    assert.equal(state.validations, 0);
    assert.ok(state.mrLabels.includes('pr_approved'));
  });

  it('does not trust verdict markers posted by another account', () => {
    const { result, state } = exercise('spoofed');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.reviews, 1);
    assert.equal(state.validations, 1);
  });

  it('honors needs-human stop labels', () => {
    const { result, state } = exercise('stopped');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.reviews, 0);
    assert.equal(state.validations, 0);
  });

  it('records failed job completion without overwriting the issue requirements', () => {
    const { result, state } = exercise('approve', 'job-failure');
    assert.equal(result.status, 9);
    assert.match(state.description, /Keep these requirements/);
    assert.match(state.description, /\[x\] \[dev\].*failure/);
    assert.equal(state.description.match(/dmtools-job:456:789:dev/g)?.length, 1);
  });

  it('records cancelled jobs distinctly from failure', () => {
    const { result, state } = exercise('approve', 'job-cancelled');
    assert.equal(result.status, 143);
    assert.match(state.description, /\[x\] \[dev\].*cancelled/);
  });

  for (const mode of ['intake-dev', 'intake-assigned']) {
    it(`starts review after development publication for ${mode}`, () => {
      const { result, state, events } = exercise(mode, 'intake', {
        object_kind: 'issue', object_attributes: { iid: 8, action: 'open' }, changes: {},
      });
      assert.equal(result.status, 0, result.stderr);
      assert.equal(state.created, true);
      assert.equal(state.reviews, 1);
      assert.match(state.description, /\[dev\].*success/);
      assert.match(state.description, /\[review-0\].*success/);
      assert.equal(events.filter((e) => e.args?.includes('POST') && e.args?.at(-1)?.endsWith('/merge_requests')).length, 1);
      const dev = events.find((e) => e.devRequest !== undefined)!;
      assert.match(dev.devRequest!, /^Implement GitLab issue #8: Money counter/);
      assert.match(dev.devRequest!, /Keep these requirements\./);
      assert.doesNotMatch(dev.devRequest!, /Machine jobs|dmtools-job/);
      assert.ok(!dev.devArgs!.includes('--inputTicketId'), 'GitLab IIDs must not be resolved through another tracker');
    });
  }

  it('publishes agent code but not installer-generated .cursor/skills changes (real Git)', () => {
    const { result, state, published } = exercise('real-intake', 'intake', {
      object_kind: 'issue', object_attributes: { iid: 8, action: 'open' }, changes: {},
    });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.created, true);
    assert.deepEqual(published, ['lib/main.dart']);
  });

  for (const mode of ['intake-blocked', 'real-intake-installer-only']) {
    it(`does not open an MR when development produces no issue work (${mode})`, () => {
      const { result, state, events } = exercise(mode, 'intake', {
        object_kind: 'issue', object_attributes: { iid: 8, action: 'open' }, changes: {},
      });
      assert.notEqual(result.status, 0);
      assert.equal(state.created, false);
      assert.ok(!events.some((e) => e.args?.includes('POST') && e.args?.at(-1)?.endsWith('/merge_requests')));
      assert.ok(state.issueLabels.includes('needs-human'));
      assert.match(result.stderr, mode === 'intake-blocked' ? /missing input or a blocker/ : /without repository changes/);
    });
  }

  for (const changes of [
    { description: { previous: 'old', current: 'progress' }, updated_at: {} },
    { labels: { previous: [{ title: 'agent:dev' }], current: [{ title: 'ai_developed' }] } },
  ]) {
    it('ignores progress and status-only issue webhook echoes', () => {
      const { result, state, events } = exercise('approve', 'intake', {
        object_kind: 'issue', object_attributes: { iid: 8, action: 'update' }, changes,
      });
      assert.equal(result.status, 0, result.stderr);
      assert.equal(state.reviews, 0);
      assert.equal(events.filter((e) => e.command === 'dmtools').length, 0);
      assert.match(result.stdout, /Ignoring factory progress/);
    });
  }

  it('starts the MR lifecycle for MR webhook events without creating another MR', () => {
    const { result, state, events } = exercise('approve', 'intake', {
      object_kind: 'merge_request', object_attributes: { iid: 36, action: 'open' }, changes: {},
    });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.reviews, 1);
    assert.ok(events.every((e) => !(e.args?.includes('POST') && e.args?.at(-1)?.endsWith('/merge_requests'))));
  });

  it('ignores status-only MR label webhook echoes', () => {
    const { result, state } = exercise('approve', 'intake', {
      object_kind: 'merge_request', object_attributes: { iid: 36, action: 'update' },
      changes: { labels: { previous: [], current: [{ title: 'pr_approved' }] } },
    });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(state.reviews, 0);
    assert.match(result.stdout, /Ignoring factory progress/);
  });
});

describe('GitLab factory schedule', () => {
  it('creates then updates only its managed schedule', async () => {
    const original = globalThis.fetch;
    const calls: { url: string; method: string; body?: Record<string, unknown> }[] = [];
    let created = false;
    let marked = false;
    globalThis.fetch = (async (input, init) => {
      const url = String(input), method = init?.method ?? 'GET';
      calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (url.includes('/hooks?') || url.includes('/triggers?')) return Response.json([]);
      if (url.endsWith('/api/v4/user')) return Response.json({ id: 7 });
      if (url.endsWith('/pipeline_schedules/1') && method === 'GET') {
        return Response.json({ variables: marked ? [{ key: 'DMTOOLS_FACTORY_SCHEDULE', value: 'true' }] : [] });
      }
      if (url.endsWith('/pipeline_schedules/1/variables') && method === 'POST') {
        marked = true;
        return Response.json({ key: 'DMTOOLS_FACTORY_SCHEDULE', value: 'true' });
      }
      if (method === 'GET') return Response.json([
        { id: 99, description: 'Unrelated schedule' },
        ...(created ? [{ id: 1, description: 'DMTools factory reconciliation', owner: { id: 7 } }] : []),
      ]);
      created = true;
      return Response.json({ id: 1 });
    }) as typeof fetch;
    try {
      await ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main');
      await ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main');
      assert.equal(calls.filter((c) => c.method === 'POST').length, 2);
      assert.ok(calls.some((c) => c.method === 'POST' && c.body?.key === 'DMTOOLS_FACTORY_SCHEDULE'));
      const update = calls.find((c) => c.method === 'PUT')!;
      assert.match(update.url, /pipeline_schedules\/1$/);
      assert.equal(update.body?.cron, '*/10 * * * *');
      assert.equal(update.body?.active, true);
      assert.ok(!calls.some((c) => c.url.endsWith('/99')));
      assert.ok(!calls.some((c) => c.url.endsWith('/take_ownership')));
    } finally { globalThis.fetch = original; }
  });

  it('creates the schedule before removing only legacy factory hooks and trigger tokens', async () => {
    const original = globalThis.fetch;
    const calls: { url: string; method: string }[] = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input), method = init?.method ?? 'GET';
      calls.push({ url, method });
      if (url.includes('/pipeline_schedules?')) return Response.json([]);
      if (url.endsWith('/pipeline_schedules/5') && method === 'GET') return Response.json({ variables: [] });
      if (url.includes('/hooks?')) return Response.json([
        { id: 1, url: 'https://gitlab.example.com/api/v4/projects/123/trigger/pipeline?variables%5BDMTOOLS_FACTORY_TRIGGER%5D=issue_webhook' },
        { id: 2, url: 'https://other.example.com/notification' },
      ]);
      if (url.includes('/triggers?')) return Response.json([
        { id: 3, description: 'DMTools factory issue webhook' },
        { id: 4, description: 'Unrelated trigger' },
      ]);
      return Response.json({ id: 5 });
    }) as typeof fetch;
    try {
      await ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main');
      const writes = calls.filter((c) => c.method !== 'GET');
      assert.deepEqual(writes.map((c) => [c.method, new URL(c.url).pathname]), [
        ['POST', '/api/v4/projects/team%2Fapp/pipeline_schedules'],
        ['POST', '/api/v4/projects/team%2Fapp/pipeline_schedules/5/variables'],
        ['DELETE', '/api/v4/projects/team%2Fapp/hooks/1'],
        ['DELETE', '/api/v4/projects/team%2Fapp/triggers/3'],
      ]);
    } finally { globalThis.fetch = original; }
  });

  it('does not remove webhooks if the replacement schedule cannot be created', async () => {
    const original = globalThis.fetch;
    const calls: string[] = [];
    globalThis.fetch = (async (input, init) => {
      calls.push(String(input));
      return init?.method === 'POST'
        ? new Response('Schedule access denied', { status: 403 })
        : Response.json([]);
    }) as typeof fetch;
    try {
      await assert.rejects(
        ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main'), /403/);
      assert.ok(calls.every((url) => url.includes('/pipeline_schedules')));
    } finally { globalThis.fetch = original; }
  });

  it('takes ownership of a schedule owned by another account before updating it', async () => {
    const original = globalThis.fetch;
    const calls: { url: string; method: string }[] = [];
    let owned = false;
    globalThis.fetch = (async (input, init) => {
      const url = String(input), method = init?.method ?? 'GET';
      calls.push({ url, method });
      if (url.includes('/hooks?') || url.includes('/triggers?')) return Response.json([]);
      if (url.endsWith('/api/v4/user')) return Response.json({ id: 7 });
      if (url.includes('/pipeline_schedules?')) return Response.json([{ id: 1, description: 'DMTools factory reconciliation', owner: { id: 3 } }]);
      if (url.endsWith('/take_ownership')) { owned = true; return Response.json({ id: 1 }); }
      if (method === 'PUT' && !owned) return new Response('403 Forbidden', { status: 403 });
      if (url.endsWith('/pipeline_schedules/1') && method === 'GET') return Response.json({ variables: [] });
      return Response.json({ id: 1 });
    }) as typeof fetch;
    try {
      await ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main');
      const writes = calls.filter((c) => c.method !== 'GET').map((c) => [c.method, new URL(c.url).pathname]);
      assert.deepEqual(writes, [
        ['POST', '/api/v4/projects/team%2Fapp/pipeline_schedules/1/take_ownership'],
        ['PUT', '/api/v4/projects/team%2Fapp/pipeline_schedules/1'],
        ['POST', '/api/v4/projects/team%2Fapp/pipeline_schedules/1/variables'],
      ]);
    } finally { globalThis.fetch = original; }
  });

  it('updates an existing factory schedule marker when its value is wrong', async () => {
    const original = globalThis.fetch;
    const calls: { url: string; method: string; body?: Record<string, unknown> }[] = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input), method = init?.method ?? 'GET';
      calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (url.includes('/hooks?') || url.includes('/triggers?')) return Response.json([]);
      if (url.endsWith('/api/v4/user')) return Response.json({ id: 7 });
      if (url.includes('/pipeline_schedules?')) return Response.json([{ id: 1, description: 'DMTools factory reconciliation', owner: { id: 7 } }]);
      if (url.endsWith('/pipeline_schedules/1') && method === 'GET') {
        return Response.json({ variables: [{ key: 'DMTOOLS_FACTORY_SCHEDULE', value: 'false' }] });
      }
      return Response.json({ id: 1 });
    }) as typeof fetch;
    try {
      await ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main');
      const variable = calls.find((call) => call.url.endsWith('/variables/DMTOOLS_FACTORY_SCHEDULE'));
      assert.equal(variable?.method, 'PUT');
      assert.equal(variable?.body?.value, 'true');
    } finally { globalThis.fetch = original; }
  });

  it('converts an existing file-type factory schedule marker to an environment variable', async () => {
    const original = globalThis.fetch;
    const calls: { url: string; method: string; body?: Record<string, unknown> }[] = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input), method = init?.method ?? 'GET';
      calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (url.includes('/hooks?') || url.includes('/triggers?')) return Response.json([]);
      if (url.endsWith('/api/v4/user')) return Response.json({ id: 7 });
      if (url.includes('/pipeline_schedules?')) return Response.json([{ id: 1, description: 'DMTools factory reconciliation', owner: { id: 7 } }]);
      if (url.endsWith('/pipeline_schedules/1') && method === 'GET') {
        return Response.json({ variables: [{ key: 'DMTOOLS_FACTORY_SCHEDULE', value: 'true', variable_type: 'file' }] });
      }
      return Response.json({ id: 1 });
    }) as typeof fetch;
    try {
      await ensureGitlabFactorySchedule('token', 'team/app', 'https://gitlab.example.com', 'main');
      const variable = calls.find((call) => call.url.endsWith('/variables/DMTOOLS_FACTORY_SCHEDULE'));
      assert.equal(variable?.method, 'PUT');
      assert.equal(variable?.body?.variable_type, 'env_var');
    } finally { globalThis.fetch = original; }
  });
});
