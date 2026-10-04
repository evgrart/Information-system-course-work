package ru.itmo.is.tools;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A fixture owns only its reserved prefix and explicitly uploaded temporary files. */
final class Fixture implements AutoCloseable {
    final Remote remote;
    final String schema, prefix, folder, before, sequencesBefore;
    final List<Path> sources;
    boolean created;
    Fixture(Remote remote, Path root, String prefix) throws Exception {
        this.remote = remote; this.schema = DatabaseCommand.schema(); this.prefix = prefix;
        if (!Set.of("lfcheck_", "lf3check_").contains(prefix)) throw new IllegalArgumentException("Reserved fixture prefix required");
        if (!count().equals("0")) throw new IllegalStateException("Existing " + prefix + " objects; refusing to adopt or delete them");
        before = remote.sql(schema, fingerprint()); sequencesBefore = remote.sql(schema, sequences());
        try (var files = Files.list(root.resolve("database"))) { sources = files.filter(p -> p.toString().endsWith(".sql")).sorted().toList(); }
        folder = remote.run("mktemp -d /tmp/poteryashki-java-XXXXXX").strip();
        try {
            for (Path source : sources) remote.write(folder + "/" + source.getFileName(), remap(Files.readString(source), prefix).getBytes(StandardCharsets.UTF_8));
            file("create"); created = true; file("seed");
        } catch (Exception e) { try { close(); } catch (Exception cleanup) { e.addSuppressed(cleanup); } throw e; }
    }
    static String remap(String sql, String prefix) {
        return sql.replace("lf\\_", prefix.substring(0, prefix.length() - 1) + "\\_").replace("lf_", prefix);
    }
    String file(String name) throws Exception {
        return remote.run("psql -h pg -d studs -X -w -v ON_ERROR_STOP=1 -v schema=" + Remote.quote(schema) + " -f " + Remote.quote(folder + "/" + name + ".sql"));
    }
    String sql(String sql) throws Exception { return remote.sql(schema, remap(sql, prefix)); }
    String condition() { return "relnamespace='" + schema + "'::regnamespace"; }
    String pattern() { return prefix.substring(0, prefix.length() - 1) + "\\_%"; }
    String fingerprint() {
        return "SELECT md5(string_agg(oid::text||':'||relname||':'||relkind::text,'|' ORDER BY oid)) FROM pg_class WHERE "
                + condition() + " AND relname NOT LIKE '" + pattern() + "' ESCAPE '\\';";
    }
    String sequences() {
        return "SELECT coalesce(jsonb_object_agg(sequencename,last_value)::text,'{}') FROM pg_sequences WHERE schemaname='"
                + schema + "' AND sequencename NOT LIKE '" + pattern() + "' ESCAPE '\\';";
    }
    String count() throws Exception {
        return remote.sql(schema, "SELECT count(*) FROM pg_class WHERE " + condition() + " AND relname LIKE '" + pattern() + "' ESCAPE '\\';");
    }
    String evidence;
    public void close() throws Exception {
        if (created) { file("drop"); created = false; }
        try (var sftp = remote.ssh.newSFTPClient()) {
            for (Path source : sources) {
                String target = folder + "/" + source.getFileName();
                if (sftp.statExistence(target) != null) sftp.rm(target);
            }
            sftp.rmdir(folder);
        }
        String after = remote.sql(schema, fingerprint()), sequencesAfter = remote.sql(schema, sequences()), remains = count();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var original=mapper.readTree(sequencesBefore);var current=mapper.readTree(sequencesAfter);
        var changed=new java.util.TreeSet<String>();
        original.fieldNames().forEachRemaining(name->{if(!original.get(name).equals(current.get(name)))changed.add(name);});
        current.fieldNames().forEachRemaining(name->{if(!current.get(name).equals(original.get(name)))changed.add(name);});
        evidence = "Existing objects before: " + before + "\nExisting objects after: " + after
                + "\nOriginal sequence values preserved: " + sequencesBefore.equals(sequencesAfter) + "\nChanged original sequences: " + changed + "\nRemaining fixture objects: " + remains + "\n";
        if (!before.equals(after) || !sequencesBefore.equals(sequencesAfter) || !remains.equals("0"))
            throw new IllegalStateException("Fixture isolation verification failed");
    }
}
