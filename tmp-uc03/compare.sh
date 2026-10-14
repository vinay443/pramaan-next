#!/usr/bin/env bash
# Read-only DB vs API comparison (SELECT + GET only). Output: compare-result.txt
cd "$(dirname "$0")"
M="r3mv7mgsjz"
docker exec -i pramaan-pgvector psql -U pramaan -d pramaan_vectors -X -A -t -v ON_ERROR_STOP=1 -c "
select coalesce(json_agg(x),'[]'::json) from (
 select r.id as \"evidenceId\", r.evidence_key as \"evidenceKey\", r.application_slug as \"applicationSlug\",
  r.control_id as \"controlId\", r.framework, r.title, r.current_version as \"currentVersion\", r.lifecycle_state as \"lifecycleState\",
  (select coalesce(json_object_agg(t.tag_key,t.tag_value),'{}'::json) from evidence_tag t where t.evidence_record_id=r.id) as tags,
  (select json_build_object('sha256',v.sha256,'sizeBytes',v.size_bytes,'contentType',v.content_type,
          'collectedAt',to_char(v.collected_at at time zone 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US'))
     from evidence_version v where v.evidence_record_id=r.id and v.version_number=r.current_version) as latest
 from evidence_record r) x" > db.json
: > api-items.ndjson
page=0; total=1
while [ "$page" -lt "$total" ]; do
  curl -s -m 60 "http://localhost:8080/api/v1/evidence?size=500&page=$page" > api-page.json
  total=$(node -e 'console.log(JSON.parse(require("fs").readFileSync("api-page.json","utf8")).totalPages)')
  node -e 'const o=JSON.parse(require("fs").readFileSync("api-page.json","utf8"));for(const i of o.items)console.log(JSON.stringify(i))' >> api-items.ndjson
  page=$((page+1))
done
node compare.js "$M"
