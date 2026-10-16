#!/usr/bin/env bash
# Executed ONLY by the official MySQL entrypoint on an empty data volume.
# A failed/partial first init requires operator inspection; never erase a volume automatically.
set -euo pipefail

if [[ "${DSS_NEW_DATABASE_CONFIRM:-}" != CREATE_NEW_DSS ]]; then
  echo 'Refusing schema initialization: review dss-init.sql and confirm CREATE_NEW_DSS.' >&2
  exit 1
fi
# Hex secrets avoid SQL quoting and .env/properties/shell interpolation ambiguities.
for name in DSS_MYSQL_PASSWORD DSS_LOGSTASH_MYSQL_PASSWORD; do
  if [[ ! "${!name:-}" =~ ^[a-fA-F0-9]{32,128}$ ]]; then
    echo "$name must be 32-128 hexadecimal characters (for example: openssl rand -hex 32)." >&2
    exit 1
  fi
done

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?}"
mysql --protocol=socket -uroot < /opt/tikcookie/dss-init.sql
mysql --protocol=socket -uroot <<SQL
CREATE USER 'dss_app'@'%' IDENTIFIED BY '${DSS_MYSQL_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON dss.* TO 'dss_app'@'%';
CREATE USER 'dss_logstash'@'%' IDENTIFIED BY '${DSS_LOGSTASH_MYSQL_PASSWORD}';
GRANT SELECT ON dss.products TO 'dss_logstash'@'%';
GRANT SELECT ON dss.shops TO 'dss_logstash'@'%';
SQL
unset MYSQL_PWD
