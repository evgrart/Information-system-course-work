package ru.itmo.is.poteryashki.web;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration @ConditionalOnWebApplication
public class WebSecurity {
    @Bean SecurityFilterChain webSecurity(HttpSecurity http, WebTokens tokens, WebSessions sessions) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.csrfTokenRepository(csrf))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/auth/**", "/assets/**", "/error", "/metro").permitAll()
                        .requestMatchers("/staff/**").hasAnyRole("MODERATOR", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> response.sendRedirect("/auth/login")))
                .addFilterBefore(new CookieAuthentication(tokens, sessions), UsernamePasswordAuthenticationFilter.class)
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")))
                .logout(l -> l.disable());
        return http.build();
    }
}
