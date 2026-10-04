package ru.itmo.is.poteryashki.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.SqlStore;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

/** Atomic delivery into the internal inbox. SMTP is an independent later adapter. */
@Service @RequiredArgsConstructor @Transactional
public class OutboxService {
    private final SqlStore store;
    private final AccessPolicy access;
    public int deliver(int limit) {
        if(limit<1 || limit>100) throw new IllegalArgumentException("Invalid delivery batch");
        access.audit(null);
        store.update("insert into lf_outbox_events(kind,aggregate_id,payload) select 'subscription.expired',s.id,jsonb_build_object('user_id',s.user_id) from lf_subscriptions s where s.ends_at<=clock_timestamp() and not exists(select 1 from lf_subscriptions n where n.user_id=s.user_id and n.ends_at>s.ends_at) and not exists(select 1 from lf_outbox_events e where e.kind='subscription.expired' and e.aggregate_id=s.id) on conflict do nothing");
        var events=store.rows("select id,kind,aggregate_id,payload::text from lf_outbox_events where delivered_at is null and next_attempt_at<=clock_timestamp() order by id for update skip locked limit ?",limit);
        for(var event:events) {
            long aggregate=owner(event,"aggregate_id");String kind=event.get("kind").toString();
            List<Map<String,Object>> recipients=switch(kind) {
                // The subscription function already inserts an inbox notification.
                case "subscription.activated" -> List.of();
                case "subscription.expired" -> store.rows("select user_id from lf_subscriptions where id=?",aggregate);
                case "message.sent" -> store.rows("select distinct user_id from (select c.claimant_id as user_id from lf_messages m join lf_conversations d on d.id=m.conversation_id join lf_claims c on c.id=d.claim_id where m.id=? and c.claimant_id<>m.sender_id union select l.author_id from lf_messages m join lf_conversations d on d.id=m.conversation_id join lf_claims c on c.id=d.claim_id join lf_listings l on l.id=c.listing_id where m.id=? and l.author_id<>m.sender_id) r",aggregate,aggregate);
                case "claim.created" -> store.rows("select l.author_id as user_id from lf_claims c join lf_listings l on l.id=c.listing_id where c.id=?",aggregate);
                case "listing.reviewed" -> store.rows("select author_id as user_id from lf_listings where id=?",aggregate);
                case "found.reserved","found.returned" -> store.rows("select distinct user_id from (select author_id as user_id from lf_listings where id=? union select claimant_id from lf_claims where listing_id=? and state in('accepted','fulfilled')) r",aggregate,aggregate);
                case "auction.bid","auction.finished" -> store.rows("select distinct user_id from (select seller_id as user_id from lf_auctions where id=? union select bidder_id from lf_bids where auction_id=?) r",aggregate,aggregate);
                default -> throw new IllegalStateException("Unsupported outbox event: "+kind);
            };
            String body=switch(kind){case "subscription.expired"->"Подписка закончилась. Продлите её в кабинете.";case "message.sent"->"Новое сообщение в диалоге по заявке.";case "listing.reviewed"->"Объявление №"+aggregate+" проверено модератором.";case "claim.created"->"Поступила заявка на вашу находку.";case "found.reserved"->"Передача вещи согласована.";case "found.returned"->"Передача вещи завершена.";case "auction.bid"->"В аукционе №"+aggregate+" принята новая ставка.";case "auction.finished"->"Аукцион №"+aggregate+" завершён.";default->"Подписка активирована.";};
            for(var recipient:recipients) store.update("insert into lf_notifications(user_id,kind,body) values (?,?,?)",owner(recipient,"user_id"),kind,body);
            store.update("update lf_outbox_events set delivered_at=clock_timestamp(),attempts=attempts+1 where id=?",owner(event,"id"));
        }
        return events.size();
    }
}
