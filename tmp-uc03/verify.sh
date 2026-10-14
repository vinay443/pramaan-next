#!/usr/bin/env bash
# Read-only UC03 verification (SELECT only). Usage: bash verify.sh
Q() { docker exec -i pramaan-pgvector psql -U pramaan -d pramaan_vectors -X -A -F ' | ' -P footer=off -v ON_ERROR_STOP=1 "$@"; }
M="r3mv7mgsjz"
echo "=== RECORDS ==="
Q -c "select r.evidence_key, r.title, r.current_version as cur_ver, r.lifecycle_state as state
from evidence_record r
where r.evidence_key like '%$M%' or r.title like '%$M%'
   or exists (select 1 from evidence_tag t where t.evidence_record_id=r.id and t.tag_key='sourceTitle' and t.tag_value like '%$M%')
order by r.evidence_key"
echo "=== TAGS ==="
Q -c "select r.evidence_key, t.tag_key, t.tag_value
from evidence_record r join evidence_tag t on t.evidence_record_id=r.id
where (r.evidence_key like '%$M%' or r.title like '%$M%'
   or exists (select 1 from evidence_tag s where s.evidence_record_id=r.id and s.tag_key='sourceTitle' and s.tag_value like '%$M%'))
order by r.evidence_key, t.tag_key"
echo "=== VERSIONS ==="
Q -c "select r.evidence_key, v.version_number, v.sha256, v.size_bytes, v.collected_at
from evidence_record r join evidence_version v on v.evidence_record_id=r.id
where (r.evidence_key like '%$M%' or r.title like '%$M%'
   or exists (select 1 from evidence_tag s where s.evidence_record_id=r.id and s.tag_key='sourceTitle' and s.tag_value like '%$M%'))
order by r.evidence_key, v.version_number"
echo "=== REJECTED-INPUT ROW COUNT (expect 0) ==="
Q -c "select count(*) as rejected_rows from evidence_record r
where r.title ~* '\.exe\$' or r.evidence_key ~* '\.exe\$' or r.title ~* '\.pdf\.exe' or r.evidence_key ~* '\.pdf\.exe'
   or r.title ilike '%uc03ft004noext%' or r.evidence_key ilike '%uc03ft004noext%'
   or length(r.title) >= 260 or r.title like '..%' or r.evidence_key like '..%'
   or exists (select 1 from evidence_tag s where s.evidence_record_id=r.id and s.tag_key='sourceTitle'
        and (s.tag_value ~* '\.exe\$' or s.tag_value ilike '%uc03ft004noext%' or length(s.tag_value)>=260 or s.tag_value like '..%'))"
