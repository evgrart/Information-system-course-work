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
@Table(name = "lf_verifications")
public class Verification {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "reviewer_id", columnDefinition = "bigint")
    private Long reviewerId;
    @Column(name = "reason", columnDefinition = "text")
    private String reason;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
    @Column(name = "reviewed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime reviewedAt;
}
