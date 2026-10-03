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
@Table(name = "lf_subscriptions")
public class Subscription {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Column(name = "payment_order_id", columnDefinition = "bigint")
    private Long paymentOrderId;
    @Column(name = "starts_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime startsAt;
    @Column(name = "ends_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime endsAt;
}
