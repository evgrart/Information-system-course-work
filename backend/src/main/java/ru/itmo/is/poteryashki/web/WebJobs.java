package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.scheduling.annotation.*;
import ru.itmo.is.poteryashki.service.*;

@Configuration @EnableScheduling @RequiredArgsConstructor
@ConditionalOnWebApplication @ConditionalOnProperty(name="app.jobs-enabled",havingValue="true",matchIfMissing=true)
public class WebJobs {
    private final AuctionService auctions;private final OutboxService outbox;
    @Scheduled(fixedDelay=30000,initialDelay=30000) public void tick(){
        try{auctions.closeDue(100);outbox.deliver(20);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(WebJobs.class).warn("Background processing failed; next run will retry");}
    }
}
