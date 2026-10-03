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
@Table(name = "lf_tariffs")
public class Tariff {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "title", columnDefinition = "character varying(80)")
    private String title;
    @Column(name = "amount", columnDefinition = "numeric(12,2)")
    private BigDecimal amount;
    @Column(name = "duration_days", columnDefinition = "integer")
    private Integer durationDays;
    @Column(name = "active", columnDefinition = "boolean")
    private Boolean active;
}
