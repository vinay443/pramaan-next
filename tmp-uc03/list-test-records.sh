#!/usr/bin/env bash
# READ-ONLY (SELECT only): lists UC03 test evidence for review before manual deletion. Deletes nothing.
Q() { docker exec -i pramaan-pgvector psql -U pramaan -d pramaan_vectors -X -A -F ' | ' -P footer=off -v ON_ERROR_STOP=1 "$@"; }
PAT="uc03-|mv6k5qyh|mv6lcmz9|r3mv7mgsjz|mv7o2q9a|uc03-ft015-|uc03-r4"
WHERE="(r.evidence_key ~* '$PAT' or coalesce(r.title,'') ~* '$PAT' or coalesce(r.source_object_id,'') ~* '$PAT'
  or exists (select 1 from evidence_tag s where s.evidence_record_id=r.id and s.tag_key='sourceTitle' and s.tag_value ~* '$PAT'))"
echo "=== matching records ==="
Q -c "select r.id, r.evidence_key, r.title, r.current_version as ver, r.lifecycle_state as state, r.created_at,
  (select count(*) from evidence_version v where v.evidence_record_id=r.id) as versions,
  (select tag_value from evidence_tag t where t.evidence_record_id=r.id and t.tag_key='batchId') as batch_id
from evidence_record r where $WHERE order by r.created_at"
echo "=== summary ==="
Q -c "select count(*) as records, coalesce(sum((select count(*) from evidence_version v where v.evidence_record_id=r.id)),0) as versions,
  min(r.created_at) as first, max(r.created_at) as last from evidence_record r where $WHERE"
echo "=== by application ==="
Q -c "select r.application_slug, count(*) from evidence_record r where $WHERE group by 1 order by 2 desc"
echo "=== referenced elsewhere (would block a plain delete) ==="
Q -c "select 'audit_observation' as tbl, count(*) from audit_observation o join evidence_record r on r.id=o.evidence_id where $WHERE
union all select 'check_result', count(*) from check_result c join evidence_record r on r.id=c.evidence_record_id where $WHERE
union all select 'lifecycle_event', count(*) from evidence_lifecycle_event e join evidence_record r on r.id=e.evidence_record_id where $WHERE"
echo "=== bulk batches (scheduler_run) touched by these records ==="
Q -c "select distinct t.tag_value as batch_id, sr.status, sr.started_at from evidence_tag t join evidence_record r on r.id=t.evidence_record_id
  join scheduler_run sr on sr.id::text=t.tag_value where t.tag_key='batchId' and $WHERE order by sr.started_at"
