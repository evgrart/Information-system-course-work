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
@Table(name = "lf_bids")
public class Bid {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "auction_id", columnDefinition = "bigint")
    private Long auctionId;
    @Column(name = "bidder_id", columnDefinition = "bigint")
    private Long bidderId;
    @Column(name = "request_key", columnDefinition = "uuid")
    private UUID requestKey;
    @Column(name = "amount", columnDefinition = "numeric(12,2)")
    private BigDecimal amount;
    @Column(name = "placed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime placedAt;
}
