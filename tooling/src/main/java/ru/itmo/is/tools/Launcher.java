package ru.itmo.is.tools;

import java.net.URI;
import java.nio.file.*;
import java.util.*;

final class Launcher {
    static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java").toString();
    }
    static boolean windows() { return System.getProperty("os.name").startsWith("Windows"); }
    static void run(Path root, List<String> args) throws Exception {
        boolean demo = args.remove("--demo");
        Path jar = root.resolve("backend/build/libs/poteryashki.jar");
        if (args.size() == 2 && args.get(0).equals("--jar")) { jar = Path.of(args.get(1)); args.clear(); }
        if (!args.isEmpty()) throw new IllegalArgumentException("launch [--demo] [--jar PATH]");
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Build bootJar first");
        var command = new ArrayList<>(List.of(javaExecutable(), "-Xms64m", "-Xmx256m",
                "-Dfile.encoding=UTF-8", "-jar", jar.toAbsolutePath().toString()));
        if (demo) command.add("--app.demo=true");
        var process = new ProcessBuilder(command).directory(root.toFile()).inheritIO();
        var env = process.environment();
        env.putIfAbsent("DB_URL", "jdbc:postgresql://pg:5432/studs?currentSchema=s465826");
        env.putIfAbsent("DB_USER", "s465826");
        if (env.getOrDefault("DB_PASSWORD", "").isEmpty()) {
            var uri = URI.create(env.get("DB_URL").replaceFirst("^jdbc:", ""));
            Path pass = Path.of(env.getOrDefault("PGPASSFILE", Path.of(System.getProperty("user.home"), ".pgpass").toString()));
            env.put("DB_PASSWORD", PgPass.find(Files.readString(pass), uri.getHost(),
                    uri.getPort() < 0 ? 5432 : uri.getPort(), uri.getPath().substring(1), env.get("DB_USER")));
        }
        int status = process.start().waitFor();
        if (status != 0) throw new IllegalStateException("Application exited with code " + status);
    }
}
