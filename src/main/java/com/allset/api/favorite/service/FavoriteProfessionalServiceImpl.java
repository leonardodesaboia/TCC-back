package com.allset.api.favorite.service;

import com.allset.api.favorite.domain.FavoriteProfessional;
import com.allset.api.favorite.dto.FavoriteProfessionalResponse;
import com.allset.api.favorite.dto.FavoriteStatusResponse;
import com.allset.api.favorite.exception.FavoriteProfessionalAlreadyExistsException;
import com.allset.api.favorite.exception.FavoriteProfessionalNotFoundException;
import com.allset.api.favorite.mapper.FavoriteProfessionalMapper;
import com.allset.api.favorite.repository.FavoriteProfessionalRepository;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FavoriteProfessionalServiceImpl implements FavoriteProfessionalService {

    private final FavoriteProfessionalRepository favoriteProfessionalRepository;
    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final FavoriteProfessionalMapper favoriteProfessionalMapper;

    @Override
    public FavoriteProfessionalResponse favorite(UUID clientId, UUID professionalId) {
        validateClient(clientId);
        validateProfessional(professionalId);

        if (favoriteProfessionalRepository.existsByClientIdAndProfessionalId(clientId, professionalId)) {
            throw new FavoriteProfessionalAlreadyExistsException(clientId, professionalId);
        }

        FavoriteProfessional favorite = FavoriteProfessional.builder()
                .clientId(clientId)
                .professionalId(professionalId)
                .build();

        return favoriteProfessionalMapper.toResponse(favoriteProfessionalRepository.save(favorite));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FavoriteProfessionalResponse> list(UUID clientId, Pageable pageable) {
        validateClient(clientId);
        return favoriteProfessionalRepository.findAllActiveByClientId(clientId, pageable)
                .map(favoriteProfessionalMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(UUID clientId, UUID professionalId) {
        validateClient(clientId);
        validateProfessional(professionalId);

        boolean favorite = favoriteProfessionalRepository.existsByClientIdAndProfessionalId(clientId, professionalId);
        return new FavoriteStatusResponse(professionalId, favorite);
    }

    @Override
    public void unfavorite(UUID clientId, UUID professionalId) {
        validateClient(clientId);
        validateProfessional(professionalId);

        FavoriteProfessional favorite = favoriteProfessionalRepository
                .findByClientIdAndProfessionalId(clientId, professionalId)
                .orElseThrow(() -> new FavoriteProfessionalNotFoundException(clientId, professionalId));

        favoriteProfessionalRepository.delete(favorite);
    }

    private void validateClient(UUID clientId) {
        User client = userRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> new UserNotFoundException(clientId));

        if (client.getRole() != UserRole.client) {
            throw new AccessDeniedException("Apenas clientes podem gerenciar profissionais favoritos");
        }
    }

    private void validateProfessional(UUID professionalId) {
        professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));
    }
}
