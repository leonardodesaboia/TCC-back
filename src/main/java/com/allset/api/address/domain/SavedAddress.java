package com.allset.api.address.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "saved_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedAddress extends PostgresEntity {

    /**
     * FK para o usuário dono do endereço.
     * Armazenado como UUID simples para manter o módulo address desacoplado
     * do módulo user no nível de entidade JPA.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 60)
    private String label;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 80)
    private String complement;

    @Column(length = 80)
    private String district;

    @Column(nullable = false, length = 80)
    private String city;

    /** Sigla do estado — exatamente 2 caracteres (ex: SP, RJ). */
    @Column(nullable = false, length = 2)
    private String state;

    @Column(name = "zip_code", nullable = false, length = 9)
    private String zipCode;

    /** Latitude geográfica. NUMERIC(9,6) = até ±999.999999°. */
    @Column(precision = 9, scale = 6)
    private BigDecimal lat;

    /** Longitude geográfica. NUMERIC(9,6) = até ±999.999999°. */
    @Column(precision = 9, scale = 6)
    private BigDecimal lng;

    /**
     * Indica se este é o endereço padrão do usuário.
     * Invariante: apenas um endereço por usuário pode ter isDefault=true.
     * O service garante isso via bulk update antes de setar o novo padrão.
     */
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
}
