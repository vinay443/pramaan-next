const fs = require('fs');
const M = process.argv[2];
const db = JSON.parse(fs.readFileSync('db.json', 'utf8'));
const api = fs.readFileSync('api-items.ndjson', 'utf8').split('\n').filter(Boolean).map(JSON.parse);
// Normalisation: tags sorted by key; timestamps -> epoch millis (UTC, truncated to ms); null/undefined -> null; sizeBytes -> Number
const ts = s => s == null ? null : Math.floor(Date.parse(/Z|[+-]\d\d:?\d\d$/.test(s) ? s : s + 'Z'));
const norm = r => ({
  evidenceId: r.evidenceId, evidenceKey: r.evidenceKey, applicationSlug: r.applicationSlug, controlId: r.controlId,
  framework: r.framework, title: r.title ?? null, currentVersion: r.currentVersion, lifecycleState: r.lifecycleState,
  tags: Object.fromEntries(Object.entries(r.tags || {}).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([k, v]) => [k, v ?? null])),
  latest: r.latest ? { sha256: r.latest.sha256, sizeBytes: Number(r.latest.sizeBytes), contentType: r.latest.contentType, collectedAt: ts(r.latest.collectedAt) } : null,
});
const D = new Map(db.map(r => [r.evidenceId, norm(r)]));
const A = new Map(api.map(r => [r.evidenceId, norm(r)]));
const out = [];
out.push(`Normalisation: tags sorted by key; collectedAt compared as UTC epoch ms (tz/precision ignored); sizeBytes as number; null==missing.`);
out.push(`DB records: ${D.size} | API records: ${A.size} (API rows incl. duplicates: ${api.length})`);
const onlyDb = [...D.keys()].filter(k => !A.has(k)), onlyApi = [...A.keys()].filter(k => !D.has(k));
out.push(`Only in DB (${onlyDb.length}): ${onlyDb.join(', ') || '-'}`);
out.push(`Only in API (${onlyApi.length}): ${onlyApi.join(', ') || '-'}`);
const diffs = [];
const cmp = (id, f, a, b) => { if (JSON.stringify(a) !== JSON.stringify(b)) diffs.push(`${id} | ${f} | DB=${JSON.stringify(a)} | API=${JSON.stringify(b)}`); };
for (const [id, d] of D) {
  const a = A.get(id); if (!a) continue;
  for (const f of ['evidenceKey', 'applicationSlug', 'controlId', 'framework', 'title', 'currentVersion', 'lifecycleState']) cmp(id, f, d[f], a[f]);
  for (const k of new Set([...Object.keys(d.tags), ...Object.keys(a.tags)])) cmp(id, `tags.${k}`, d.tags[k] ?? null, a.tags[k] ?? null);
  for (const f of ['sha256', 'sizeBytes', 'contentType', 'collectedAt']) cmp(id, `latest.${f}`, d.latest?.[f] ?? null, a.latest?.[f] ?? null);
}
out.push(`Field differences: ${diffs.length}`);
diffs.slice(0, 200).forEach(x => out.push('  ' + x));
out.push(diffs.length === 0 && !onlyDb.length && !onlyApi.length ? 'RESULT: PASS' : 'RESULT: FAIL');
out.push('', `Records matching ${M} (from DB):`, 'title | ver | env | evidenceType | technology | sha256[:12]');
[...D.values()].filter(r => r.evidenceKey.includes(M) || (r.title || '').includes(M) || (r.tags.sourceTitle || '').includes(M))
  .sort((a, b) => a.title < b.title ? -1 : 1)
  .forEach(r => out.push([r.title, 'v' + r.currentVersion, r.tags.environment ?? '-', r.tags.evidenceType ?? '-', r.tags.technology ?? '-', r.latest?.sha256.slice(0, 12)].join(' | ')));
console.log(out.join('\n'));
