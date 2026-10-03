package ru.itmo.is.poteryashki.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class AdministrationService {
    private final SqlStore store;
    private final AccessPolicy access;
    public long complain(long actor,Long listing,Long user,String reason) {
        access.actor(actor,false);
        require((listing==null)!=(user==null) && reason!=null && !reason.isBlank(),"One complaint target and reason required");
        if (listing!=null) require(store.exists("select exists(select 1 from lf_listings l where l.id=? and (l.author_id=? or exists(select 1 from lf_claims c where c.listing_id=l.id and c.claimant_id=?)))",listing,actor,actor)
                || hasPaid(actor),"Subscription or participation required");
        else require(hasPaid(actor),"Subscription required");
        return store.id("insert into lf_complaints(reporter_id,listing_id,reported_user_id,reason) values (?,?,?,?) returning id",actor,listing,user,reason);
    }
    public void reviewComplaint(long moderator,long complaint,boolean resolved,String reason) {
        access.moderator(moderator);
        var row=store.one("select reporter_id,reported_user_id,listing_id from lf_complaints where id=? and state='pending' for update",complaint);
        require(owner(row,"reporter_id")!=moderator && !Objects.equals(row.get("reported_user_id"),moderator),"Independent review required");
        if (row.get("listing_id")!=null) require(!store.exists("select exists(select 1 from lf_listings where id=? and author_id=?)",row.get("listing_id"),moderator),"Independent review required");
        require(reason!=null && !reason.isBlank(),"Resolution required");
        store.update("update lf_complaints set state=?,moderator_id=?,resolution=? where id=?",resolved?"resolved":"rejected",moderator,reason,complaint);
        access.record(moderator,"complaint.reviewed","lf_complaints",complaint,reason);
    }
    public void block(long moderator,long user,String reason) {
        access.moderator(moderator);require(moderator!=user,"Cannot block yourself");
        require(reason!=null && !reason.isBlank(),"Reason required");
        var roles=store.rows("select role_code from lf_user_roles where user_id=? and role_code in('admin','moderator')",user);
        if (!roles.isEmpty()) access.admin(moderator);
        require(store.update("update lf_users set state='blocked' where id=?",user)==1,"User required");
        store.update("update lf_refresh_tokens set revoked_at=clock_timestamp() where user_id=? and revoked_at is null",user);
        access.record(moderator,"user.blocked","lf_users",user,reason);
    }
    public void grantRole(long admin,long user,String role,String reason) {
        access.admin(admin);
        require(reason!=null && !reason.isBlank(),"Reason required");
        if(store.update("insert into lf_user_roles(user_id,role_code) values (?,?) on conflict do nothing",user,role)>0)
            access.record(admin,"role.granted:"+role,"lf_users",user,reason);
    }
    public void revokeRole(long admin,long user,String role,String reason) {
        access.admin(admin);
        require(reason!=null && !reason.isBlank(),"Reason required");
        store.one("select code from lf_roles where code=? for update",role);
        access.admin(admin);
        if(role.equals("admin") && store.exists("select exists(select 1 from lf_user_roles r join lf_users u on u.id=r.user_id where r.user_id=? and r.role_code='admin' and u.state='verified')",user))
            require(store.id("select count(*) from lf_user_roles r join lf_users u on u.id=r.user_id where r.role_code='admin' and u.state='verified'")>1,"Last active administrator cannot be removed");
        if(store.update("delete from lf_user_roles where user_id=? and role_code=?",user,role)>0)
            access.record(admin,"role.revoked:"+role,"lf_users",user,reason);
    }
    public long tariff(long admin,String title,BigDecimal amount,int days) {
        access.admin(admin);long id=store.id("insert into lf_tariffs(title,amount,duration_days) values (?,?,?) returning id",title,amount,days);
        access.record(admin,"tariff.created","lf_tariffs",id,"Создан тариф");return id;
    }
    public void retireTariff(long admin,long tariff) {
        access.admin(admin);require(store.update("update lf_tariffs set active=false where id=?",tariff)==1,"Tariff required");
        access.record(admin,"tariff.retired","lf_tariffs",tariff,"Тариф отключён для новых заказов");
    }
    public void updateOrganization(long admin,long id,String phone,String address,String instructions,String source,LocalDate checked) {
        access.admin(admin);
        require(store.update("update lf_organizations set phone=?,address=?,instructions=?,source_url=?,verified_on=? where id=?",phone,address,instructions,source,checked,id)==1,"Organization required");
        access.record(admin,"organization.updated","lf_organizations",id,"Обновлены справочные сведения");
    }
    public List<Map<String,Object>> audit(long moderator,Long before,int limit) {
        access.moderator(moderator);if (limit<1 || limit>100) throw new IllegalArgumentException("Invalid limit");
        return store.rows("select id,actor_id,action,entity_table,entity_id,details,created_at from lf_audit_entries where (?::bigint is null or id<?) order by id desc limit ?",before,before,limit);
    }
    public List<Map<String,Object>> notifications(long actor) {
        access.actor(actor,false);return store.rows("select id,kind,body,read_at,created_at from lf_notifications where user_id=? order by id desc limit 100",actor);
    }
    public void markRead(long actor,long notification) {
        access.actor(actor,false);require(store.update("update lf_notifications set read_at=coalesce(read_at,clock_timestamp()) where id=? and user_id=?",notification,actor)==1,"Not your notification");
    }
    private boolean hasPaid(long actor) { return store.exists("select exists(select 1 from lf_subscriptions where user_id=? and starts_at<=clock_timestamp() and ends_at>clock_timestamp())",actor); }
}
