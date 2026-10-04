package ru.itmo.is.poteryashki.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.util.*;

public class CookieAuthentication extends OncePerRequestFilter {
    private final WebTokens tokens;
    private final WebSessions sessions;
    public CookieAuthentication(WebTokens tokens, WebSessions sessions) { this.tokens = tokens; this.sessions = sessions; }
    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(name)).map(Cookie::getValue).findFirst().orElse(null);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String raw = cookie(request, "LF_ACCESS");
        if (raw != null) try {
            var jwt = tokens.decode(raw);
            var identity = sessions.identity(Long.parseLong(jwt.getSubject()), ((Number) jwt.getClaim("sid")).longValue(), ((Number) jwt.getClaim("ver")).intValue());
            var authorities = identity.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase(Locale.ROOT))).toList();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, authorities));
        } catch (org.springframework.security.oauth2.jwt.JwtException | SecurityException | NoSuchElementException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
