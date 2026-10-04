package ru.itmo.is.poteryashki.service;

import java.security.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.itmo.is.poteryashki.persistence.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class AccountService {
    private final SqlStore store;
    private final DatabaseFunctions functions;
    private final UserRepository users;
    private final AccessPolicy access;
    private final PasswordEncoder passwords;
    public record Registration(long userId,String confirmationToken) { }
    public record Identity(long userId,int tokenVersion) { }
    public Registration register(String email,String name,String password) {
        access.audit(null);
        checkPassword(password);
        email=email.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || name.isBlank()) throw new IllegalArgumentException("Invalid profile");
        long id=store.id("insert into lf_users(email,display_name,password_hash) values (?,?,?) returning id",email,name.trim(),passwords.encode(password));
        store.update("insert into lf_user_roles(user_id,role_code) values (?,'user')",id);
        store.update("insert into lf_verifications(user_id) values (?)",id);
        return new Registration(id,issueToken(id,"email_confirm"));
    }
    public long confirmEmail(String rawToken) { access.audit(null); return functions.consumeToken(hash(rawToken),"email_confirm"); }
    public Identity authenticate(String email,String password) {
        var user=users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new SecurityException("Invalid credentials"));
        require(!user.getState().equals("blocked") && passwords.matches(password,user.getPasswordHash()),"Invalid credentials");
        return new Identity(user.getId(),user.getTokenVersion());
    }
    public void checkIdentity(Identity identity) {
        var user=users.findById(identity.userId()).orElseThrow();
        require(!user.getState().equals("blocked") && user.getTokenVersion()==identity.tokenVersion(),"Identity revoked");
    }
    public String resendConfirmation(long user) {
        access.audit(user);var row=store.one("select email_confirmed_at,state from lf_users where id=? for update",user);
        require(row.get("email_confirmed_at")==null&&!row.get("state").equals("blocked"),"Email already confirmed");
        require(!store.exists("select exists(select 1 from lf_verification_tokens where user_id=? and purpose='email_confirm' and created_at>clock_timestamp()-interval '1 minute')",user),"Wait before requesting another letter");
        return issueToken(user,"email_confirm");
    }
    /** Raw token is delivered only by the mail adapter, never by a public endpoint. */
    public Optional<String> requestPasswordReset(String email) {
        access.audit(null);
        var user=users.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        return user.filter(u -> !u.getState().equals("blocked")).map(u -> issueToken(u.getId(),"password_reset"));
    }
    public void resetPassword(String token,String password) {
        access.audit(null); checkPassword(password);
        long user=functions.consumeToken(hash(token),"password_reset");
        store.update("update lf_users set password_hash=? where id=?",passwords.encode(password),user);
        store.update("update lf_refresh_tokens set revoked_at=clock_timestamp() where user_id=? and revoked_at is null",user);
    }
    public void reviewProfile(long moderator,long verification,boolean approve,String reason) {
        access.moderator(moderator);
        int n=store.update("update lf_verifications set state=?,reviewer_id=?,reason=?,reviewed_at=clock_timestamp() where id=? and state='pending'",
                approve?"approved":"rejected",moderator,reason,verification);
        require(n==1,"Pending verification required");
        access.record(moderator,"profile.reviewed","lf_verifications",verification,reason==null?"Профиль одобрен":reason);
    }
    public long submitProfile(long user) {
        access.audit(user);
        var row=store.one("select state,email_confirmed_at from lf_users where id=? for update",user);
        require(row.get("state").equals("pending") && row.get("email_confirmed_at")!=null,"Confirmed pending profile required");
        require(!store.exists("select exists(select 1 from lf_verifications where user_id=? and state='pending')",user),"Profile already awaiting review");
        return store.id("insert into lf_verifications(user_id) values (?) returning id",user);
    }
    private String issueToken(long user,String purpose) {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.update("insert into lf_verification_tokens(user_id,purpose,token_hash,expires_at) values (?,?,?,clock_timestamp()+interval '1 hour')",user,purpose,hash(token));
        return token;
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void checkPassword(String password) {
        if (password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Password must contain 12–72 UTF-8 bytes");
    }
}
