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
@Table(name = "lf_complaints")
public class Complaint {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "reporter_id", columnDefinition = "bigint")
    private Long reporterId;
    @Column(name = "listing_id", columnDefinition = "bigint")
    private Long listingId;
    @Column(name = "reported_user_id", columnDefinition = "bigint")
    private Long reportedUserId;
    @Column(name = "reason", columnDefinition = "text")
    private String reason;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "moderator_id", columnDefinition = "bigint")
    private Long moderatorId;
    @Column(name = "resolution", columnDefinition = "text")
    private String resolution;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
