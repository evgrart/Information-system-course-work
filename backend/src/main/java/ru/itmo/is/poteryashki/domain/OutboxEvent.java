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
@Table(name = "lf_outbox_events")
public class OutboxEvent {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "kind", columnDefinition = "character varying(40)")
    private String kind;
    @Column(name = "aggregate_id", columnDefinition = "bigint")
    private Long aggregateId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;
    @Column(name = "attempts", columnDefinition = "integer")
    private Integer attempts;
    @Column(name = "next_attempt_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime nextAttemptAt;
    @Column(name = "delivered_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime deliveredAt;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
