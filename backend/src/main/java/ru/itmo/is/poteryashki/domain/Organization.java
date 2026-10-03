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
@Table(name = "lf_organizations")
public class Organization {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "name", columnDefinition = "character varying(160)")
    private String name;
    @Column(name = "phone", columnDefinition = "character varying(40)")
    private String phone;
    @Column(name = "address", columnDefinition = "text")
    private String address;
    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;
    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;
    @Column(name = "verified_on", columnDefinition = "date")
    private LocalDate verifiedOn;
}
