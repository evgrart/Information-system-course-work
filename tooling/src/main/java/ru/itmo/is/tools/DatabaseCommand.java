package ru.itmo.is.tools;

import java.nio.file.Path;
import java.util.*;

final class DatabaseCommand {
    static String schema() {
        String schema = System.getenv().getOrDefault("DB_SCHEMA", "s465826");
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid DB_SCHEMA");
        return schema;
    }
    static void run(Path root, List<String> args) throws Exception {
        if (args.size() != 1 || !Set.of("create", "seed", "test", "drop", "explain", "catalog","upgrade_web").contains(args.get(0)))
            throw new IllegalArgumentException("db create|seed|test|drop|explain|catalog");
        var builder = new ProcessBuilder("psql", "-X", "-w", "-v", "ON_ERROR_STOP=1",
                "-v", "schema=" + schema(), "-f", root.resolve("database/" + args.get(0) + ".sql").toString())
                .directory(root.toFile()).inheritIO();
        builder.environment().putIfAbsent("PGHOST", "pg");
        builder.environment().putIfAbsent("PGDATABASE", "studs");
        int code = builder.start().waitFor();
        if (code != 0) throw new IllegalStateException("psql exited with code " + code);
    }
}
