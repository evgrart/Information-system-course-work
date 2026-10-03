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
@Table(name = "lf_users")
public class User {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "email", columnDefinition = "character varying(254)")
    private String email;
    @Column(name = "display_name", columnDefinition = "character varying(80)")
    private String displayName;
    @Column(name = "password_hash", columnDefinition = "character varying(100)")
    private String passwordHash;
    @Column(name = "state", columnDefinition = "character varying(12)")
    private String state;
    @Column(name = "email_confirmed_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime emailConfirmedAt;
    @Column(name = "token_version", columnDefinition = "integer")
    private Integer tokenVersion;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
