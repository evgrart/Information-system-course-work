package ru.itmo.is.poteryashki.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Demo letters are visible only to their authenticated recipient. */
@Service
public class PrivateMailbox {
    public record Letter(String subject,String text,String link,Instant at) {}
    private final Map<Long,List<Letter>> inbox=new ConcurrentHashMap<>();
    private final JavaMailSenderImpl sender;
    private final String publicUrl,from;
    public PrivateMailbox(@Value("${app.mail-mode}") String mode,@Value("${app.public-url}") String url,
            @Value("${app.mail-host}") String host,@Value("${app.mail-port}") int port,
            @Value("${app.mail-user}") String user,@Value("${app.mail-password}") String password,@Value("${app.mail-from}") String from) {
        this.publicUrl=url;this.from=from;
        if(!Set.of("demo","smtp").contains(mode)) throw new IllegalArgumentException("MAIL_MODE must be demo or smtp");
        if(mode.equals("demo")) sender=null;
        else {
            if(host.isBlank()) throw new IllegalArgumentException("MAIL_HOST is required");
            sender=new JavaMailSenderImpl();sender.setHost(host);sender.setPort(port);sender.setUsername(user);sender.setPassword(password);
            var p=sender.getJavaMailProperties();p.setProperty("mail.smtp.auth",Boolean.toString(!user.isBlank()));
            p.setProperty("mail.smtp.starttls.enable","true");p.setProperty("mail.smtp.starttls.required","true");
            p.setProperty("mail.smtp.connectiontimeout","5000");p.setProperty("mail.smtp.timeout","5000");p.setProperty("mail.smtp.writetimeout","5000");
        }
    }
    public boolean demo(){return sender==null;}
    public List<Letter> letters(long user){return inbox.getOrDefault(user,List.of());}
    public void send(long user,String email,String subject,String text,String path){
        if(demo()) {
            inbox.compute(user,(id,old)->{var list=new ArrayList<Letter>();list.add(new Letter(subject,text,path,Instant.now()));if(old!=null)list.addAll(old);return List.copyOf(list.subList(0,Math.min(20,list.size())));});
        } else {
            var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);message.setSubject(subject);message.setText(text+"\n"+publicUrl+path);
            sender.send(message);
        }
    }
}
