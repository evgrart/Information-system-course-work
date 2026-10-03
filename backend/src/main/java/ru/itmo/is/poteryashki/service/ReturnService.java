package ru.itmo.is.poteryashki.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class ReturnService {
    private final SqlStore store;
    private final DatabaseFunctions functions;
    private final AccessPolicy access;
    public long claim(long actor,long listing,String evidence) {
        access.actor(actor,true);
        require(evidence!=null && !evidence.isBlank(),"Evidence required");
        var row=store.one("select custodian_org_id from lf_listings where id=? for update",listing);
        require(row.get("custodian_org_id")==null,"Contact the custodian organization");
        long claim=store.id("insert into lf_claims(listing_id,claimant_id,evidence) values (?,?,?) returning id",listing,actor,evidence);
        store.update("insert into lf_conversations(claim_id) values (?)",claim);
        store.update("insert into lf_outbox_events(kind,aggregate_id,payload) values ('claim.created',?,jsonb_build_object('listing_id',?::bigint))",claim,listing);
        return claim;
    }
    public long reserve(long finder,long claim) { access.audit(finder);return functions.reserve(claim,finder); }
    public boolean confirm(long actor,long transfer) { access.audit(actor);return functions.confirm(transfer,actor); }
    public void resolve(long moderator,long transfer,String reason) { access.audit(moderator);functions.resolveReturn(transfer,moderator,reason); }
    public void reject(long finder,long claim) {
        access.actor(finder,false);
        var row=claimRow(claim);require(owner(row,"author_id")==finder,"Not your found item");
        require(row.get("state").equals("pending"),"Pending claim required");
        store.update("update lf_claims set state='rejected' where id=?",claim);
    }
    public void cancel(long actor,long claim) {
        access.actor(actor,false);var row=claimRow(claim);
        require(owner(row,"claimant_id")==actor,"Not your claim");
        require(Set.of("pending","accepted").contains(row.get("state")),"Claim already closed");
        if (row.get("state").equals("accepted"))
            store.update("update lf_listings set state='published' where id=?",owner(row,"listing_id"));
        store.update("update lf_claims set state='cancelled' where id=?",claim);
    }
    public long send(long actor,long conversation,String body) {
        store.one("select id from lf_users where id=? for update",actor);
        access.actor(actor,false);participant(actor,conversation);
        require(body!=null && !body.isBlank() && body.length()<=4000,"Invalid message");
        require(!store.exists("select count(*)>=30 from lf_messages where sender_id=? and sent_at>clock_timestamp()-interval '1 minute'",actor),"Message rate exceeded");
        return store.id("insert into lf_messages(conversation_id,sender_id,body) values (?,?,?) returning id",conversation,actor,body);
    }
    public List<Map<String,Object>> messages(long actor,long conversation) {
        access.actor(actor,false);participant(actor,conversation);
        return store.rows("select id,sender_id,body,sent_at from lf_messages where conversation_id=? order by id",conversation);
    }
    private void participant(long actor,long conversation) {
        require(store.exists("select exists(select 1 from lf_conversations d join lf_claims c on c.id=d.claim_id join lf_listings l on l.id=c.listing_id where d.id=? and ? in(c.claimant_id,l.author_id))",conversation,actor),"Private conversation");
    }
    private Map<String,Object> claimRow(long claim) {
        // Same lock order as PL/pgSQL: listing first, claim second.
        var row=store.one("select c.listing_id from lf_claims c where c.id=?",claim);
        store.one("select id from lf_listings where id=? for update",owner(row,"listing_id"));
        return store.one("select c.listing_id,c.claimant_id,c.state,l.author_id from lf_claims c join lf_listings l on l.id=c.listing_id where c.id=? for update of c",claim);
    }
}
