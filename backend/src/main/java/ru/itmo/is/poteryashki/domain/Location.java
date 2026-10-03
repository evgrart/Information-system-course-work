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
@Table(name = "lf_locations")
public class Location {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "city", columnDefinition = "character varying(80)")
    private String city;
    @Column(name = "description", columnDefinition = "character varying(240)")
    private String description;
    @Column(name = "latitude", columnDefinition = "numeric(9,6)")
    private BigDecimal latitude;
    @Column(name = "longitude", columnDefinition = "numeric(9,6)")
    private BigDecimal longitude;
    @Column(name = "metro_station", columnDefinition = "character varying(100)")
    private String metroStation;
    @Column(name = "organization_id", columnDefinition = "bigint")
    private Long organizationId;
}
