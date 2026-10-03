package ru.itmo.is.poteryashki;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.itmo.is.poteryashki.service.*;

@Component @RequiredArgsConstructor @ConditionalOnProperty(name="app.demo",havingValue="true")
public class DemoRunner implements ApplicationRunner {
    private final AccountService accounts;
    private final ListingService listings;
    private final AdministrationService admin;
    @Override public void run(ApplicationArguments args) {
        var identity=accounts.authenticate("finder@example.invalid","DemoCourse2026!");
        System.out.println("DEMO identity="+identity.userId());
        System.out.println("DEMO search="+listings.search(identity.userId(),null,null,null,null,null,null,null,5));
        System.out.println("DEMO organizations="+listings.organizations(identity.userId()));
        System.out.println("DEMO notifications="+admin.notifications(identity.userId()).size());
        System.out.println("DEMO completed: service -> repository -> PostgreSQL");
    }
}
