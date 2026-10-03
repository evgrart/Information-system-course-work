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
@Table(name = "lf_notifications")
public class Notification {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Column(name = "kind", columnDefinition = "character varying(40)")
    private String kind;
    @Column(name = "body", columnDefinition = "text")
    private String body;
    @Column(name = "read_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime readAt;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
