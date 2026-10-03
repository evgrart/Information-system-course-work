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
@Table(name = "lf_verification_tokens")
public class VerificationToken {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Column(name = "purpose", columnDefinition = "character varying(16)")
    private String purpose;
    @Column(name = "token_hash", columnDefinition = "character varying(64)")
    private String tokenHash;
    @Column(name = "expires_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime expiresAt;
    @Column(name = "consumed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime consumedAt;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
