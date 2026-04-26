package com.allset.api.catalog.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_areas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceArea extends PostgresEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "icon_url")
    private String iconKey;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
