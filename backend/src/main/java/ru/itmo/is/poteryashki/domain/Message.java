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
@Table(name = "lf_messages")
public class Message {
    @Id
    @Column(name = "id", columnDefinition = "bigint")
    private Long id;
    @Column(name = "conversation_id", columnDefinition = "bigint")
    private Long conversationId;
    @Column(name = "sender_id", columnDefinition = "bigint")
    private Long senderId;
    @Column(name = "body", columnDefinition = "text")
    private String body;
    @Column(name = "sent_at", columnDefinition = "timestamp with time zone")
    private OffsetDateTime sentAt;
}
