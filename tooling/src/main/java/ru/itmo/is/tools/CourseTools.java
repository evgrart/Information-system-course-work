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
            System.out.println("""
                    Coursework tools, Java 17. Run from the project root or use --root PATH.
                      launch [--demo] [--jar PATH]  start the Spring application
                      db create|seed|test|drop|explain|catalog  run PostgreSQL scripts
                      verify                       run service tests through SSH
                      verify-db                    check PostgreSQL integrity and races through SSH
                      entities                     generate JPA projections from the catalog
                      models                       generate dictionary and ER models
                      diagrams                     render editable PlantUML sources to PNG/SVG
                      reports                      build PDF reports for stages 1, 1–2, 3, 1–3
                      audit                        check PDF text, bookmarks and page bounds
                      deploy                       upload the coursework and check its demo on helios
                    """);
            return;
        }
        String command = options.remove(0);
        if (!Set.of("launch", "db").contains(command) && !options.isEmpty())
            throw new IllegalArgumentException("Unexpected arguments; use help");
        switch (command) {
            case "launch" -> Launcher.run(root, options);
            case "db" -> DatabaseCommand.run(root, options);
            case "verify" -> Verification.services(root);
            case "verify-db" -> Verification.database(root);
            case "entities" -> Entities.generate(root);
            case "models" -> Models.generate(root);
            case "diagrams" -> Diagrams.render(root);
            case "reports" -> Reports.build(root);
            case "audit" -> PdfAudit.run(root);
            case "deploy" -> Deployment.run(root);
            default -> throw new IllegalArgumentException("Unknown command; use help");
        }
    }
}
