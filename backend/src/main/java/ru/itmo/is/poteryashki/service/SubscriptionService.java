package ru.itmo.is.poteryashki.service;

import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class SubscriptionService {
    private final SqlStore store;
    private final DatabaseFunctions functions;
    private final AccessPolicy access;
    public long createOrder(long actor,long tariff,UUID key) {
        // Serializes retries for this user; the database also enforces request-key uniqueness.
        store.one("select id from lf_users where id=? for update",actor);
        access.actor(actor,false);
        var old=store.rows("select id,user_id,tariff_id from lf_payment_orders where request_key=?",key);
        if (!old.isEmpty()) {
            var order=old.get(0);require(owner(order,"user_id")==actor && owner(order,"tariff_id")==tariff,"Request key conflict");
            return owner(order,"id");
        }
        return store.id("insert into lf_payment_orders(user_id,tariff_id,request_key,amount,duration_days) select ?,id,?,amount,duration_days from lf_tariffs where id=? and active returning id",actor,key,tariff);
    }
    /** Internal demo adapter derives amount from stored order, with ownership checked. */
    public long payDemo(long actor,long order,UUID event) {
        store.one("select id from lf_users where id=? for update",actor);
        access.actor(actor,false);
        var row=store.one("select user_id,amount,currency from lf_payment_orders where id=?",order);
        require(owner(row,"user_id")==actor,"Not your payment order");
        return functions.activateSubscription(order,"demo:"+event,(BigDecimal)row.get("amount"),row.get("currency").toString());
    }
    public List<Map<String,Object>> periods(long actor) {
        access.actor(actor,false);
        return store.rows("select starts_at,ends_at from lf_subscriptions where user_id=? order by starts_at",actor);
    }
}
