package com.allset.api.favorite.service;

import com.allset.api.favorite.dto.FavoriteProfessionalResponse;
import com.allset.api.favorite.dto.FavoriteStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FavoriteProfessionalService {

    FavoriteProfessionalResponse favorite(UUID clientId, UUID professionalId);

    Page<FavoriteProfessionalResponse> list(UUID clientId, Pageable pageable);

    FavoriteStatusResponse status(UUID clientId, UUID professionalId);

    void unfavorite(UUID clientId, UUID professionalId);
}
