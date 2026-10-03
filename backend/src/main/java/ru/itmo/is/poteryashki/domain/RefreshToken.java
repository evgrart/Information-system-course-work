package ru.itmo.is.poteryashki.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Immutable @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "lf_refresh_tokens")
public class RefreshToken {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Column(name = "token_hash", columnDefinition = "character varying(64)")
    private String tokenHash;
    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime expiresAt;
    @Column(name = "revoked_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime revokedAt;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
