package com.allset.api.user.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import com.allset.api.shared.crypto.CpfConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends PostgresEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Convert(converter = CpfConverter.class)
    @Column(nullable = false, unique = true)
    private String cpf;

    @Column(name = "cpf_hash", nullable = false, unique = true, length = 64)
    private String cpfHash;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "user_role", nullable = false)
    private UserRole role;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "ban_reason")
    private String banReason;
}
