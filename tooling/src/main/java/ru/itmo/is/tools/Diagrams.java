package ru.itmo.is.tools;

import net.sourceforge.plantuml.*;
import java.nio.file.*;
import java.util.*;

final class Diagrams {
    static void render(Path root) throws Exception {
        int count = 0;
        for (String folder : List.of("docs/part1/uml", "docs/part3/uml", "docs/part4/uml", "docs/part2/model")) {
            try (var files = Files.list(root.resolve(folder))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".puml")).sorted().toList()) {
                    renderFile(file); count++;
                }
            }
        }
        System.out.println("Rendered " + count + " UML diagrams (PNG and SVG)");
    }
    static void renderFile(Path source) throws Exception {
        String text = Files.readString(source);
        // Embedded Smetana performs layout without a Graphviz installation.
        if (!text.contains("!pragma layout smetana")) text = text.replace("@startuml", "@startuml\n!pragma layout smetana");
        var reader = new SourceStringReader(text);
        if (reader.getBlocks().isEmpty() || reader.getBlocks().get(0).getDiagram() instanceof net.sourceforge.plantuml.error.PSystemError)
            throw new IllegalStateException("Invalid diagram: " + source);
        String stem = source.toString().substring(0, source.toString().length() - 5);
        for (FileFormat format : List.of(FileFormat.PNG, FileFormat.SVG)) {
            Path target = Path.of(stem + "." + format.name().toLowerCase(Locale.ROOT));
            try (var out = Files.newOutputStream(target)) {
                var description = reader.outputImage(out, new FileFormatOption(format));
                if (description == null || description.getDescription().toLowerCase(Locale.ROOT).contains("error"))
                    throw new IllegalStateException("Rendering failed: " + source);
            }
        }
    }
}
