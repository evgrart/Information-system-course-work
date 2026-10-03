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
@Table(name = "lf_roles")
public class Role {
    @Id
    @Column(name = "code", columnDefinition = "character varying(16)")
    private String code;
    @Column(name = "title", columnDefinition = "character varying(80)")
    private String title;
}
