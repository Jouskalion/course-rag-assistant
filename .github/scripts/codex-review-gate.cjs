'use strict';

const CHECK_NAME = 'codex-review';
const BOT_ID = 199175422;
const APP_ID = 1144995;
const BOT_LOGIN = 'chatgpt-codex-connector[bot]';
const SUMMARY = '<!-- codex-pull-request-review-summary -->';

const block = (reason) => ({ conclusion: 'failure', reason });

function trustedAuthor(comment) {
  return comment?.user?.id === BOT_ID && comment.user.type === 'Bot'
    && comment.user.login === BOT_LOGIN
    && comment.performed_via_github_app?.id === APP_ID;
}

function isCodexActor(actor) {
  return actor?.__typename === 'Bot' && actor.login === 'chatgpt-codex-connector';
}

// The native Codex comment format is not an API contract. Unknown formats block
// merging until this adapter is updated; silence must never become approval.
function evaluate({ headSha, draft, comment, node }) {
  if (draft) return block('PR 仍为草稿，请先标记为可以审查。');
  if (!comment) return block('等待 Codex 发布当前提交的审查结果。');
  if (!trustedAuthor(comment)) return block('审查反馈并非来自已验证的 Codex 应用。');
  if (!node || !isCodexActor(node.author)
    || (node.lastEditedAt && !isCodexActor(node.editor))) {
    return block('无法验证审查反馈的作者或最后编辑者，请重新运行 Codex 审查。');
  }
  if (node.body !== comment.body) return block('审查结果正在更新，等待下一次检查。');
  const body = comment.body || '';
  const markers = [...body.matchAll(/<!-- codex-review-state:v1 ([^\r\n]*?) -->/g)];
  if (!body.includes(SUMMARY) || markers.length !== 1
    || (body.match(/<!-- codex-review-state:/g) || []).length !== 1) {
    return block('无法识别唯一的 Codex 审查结果，不能自动放行。');
  }
  let review;
  try { review = JSON.parse(markers[0][1]).code_review; }
  catch { return block('Codex 审查结果格式无效，不能自动放行。'); }
  if (!review || review.commit_sha !== headSha) {
    return block('等待 Codex 审查当前最新提交，旧版本结果不能用于合并。');
  }
  if (review.status !== 'completed') {
    return block(`Codex 审查尚未成功完成（${String(review.status)}），暂不允许合并。`);
  }
  // Requiring a fresh clean review also prevents resolving a thread by hand
  // from turning a reported finding into a passing check.
  if (review.has_any_findings !== false || review.has_unresolved_findings !== false) {
    return block('Codex 仍报告问题或结果不完整；修复后等待新提交的审查通过。');
  }
  return { conclusion: 'success', reason: 'Codex 已完成当前最新提交的代码审查，未报告问题。' };
}

async function run({ github, context, core }) {
  const { owner, repo } = context.repo;
  const rawNumber = context.payload.pull_request?.number
    ?? context.payload.issue?.number ?? context.payload.inputs?.pr_number;
  const pull_number = Number(rawNumber);
  if (!Number.isSafeInteger(pull_number) || pull_number < 1) {
    throw new Error('A valid pull request number is required.');
  }
  const { data: pull } = await github.rest.pulls.get({ owner, repo, pull_number });
  if (pull.state !== 'open' || pull.base.ref !== 'main') {
    core.info('Only open pull requests targeting main require this gate.');
    return;
  }
  const sha = pull.head.sha;
  const runUrl = `${context.serverUrl}/${owner}/${repo}/actions/runs/${context.runId}`;
  const status = {
    owner, repo, sha, context: CHECK_NAME, target_url: runUrl,
  };
  // Commit statuses are attached directly to this SHA and context, independent
  // of workflow check suites. Repeated PR, comment, and manual events therefore
  // replace the same required status instead of leaving an old suite selected.
  // A cancelled run or API failure leaves pending/failure, never approval.
  await github.rest.repos.createCommitStatus({
    ...status, state: 'pending', description: '正在核对当前提交的 AI 审查，暂不允许合并。',
  });

  let result;
  let comment;
  try {
    const comments = await github.paginate(github.rest.issues.listComments, {
      owner, repo, issue_number: pull_number, per_page: 100,
    });
    // A human copying a summary cannot hide or replace an authentic report.
    comment = comments.filter(c => trustedAuthor(c) && (c.body || '').includes(SUMMARY))
      .sort((a, b) => b.id - a.id)[0];
    let node;
    if (comment) {
      ({ node } = await github.graphql(`query($id: ID!) {
        node(id: $id) { ... on IssueComment {
          body lastEditedAt author { login __typename } editor { login __typename }
        } }
      }`, { id: comment.node_id }));
    }
    result = evaluate({ headSha: sha, draft: pull.draft, comment, node });
    const { data: latest } = await github.rest.pulls.get({ owner, repo, pull_number });
    if (latest.state !== 'open' || latest.base.ref !== 'main'
      || latest.head.sha !== sha || latest.draft) {
      result = block('PR 状态或提交已变化，请等待最新版本的检查。');
    }
  } catch (error) {
    core.setFailed('无法读取或验证 Codex 审查结果。修复后重新运行此工作流。');
    core.warning(`GitHub API error: ${error.status || 'unknown'}`);
    result = block('审查结果校验发生错误，已阻止合并。');
  }
  await github.rest.repos.createCommitStatus({
    ...status, state: result.conclusion === 'success' ? 'success' : 'failure',
    description: result.reason.slice(0, 140),
  });
  core.info(`Current commit: ${sha}`);
  if (comment) core.info(`Codex feedback: ${comment.html_url}`);
  core.info(result.reason);
}

module.exports = { evaluate, run };
