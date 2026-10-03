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
@Table(name = "lf_private_attributes")
public class PrivateAttribute {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "listing_id", columnDefinition = "bigint")
    private Long listingId;
    @Column(name = "name", columnDefinition = "character varying(80)")
    private String name;
    @Column(name = "value", columnDefinition = "text")
    private String value;
}
