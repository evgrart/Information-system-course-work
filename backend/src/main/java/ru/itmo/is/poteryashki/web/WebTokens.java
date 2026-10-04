package ru.itmo.is.poteryashki.web;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Component
public class WebTokens {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final boolean secure;
    public WebTokens(@Value("${app.jwt-secret}") String configured, @Value("${app.cookie-secure}") boolean secure) {
        byte[] key;
        if (configured.isBlank()) { key = new byte[32]; new SecureRandom().nextBytes(key); }
        else { key = Base64.getDecoder().decode(configured); if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET requires at least 32 random bytes in Base64"); }
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var jwtDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("poteryashki"));
        decoder = jwtDecoder; this.secure = secure;
    }
    public Jwt decode(String token) { return decoder.decode(token); }
    public String access(WebIdentity identity) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("poteryashki").subject(Long.toString(identity.userId()))
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("sid", identity.sessionId()).claim("ver", identity.tokenVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    public void issue(HttpServletResponse response, WebSessions.Issued issued) {
        cookie(response, "LF_ACCESS", access(issued.identity()), 900);
        cookie(response, "LF_REFRESH", issued.refreshToken(), 30 * 86400);
        cookie(response, "XSRF-TOKEN", "", 0);
    }
    public void clear(HttpServletResponse response) { cookie(response, "LF_ACCESS", "", 0); cookie(response, "LF_REFRESH", "", 0); cookie(response, "XSRF-TOKEN", "", 0); }
    private void cookie(HttpServletResponse response, String name, String value, int age) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }
}
