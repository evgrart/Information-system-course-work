package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.SqlStore;
import ru.itmo.is.poteryashki.service.AccountService;
import java.security.SecureRandom;
import java.util.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class WebSessions {
    private final SqlStore store;
    private final AccountService accounts;
    private final LoginThrottle throttle;
    public record Issued(WebIdentity identity, String refreshToken) {}
    public Issued login(String email, String password) {
        throttle.check(email);
        AccountService.Identity identity;
        try { identity = accounts.authenticate(email, password); }
        catch (SecurityException e) { throttle.failed(email); throw e; }
        throttle.success(email);
        return issue(identity.userId());
    }
    public Issued refresh(String raw) {
        var row = store.one("select user_id,id from lf_refresh_tokens where token_hash=? and revoked_at is null and expires_at>clock_timestamp() for update", AccountService.hash(raw));
        store.update("update lf_refresh_tokens set revoked_at=clock_timestamp() where id=?", owner(row, "id"));
        return issue(owner(row, "user_id"));
    }
    private Issued issue(long user) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long id = store.id("insert into lf_refresh_tokens(user_id,token_hash,expires_at) values (?,?,clock_timestamp()+interval '30 days') returning id", user, AccountService.hash(raw));
        int version = ((Number) store.one("select token_version from lf_users where id=?", user).get("token_version")).intValue();
        return new Issued(identity(user, id, version), raw);
    }
    @Transactional(readOnly=true)
    public WebIdentity identity(long user, long session, int version) {
        var row = store.one("select u.token_version from lf_users u join lf_refresh_tokens r on r.user_id=u.id where u.id=? and r.id=? and u.state<>'blocked' and r.revoked_at is null and r.expires_at>clock_timestamp()", user, session);
        require(((Number) row.get("token_version")).intValue() == version, "Identity revoked");
        var roles = new HashSet<String>();
        for (var role : store.rows("select role_code from lf_user_roles where user_id=?", user)) roles.add(role.get("role_code").toString());
        return new WebIdentity(user, session, version, Set.copyOf(roles));
    }
    public void logout(WebIdentity identity) {
        store.update("update lf_refresh_tokens set revoked_at=clock_timestamp() where id=? and user_id=?", identity.sessionId(), identity.userId());
    }
    public void logoutToken(String raw) {
        if (raw != null && !raw.isBlank()) store.update("update lf_refresh_tokens set revoked_at=clock_timestamp() where token_hash=? and revoked_at is null", AccountService.hash(raw));
    }
}
