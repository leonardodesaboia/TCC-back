package com.allset.api.chat.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends PostgresEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "professional_user_id", nullable = false, updatable = false)
    private UUID professionalUserId;

    // Sem @OneToMany de messages — paginação sempre via MessageRepository
}
