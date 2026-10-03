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
@Table(name = "lf_payment_orders")
public class PaymentOrder {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Column(name = "tariff_id", columnDefinition = "bigint")
    private Long tariffId;
    @Column(name = "request_key", columnDefinition = "uuid")
    private UUID requestKey;
    @Column(name = "amount", columnDefinition = "numeric(12,2)")
    private BigDecimal amount;
    @Column(name = "duration_days", columnDefinition = "integer")
    private Integer durationDays;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", columnDefinition = "character(3)")
    private String currency;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
    @Column(name = "paid_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime paidAt;
}
