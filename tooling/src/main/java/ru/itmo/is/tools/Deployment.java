package ru.itmo.is.tools;

import net.schmizz.sshj.sftp.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Upload only explicitly selected coursework paths; never traverse remote symlinks. */
final class Deployment {
    static final List<String> LEGACY = List.of("build_report.py", "build_reports.py", "build_models.py", "build_stage3_diagrams.py", "build_stage3_report.py", "render_stage3_uml.py", "generate_entities.py", "run_backend.py", "verify_stage3.py", "validate_database.py", "db.sh", "update_contents.ps1");
    static void run(Path root) throws Exception {
        var files = new ArrayList<Path>();
        for (String folder : List.of("backend", "tooling", "database", "docs")) {
            try (var paths = Files.walk(root.resolve(folder))) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    if (relative.contains("/build/") || relative.contains("/data/") || relative.contains("/.gradle/") || relative.contains("/.idea/") || relative.endsWith(".local.env")) continue;
                    Verification.require(path.toRealPath().startsWith(root.toRealPath()), "Local file outside coursework"); files.add(path);
                }
            }
        }
        for (String name : List.of("README.md", "TASK.md", ".gitattributes", ".gitignore", "backend/build/libs/poteryashki.jar", "tooling/build/libs/course-tools.jar")) {
            Path path = root.resolve(name); Verification.require(Files.isRegularFile(path), "Missing deployment file " + name); files.add(path);
        }
        try (var remote = new Remote(); var sftp = remote.ssh.newSFTPClient()) {
            String base = "/home/studs/" + remote.user + "/poteryashki-course";
            Verification.require(sftp.canonicalize(base).equals(base), "Remote coursework directory is not canonical");
            var checkedDirectories=new HashSet<String>();
            int uploaded = 0;
            for (Path file : files) {
                String target = base + "/" + root.relativize(file).toString().replace('\\', '/');
                String parent = target.substring(0, target.lastIndexOf('/')); if(checkedDirectories.add(parent))ensureDirectory(sftp, base, parent);
                verifyTarget(sftp, base, target);
                Path relative = root.relativize(file);
                // Reuse unchanged files only after checking their entire content hash.
                String sha = checksum(file);
                if (sftp.statExistence(target) != null
                        && remote.run("sha256 -q " + Remote.quote(target)).strip().equals(sha)) continue;
                String temporary = target + ".upload"; verifyTarget(sftp, base, temporary);
                System.out.println("Uploading " + relative);
                sftp.put(file.toString(), temporary);
                Verification.require(remote.run("sha256 -q " + Remote.quote(temporary)).strip().equals(sha), "Uploaded file checksum mismatch");
                sftp.rename(temporary, target, EnumSet.of(RenameFlags.OVERWRITE, RenameFlags.ATOMIC)); uploaded++;
            }
            sftp.chmod(base + "/backend/gradlew", 0755);
            // Remove only known superseded scripts, after all replacements are installed.
            for (String name : LEGACY) remove(sftp, base, base + "/scripts/" + name);
            for (String name : List.of("requirements.txt", "requirements-remote.txt")) remove(sftp, base, base + "/" + name);
            // Apply the owned schema migration before Hibernate validates the new application.
            remote.run("cd "+Remote.quote(base)+" && java -Xms64m -Xmx256m -jar tooling/build/libs/course-tools.jar web stop");
            remote.run("cd "+Remote.quote(base)+" && java -Xms64m -Xmx256m -jar tooling/build/libs/course-tools.jar db upgrade_web");
            String demo = remote.run("cd " + Remote.quote(base) + " && java -Xms64m -Xmx256m -Dfile.encoding=UTF-8 -jar tooling/build/libs/course-tools.jar launch --demo");
            Verification.require(demo.contains("Потеряшки") || demo.contains("DEMO") || demo.contains("Demo"), "Missing demo output");
            Files.writeString(root.resolve("docs/part3/validation/helios.txt"), demo);
            String evidence = "Java tooling deployment\nUploaded files: " + uploaded + "\nApplication SHA-256: "
                    + checksum(root.resolve("backend/build/libs/poteryashki.jar")) + "\nTools SHA-256: "
                    + checksum(root.resolve("tooling/build/libs/course-tools.jar")) + "\nDemo completed on helios\n";
            Files.writeString(root.resolve("docs/part3/validation/deployment.txt"), evidence);
            for (String name : List.of("helios.txt", "deployment.txt")) sftp.put(root.resolve("docs/part3/validation/" + name).toString(), base + "/docs/part3/validation/" + name);
            System.out.println("PASS: Java tools and application installed; helios demo completed");
            String web=remote.run("cd "+Remote.quote(base)+" && java -Xms64m -Xmx256m -jar tooling/build/libs/course-tools.jar web start");
            Files.createDirectories(root.resolve("docs/part4/validation"));
            Files.writeString(root.resolve("docs/part4/validation/helios.txt"),web);
            sftp.put(root.resolve("docs/part4/validation/helios.txt").toString(),base+"/docs/part4/validation/helios.txt");
            System.out.println(web.strip());
        }
    }
    private static void ensureDirectory(SFTPClient sftp, String base, String parent) throws Exception {
        if (parent.equals(base)) return;
        String path = base;
        for (String part : parent.substring(base.length() + 1).split("/")) {
            if (part.isEmpty()) continue; path += "/" + part;
            var attrs = sftp.statExistence(path);
            if (attrs == null) sftp.mkdir(path);
            Verification.require(sftp.lstat(path).getType() == FileMode.Type.DIRECTORY, "Remote path is not a directory");
        }
    }
    private static void verifyTarget(SFTPClient sftp, String base, String target) throws Exception {
        Verification.require(target.startsWith(base + "/") && !target.contains("/../"), "Remote path outside coursework");
        String parent = target.substring(0, target.lastIndexOf('/'));
        Verification.require(sftp.canonicalize(parent).equals(parent), "Remote parent contains a symlink");
        try {
            Verification.require(sftp.lstat(target).getType() == FileMode.Type.REGULAR, "Remote target is not a regular file");
        } catch (SFTPException e) {
            if (e.getStatusCode() != Response.StatusCode.NO_SUCH_FILE) throw e;
        }
    }
    private static void remove(SFTPClient sftp, String base, String target) throws Exception {
        if (sftp.statExistence(target) != null) { verifyTarget(sftp, base, target); sftp.rm(target); }
    }
    static String checksum(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(file)) { byte[] buffer = new byte[65536]; int n; while ((n = stream.read(buffer)) > 0) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
