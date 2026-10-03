package ru.itmo.is.poteryashki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class PoteryashkiApplication {
    public static void main(String[] args) { SpringApplication.run(PoteryashkiApplication.class, args); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
