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
@Table(name = "lf_conversations")
public class Conversation {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "claim_id", columnDefinition = "bigint")
    private Long claimId;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
