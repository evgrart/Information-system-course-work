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
@Table(name = "lf_auctions")
public class Auction {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "listing_id", columnDefinition = "bigint")
    private Long listingId;
    @Column(name = "seller_id", columnDefinition = "bigint")
    private Long sellerId;
    @Column(name = "permission_id", columnDefinition = "bigint")
    private Long permissionId;
    @Column(name = "starts_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime startsAt;
    @Column(name = "ends_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime endsAt;
    @Column(name = "start_price", columnDefinition = "numeric(12,2)")
    private BigDecimal startPrice;
    @Column(name = "bid_step", columnDefinition = "numeric(12,2)")
    private BigDecimal bidStep;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "winner_bid_id", columnDefinition = "bigint")
    private Long winnerBidId;
    @Column(name = "closed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime closedAt;
}
