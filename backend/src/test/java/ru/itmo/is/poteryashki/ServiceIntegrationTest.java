package ru.itmo.is.poteryashki;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.service.*;
import ru.itmo.is.poteryashki.persistence.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL, isolated stage-two fixture, each scenario rolled back. */
@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class ServiceIntegrationTest {
    @BeforeAll static void requireDisposableFixture() {
        if (!"lf3check_".equals(System.getenv("DB_PREFIX"))) throw new IllegalStateException("Integration tests require the disposable lf3check_ fixture");
    }
    @Autowired AccountService accounts;
    @Autowired SubscriptionService subscriptions;
    @Autowired ListingService listings;
    @Autowired ReturnService returns;
    @Autowired AuctionService auctions;
    @Autowired AdministrationService admin;
    @Autowired SqlStore store;
    @Autowired DatabaseFunctions functions;
    @Autowired AccessPolicy access;
    @Autowired OutboxService outbox;
    private static final String PASS="DemoCourse2026!";
    private ListingService.Draft draft(String kind) {
        return new ListingService.Draft(kind,1,1,"Umbrella","Found near garden",OffsetDateTime.now().minusDays(1),null,null,null);
    }
    private long published() {
        long listing=listings.create(3,draft("found"));listings.submit(3,listing);listings.moderate(1,listing,true);return listing;
    }
    private void constraints() { store.update("set constraints all immediate"); }
    @Test void authenticationUsesBcryptAndNeverReturnsPassword() {
        var identity=accounts.authenticate("FINDER@example.invalid",PASS);
        assertEquals(3,identity.userId());accounts.checkIdentity(identity);
        assertThrows(SecurityException.class,()->accounts.authenticate("finder@example.invalid","incorrect"));
        assertThrows(SecurityException.class,()->accounts.authenticate("blocked@example.invalid",PASS));
    }
    @Test void registrationConfirmationAndIndependentModeration() {
        var registered=accounts.register("new@example.invalid","New user",PASS);
        assertNotEquals(registered.confirmationToken(),store.one("select token_hash from lf_verification_tokens where user_id=?",registered.userId()).get("token_hash"));
        assertEquals(registered.userId(),accounts.confirmEmail(registered.confirmationToken()));
        long verification=store.id("select id from lf_verifications where user_id=?",registered.userId());
        accounts.reviewProfile(1,verification,true,null);
        assertEquals("verified",store.one("select state from lf_users where id=?",registered.userId()).get("state"));constraints();
    }
    @Test void confirmationTokenCannotBeConsumedTwice() {
        var registered=accounts.register("once@example.invalid","Once",PASS);
        accounts.confirmEmail(registered.confirmationToken());
        assertThrows(DataAccessException.class,()->accounts.confirmEmail(registered.confirmationToken()));
    }
    @Test void passwordResetRevokesIdentityAndRefreshTokens() {
        var identity=accounts.authenticate("owner@example.invalid",PASS);
        String token=accounts.requestPasswordReset("owner@example.invalid").orElseThrow();
        accounts.resetPassword(token,"AnotherDemo2026!");
        assertEquals(4,accounts.authenticate("owner@example.invalid","AnotherDemo2026!").userId());
        assertThrows(SecurityException.class,()->accounts.checkIdentity(identity));
        assertEquals(0,store.id("select count(*) from lf_refresh_tokens where user_id=4 and revoked_at is null"));
    }
    @Test void unknownEmailResetDoesNotIssueToken() { assertTrue(accounts.requestPasswordReset("none@example.invalid").isEmpty()); }
    @Test void paymentOrderAndEventRetriesCreateOnePeriod() {
        int original=subscriptions.periods(3).size();
        UUID key=UUID.randomUUID(),event=UUID.randomUUID();long order=subscriptions.createOrder(3,1,key);
        assertEquals(order,subscriptions.createOrder(3,1,key));
        long period=subscriptions.payDemo(3,order,event);assertEquals(period,subscriptions.payDemo(3,order,event));
        assertEquals(1,store.id("select count(*) from lf_subscriptions where payment_order_id=?",order));
        assertEquals(original+1,subscriptions.periods(3).size());constraints();
    }
    @Test void anotherUserCannotPayOrder() { assertThrows(SecurityException.class,()->subscriptions.payDemo(4,1,UUID.randomUUID())); }
    @Test void publicationAndEditRequireNewReview() {
        long listing=published();assertEquals("published",listings.state(3,listing));
        listings.edit(3,listing,new ListingService.Draft("found",1,1,"Edited","Different description",OffsetDateTime.now().minusDays(1),null,null,null));
        assertEquals("pending",listings.state(3,listing));constraints();
    }
    @Test void anotherUserCannotEditListing() { assertThrows(SecurityException.class,()->listings.edit(4,1,draft("found"))); }
    @Test void moderatorCannotPublishUnsubmittedListing() {
        long listing=listings.create(3,draft("found"));assertThrows(SecurityException.class,()->listings.moderate(1,listing,true));
    }
    @Test void validationRejectsFutureEvent() {
        assertThrows(IllegalArgumentException.class,()->listings.create(3,new ListingService.Draft("found",1,1,"Title","Text",OffsetDateTime.now().plusDays(1),null,null,null)));
    }
    @Test void imageMetadataIsOwnedAndBounded() {
        long listing=listings.create(3,draft("found"));
        assertTrue(listings.attachImage(3,listing,"listings/"+listing+"/photo.jpg","image/jpeg",1000,1)>0);
        assertThrows(SecurityException.class,()->listings.attachImage(4,listing,"listings/"+listing+"/photo2.jpg","image/jpeg",1000,2));
        assertThrows(SecurityException.class,()->listings.attachImage(3,listing,"listings/"+listing+"/photo.exe","application/octet-stream",1000,2));
    }
    @Test void searchCallsFunctionAndReturnsBoundedPublicProjection() {
        var found=listings.search(3,null,"found",null,null,null,null,null,2);assertEquals(2,found.size());
        assertFalse(found.get(0).containsKey("evidence"));assertFalse(found.get(0).containsKey("password_hash"));
        assertFalse(listings.detail(3,1).containsKey("author_id"));
    }
    @Test void invalidSearchLimitRejectedByPostgres() { assertThrows(DataAccessException.class,()->listings.search(3,null,null,null,null,null,null,null,101)); }
    @Test void privateAttributesOnlyForOwner() {
        assertFalse(listings.privateAttributes(3,1).isEmpty());assertThrows(SecurityException.class,()->listings.privateAttributes(4,1));
    }
    @Test void metroDirectoryReturnsOfficialContacts() {
        var org=listings.organizations(3).get(0);assertEquals("8-800-350-11-55",org.get("phone"));assertTrue(org.get("source_url").toString().startsWith("https://metro.spb.ru/"));
    }
    @Test void returnRequiresBothPartiesAndAuditActor() {
        long listing=published(),claim=returns.claim(4,listing,"Hidden mark matches"),transfer=returns.reserve(3,claim);
        assertEquals(transfer,returns.reserve(3,claim));assertFalse(returns.confirm(3,transfer));assertTrue(returns.confirm(4,transfer));assertTrue(returns.confirm(4,transfer));
        assertEquals("returned",listings.state(3,listing));
        assertTrue(store.exists("select exists(select 1 from lf_audit_entries where actor_id=4 and entity_id=?)",transfer));constraints();
    }
    @Test void acceptedClaimCanBeCancelledAtomically() {
        long listing=published(),claim=returns.claim(4,listing,"My item");returns.reserve(3,claim);returns.cancel(4,claim);
        assertEquals("published",listings.state(3,listing));constraints();
    }
    @Test void metroItemMustBeCollectedThroughOrganization() { assertThrows(SecurityException.class,()->returns.claim(4,3,"Mine")); }
    @Test void privateConversationRejectsOutsider() {
        long id=returns.send(4,1,"Please check the hidden mark");assertTrue(returns.messages(3,1).stream().anyMatch(r->((Number)r.get("id")).longValue()==id));
        assertThrows(SecurityException.class,()->returns.messages(5,1));
    }
    @Test void expiredSubscriptionCanCompletePreviouslyAgreedReturn() {
        long listing=published(),claim=returns.claim(4,listing,"Matches"),transfer=returns.reserve(3,claim);
        store.update("update lf_subscriptions set starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' where user_id in(3,4)");
        assertFalse(returns.confirm(3,transfer));assertTrue(returns.confirm(4,transfer));constraints();
    }
    @Test void expiredSubscriptionCannotSearch() {
        store.update("update lf_subscriptions set starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' where user_id=3");
        assertThrows(DataAccessException.class,()->listings.search(3,null,null,null,null,null,null,null,20));
    }
    @Test void moderatorCanResolveDisputedReturn() {
        long listing=published(),claim=returns.claim(4,listing,"Proof"),transfer=returns.reserve(3,claim);
        returns.resolve(1,transfer,"Verified handover in person");assertEquals("returned",listings.state(3,listing));constraints();
    }
    @Test void bidRetryDoesNotInsertDuplicate() {
        UUID key=UUID.randomUUID();long bid=auctions.bid(4,1,new BigDecimal("1200"),key);
        assertEquals(bid,auctions.bid(4,1,new BigDecimal("1200"),key));assertEquals(1,store.id("select count(*) from lf_bids where request_key=?",key));constraints();
    }
    @Test void changedBidWithSameKeyRejectedByDatabase() {
        UUID key=UUID.randomUUID();auctions.bid(4,1,new BigDecimal("1200"),key);
        assertThrows(DataAccessException.class,()->auctions.bid(4,1,new BigDecimal("1300"),key));
    }
    @Test void sellerCannotBid() { assertThrows(DataAccessException.class,()->auctions.bid(3,1,new BigDecimal("1200"),UUID.randomUUID())); }
    @Test void blockedBidderCannotReplayPreviouslyAcceptedBid() {
        admin.block(1,5,"Нарушение правил торгов");
        assertThrows(DataAccessException.class,()->auctions.bid(5,1,new BigDecimal("1100"),UUID.fromString("20000000-0000-0000-0000-000000000002")));
    }
    @Test void newClaimSuspendsAuctionAndModeratorCanCancelIt() {
        returns.claim(4,2,"Owner proof");assertEquals("suspended",store.one("select state from lf_auctions where id=1").get("state"));
        auctions.cancel(1,1);assertEquals("cancelled",store.one("select state from lf_auctions where id=1").get("state"));constraints();
    }
    @Test void blockingBidderRevokesIdentityAndFreezesLot() {
        var identity=accounts.authenticate("buyer@example.invalid",PASS);admin.block(1,5,"Нарушение правил торгов");
        assertThrows(SecurityException.class,()->accounts.checkIdentity(identity));assertEquals("suspended",store.one("select state from lf_auctions where id=1").get("state"));
    }
    @Test void pendingPermissionReviewedByModerator() {
        auctions.reviewPermission(1,2,true,null);assertEquals("approved",store.one("select state from lf_auction_permissions where id=2").get("state"));
    }
    @Test void auctionNeedsValidOwnershipPermission() {
        assertThrows(DataAccessException.class,()->auctions.create(3,1,1,OffsetDateTime.now(),OffsetDateTime.now().plusDays(1),new BigDecimal("100"),BigDecimal.TEN));
    }
    @Test void documentsCannotBeSold() { assertThrows(DataAccessException.class,()->auctions.requestPermission(3,4,"six_months","demo/claim.pdf")); }
    @Test void moderatorCannotReadAuditWithoutRole() { assertThrows(DataAccessException.class,()->admin.audit(3,null,10)); }
    @Test void administratorCanManageTariffsAndRoles() {
        long tariff=admin.tariff(2,"New tariff",new BigDecimal("199"),30);admin.retireTariff(2,tariff);admin.grantRole(2,3,"moderator","Назначение модератора");
        assertEquals(false,store.one("select active from lf_tariffs where id=?",tariff).get("active"));assertTrue(store.exists("select exists(select 1 from lf_user_roles where user_id=3 and role_code='moderator')"));
        assertEquals(2,store.id("select count(*) from lf_audit_entries where entity_table='lf3check_tariffs' and entity_id=? and actor_id=2",tariff));
    }
    @Test void accessChangeRequiresReasonAndLastAdministratorIsProtected() {
        assertThrows(SecurityException.class,()->admin.block(1,5," "));
        assertThrows(SecurityException.class,()->admin.grantRole(2,3,"admin"," "));
        assertThrows(SecurityException.class,()->admin.revokeRole(2,2,"admin","Test removal"));
    }
    @Test void roleRevocationRecordsReasonAndRechecksAccess() {
        admin.grantRole(2,3,"admin","Temporary appointment");admin.revokeRole(2,3,"admin","Appointment ended");
        assertFalse(store.exists("select exists(select 1 from lf_user_roles where user_id=3 and role_code='admin')"));
        assertEquals(2,store.id("select count(*) from lf_audit_entries where entity_id=3 and action like 'role.%'"));
    }
    @Test void complaintReviewedAndNotificationRead() {
        long complaint=admin.complain(4,1L,null,"Wrong description");admin.reviewComplaint(1,complaint,true,"Corrected");
        var notification=admin.notifications(3).get(0);admin.markRead(3,((Number)notification.get("id")).longValue());assertFalse(admin.audit(1,null,10).isEmpty());
    }
    @Test void backgroundCallsCanonicalClosingProcedure() { auctions.closeDue(10);constraints(); }
    @Test void invalidProcedureBatchRejectedByDatabase() { assertThrows(DataAccessException.class,()->auctions.closeDue(0)); }
    @Test void outboxDeliveryAndRetryDoNotDuplicateNotifications() {
        long listing=published(),claim=returns.claim(4,listing,"Proof");returns.reserve(3,claim);
        assertTrue(outbox.deliver(100)>0);
        long count=store.id("select count(*) from lf_notifications");assertEquals(0,outbox.deliver(100));
        assertEquals(count,store.id("select count(*) from lf_notifications"));constraints();
    }
}
