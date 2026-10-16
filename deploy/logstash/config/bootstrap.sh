#!/usr/bin/env bash
set -euo pipefail
# Fixed internal endpoints: never use the host-JAR DSS_ES_URIS=127.0.0.1 here.
: "${DSS_ES_PASSWORD:?}"
es=http://es:9200
auth="elastic:${DSS_ES_PASSWORD}"
curl_args=(--silent --show-error --connect-timeout 5 --max-time 30 --user "$auth")

for attempt in {1..60}; do
  if curl "${curl_args[@]}" --fail "$es/_cluster/health?wait_for_status=yellow&timeout=5s" >/dev/null; then
    break
  fi
  if [[ "$attempt" == 60 ]]; then
    echo 'Elasticsearch not ready; refusing to start JDBC before template/index initialization.' >&2
    exit 1
  fi
  sleep 2
done

for kind in product shop; do
  # Always install explicit composable templates, even on an empty source database.
  curl "${curl_args[@]}" --fail-with-body -X PUT \
    -H 'Content-Type: application/json' \
    --data-binary "@/usr/share/logstash/templates/${kind}.json" \
    "$es/_index_template/dss_${kind}" >/dev/null
done

for index in dss_product dss_shop; do
  code=$(curl "${curl_args[@]}" -o /tmp/tikcookie-index-response.json -w '%{http_code}' -X PUT "$es/$index")
  if [[ "$code" != 200 ]] && ! { [[ "$code" == 400 ]] && grep -q 'resource_already_exists_exception' /tmp/tikcookie-index-response.json; }; then
    echo "Index initialization failed: $index (HTTP $code). No existing index was changed or removed." >&2
    cat /tmp/tikcookie-index-response.json >&2
    exit 1
  fi
done

mkdir -p /usr/share/logstash/data/jdbc /usr/share/logstash/data/quarantine
exec /usr/local/bin/docker-entrypoint "$@"
