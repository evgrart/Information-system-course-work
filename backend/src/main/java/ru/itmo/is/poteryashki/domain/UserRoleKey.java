package ru.itmo.is.poteryashki.domain;
import lombok.*;
import java.io.Serializable;
@Data @NoArgsConstructor
public class UserRoleKey implements Serializable {
    private Long userId;
    private String roleCode;
}
