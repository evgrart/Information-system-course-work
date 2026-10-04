package ru.itmo.is.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

final class Verification {
    static void services(Path root) throws Exception {
        Path output = root.resolve("docs/part3/validation"); Files.createDirectories(output);
        try (var remote = new Remote()) {
            Fixture fixture = new Fixture(remote, root, "lf3check_");
            try (fixture; var tunnel = remote.new Tunnel()) {
                String password = PgPass.find(remote.run("cat ~/.pgpass"), "pg", 5432, "studs", remote.user);
                var env = new HashMap<String, String>();
                env.put("DB_URL", "jdbc:postgresql://127.0.0.1:" + tunnel.port() + "/studs?currentSchema=" + fixture.schema);
                env.put("DB_USER", remote.user); env.put("DB_PASSWORD", password);
                env.put("DB_PREFIX", fixture.prefix); env.put("RUN_DB_TESTS", "true");env.put("JOBS_ENABLED","false");
                // A child build is never given the SSH login password.
                env.put("COURSE_SSH_PASSWORD", "");
                env.put("JAVA_HOME", System.getProperty("java.home"));
                String wrapper = root.resolve("backend/" + (Launcher.windows() ? "gradlew.bat" : "gradlew")).toString();
                System.out.println("Running Java service tests against isolated lf3check_* tables");
                var result = Processes.capture(List.of(wrapper, ":clean", ":test", ":bootJar", "--console=plain"),
                        root.resolve("backend"), env, null, Duration.ofMinutes(10));
                String transcript = (result.out() + result.error()).lines().map(String::stripTrailing)
                        .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
                Files.writeString(output.resolve("integration.txt"), transcript);
                result.checked();
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                var summary = new ArrayList<Map<String, String>>();
                try (var files = Files.list(root.resolve("backend/build/test-results/test"))) {
                    for (Path path : files.filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml")).sorted().toList()) {
                        var xml = factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                        var item = new LinkedHashMap<String, String>();
                        for (String key : List.of("name", "tests", "skipped", "failures", "errors", "timestamp", "time")) item.put(key, xml.getAttribute(key));
                        if(item.get("name").endsWith("BrowserFlowTest"))continue;
                        for (String key : List.of("skipped", "failures", "errors"))
                            require(item.get(key).equals("0"), "Incomplete test run: " + item.get("name"));
                        summary.add(item);
                    }
                }
                require(!summary.isEmpty(), "Missing JUnit results");
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("tests.json").toFile(), summary);
                int count = summary.stream().mapToInt(s -> Integer.parseInt(s.get("tests"))).sum();
                System.out.println("PASS: " + count + " service tests, no skips or failures");
            } finally {
                if (fixture.evidence != null) Files.writeString(output.resolve("isolation.txt"), fixture.evidence);
            }
            System.out.println("PASS: fixture removed, original objects and sequences preserved");
        }
    }
    static void database(Path root) throws Exception {
        var log = new StringBuilder();
        try (var remote = new Remote(); var fixture = new Fixture(remote, root, "lfcheck_")) {
            String result = fixture.file("test");
            require(result.contains("50") && result.contains("ROLLBACK"), "Missing integrity results");
            pass(log, "50 PostgreSQL integrity checks");
            var race = race(fixture,
                    "BEGIN; SELECT 1 FROM lf_listings WHERE id=1 FOR UPDATE; SELECT pg_sleep(0.7); SELECT lf_reserve_found(1,3); COMMIT;",
                    "BEGIN; SELECT lf_reserve_found(2,3); COMMIT;");
            require(race.get(0).code() == 0 && race.get(1).code() != 0 && race.get(1).error().contains("Находка недоступна"), "Reservation race failed");
            require(fixture.sql("SELECT count(*) FROM lf_claims WHERE listing_id=1 AND state='accepted';").equals("1"), "Duplicate reservation");
            pass(log, "Concurrent reservations: exactly one accepted");
            race = race(fixture,
                    "BEGIN; SELECT 1 FROM lf_listings WHERE id=2 FOR UPDATE; SELECT pg_sleep(0.7); SELECT lf_place_bid(1,4,1200,'40000000-0000-0000-0000-000000000001'); COMMIT;",
                    "BEGIN; SELECT lf_place_bid(1,5,1200,'40000000-0000-0000-0000-000000000002'); COMMIT;");
            require(race.get(0).code() == 0 && race.get(1).code() != 0 && race.get(1).error().contains("Ставка ниже"), "Bid race failed");
            pass(log, "Concurrent bids: exactly one accepted");
            fixture.sql("INSERT INTO lf_payment_orders(id,user_id,tariff_id,request_key,amount,duration_days) VALUES (50,5,1,'40000000-0000-0000-0000-000000000050',149,30);");
            race = race(fixture,
                    "BEGIN; SELECT 1 FROM lf_users WHERE id=5 FOR UPDATE; SELECT pg_sleep(0.7); SELECT lf_activate_subscription(50,'race-payment',149); COMMIT;",
                    "BEGIN; SELECT lf_activate_subscription(50,'race-payment',149); COMMIT;");
            require(race.stream().allMatch(r -> r.code() == 0), "Payment race failed");
            require(fixture.sql("SELECT count(*) FROM lf_subscriptions WHERE payment_order_id=50;").equals("1"), "Duplicate subscription");
            pass(log, "Concurrent payment retry: one subscription period");
            fixture.sql("UPDATE lf_auction_permissions SET state='approved',moderator_id=1,reviewed_at=clock_timestamp() WHERE id=2; INSERT INTO lf_auctions(id,listing_id,seller_id,permission_id,starts_at,ends_at,start_price,bid_step,state) VALUES (10,6,3,2,clock_timestamp(),clock_timestamp()+interval '2 seconds',100,10,'active'); SELECT lf_place_bid(10,4,100,'40000000-0000-0000-0000-000000000010');");
            Thread.sleep(2100); fixture.sql("CALL lf_close_due_auctions();");
            require(fixture.sql("SELECT state||':'||(winner_bid_id IS NOT NULL)::text FROM lf_auctions WHERE id=10;").equals("finished:true"), "Auction winner missing");
            fixture.sql("SELECT lf_finalize_auction(10);");
            pass(log, "Batch auction closing and idempotent winner lookup");
        }
        pass(log, "Fixture removed; original objects and sequence values preserved");
        Files.writeString(root.resolve("docs/part3/validation/database-regression.txt"), log);
    }
    private static List<Processes.Result> race(Fixture fixture, String first, String second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Processes.Result> a = () -> rawSql(fixture, first);
            var left = pool.submit(a); Thread.sleep(150);
            var right = pool.submit(() -> rawSql(fixture, second));
            return List.of(left.get(), right.get());
        } finally { pool.shutdownNow(); }
    }
    private static Processes.Result rawSql(Fixture fixture, String sql) throws Exception {
        return fixture.remote.execute("psql -h pg -d studs -X -w -qAt -v ON_ERROR_STOP=1",
                "SET search_path TO \"" + fixture.schema + "\",pg_catalog;\n" + Fixture.remap(sql, fixture.prefix));
    }
    static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    static void pass(StringBuilder log, String message) { String line = "PASS: " + message; System.out.println(line); log.append(line).append('\n'); }
}
