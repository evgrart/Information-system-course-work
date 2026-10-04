package ru.itmo.is.poteryashki.web;

import java.util.Set;

public record WebIdentity(long userId, long sessionId, int tokenVersion, Set<String> roles) {
    public boolean moderator() { return roles.contains("moderator") || roles.contains("admin"); }
    public boolean admin() { return roles.contains("admin"); }
}
