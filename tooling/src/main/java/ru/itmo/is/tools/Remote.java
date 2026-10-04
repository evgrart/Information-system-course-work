package ru.itmo.is.tools;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.*;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

final class Remote implements AutoCloseable {
    static { System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn"); }
    final SSHClient ssh = new SSHClient();
    final String user;
    Remote() throws Exception {
        user = System.getenv().getOrDefault("COURSE_SSH_USER", "s465826");
        if (!user.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid SSH user");
        ssh.loadKnownHosts();
        ssh.setConnectTimeout(20000); ssh.setTimeout(120000);
        ssh.connect(System.getenv().getOrDefault("COURSE_SSH_HOST", "helios.cs.ifmo.ru"),
                Integer.parseInt(System.getenv().getOrDefault("COURSE_SSH_PORT", "2222")));
        String password = System.getenv("COURSE_SSH_PASSWORD");
        if (password == null) {
            if (System.console() == null) throw new IllegalStateException("Set COURSE_SSH_PASSWORD or run from a terminal");
            char[] chars = System.console().readPassword("SSH password: ");
            password = new String(chars); Arrays.fill(chars, '\0');
        }
        try { ssh.authPassword(user, password); }
        catch (Exception e) { ssh.close(); throw e; }
    }
    static String quote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
    Processes.Result execute(String command, String input) throws Exception {
        try (var session = ssh.startSession()) {
            var cmd = session.exec(command);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var out = executor.submit(() -> new String(cmd.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                var err = executor.submit(() -> new String(cmd.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
                try (var stream = cmd.getOutputStream()) {
                    if (input != null) stream.write(input.getBytes(StandardCharsets.UTF_8));
                }
                cmd.join(120, TimeUnit.SECONDS);
                if (cmd.getExitStatus() == null) throw new IllegalStateException("Remote command timeout");
                return new Processes.Result(cmd.getExitStatus(), out.get(), err.get());
            } finally { executor.shutdownNow(); }
        }
    }
    String run(String command) throws Exception { return execute(command, null).checked(); }
    String sql(String schema, String sql) throws Exception {
        return execute("psql -h pg -d studs -X -w -qAt -v ON_ERROR_STOP=1",
                "SET search_path TO \"" + schema + "\",pg_catalog;\n" + sql).checked().strip();
    }
    void write(String target, byte[] contents) throws Exception {
        try (var sftp = ssh.newSFTPClient(); var file = sftp.open(target, EnumSet.of(OpenMode.CREAT, OpenMode.TRUNC, OpenMode.WRITE))) {
            file.write(0, contents, 0, contents.length);
        }
    }
    final class Tunnel implements AutoCloseable {
        final ServerSocket socket = new ServerSocket();
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final net.schmizz.sshj.connection.channel.direct.LocalPortForwarder forwarder;
        Tunnel() throws Exception {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            forwarder = ssh.newLocalPortForwarder(new Parameters("127.0.0.1", socket.getLocalPort(), "pg", 5432), socket);
            worker.submit(() -> { try { forwarder.listen(); } catch (Exception e) { if (!socket.isClosed()) throw new RuntimeException(e); } });
        }
        int port() { return socket.getLocalPort(); }
        public void close() throws Exception { forwarder.close(); socket.close(); worker.shutdownNow(); }
    }
    public void close() throws Exception { ssh.close(); }
}
