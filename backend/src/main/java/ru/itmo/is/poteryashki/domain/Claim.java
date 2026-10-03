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
@Table(name = "lf_claims")
public class Claim {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "listing_id", columnDefinition = "bigint")
    private Long listingId;
    @Column(name = "claimant_id", columnDefinition = "bigint")
    private Long claimantId;
    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
