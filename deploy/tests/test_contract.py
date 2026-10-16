"""Offline deployment contracts, including Compose config with a fabricated env.
Run: python -B -m unittest discover -s deploy/tests -v
Does not certify container startup or live synchronization.
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"


class DeploymentContract(unittest.TestCase):
    def read(self, relative):
        path = ROOT / relative
        self.assertTrue(path.is_file(), f"Required deployment file missing: {relative}")
        return path.read_text(encoding="utf-8")

    def test_five_services_and_local_bindings(self):
        text = self.read("deploy/compose/middleware.yml")
        services_block = text.split("\nservices:\n", 1)[1].split("\nvolumes:", 1)[0]
        services = re.findall(r"^  ([a-z]+):$", services_block, re.M)
        self.assertEqual(set(services), {"mysql", "redis", "rabbitmq", "es", "logstash"})
        self.assertNotIn(":latest", text)
        for key in ("healthcheck:", "mem_limit:", "pids_limit:"):
            self.assertEqual(text.count(key), 5)
        self.assertNotIn("env_file:", text)
        self.assertNotIn("privileged: true", text)
        self.assertIn("127.0.0.1:${DSS_MYSQL_PORT:-3306}:3306", text)
        self.assertIn("127.0.0.1:9200:9200", text)
        self.assertIn("xpack.security.enabled: 'true'", text)
        self.assertIn("requirepass", text)

    def test_explicit_schema_confirmation_no_reset(self):
        text = self.read("deploy/compose/mysql-init.sh")
        self.assertIn("CREATE_NEW_DSS", text)
        self.assertIn("/opt/tikcookie/dss-init.sql", text)
        self.assertNotRegex(text.upper(), r"\b(DROP|TRUNCATE)\s+(TABLE|DATABASE)")
        self.assertIn("GRANT SELECT, INSERT, UPDATE, DELETE", text)
        self.assertIn("dss_logstash", text)
        self.assertIn("dss.products", text)
        self.assertIn("dss.shops", text)

    def test_java_fields_equal_strict_index_mappings(self):
        for name, index in [("ProductDoc", "product"), ("ShopDoc", "shop")]:
            java = self.read(f"src/main/java/com/dss/search/model/entity/{name}.java")
            fields = dict((field, typ) for typ, field in re.findall(
                r"private (String|Long|Integer|LocalDateTime) (\w+);", java))
            template = json.loads(self.read(f"deploy/logstash/templates/{index}.json"))
            mapping = template["template"]["mappings"]
            self.assertEqual(mapping["dynamic"], "strict")
            self.assertEqual(set(mapping["properties"]), set(fields))
            self.assertEqual(template["index_patterns"], [f"dss_{index}"])
            for field, typ in fields.items():
                actual = mapping["properties"][field]
                if typ == "LocalDateTime":
                    self.assertEqual(actual, {"type": "date", "format": "yyyy-MM-dd HH:mm:ss"})
                elif typ in ("Long", "Integer"):
                    self.assertEqual(actual["type"], {"Long": "long", "Integer": "integer"}[typ])
            self.assertEqual(mapping["properties"]["id"]["type"], "keyword")
            self.assertEqual(template["template"]["settings"]["number_of_replicas"], 0)

    def test_sql_watermarks_and_shop_changes(self):
        product = self.read("deploy/logstash/sql/products.sql")
        shop = self.read("deploy/logstash/sql/shops.sql")
        self.assertIn("GREATEST(p.updated_at, s.updated_at)", product)
        for text in (product, shop):
            self.assertIn(":sql_last_value", text)
            self.assertIn("INTERVAL 5 SECOND", text)
            self.assertIn("INTERVAL 1 SECOND", text)
            self.assertIn("ORDER BY sync_updated_at", text)
            self.assertNotIn("LIMIT ", text.upper())
            self.assertRegex(text, r"CAST\([^\n]+AS CHAR\) AS id")
        self.assertIn("'%Y-%m-%d %H:%i:%s'", shop)
        self.assertIn("AS createdAt", shop)
        self.assertIn("$[0]", shop)
        self.assertNotIn("ON_SHELF", product)

    def test_independent_durable_jdbc_cursors_and_payload(self):
        text = self.read("deploy/logstash/pipeline/sync.conf")
        for setting in ('tracking_column => "sync_updated_at"', 'tracking_column_type => "timestamp"',
                        'jdbc_default_timezone => "Asia/Shanghai"', 'lowercase_column_names => false'):
            self.assertEqual(text.count(setting), 2)
        self.assertIn("products.yml", text)
        self.assertIn("shops.yml", text)
        self.assertIn('document_id => "%{id}"', text)
        self.assertIn('manage_template => false', text)
        ruby = self.read("deploy/logstash/config/normalize.rb")
        for part in ('group.fetch("items", [])', 'item["name"]', 'event.set("contentsText"',
                     '.to_s', '"@timestamp"', '[@metadata][quarantine]'):
            self.assertIn(part, ruby)
        self.assertIn('data/quarantine/invalid.jsonl', text)

    def test_bootstrap_template_and_empty_index(self):
        text = self.read("deploy/logstash/config/bootstrap.sh")
        for part in ("/_index_template/", "dss_product", "dss_shop", "resource_already_exists_exception",
                     "curl", "--fail-with-body", "exec /usr/local/bin/docker-entrypoint"):
            self.assertIn(part, text)
        self.assertNotIn("DELETE", text)

    def test_pinned_jdbc_download(self):
        text = self.read("deploy/logstash/Dockerfile")
        self.assertIn("docker.elastic.co/logstash/logstash:8.18.8", text)
        self.assertIn("mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar", text)
        self.assertIn("sha256sum --check", text)
        self.assertRegex(text, r"[0-9a-f]{64}")

    def test_nginx_prefix_and_public_boundary(self):
        text = self.read("deploy/nginx/tikcookie.conf")
        for part in ("listen 80", "root /var/www/tikcookie-web/dist;", "internal", "swagger", "api-docs",
                     "return 404", "X-Internal-Key", "try_files $uri $uri/ /index.html;"):
            self.assertIn(part, text)
        self.assertIn("proxy_pass http://127.0.0.1:8080;", text)
        self.assertNotIn("proxy_pass http://127.0.0.1:8080/;", text)
        self.assertNotIn("DSS_OSS_ACCESS_KEY", text)
        self.assertNotIn("proxy_pass https://", text)

    def test_single_root_env_local_hosts_and_safe_defaults(self):
        values = dict(re.findall(r"^(\w+)=(.*)$", self.read(".env.example"), re.M))
        for kind in ("MYSQL", "REDIS", "RABBIT"):
            self.assertEqual(values[f"DSS_{kind}_HOST"], "127.0.0.1")
        self.assertEqual(values["DSS_ES_URIS"], "http://127.0.0.1:9200")
        self.assertEqual(values["DSS_ES_USERNAME"], "elastic")
        for key in ("DSS_SWAGGER_ENABLED", "DSS_FLASH_ENABLED"):
            self.assertEqual(values[key], "false")
        self.assertEqual(values["SERVER_ADDRESS"], "127.0.0.1")
        self.assertEqual(values["DSS_NEW_DATABASE_CONFIRM"], "")
        for key in ("DSS_MYSQL_ROOT_PASSWORD", "DSS_LOGSTASH_MYSQL_PASSWORD", "JAVA_TOOL_OPTIONS"):
            self.assertIn(key, values)
        self.assertEqual(values["DSS_FILE_BASE_URL"], "/api/v1/images")
        self.assertNotIn("?", values["DSS_FILE_BASE_URL"])
        self.assertEqual(list(DEPLOY.rglob(".env.example")), [])

    def test_ignore_local_secrets(self):
        text = self.read(".gitignore")
        for part in (".env.*", "!.env.example", "__pycache__/"):
            self.assertIn(part, text)

    def test_compose_config_with_fabricated_env(self):
        self.read("deploy/compose/middleware.yml")
        self.assertIsNotNone(shutil.which("docker"), "Docker Compose CLI required (no daemon needed)")
        fixture = {"DSS_NEW_DATABASE_CONFIRM": "CREATE_NEW_DSS", "DSS_MYSQL_PORT": "23306",
                   "DSS_MYSQL_ROOT_PASSWORD": "1" * 32, "DSS_MYSQL_PASSWORD": "2" * 32,
                   "DSS_LOGSTASH_MYSQL_PASSWORD": "3" * 32, "DSS_REDIS_PASSWORD": "4" * 32,
                   "DSS_REDIS_PORT": "26379", "DSS_RABBIT_PORT": "25672",
                   "DSS_RABBIT_USERNAME": "dss_app", "DSS_RABBIT_PASSWORD": "5" * 32,
                   "DSS_RABBIT_VHOST": "/dss", "DSS_ES_PASSWORD": "6" * 32}
        env = {k: v for k, v in os.environ.items()
               if not k.startswith(("DSS_", "COMPOSE_", "DOCKER_"))}
        with tempfile.TemporaryDirectory(prefix=".contract-", dir=DEPLOY / "tests") as tmp:
            path = Path(tmp)
            fake = path / "fake.env"
            fake.write_text("\n".join(f"{k}={v}" for k, v in fixture.items()), encoding="utf-8")
            (path / "docker").mkdir()
            args = ["docker", "--config", str(path / "docker"), "compose",
                    "--env-file", str(fake),
                    "-f", str(DEPLOY / "compose/middleware.yml"), "config", "--format", "json"]
            result = subprocess.run(args, env=env, text=True, capture_output=True, timeout=45)
            self.assertEqual(result.returncode, 0, result.stderr)
            config = json.loads(result.stdout)
            self.assertEqual(len(config["services"]), 5)
            self.assertEqual(str(config["services"]["mysql"]["ports"][0]["published"]), "23306")
            self.assertEqual(str(config["services"]["redis"]["ports"][0]["published"]), "26379")
            self.assertEqual(str(config["services"]["rabbitmq"]["ports"][0]["published"]), "25672")
            docs = self.read("docs/DEPLOYMENT.md")
            function = re.search(r"dc\(\)\s*\{(.*?)\n\}", docs, re.S).group(1)
            self.assertNotIn("--project-directory", function, "Relative Compose paths must stay based on deploy/compose")
            self.assertIn('--env-file "$API/.env"', function)
            self.assertIn('-f "$API/deploy/compose/middleware.yml"', function)
            self.assertEqual(Path(config["services"]["logstash"]["build"]["context"]).resolve(),
                             (DEPLOY / "logstash").resolve())
            binds = {v["target"]: Path(v["source"]).resolve()
                     for v in config["services"]["mysql"]["volumes"] if v["type"] == "bind"}
            self.assertEqual(binds["/opt/tikcookie/dss-init.sql"], (ROOT / "dss-init.sql").resolve())
            self.assertEqual(binds["/docker-entrypoint-initdb.d/10-tikcookie.sh"],
                             (DEPLOY / "compose/mysql-init.sh").resolve())
            for service in config["services"].values():
                for port in service.get("ports", []):
                    self.assertEqual(port["host_ip"], "127.0.0.1")
                self.assertIn("healthcheck", service)
            fixture["DSS_NEW_DATABASE_CONFIRM"] = ""
            fake.write_text("\n".join(f"{k}={v}" for k, v in fixture.items()), encoding="utf-8")
            denied = subprocess.run(args, env=env, text=True, capture_output=True, timeout=45)
            self.assertNotEqual(denied.returncode, 0)
            self.assertIn("DSS_NEW_DATABASE_CONFIRM", denied.stderr)


if __name__ == "__main__":
    unittest.main()
