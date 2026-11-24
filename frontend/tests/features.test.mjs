import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { rolesFromJwt, allowsRole } from '../src/app/core/access.ts';
import { profileProgress, takeSseEvents } from '../src/app/core/sse.ts';
import { confidenceClass, sourceReference } from '../src/app/features/qa/qa-view.ts';
import { impactedPaths, orderedTimeline } from '../src/app/features/traceability/traceability-view.ts';

function tokenWithAuthorities(authorities) {
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url');
  return `header.${payload}.signature`;
}

test('auth store role selectors read only supported JWT authorities', () => {
  const roles = rolesFromJwt(tokenWithAuthorities(['ROLE_USER', 'ROLE_ADMIN', 'SOMETHING_ELSE']));
  assert.deepEqual(roles, ['ROLE_USER', 'ROLE_ADMIN']);
  assert.equal(allowsRole(rolesFromJwt(tokenWithAuthorities(['ROLE_USER'])), 'ROLE_ADMIN'), false);
  assert.equal(allowsRole(roles, 'ROLE_USER'), true);
  assert.deepEqual(rolesFromJwt('invalid'), []);
});

test('SSE client parser handles multi-event and CRLF chunks', () => {
  const parsed = takeSseEvents('event: answer\r\ndata: {"answer":"a"}\r\n\r\nevent: answer\ndata: {"answer":"b"}\n\npartial');
  assert.deepEqual(parsed.events, ['{"answer":"a"}', '{"answer":"b"}']);
  assert.equal(parsed.remainder, 'partial');
});

test('profiling store progress and QA source presentation preserve useful status', () => {
  assert.equal(profileProgress('experience', false), 0);
  assert.equal(profileProgress('riskTolerance', false), 83);
  assert.equal(profileProgress(null, true), 100);
  const answer = { confidence: 'LOW' };
  assert.equal(confidenceClass(answer), 'low');
  assert.equal(sourceReference('src/Rule.java', 3, 12), 'src/Rule.java · p.3 · ligne 12');
});

test('traceability store view orders issue to file and de-duplicates impacted paths', () => {
  const entities = [
    { sourceType: 'github-file', title: 'Rule.java' },
    { sourceType: 'github-commit', title: 'commit' },
    { sourceType: 'github-issue', title: 'issue' },
    { sourceType: 'github-pr', title: 'pr' }
  ];
  assert.deepEqual(orderedTimeline(entities).map(item => item.sourceType), ['github-issue', 'github-pr', 'github-commit', 'github-file']);
  assert.deepEqual(impactedPaths([{ path: 'b.java' }, { path: 'a.java' }, { path: 'b.java' }, { path: '' }]), ['a.java', 'b.java']);
});

test('key page templates expose accessible labels and empty/error status regions', async () => {
  const qa = await readFile(new URL('../src/app/features/qa/qa.page.ts', import.meta.url), 'utf8');
  const login = await readFile(new URL('../src/app/features/auth/login.page.ts', import.meta.url), 'utf8');
  const profiling = await readFile(new URL('../src/app/features/profiling/profiling.page.ts', import.meta.url), 'utf8');
  assert.match(qa, /mat-label>Votre question/);
  assert.match(qa, /aria-live="polite"/);
  assert.match(login, /autocomplete="username"/);
  assert.match(profiling, /aria-label="Progression de l'entretien"/);
  assert.match(profiling, /role="alert"/);
});
