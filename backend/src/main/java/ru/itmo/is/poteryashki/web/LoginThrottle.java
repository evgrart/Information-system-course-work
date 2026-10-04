package ru.itmo.is.poteryashki.web;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import ru.itmo.is.poteryashki.service.AccountService;

@Component
public class LoginThrottle {
    private record Window(Instant start, int failures) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private String key(String email) { return AccountService.hash(email.trim().toLowerCase(java.util.Locale.ROOT)); }
    public void check(String email) {
        var window = windows.get(key(email));
        if (window != null && window.start.plusSeconds(900).isAfter(Instant.now()) && window.failures >= 5)
            throw new SecurityException("Too many login attempts");
    }
    public void failed(String email) {
        Instant now = Instant.now();
        windows.entrySet().removeIf(e -> e.getValue().start.plusSeconds(900).isBefore(now));
        if (windows.size() >= 10000 && !windows.containsKey(key(email))) throw new SecurityException("Too many login attempts");
        windows.compute(key(email), (k, old) -> new Window(old == null ? now : old.start, old == null ? 1 : old.failures + 1));
    }
    public void success(String email) { windows.remove(key(email)); }
}
