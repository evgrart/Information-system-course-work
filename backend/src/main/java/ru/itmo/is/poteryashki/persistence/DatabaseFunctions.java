package ru.itmo.is.poteryashki.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Each method invokes the canonical stage-two PL/pgSQL operation. */
@Repository
@RequiredArgsConstructor
public class DatabaseFunctions {
    private final JdbcTemplate jdbc;
    private final SqlNames names;
    private final EntityManager entities;
    public void assertUser(long user, boolean subscription) { call("select lf_assert_user(?,?)", user, subscription); }
    public void assertModerator(long user) { call("select lf_assert_moderator(?)", user); }
    public long consumeToken(String hash, String purpose) { return number("select lf_consume_token(?,?)",hash,purpose); }
    public long activateSubscription(long order, String event, BigDecimal amount, String currency) {
        return number("select lf_activate_subscription(?,?,?,?::char(3))",order,event,amount,currency);
    }
    public long reserve(long claim, long finder) { return number("select lf_reserve_found(?,?)",claim,finder); }
    public boolean confirm(long transfer, long actor) {
        boolean result=Boolean.TRUE.equals(jdbc.queryForObject(names.sql("select lf_confirm_transfer(?,?)"),Boolean.class,transfer,actor));
        entities.clear();return result;
    }
    public void resolveReturn(long transfer,long moderator,String reason) { call("select lf_resolve_return(?,?,?)",transfer,moderator,reason); }
    public long bid(long auction,long bidder,BigDecimal amount,UUID key) { return number("select lf_place_bid(?,?,?,?)",auction,bidder,amount,key); }
    public Long finalizeAuction(long auction) { return number("select lf_finalize_auction(?)",auction); }
    public void closeDue(int limit) { jdbc.update(names.sql("call lf_close_due_auctions(?)"),limit);entities.clear(); }
    public List<Map<String,Object>> search(String text,String kind,Long category,Long location,
            OffsetDateTime from,OffsetDateTime to,Long before,int limit) {
        return jdbc.queryForList(names.sql("select * from lf_search_listings(?,?::varchar,?,?,?,?,?,?)"),
                text,kind,category,location,from,to,before,limit);
    }
    private Long number(String sql,Object... args) { Long result=jdbc.queryForObject(names.sql(sql),Long.class,args);entities.clear();return result; }
    public List<Map<String,Object>> searchByState(String text,String kind,Long category,Long location,OffsetDateTime from,OffsetDateTime to,Long before,int limit,String state) {
        return jdbc.queryForList(names.sql("select * from lf_search_listings_state(?,?::varchar,?,?,?,?,?,?,?::varchar)"),text,kind,category,location,from,to,before,limit,state);
    }
    private void call(String sql,Object... args) { jdbc.query(names.sql(sql),rs -> { },args);entities.clear(); }
}
