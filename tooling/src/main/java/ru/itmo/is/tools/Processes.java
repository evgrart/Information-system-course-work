package ru.itmo.is.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

final class Processes {
    record Result(int code, String out, String error) {
        String checked() {
            if (code != 0) throw new IllegalStateException("Process failed (" + code + "): " + error);
            return out;
        }
    }
    static Result capture(List<String> command, Path cwd, Map<String, String> env,
                          String input, Duration timeout) throws Exception {
        var builder = new ProcessBuilder(command).directory(cwd.toFile());
        builder.environment().putAll(env); builder.environment().remove("DEBUG");
        Process process = builder.start();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var stdout = executor.submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            var stderr = executor.submit(() -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            try (var in = process.getOutputStream()) {
                if (input != null) in.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly(); throw new IllegalStateException("Process timeout");
            }
            return new Result(process.exitValue(), stdout.get(), stderr.get());
        } finally { if (process.isAlive()) process.destroyForcibly(); executor.shutdownNow(); }
    }
}
