package ru.itmo.is.poteryashki.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class AuctionService {
    private final SqlStore store;
    private final DatabaseFunctions functions;
    private final AccessPolicy access;
    public long requestPermission(long actor,long listing,String basis,String evidenceKey) {
        access.actor(actor,true);
        require(evidenceKey!=null && !evidenceKey.isBlank(),"Evidence object required");
        return store.id("insert into lf_auction_permissions(listing_id,applicant_id,basis,evidence_object_key) values (?,?,?,?) returning id",listing,actor,basis,evidenceKey);
    }
    public void reviewPermission(long moderator,long permission,boolean approve,String reason) {
        access.moderator(moderator);
        require(store.update("update lf_auction_permissions set state=?,moderator_id=?,reviewed_at=clock_timestamp(),reason=? where id=? and state='pending'",approve?"approved":"rejected",moderator,reason,permission)==1,"Pending permission required");
    }
    public long create(long actor,long listing,long permission,OffsetDateTime start,OffsetDateTime end,BigDecimal price,BigDecimal step) {
        access.actor(actor,true);money(price);money(step);
        if (start==null || end==null || !end.isAfter(start)) throw new IllegalArgumentException("Invalid auction window");
        return store.id("insert into lf_auctions(listing_id,seller_id,permission_id,starts_at,ends_at,start_price,bid_step) values (?,?,?,?,?,?,?) returning id",listing,actor,permission,start,end,price,step);
    }
    public long bid(long actor,long auction,BigDecimal amount,UUID key) {
        access.audit(actor);money(amount);
        if (key==null) throw new IllegalArgumentException("Idempotency key required");
        long id=functions.bid(auction,actor,amount,key);
        // Also authorize an idempotent retry, for which the SQL function returns before inserting.
        access.actor(actor,true);
        return id;
    }
    public Long finalizeAuction(long moderator,long auction) {
        access.moderator(moderator);return functions.finalizeAuction(auction);
    }
    public void cancel(long moderator,long auction) {
        access.moderator(moderator);
        var row=store.one("select listing_id from lf_auctions where id=?",auction);
        store.one("select id from lf_listings where id=? for update",owner(row,"listing_id"));
        require(store.update("update lf_auctions set state='cancelled',closed_at=clock_timestamp() where id=? and state in('scheduled','active','suspended')",auction)==1,"Unfinished auction required");
    }
    /** Intended for a trusted scheduler or command runner, not a user endpoint. */
    public void closeDue(int limit) { access.audit(null);functions.closeDue(limit); }
    private static void money(BigDecimal value) {
        if (value==null || value.signum()<=0 || value.stripTrailingZeros().scale()>2 || value.compareTo(new BigDecimal("9999999999.99"))>0)
            throw new IllegalArgumentException("Positive money with two decimal places required");
    }
}
