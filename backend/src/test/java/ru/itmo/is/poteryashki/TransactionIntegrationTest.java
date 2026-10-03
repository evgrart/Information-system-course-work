package ru.itmo.is.poteryashki;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itmo.is.poteryashki.persistence.*;
import ru.itmo.is.poteryashki.service.*;
import static org.junit.jupiter.api.Assertions.*;

/** Separate committed transactions, only in the disposable lf3check_* fixture. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class TransactionIntegrationTest {
    @BeforeAll static void requireDisposableFixture() {
        if (!"lf3check_".equals(System.getenv("DB_PREFIX"))) throw new IllegalStateException("Integration tests require the disposable lf3check_ fixture");
    }
    @Autowired ListingService listings;
    @Autowired ReturnService returns;
    @Autowired AuctionService auctions;
    @Autowired SubscriptionService subscriptions;
    @Autowired SqlStore store;
    @Autowired DatabaseFunctions functions;
    @Autowired AccessPolicy access;
    @Autowired PlatformTransactionManager manager;
    @Test void twoTransactionsCannotReserveOneItemTwice() throws Exception {
        long listing=listings.create(3,new ListingService.Draft("found",1,1,"Race item","Two claimants",OffsetDateTime.now().minusDays(1),null,null,null));
        listings.submit(3,listing);listings.moderate(1,listing,true);
        long first=returns.claim(4,listing,"First proof"),second=returns.claim(5,listing,"Second proof");
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(long claim:new long[]{first,second}) results.add(pool.submit(()-> {
                barrier.await(10,TimeUnit.SECONDS);
                try { returns.reserve(3,claim);return true; } catch(DataAccessException ex) { return false; }
            }));
            int successes=0;for(var result:results) if(result.get(20,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);
            assertEquals(1,store.id("select count(*) from lf_claims where listing_id=? and state='accepted'",listing));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentCallbackCreatesSingleSubscription() throws Exception {
        long order=subscriptions.createOrder(3,1,UUID.randomUUID());UUID event=UUID.randomUUID();
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->subscriptions.payDemo(3,order,event));var second=pool.submit(()->subscriptions.payDemo(3,order,event));
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(1,store.id("select count(*) from lf_subscriptions where payment_order_id=?",order));
        } finally { pool.shutdownNow(); }
    }
    @Test void failedPaymentRollsBackEventAndOrderState() {
        long order=subscriptions.createOrder(3,1,UUID.randomUUID());
        var tx=new TransactionTemplate(manager);
        assertThrows(DataAccessException.class,()->tx.execute(status->{access.audit(null);return functions.activateSubscription(order,"invalid-payment",BigDecimal.ONE,"RUB");}));
        assertEquals("pending",store.one("select state from lf_payment_orders where id=?",order).get("state"));
        assertEquals(0,store.id("select count(*) from lf_payment_events where provider_event_id='invalid-payment'"));
    }
    @Test void closingProcedureAndFinalizeFunctionSelectActualWinner() throws Exception {
        var tx=new TransactionTemplate(manager);
        long[] pair=tx.execute(status->{
            access.audit(3L);
            long listing=store.id("insert into lf_listings(author_id,category_id,location_id,kind,title,description,event_at,official_reported_at,state,moderator_id,moderated_at) values (3,1,1,'found','Old item','Eligible for auction',clock_timestamp()-interval '8 months',clock_timestamp()-interval '7 months','published',1,clock_timestamp()) returning id");
            long permission=auctions.requestPermission(3,listing,"six_months","demo/ownership.pdf");auctions.reviewPermission(1,permission,true,null);
            long lot=auctions.create(3,listing,permission,OffsetDateTime.now().minusSeconds(1),OffsetDateTime.now().plusSeconds(4),BigDecimal.TEN,BigDecimal.ONE);
            long bid=auctions.bid(4,lot,BigDecimal.TEN,UUID.randomUUID());return new long[]{lot,bid};
        });
        Thread.sleep(4200);
        auctions.closeDue(100);
        assertEquals(pair[1],auctions.finalizeAuction(1,pair[0]));assertEquals(pair[1],auctions.finalizeAuction(1,pair[0]));
    }
}
