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
@Table(name = "lf_listing_images")
public class ListingImage {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "listing_id", columnDefinition = "bigint")
    private Long listingId;
    @Column(name = "object_key", columnDefinition = "character varying(240)")
    private String objectKey;
    @Column(name = "media_type", columnDefinition = "character varying(20)")
    private String mediaType;
    @Column(name = "size_bytes", columnDefinition = "integer")
    private Integer sizeBytes;
    @Column(name = "position", columnDefinition = "smallint")
    private Short position;
}
