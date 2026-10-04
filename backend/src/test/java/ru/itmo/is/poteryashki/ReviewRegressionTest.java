package ru.itmo.is.poteryashki;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.*;
import ru.itmo.is.poteryashki.service.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class ReviewRegressionTest {
    @BeforeAll static void guard(){assertEquals("lf3check_",System.getenv("DB_PREFIX"));}
    @Autowired SqlStore store;
    @Autowired AuctionService auctions;
    @Autowired ReturnService returns;
    @Autowired AdministrationService admin;
    @Autowired AccountService accounts;
    @Autowired OutboxService outbox;
    @Autowired SubscriptionService subscriptions;
    private long closingLot(boolean withBid)throws Exception{
        auctions.reviewPermission(1,2,true,"Основание проверено");
        long auction=auctions.create(3,6,2,OffsetDateTime.now().minusSeconds(1),OffsetDateTime.now().plusSeconds(3),new BigDecimal("100"),BigDecimal.TEN);
        if(withBid)auctions.bid(4,auction,new BigDecimal("100"),UUID.randomUUID());
        Thread.sleep(3100);return auction;
    }
    private void constraints(){store.update("set constraints all immediate");}
    @Test void winnerGetsOneConversationAndTwoPartyHandover()throws Exception{
        long auction=closingLot(true);auctions.finalizeAuction(1,auction);
        long claim=store.id("select id from lf_claims where auction_id=?",auction);
        long transfer=store.id("select id from lf_transfers where claim_id=?",claim);
        assertEquals(1,store.id("select count(*) from lf_conversations where claim_id=?",claim));
        auctions.finalizeAuction(1,auction);assertEquals(1,store.id("select count(*) from lf_claims where auction_id=?",auction));
        assertFalse(returns.confirm(3,transfer));assertTrue(returns.confirm(4,transfer));
        auctions.finalizeAuction(1,auction);assertEquals("returned",store.one("select state from lf_listings where id=6").get("state"));constraints();
    }
    @Test void auctionWithoutBidsCreatesNoHandover()throws Exception{
        long auction=closingLot(false);assertNull(auctions.finalizeAuction(1,auction));
        assertEquals(0,store.id("select count(*) from lf_claims where auction_id=?",auction));
        assertEquals("published",store.one("select state from lf_listings where id=6").get("state"));constraints();
    }
    @Test void winningBidRemainsValidAfterSubscriptionExpires()throws Exception{
        long auction=closingLot(true);
        store.update("update lf_subscriptions set starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' where user_id=4");
        auctions.finalizeAuction(1,auction);
        long transfer=store.id("select t.id from lf_transfers t join lf_claims c on c.id=t.claim_id where c.auction_id=?",auction);
        assertFalse(returns.confirm(4,transfer));assertTrue(returns.confirm(3,transfer));constraints();
    }
    @Test void handoverCannotBeCancelledAfterOneConfirmation(){
        long transfer=returns.reserve(3,1);returns.confirm(4,transfer);
        assertThrows(SecurityException.class,()->returns.cancel(4,1));
        assertEquals("reserved",store.one("select state from lf_listings where id=1").get("state"));constraints();
    }
    @Test void winnerCannotRepublishSoldThingByCancellingClaim()throws Exception{
        long auction=closingLot(true);auctions.finalizeAuction(1,auction);
        long claim=store.id("select id from lf_claims where auction_id=?",auction);
        assertThrows(SecurityException.class,()->returns.cancel(4,claim));constraints();
    }
    @Test void forgedAuctionHandoverRejectedByDatabase(){
        assertThrows(org.springframework.dao.DataAccessException.class,()->store.update("insert into lf_claims(listing_id,claimant_id,auction_id,evidence,state) values (2,4,1,'Подмена','accepted')"));
    }
    @Test void moderatorConversationAccessEndsWithComplaint(){
        assertThrows(SecurityException.class,()->returns.messages(1,1));
        long complaint=admin.complain(4,1L,null,"Нужна проверка");
        assertFalse(returns.messages(1,1).isEmpty());returns.send(1,1,"Уточните обстоятельства");
        admin.reviewComplaint(1,complaint,true,"Обстоятельства проверены");
        assertThrows(SecurityException.class,()->returns.messages(1,1));
        assertThrows(SecurityException.class,()->returns.send(1,1,"Дело уже закрыто"));constraints();
    }
    @Test void openComplaintDoesNotGiveOutsiderConversationAccess(){
        admin.complain(4,1L,null,"Нужна проверка");
        assertThrows(RuntimeException.class,()->returns.messages(5,1));
    }
    @Test void messageNotificationDoesNotRepeatOrLeakEvidence(){
        returns.send(4,1,"Частное содержание сообщения");outbox.deliver(100);
        var message=store.one("select body from lf_notifications where user_id=3 and kind='message.sent'");
        assertFalse(message.get("body").toString().contains("Частное содержание"));
        assertEquals(0,store.id("select count(*) from lf_notifications where user_id=4 and kind='message.sent'"));
        outbox.deliver(100);assertEquals(1,store.id("select count(*) from lf_notifications where user_id=3 and kind='message.sent'"));constraints();
    }
    @Test void expiryNotificationIsPersistentAndSentOnce(){
        store.update("update lf_subscriptions set starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' where user_id=4");
        outbox.deliver(100);outbox.deliver(100);
        assertEquals(1,store.id("select count(*) from lf_notifications where user_id=4 and kind='subscription.expired'"));constraints();
    }
    @Test void renewedSubscriptionDoesNotReceiveFalseExpiry(){
        store.update("update lf_subscriptions set starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' where user_id=4");
        long order=subscriptions.createOrder(4,1,UUID.randomUUID());subscriptions.payDemo(4,order,UUID.randomUUID());
        outbox.deliver(100);assertEquals(0,store.id("select count(*) from lf_notifications where user_id=4 and kind='subscription.expired'"));constraints();
    }
    @Test void rejectedProfileCanReadOwnDecisionAndMarkItRead(){
        long verification=store.id("select id from lf_verifications where user_id=6 and state='pending'");
        accounts.reviewProfile(1,verification,false,"Уточните имя");
        var notification=admin.notifications(6).stream().filter(n->n.get("kind").equals("profile.reviewed")).findFirst().orElseThrow();
        assertTrue(notification.get("body").toString().contains("Уточните имя"));
        admin.markRead(6,((Number)notification.get("id")).longValue());constraints();
    }
}
