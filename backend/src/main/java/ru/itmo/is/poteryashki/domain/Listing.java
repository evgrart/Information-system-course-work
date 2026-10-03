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
@Table(name = "lf_listings")
public class Listing {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "author_id", columnDefinition = "bigint")
    private Long authorId;
    @Column(name = "category_id", columnDefinition = "bigint")
    private Long categoryId;
    @Column(name = "location_id", columnDefinition = "bigint")
    private Long locationId;
    @Column(name = "kind", columnDefinition = "character varying(5)")
    private String kind;
    @Column(name = "title", columnDefinition = "character varying(120)")
    private String title;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Column(name = "event_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime eventAt;
    @Column(name = "event_until", columnDefinition = "timestamp with time zone")
    private OffsetDateTime eventUntil;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "custodian_org_id", columnDefinition = "bigint")
    private Long custodianOrgId;
    @Column(name = "official_reported_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime officialReportedAt;
    @Column(name = "moderator_id", columnDefinition = "bigint")
    private Long moderatorId;
    @Column(name = "moderated_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime moderatedAt;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
    @Transient
    private String searchVector;
}
