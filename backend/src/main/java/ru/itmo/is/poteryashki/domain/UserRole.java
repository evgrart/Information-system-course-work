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
@Table(name = "lf_user_roles")
@IdClass(UserRoleKey.class)
public class UserRole {
    @Id
    @Column(name = "user_id", columnDefinition = "bigint")
    private Long userId;
    @Id
    @Column(name = "role_code", columnDefinition = "character varying(16)")
    private String roleCode;
}
