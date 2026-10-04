package ru.itmo.is.tools;

import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Own PID file and an exact JAR argument prevent interference with other applications. */
final class WebServer {
    static void run(Path root,List<String> args)throws Exception{
        if(args.size()!=1||!Set.of("start","stop","status").contains(args.get(0)))throw new IllegalArgumentException("web start|stop|status");
        Path jar=root.resolve("backend/build/libs/poteryashki.jar").toRealPath();Path folder=root.resolve("data");Files.createDirectories(folder);
        Verification.require(!Files.isSymbolicLink(folder),"Symlink data directory");Path pidfile=folder.resolve("web.pid");
        ProcessHandle existing=null;
        if(Files.exists(pidfile)){
            Verification.require(!Files.isSymbolicLink(pidfile),"Symlink PID file");long pid=Long.parseLong(Files.readString(pidfile).strip());existing=ProcessHandle.of(pid).orElse(null);
            if(existing!=null&&existing.isAlive()){
                var arguments=existing.info().arguments().orElseThrow(()->new IllegalStateException("Cannot identify recorded process; refusing to stop it"));
                Verification.require(Arrays.asList(arguments).contains(jar.toString()),"Recorded PID belongs to another process");
            }else existing=null;
        }
        String action=args.get(0);
        if(action.equals("status")){System.out.println(existing==null?"Coursework web server is stopped":"Coursework web server PID "+existing.pid());return;}
        if(action.equals("stop")){
            if(existing!=null){existing.destroy();for(int i=0;i<100&&existing.isAlive();i++)Thread.sleep(100);Verification.require(!existing.isAlive(),"Server did not stop; no forced kill was issued");}
            Files.deleteIfExists(pidfile);System.out.println("Coursework web server stopped");return;
        }
        Verification.require(existing==null,"Coursework web server already running");
        int port=Integer.parseInt(System.getenv().getOrDefault("APP_PORT","18081"));
        try(var socket=new ServerSocket()){socket.bind(new InetSocketAddress("127.0.0.1",port));}
        var command=new ArrayList<String>();if(!Launcher.windows())command.add("nohup");
        command.addAll(List.of(Launcher.javaExecutable(),"-Xms64m","-Xmx256m","-Dfile.encoding=UTF-8","-jar",jar.toString(),"--app.demo=false","--spring.main.web-application-type=servlet","--server.address=127.0.0.1","--server.port="+port));
        var builder=new ProcessBuilder(command).directory(root.toFile());var env=builder.environment();env.remove("COURSE_SSH_PASSWORD");
        env.putIfAbsent("DB_URL","jdbc:postgresql://pg:5432/studs?currentSchema=s465826");env.putIfAbsent("DB_USER","s465826");
        if(env.getOrDefault("DB_PASSWORD","").isEmpty()){
            URI uri=URI.create(env.get("DB_URL").replaceFirst("^jdbc:",""));Path pass=Path.of(env.getOrDefault("PGPASSFILE",Path.of(System.getProperty("user.home"),".pgpass").toString()));
            env.put("DB_PASSWORD",PgPass.find(Files.readString(pass),uri.getHost(),uri.getPort()<0?5432:uri.getPort(),uri.getPath().substring(1),env.get("DB_USER")));
        }
        builder.redirectInput(ProcessBuilder.Redirect.from(Launcher.windows()?Path.of("NUL").toFile():Path.of("/dev/null").toFile()));
        Path log=folder.resolve("web.log");Verification.require(!Files.isSymbolicLink(log),"Symlink log file");builder.redirectErrorStream(true);builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Process child=builder.start();Files.writeString(pidfile,Long.toString(child.pid()));
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        for(int i=0;i<100&&child.isAlive();i++){
            try{var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port)).timeout(Duration.ofSeconds(2)).build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()==200&&response.body().contains("потеряшки")){System.out.println("PASS: coursework HTTP server at 127.0.0.1:"+port+", PID "+child.pid());return;}}catch(Exception ignored){}
            Thread.sleep(500);
        }
        if(child.isAlive())child.destroy();Files.deleteIfExists(pidfile);throw new IllegalStateException("Server startup failed; inspect data/web.log");
    }
}
