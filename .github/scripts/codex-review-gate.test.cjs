'use strict';

// These tests validate merge enforcement only, not the deferred application tests.
const test = require('node:test');
const assert = require('node:assert/strict');
const { evaluate, run } = require('./codex-review-gate.cjs');

const SHA = 'a'.repeat(40);
function fixture(review = {}) {
  const state = { code_review: {
    commit_sha: SHA, status: 'completed', has_any_findings: false,
    has_unresolved_findings: false, ...review,
  } };
  const body = '<!-- codex-pull-request-review-summary -->\n'
    + `<!-- codex-review-state:v1 ${JSON.stringify(state)} -->`;
  return {
    headSha: SHA, draft: false,
    comment: {
      id: 100, node_id: 'trusted-comment', body,
      html_url: 'https://github.com/example/project/pull/1#issuecomment-100',
      user: { id: 199175422, login: 'chatgpt-codex-connector[bot]', type: 'Bot' },
      performed_via_github_app: { id: 1144995 },
    },
    node: {
      body, lastEditedAt: '2026-11-17T11:39:03Z',
      author: { login: 'chatgpt-codex-connector', __typename: 'Bot' },
      editor: { login: 'chatgpt-codex-connector', __typename: 'Bot' },
    },
  };
}

test('only a completed, authentic, clean review of the current commit passes', () => {
  assert.equal(evaluate(fixture()).conclusion, 'success');
});

test('old, missing, running, draft, failed, and cancelled reviews block merging', () => {
  for (const data of [
    fixture({ commit_sha: 'b'.repeat(40) }),
    { ...fixture(), comment: undefined }, { ...fixture(), draft: true },
    fixture({ status: 'running' }), fixture({ status: 'failed' }),
    fixture({ status: 'cancelled' }),
  ]) assert.equal(evaluate(data).conclusion, 'failure');
});

test('findings still block after someone marks all threads resolved', () => {
  for (const changes of [
    { has_any_findings: true, has_unresolved_findings: true },
    { has_any_findings: true, has_unresolved_findings: false },
    { has_any_findings: false, has_unresolved_findings: true },
    { has_any_findings: null }, { has_unresolved_findings: null },
  ]) assert.equal(evaluate(fixture(changes)).conclusion, 'failure');
});

test('copied reports, another app, and a human editing the bot report cannot pass', () => {
  const changes = [
    x => { x.comment.user.id = 1; },
    x => { x.comment.user.type = 'User'; },
    x => { x.comment.user.login = 'another-bot[bot]'; },
    x => { x.comment.performed_via_github_app.id = 1; },
    x => { x.node.author.login = 'another-bot'; },
    x => { x.node.editor = { login: 'maintainer', __typename: 'User' }; },
    x => { x.node.editor = null; },
    x => { x.node = undefined; },
  ];
  for (const change of changes) {
    const data = fixture(); change(data);
    assert.equal(evaluate(data).conclusion, 'failure');
  }
});

test('an unedited original bot comment is allowed', () => {
  const data = fixture(); data.node.lastEditedAt = null; data.node.editor = null;
  assert.equal(evaluate(data).conclusion, 'success');
});

test('unknown, malformed, and ambiguous report formats block merging', () => {
  for (const change of [
    body => body.replace('v1', 'v2'),
    body => body.replace('"code_review":', 'invalid:'),
    body => body + '\n' + body,
    body => body.replace('code_review', 'security_review'),
  ]) {
    const data = fixture(); data.comment.body = change(data.comment.body);
    data.node.body = data.comment.body;
    assert.equal(evaluate(data).conclusion, 'failure');
  }
});

test('a concurrent comment edit cannot pass using mismatched API snapshots', () => {
  const data = fixture(); data.node.body += '\nchanged';
  assert.equal(evaluate(data).conclusion, 'failure');
});

function harness({ latestSha = SHA, apiFails = false } = {}) {
  const data = fixture();
  const writes = [];
  let reads = 0;
  let failed = false;
  const listComments = () => {};
  const github = {
    rest: {
      pulls: { get: async () => ({ data: {
        state: 'open', draft: false, base: { ref: 'main' },
        head: { sha: ++reads === 1 ? SHA : latestSha },
      } }) },
      issues: { listComments },
      checks: {
        create: async params => { writes.push(params); return { data: { id: 42 } }; },
        update: async params => { writes.push(params); return { data: { id: 42 } }; },
      },
    },
    paginate: async method => {
      assert.equal(method, listComments);
      if (apiFails) throw Object.assign(new Error('Unavailable'), { status: 503 });
      // A newer human-written copy must never replace the real review.
      return [data.comment, { ...data.comment, id: 101, user: { id: 1, type: 'User' } }];
    },
    graphql: async () => ({ node: data.node }),
  };
  const context = {
    repo: { owner: 'example', repo: 'project' }, payload: { inputs: { pr_number: '1' } },
    serverUrl: 'https://github.com', runId: 123, runAttempt: 1,
  };
  const core = { info() {}, warning() {}, setFailed() { failed = true; } };
  return { github, context, core, writes, failed: () => failed };
}

test('every workflow invocation publishes its own check before passing the verified head', async () => {
  const ids = [];
  for (const runId of [123, 124]) {
    const h = harness(); h.context.runId = runId; await run(h);
    assert.equal(h.writes[0].conclusion, 'failure');
    assert.equal(h.writes[1].conclusion, 'success');
    assert.equal(h.writes[1].check_run_id, 42);
    assert.equal(h.writes[0].head_sha, SHA);
    assert.equal(h.writes[0].check_run_id, undefined);
    ids.push(h.writes[0].external_id);
  }
  assert.notEqual(ids[0], ids[1]);
});

test('a push during verification cannot receive an obsolete passing result', async () => {
  const h = harness({ latestSha: 'b'.repeat(40) }); await run(h);
  assert.ok(h.writes.every(write => write.conclusion === 'failure'));
});

test('an API outage leaves an explicitly failing check and a failed workflow', async () => {
  const h = harness({ apiFails: true }); await run(h);
  assert.ok(h.writes.every(write => write.conclusion === 'failure'));
  assert.equal(h.failed(), true);
});
