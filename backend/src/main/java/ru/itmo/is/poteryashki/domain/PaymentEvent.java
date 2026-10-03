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
@Table(name = "lf_payment_events")
public class PaymentEvent {
    @Id
    @Column(name = "provider_event_id", columnDefinition = "character varying(100)")
    private String providerEventId;
    @Column(name = "order_id", columnDefinition = "bigint")
    private Long orderId;
    @Column(name = "amount", columnDefinition = "numeric(12,2)")
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", columnDefinition = "character(3)")
    private String currency;
    @Column(name = "received_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime receivedAt;
}
