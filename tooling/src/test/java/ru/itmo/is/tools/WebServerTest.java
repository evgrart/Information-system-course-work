package ru.itmo.is.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WebServerTest {
    @TempDir Path root;
    @Test void refusesToStopPidOfAnotherJavaProcess()throws Exception{
        Path jar=root.resolve("backend/build/libs/poteryashki.jar");Files.createDirectories(jar.getParent());Files.write(jar,new byte[]{0});
        Files.createDirectories(root.resolve("data"));Files.writeString(root.resolve("data/web.pid"),Long.toString(ProcessHandle.current().pid()));
        var failure=assertThrows(IllegalStateException.class,()->WebServer.run(root,List.of("stop")));
        assertTrue(failure.getMessage().contains("another process")||failure.getMessage().contains("identify recorded process"));assertTrue(ProcessHandle.current().isAlive());
    }
}
