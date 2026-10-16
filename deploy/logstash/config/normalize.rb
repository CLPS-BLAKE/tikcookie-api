require "json"

def register(params)
  @product_fields = %w[id shopId name contentsText shopName shopCategory type status price soldCount image flashStartTime flashEndTime].freeze
  @shop_fields = %w[id name address category cover businessHours createdAt].freeze
end

def filter(event)
  target = event.get("[@metadata][target_index]")
  raise "Unknown index" unless %w[dss_product dss_shop].include?(target)
  raise "Missing document id" if event.get("id").nil? || event.get("id").to_s.empty?
  event.set("id", event.get("id").to_s)
  if target == "dss_product"
    event.set("shopId", event.get("shopId").to_s)
    groups = JSON.parse(event.get("contents_json"))
    names = groups.flat_map { |group| group.fetch("items", []).map { |item| item["name"] } }
    event.set("contentsText", names.compact.join(" "))
    allowed = @product_fields
  else
    allowed = @shop_fields
  end
  # Strip JDBC cursor/raw JSON and Logstash's "@timestamp"/version/tags before strict ES mapping.
  event.to_hash.keys.each { |key| event.remove(key) unless allowed.include?(key) || key == "@metadata" }
  [event]
rescue => error
  # Do not event.cancel: retain the payload for explicit operator inspection/replay.
  event.set("[@metadata][quarantine]", true)
  event.set("sync_error", error.message)
  warn("JDBC document quarantined; inspect data/quarantine/invalid.jsonl: #{error.message}")
  [event]
end

# Logstash Ruby-filter tests also run during bin/logstash --config.test_and_exit.
test "product JSON contents, string IDs and no cursor pollution" do
  in_event do
    {"id" => 9007199254740993, "shopId" => 42, "contents_json" => '[{"title":"Food","items":[{"name":"Noodles","count":2},{"name":"Tea","count":1}]}]',
     "flashStartTime" => nil, "@timestamp" => "ignored", "sync_updated_at" => "ignored",
     "@metadata" => {"target_index" => "dss_product"}}
  end
  expect("compatible product document") do |events|
    e = events.first
    events.size == 1 && e.get("id") == "9007199254740993" && e.get("shopId") == "42" &&
      e.get("contentsText") == "Noodles Tea" && e.get("@timestamp").nil? && e.get("sync_updated_at").nil? && e.get("contents_json").nil?
  end
end

test "empty contents are valid" do
  in_event { {"id" => "1", "shopId" => "2", "contents_json" => "[]", "@metadata" => {"target_index" => "dss_product"}} }
  expect("empty searchable text") { |events| events.first.get("contentsText") == "" }
end

test "shop preserves the Java local datetime string" do
  in_event { {"id" => 1, "createdAt" => "2026-10-16 12:34:56", "@metadata" => {"target_index" => "dss_shop"}} }
  expect("date remains a local datetime") { |events| events.first.get("createdAt") == "2026-10-16 12:34:56" }
end

test "invalid contents are quarantined not indexed" do
  in_event { {"id" => "1", "contents_json" => "invalid", "@metadata" => {"target_index" => "dss_product"}} }
  expect("retained for manual replay") { |events| events.first.get("[@metadata][quarantine]") == true }
end
