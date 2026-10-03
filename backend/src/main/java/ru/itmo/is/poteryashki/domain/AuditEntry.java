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
@Table(name = "lf_audit_entries")
public class AuditEntry {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "actor_id", columnDefinition = "bigint")
    private Long actorId;
    @Column(name = "action", columnDefinition = "character varying(80)")
    private String action;
    @Column(name = "entity_table", columnDefinition = "character varying(80)")
    private String entityTable;
    @Column(name = "entity_id", columnDefinition = "bigint")
    private Long entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;
    @Column(name = "created_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
