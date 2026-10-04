package ru.itmo.is.tools;

import java.nio.file.*;
import java.util.*;

/** Command line tools shared by local builds and the coursework on helios. */
public final class CourseTools {
    public static void main(String[] args) throws Exception {
        var options = new ArrayList<>(Arrays.asList(args));
        Path root = Path.of(System.getProperty("user.dir"));
        int at = options.indexOf("--root");
        if (at >= 0) {
            if (at + 1 >= options.size()) throw new IllegalArgumentException("--root requires a directory");
            root = Path.of(options.remove(at + 1)); options.remove(at);
        } else if (Files.isDirectory(root.resolve("../database"))) root = root.resolve("..");
        root = root.toAbsolutePath().normalize();
        if (options.isEmpty() || options.get(0).equals("help")) {
            System.out.println("Course tools (Java 17): launch [--demo] [--jar PATH]; --root PROJECT");
            return;
        }
        switch (options.remove(0)) {
            case "launch" -> Launcher.run(root, options);
            case "db" -> DatabaseCommand.run(root, options);
            case "verify" -> Verification.services(root);
            case "verify-db" -> Verification.database(root);
            case "entities" -> Entities.generate(root);
            default -> throw new IllegalArgumentException("Unknown command; use help");
        }
    }
}
