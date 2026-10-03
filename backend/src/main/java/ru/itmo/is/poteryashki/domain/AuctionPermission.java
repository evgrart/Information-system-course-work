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
@Table(name = "lf_auction_permissions")
public class AuctionPermission {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "listing_id", columnDefinition = "bigint")
    private Long listingId;
    @Column(name = "applicant_id", columnDefinition = "bigint")
    private Long applicantId;
    @Column(name = "basis", columnDefinition = "character varying(20)")
    private String basis;
    @Column(name = "evidence_object_key", columnDefinition = "character varying(240)")
    private String evidenceObjectKey;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "moderator_id", columnDefinition = "bigint")
    private Long moderatorId;
    @Column(name = "reviewed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime reviewedAt;
    @Column(name = "reason", columnDefinition = "text")
    private String reason;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
