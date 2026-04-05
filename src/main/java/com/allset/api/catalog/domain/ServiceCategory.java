package com.allset.api.catalog.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "service_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCategory extends PostgresEntity {

    @Column(name = "area_id", nullable = false, updatable = false)
    private UUID areaId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "icon_url")
    private String iconUrl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
