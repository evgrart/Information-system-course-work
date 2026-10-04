package ru.itmo.is.tools;

import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** A real HTTP server and browser share disposable PostgreSQL tables through a private tunnel. */
final class WebVerification {
    static void run(Path root)throws Exception{
        Path output=root.resolve("docs/part4/validation");Files.createDirectories(output);
        try(var remote=new Remote()){
            Fixture fixture=new Fixture(remote,root,"lf3check_");
            try(fixture;var tunnel=remote.new Tunnel()){
                int port;try(var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
                var env=new HashMap<String,String>();env.put("DB_URL","jdbc:postgresql://127.0.0.1:"+tunnel.port()+"/studs?currentSchema="+fixture.schema);
                env.put("DB_USER",remote.user);env.put("DB_PASSWORD",PgPass.find(remote.run("cat ~/.pgpass"),"pg",5432,"studs",remote.user));
                env.put("DB_PREFIX",fixture.prefix);env.put("APP_PORT",Integer.toString(port));env.put("JOBS_ENABLED","false");env.put("COURSE_SSH_PASSWORD","");
                env.put("STORAGE_ROOT",root.resolve(".tools/web-objects").toString());env.put("WEB_BASE_URL","http://127.0.0.1:"+port);env.put("COURSE_ROOT",root.toString());env.put("JAVA_HOME",System.getProperty("java.home"));env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD","1");
                var builder=new ProcessBuilder(Launcher.javaExecutable(),"-Xms64m","-Xmx256m","-Dfile.encoding=UTF-8","-jar",root.resolve("backend/build/libs/poteryashki.jar").toString()).directory(root.toFile());
                builder.environment().putAll(env);builder.environment().remove("DEBUG");builder.redirectErrorStream(true);builder.redirectOutput(output.resolve("server.txt").toFile());
                Process app=builder.start();
                try{
                    var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();boolean ready=false;
                    for(int attempt=0;attempt<120&&app.isAlive();attempt++){
                        try{if(client.send(HttpRequest.newBuilder(URI.create(env.get("WEB_BASE_URL"))).timeout(Duration.ofSeconds(2)).build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200){ready=true;break;}}catch(Exception ignored){}
                        Thread.sleep(500);
                    }
                    Verification.require(ready,"Web server did not start; see docs/part4/validation/server.txt");
                    String wrapper=root.resolve("backend/"+(Launcher.windows()?"gradlew.bat":"gradlew")).toString();
                    var result=Processes.capture(List.of(wrapper,":browserTest","--console=plain"),root.resolve("backend"),env,null,Duration.ofMinutes(10));
                    Files.writeString(output.resolve("browser.txt"),(result.out()+result.error()).lines().map(String::stripTrailing).collect(java.util.stream.Collectors.joining("\n","","\n")));result.checked();
                    System.out.println("PASS: browser workflows completed against disposable tables");
                }finally{app.destroy();if(!app.waitFor(20,java.util.concurrent.TimeUnit.SECONDS))app.destroyForcibly();}
            }finally{if(fixture.evidence!=null)Files.writeString(output.resolve("isolation.txt"),fixture.evidence);}
        }
    }
}
