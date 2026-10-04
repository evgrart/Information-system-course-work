package ru.itmo.is.poteryashki.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.itmo.is.poteryashki.persistence.*;

@Component
@RequiredArgsConstructor
public class AccessPolicy {
    private final SqlStore store;
    private final DatabaseFunctions functions;
    private final SqlNames names;
    public void actor(long user,boolean paid) { audit(user); functions.assertUser(user,paid); }
    /** Own account information remains available while independent verification is pending. */
    public void account(long user) { audit(user);require(store.exists("select exists(select 1 from lf_users where id=? and state<>'blocked')",user),"Account unavailable"); }
    public void moderator(long user) { audit(user); functions.assertModerator(user); }
    public void admin(long user) {
        actor(user,false);
        require(store.exists("select exists(select 1 from lf_user_roles where user_id=? and role_code='admin')",user),"Administrator required");
    }
    public void audit(Long user) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Transaction required");
        store.one("select set_config('lf.actor_id', ?, true) as actor",user == null ? "" : user.toString());
    }
    public void record(long actor,String action,String table,long id,String reason) {
        require(reason!=null && !reason.isBlank(),"Reason required");
        store.update("insert into lf_audit_entries(actor_id,action,entity_table,entity_id,details) values (?,?,?,?,jsonb_build_object('reason',?::text))",
                actor,action,names.sql(table),id,reason);
    }
    public static void require(boolean condition,String message) { if (!condition) throw new SecurityException(message); }
    public static long owner(java.util.Map<String,Object> row,String column) { return ((Number)row.get(column)).longValue(); }
}
