package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.SqlStore;
import ru.itmo.is.poteryashki.service.*;
import java.time.OffsetDateTime;
import java.util.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

/** Read models for templates. Every private projection checks its owner or staff role. */
@Service @RequiredArgsConstructor @Transactional
public class WebQueries {
    private final SqlStore store;
    private final AccessPolicy access;
    private final ListingService listings;
    private final ReturnService returns;
    public Map<String,Object> profile(long user) {
        return store.one("select id,email,display_name,state,email_confirmed_at,(select max(ends_at) from lf_subscriptions s where s.user_id=u.id and s.starts_at<=clock_timestamp() and s.ends_at>clock_timestamp()) as paid_until,(select state from lf_verifications v where v.user_id=u.id order by id desc limit 1) as verification_state from lf_users u where id=?", user);
    }
    public List<Map<String,Object>> tariffs() { return store.rows("select id,title,amount,duration_days from lf_tariffs where active order by amount,id"); }
    public List<Map<String,Object>> categories() { return store.rows("select id,name,auction_allowed from lf_categories order by name"); }
    public List<Map<String,Object>> locations() { return store.rows("select id,city,description as name,metro_station,organization_id from lf_locations order by city,description"); }
    public long accountForMail(String email) { return store.id("select id from lf_users where email=?",email.strip().toLowerCase(Locale.ROOT)); }
    public List<Map<String,Object>> metro() { return store.rows("select id,name,phone,address,instructions,source_url,verified_on,verified_on<current_date-30 as needs_review from lf_organizations order by id"); }
    public Map<String,Object> counts() { return store.one("select (select count(*) from lf_listings where state='published') as listings,(select count(*) from lf_transfers where completed_at is not null) as returned"); }
    public List<Map<String,Object>> catalog(long user, String text, String kind, Long category, Long location, OffsetDateTime from, OffsetDateTime to, Long cursor) {
        var matches = listings.search(user, text, kind, category, location, from, to, cursor, 24);
        var result = new ArrayList<Map<String,Object>>();
        for (var match : matches) result.add(store.one("select p.*,(select id from lf_listing_images i where i.listing_id=p.id order by position limit 1) as photo_id from lf_public_listings p where p.id=?", match.get("id")));
        return result;
    }
    public Map<String,Object> listing(long user, long id) { return listings.detail(user,id); }
    public List<Map<String,Object>> photos(long user, long listing) {
        visibleListing(user, listing);
        return store.rows("select id,position from lf_listing_images where listing_id=? order by position", listing);
    }
    public Map<String,Object> ownedListing(long user, long id) {
        require(store.exists("select exists(select 1 from lf_listings where id=? and author_id=?)", id,user), "Not your listing");
        return store.one("select id,kind,category_id,location_id,title,description,event_at,event_until,state,custodian_org_id,official_reported_at from lf_listings where id=?", id);
    }
    public List<Map<String,Object>> ownListings(long user) {
        return store.rows("select id,title,kind,state,event_at,created_at from lf_listings where author_id=? order by id desc",user);
    }
    public List<Map<String,Object>> orders(long user) { return store.rows("select o.id,o.state,o.amount,o.created_at,t.title from lf_payment_orders o join lf_tariffs t on t.id=o.tariff_id where o.user_id=? order by o.id desc",user); }
    public List<Map<String,Object>> claims(long user) {
        access.actor(user,false);
        return store.rows("select c.id,c.listing_id,c.state,c.evidence,c.claimant_id,l.author_id,l.title,d.id as conversation_id,t.id as transfer_id,t.finder_confirmed_at,t.owner_confirmed_at,t.completed_at from lf_claims c join lf_listings l on l.id=c.listing_id left join lf_conversations d on d.claim_id=c.id left join lf_transfers t on t.claim_id=c.id where ? in(c.claimant_id,l.author_id) order by c.id desc",user);
    }
    public Map<String,Object> conversation(long user, long conversation) {
        returns.messages(user,conversation);
        return store.one("select d.id,c.id as claim_id,c.state,c.evidence,c.claimant_id,l.author_id,l.title,t.id as transfer_id,t.finder_confirmed_at,t.owner_confirmed_at,t.completed_at from lf_conversations d join lf_claims c on c.id=d.claim_id join lf_listings l on l.id=c.listing_id left join lf_transfers t on t.claim_id=c.id where d.id=?",conversation);
    }
    public List<Map<String,Object>> auctions(long user) {
        access.actor(user,true);
        return store.rows("select a.id,a.listing_id,a.seller_id,a.state,a.starts_at,a.ends_at,a.start_price,a.bid_step,l.title,l.description,(select max(amount) from lf_bids b where b.auction_id=a.id) as highest_bid,(select count(*) from lf_bids b where b.auction_id=a.id) as bid_count,(select bidder_id from lf_bids b where b.id=a.winner_bid_id) as winner_id from lf_auctions a join lf_listings l on l.id=a.listing_id where l.state in('published','auctioned') order by a.id desc");
    }
    public Map<String,Object> auction(long user,long id) {
        access.actor(user,true);
        return store.one("select a.id,a.listing_id,a.seller_id,a.state,a.starts_at,a.ends_at,a.start_price,a.bid_step,l.title,l.description,(select max(amount) from lf_bids b where b.auction_id=a.id) as highest_bid,(select bidder_id from lf_bids b where b.id=a.winner_bid_id) as winner_id from lf_auctions a join lf_listings l on l.id=a.listing_id where a.id=? and (l.state in('published','auctioned') or a.seller_id=?)",id,user);
    }
    public List<Map<String,Object>> permissions(long user) { return store.rows("select p.id,p.listing_id,p.basis,p.state,p.reason,l.title from lf_auction_permissions p join lf_listings l on l.id=p.listing_id where p.applicant_id=? order by p.id desc",user); }
    public List<Map<String,Object>> verifications(long staff) {
        access.moderator(staff);
        return store.rows("select v.id,v.state,v.reason,u.id as user_id,u.display_name,u.email_confirmed_at from lf_verifications v join lf_users u on u.id=v.user_id where v.state='pending' order by v.id");
    }
    public List<Map<String,Object>> pendingListings(long staff) {
        access.moderator(staff);
        return store.rows("select l.id,l.title,l.description,l.kind,l.event_at,l.official_reported_at,l.custodian_org_id,u.display_name,c.name as category,p.description as place from lf_listings l join lf_users u on u.id=l.author_id join lf_categories c on c.id=l.category_id join lf_locations p on p.id=l.location_id where l.state='pending' order by l.id");
    }
    public List<Map<String,Object>> pendingPermissions(long staff) {
        access.moderator(staff);
        return store.rows("select p.id,p.listing_id,p.basis,l.title,l.official_reported_at,u.display_name from lf_auction_permissions p join lf_listings l on l.id=p.listing_id join lf_users u on u.id=p.applicant_id where p.state='pending' order by p.id");
    }
    public List<Map<String,Object>> complaints(long staff) {
        access.moderator(staff);
        return store.rows("select id,listing_id,reported_user_id,reason,state from lf_complaints where state='pending' order by id");
    }
    public List<Map<String,Object>> pendingTransfers(long staff) {
        access.moderator(staff);
        return store.rows("select t.id,l.title,c.state from lf_transfers t join lf_claims c on c.id=t.claim_id join lf_listings l on l.id=c.listing_id where t.completed_at is null and c.state='accepted' order by t.id");
    }
    public List<Map<String,Object>> users(long admin) {
        access.admin(admin);
        return store.rows("select u.id,u.display_name,u.email,u.state,string_agg(r.role_code,', ' order by r.role_code) as roles from lf_users u left join lf_user_roles r on r.user_id=u.id group by u.id order by u.id");
    }
    public List<Map<String,Object>> allTariffs(long admin) { access.admin(admin);return store.rows("select id,title,amount,duration_days,active from lf_tariffs order by id"); }
    public void visibleListing(long user,long listing) {
        if (store.exists("select exists(select 1 from lf_listings where id=? and author_id=?)",listing,user)) return;
        var roles = store.rows("select role_code from lf_user_roles where user_id=? and role_code in('moderator','admin')",user);
        if (!roles.isEmpty()) { access.moderator(user);return; }
        listings.detail(user,listing);
    }
    public Map<String,Object> photo(long user,long id) {
        var row=store.one("select listing_id,object_key,media_type from lf_listing_images where id=?",id);
        visibleListing(user,owner(row,"listing_id"));return row;
    }
    public Map<String,Object> evidence(long user,long id) {
        var row=store.one("select applicant_id,evidence_object_key from lf_auction_permissions where id=?",id);
        if(owner(row,"applicant_id")!=user) access.moderator(user);
        return row;
    }
}
